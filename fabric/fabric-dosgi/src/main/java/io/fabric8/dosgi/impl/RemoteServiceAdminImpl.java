/**
 *  Copyright 2016 SEEBURGER AG
 *
 *  SEEBURGER licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.dosgi.impl;

import static org.osgi.service.remoteserviceadmin.RemoteConstants.*;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.dosgi.io.ClientInvoker;
import io.fabric8.dosgi.io.ServerInvoker;
import io.fabric8.dosgi.util.AriesFrameworkUtil;
import io.fabric8.dosgi.util.Utils;
import io.fabric8.dosgi.util.UuidGenerator;


@Component
@Service(value=RemoteServiceAdmin.class)
public class RemoteServiceAdminImpl implements RemoteServiceAdmin, ServiceListener
{

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteServiceAdminImpl.class);
    public static final String CONFIG = "fabric-dosgi";
    public static final String FABRIC_ADDRESS = "fabric.address";

    private final Map<ServiceReference, ExportRegistration> exportedServices = new ConcurrentHashMap<>();

    private final Map<Long, ImportRegistrationImpl> importedServices = new ConcurrentHashMap<>();
    private BundleContext bundleContext;
    private String uuid;
    private final String uri;
    private String exportedAddress;

    private final long timeout;

    @Reference(policy=ReferencePolicy.DYNAMIC,cardinality=ReferenceCardinality.MANDATORY_UNARY/*,bind="bindClientInvoker",unbind="unbindClientInvoker"*/)
    volatile ClientInvoker client;

    @Reference(policy=ReferencePolicy.DYNAMIC,cardinality=ReferenceCardinality.MANDATORY_UNARY/*,bind="bindServerInvoker",unbind="unbindServerInvoker"*/)
    volatile ServerInvoker server;

    @Reference(referenceInterface=EndpointListener.class,cardinality=ReferenceCardinality.OPTIONAL_MULTIPLE,bind="addEndpointListener",unbind="removeEndpointListener")
    private List<EndpointListener> endpointListeners;
    private EndpointListener endpointTracker;

    private static final String EXPORTED_SERVICE_FILTER = "(" + RemoteConstants.SERVICE_EXPORTED_INTERFACES + "=*)";


    public RemoteServiceAdminImpl()
    {
        uri = "tcp://0.0.0.0:2543";
        exportedAddress = "localhost";
        try
        {
            exportedAddress = InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e)
        {
            LOGGER.warn("Failed to resolve canonical hostname. Using localhost as export address",e);
        }
        timeout = TimeUnit.MINUTES.toMillis(5);
        endpointListeners = new CopyOnWriteArrayList<>();
    }

    @org.apache.felix.scr.annotations.Activate
    public void init(BundleContext bundleContext)
    {
        // UUID
        this.bundleContext = bundleContext;
        this.uuid = Utils.getUUID(bundleContext);
        try
        {
            bundleContext.addServiceListener(this, EXPORTED_SERVICE_FILTER);
        }
        catch (InvalidSyntaxException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }


    @Deactivate
    public void destroy() throws IOException {
        bundleContext.removeServiceListener(this);
        for (ImportRegistrationImpl registration : this.importedServices.values()) {
                registration.close();
                registration.getImportedService().unregister();
        }
        for (ServiceReference reference : this.exportedServices.keySet()) {
            unExportService(reference);
        }
//        this.server.stop();
//        this.client.stop();
//        if(tree!=null)
//        {
//            this.tree.close();
//        }
//        if (clientService != null) {
//            this.clientService.unregister();
//        }
//        this.bundleContext.removeServiceListener(this);
    }

    public void addEndpointListener(EndpointListener listener)
    {
        endpointListeners.add(listener);
    }

    public void removeEndpointListener(EndpointListener listener)
    {
        endpointListeners.remove(listener);
    }

    protected void fireEndpointAdded(EndpointDescription endpoint)
    {
        for (EndpointListener endpointListener : endpointListeners)
        {
            //TODO: evaluate scope filter
            if(endpointListener!=this)
                endpointListener.endpointAdded(endpoint, "TODO");
        }
    }

    protected void fireEndpointRemoved(EndpointDescription endpoint)
    {
        for (EndpointListener endpointListener : endpointListeners)
        {
            // TODO: evaluate scope filter
            if(endpointListener!=this)
                endpointListener.endpointRemoved(endpoint, "TODO");
        }
    }

    /**
     * @see org.osgi.service.remoteserviceadmin.RemoteServiceAdmin#exportService(org.osgi.framework.ServiceReference, java.util.Map)
     */
    @Override
    public Collection<ExportRegistration> exportService(ServiceReference reference, Map<String, ? > properties)
    {
        if(properties==null)
            properties = new HashMap<>();
        if (!exportedServices.containsKey(reference)) {
            try {
                ExportRegistration registration = doExportService(reference, properties);
                if (registration != null) {
                    exportedServices.put(reference, registration);
                    return Collections.singleton(registration);
                }
            } catch (Exception e) {
                LOGGER.info("Error when exporting endpoint", e);
            }
        }
        else
            return Collections.singleton(exportedServices.get(reference));
        return Collections.emptyList();
    }

    /**
     * called by {@link ExportRegistrationImpl#close()}
     * @param reference
     */
    protected void unExportService(final ServiceReference reference) {
        try {
            ExportRegistration registration = exportedServices.remove(reference);
            if(registration!=null)
                fireEndpointRemoved(registration.getExportReference().getExportedEndpoint());

        } catch (Exception e) {
            LOGGER.info("Error when unexporting endpoint", e);
        }
    }

    /**
     * called by {@link ImportRegistrationImpl#close()}
     * @param reference
     */
    protected void unImportService(final ImportRegistrationImpl registration) {
        try {
            importedServices.remove(registration.getImportReference().getImportedEndpoint().getServiceId());
        } catch (Exception e) {
            LOGGER.info("Error when unexporting endpoint", e);
        }
    }


    /**
     * @see org.osgi.service.remoteserviceadmin.RemoteServiceAdmin#importService(org.osgi.service.remoteserviceadmin.EndpointDescription)
     */
    @Override
    public ImportRegistration importService(EndpointDescription endpoint)
    {
        return doImportService(endpoint);
    }


    /**
     * @see org.osgi.service.remoteserviceadmin.RemoteServiceAdmin#getExportedServices()
     */
    @Override
    public Collection<ExportReference> getExportedServices()
    {
        Collection<ExportRegistration> values = exportedServices.values();
        Collection<ExportReference> references = new ArrayList<>(values.size());
        for (ExportRegistration registration : values)
        {
            if(registration.getExportReference()!=null)
                references.add(registration.getExportReference());
        }
        return references;
    }


    /**
     * @see org.osgi.service.remoteserviceadmin.RemoteServiceAdmin#getImportedEndpoints()
     */
    @Override
    public Collection<ImportReference> getImportedEndpoints()
    {

        Collection<ImportReference> references = new ArrayList<>();

        Collection<ImportRegistrationImpl> registrations = importedServices.values();
        for (ImportRegistration registration : registrations)
        {
            if(registration.getImportReference()!=null)
                references.add(registration.getImportReference());
        }

        return references;
    }



    protected ExportRegistration doExportService(final ServiceReference reference, Map<String, ? > endpointProperties) throws Exception {
        endpointProperties = new HashMap<>(endpointProperties);
        /*
         * Compute properties
         * see 122.5.1 Exporting (OSGi Enterprise spec)
         */
        Set<String> availableConfigs = getExportedConfigs(reference,endpointProperties);
        if(!availableConfigs.contains(CONFIG))
            return null;
        endpointProperties.remove(Constants.OBJECTCLASS);
        endpointProperties.remove(Constants.SERVICE_PID);
        Map<String, Object> properties = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        for (String k : reference.getPropertyKeys()) {
            properties.put(k, reference.getProperty(k));
        }
        //now override with the given properties
        properties.putAll(endpointProperties);
        // Bail out if there is any intents specified, we don't support any
        Set<String> intents = Utils.normalize(properties.get(SERVICE_EXPORTED_INTENTS));
        Set<String> extraIntents = Utils.normalize(properties.get(SERVICE_EXPORTED_INTENTS_EXTRA));
        if (!intents.isEmpty() || !extraIntents.isEmpty()) {
            throw new UnsupportedOperationException();
        }
        // Bail out if there are any configurations specified, we don't support any
        Set<String> configs = Utils.normalize(properties.get(SERVICE_EXPORTED_CONFIGS));
        if (configs.isEmpty()) {
            configs.add(CONFIG);
        } else if (!configs.contains(CONFIG)) {
            throw new UnsupportedOperationException();
        }


        properties.remove(SERVICE_EXPORTED_CONFIGS);
        properties.put(SERVICE_IMPORTED_CONFIGS, new String[] { CONFIG });
        properties.put(ENDPOINT_FRAMEWORK_UUID, this.uuid);
        String uuid = UuidGenerator.getUUID();
        properties.put(ENDPOINT_ID, uuid);

        //TODO: zookeeper stuff
        URI connectUri = new URI(this.server.getConnectAddress());
        String fabricAddress = connectUri.getScheme() + "://" + exportedAddress + ":" + connectUri.getPort();
        properties.put(FABRIC_ADDRESS, fabricAddress);


        // Now, export the service
        EndpointDescription description = new EndpointDescription(properties);

        // Export it
        server.registerService(description.getId(), new ServerInvoker.ServiceFactory() {
            public Object get() {
                return reference.getBundle().getBundleContext().getService(reference);
            }
            public void unget() {
                reference.getBundle().getBundleContext().ungetService(reference);
            }
        }, AriesFrameworkUtil.getClassLoader(reference.getBundle()));

        ExportRegistrationImpl exportRegistrationImpl = new ExportRegistrationImpl(new ExportReferenceImpl(reference, description), this);
        exportedServices.put(reference, exportRegistrationImpl);
        fireEndpointAdded(description);
        return exportRegistrationImpl;
    }

    private Set<String> getExportedConfigs(ServiceReference reference, Map<String, ? > endpointProperties)
    {
        Collection<String> configs = toStringCollection(reference.getProperty(SERVICE_EXPORTED_CONFIGS));
        Set<String> result = new HashSet<>(configs);
        result.addAll(toStringCollection(endpointProperties.get(SERVICE_EXPORTED_CONFIGS)));
        return result;
    }



    @SuppressWarnings({"unchecked", "rawtypes"})
    private Collection<String> toStringCollection(Object value)
    {
        if (value instanceof String)
        {
            return Collections.singleton((String)value);
        }
        if (value instanceof String[])
        {
            String[] strings = (String[])value;
            return Arrays.asList(strings);
        }
        if (value instanceof Collection)
        {
            Collection c = (Collection)value;
            return (Collection<String>)c;

        }
        return Collections.emptyList();
    }

    //
    // Import logic
    //
    protected ImportRegistration doImportService(final EndpointDescription endpoint) {

        ImportRegistrationImpl reg = importedServices.get(endpoint.getServiceId());
        if (reg == null || reg!=null) {

//            Bundle bundle = bundleContext.getBundle(listener.getBundleContext().getBundle().getBundleId());
            BundleContext proxyContext = FrameworkUtil.getBundle(EndpointListener.class).getBundleContext();
            Hashtable<String, Object> properties = new Hashtable<String, Object>(endpoint.getProperties());
            properties.put(SERVICE_IMPORTED, "true");
            ServiceRegistration registration = proxyContext.registerService(
                    endpoint.getInterfaces().toArray(new String[endpoint.getInterfaces().size()]),
                    new Factory(endpoint),
                    properties
            );
            reg = new ImportRegistrationImpl(registration, new ImportReferenceImpl(registration.getReference(), endpoint),this);
            importedServices.put(endpoint.getServiceId(), reg);
        }
        return reg;
    }


    protected void updateService(final ServiceReference reference) {
        ExportRegistration registration = exportedServices.get(reference);
        if (registration != null) {
            try {
                // TODO: implement logic
                // TODO: need to reflect simple properties change, but also export
                // TODO: related properties like the exported interfaces
            } catch (Exception e) {
                LOGGER.info("Error when updating endpoint", e);
            }
        }
    }

//
//    //endpoint listener
//    @Override
//    public void endpointAdded(EndpointDescription endpoint, String matchedFilter)
//    {
//        importService(endpoint);
//
//    }
//
//    //endpoint listener
//    @Override
//    public void endpointRemoved(EndpointDescription endpoint, String matchedFilter)
//    {
//        LOGGER.info("Removing endpoint "+endpoint);
//        ImportRegistrationImpl importRegistration = importedServices.remove(endpoint);
//        if(importRegistration==null)
//            return;
//
//        importRegistration.getImportedService().unregister();
//
//    }



//
//    protected void bindClientInvoker(ClientInvoker invoker)
//    {
//        client = invoker;
//        Collection<ImportRegistrationImpl> values = importedServices.values();
//        for (ImportRegistrationImpl importRegistrationImpl : values)
//        {
//            EndpointDescription endpoint = importRegistrationImpl.getImportReference().getImportedEndpoint();
//            importService(endpoint);
//        }
//    }
//
//    protected void unbindClientInvoker(ClientInvoker invoker)
//    {
//        if(invoker==client)
//        {
//            Collection<ImportRegistrationImpl> values = new ArrayList(importedServices.values());
//            for (ImportRegistrationImpl registration : values)
//            {
//                endpointRemoved(registration.getImportReference().getImportedEndpoint(), null);
//            }
//            client = null;
//        }
//    }



    class Factory implements ServiceFactory
    {

        private final EndpointDescription description;


        Factory(EndpointDescription description)
        {
            this.description = description;
        }


        public Object getService(Bundle bundle, ServiceRegistration registration)
        {
            ClassLoader classLoader = AriesFrameworkUtil.getClassLoader(bundle);
            List<Class> interfaces = new ArrayList<Class>();
            for (String interfaceName : description.getInterfaces())
            {
                try
                {
                    interfaces.add(classLoader.loadClass(interfaceName));
                }
                catch (ClassNotFoundException e)
                {
                    // Ignore
                }
            }
            String address = (String)description.getProperties().get(FABRIC_ADDRESS);
            InvocationHandler handler = client.getProxy(address, description.getId(), classLoader);
            return Proxy.newProxyInstance(classLoader, interfaces.toArray(new Class[interfaces.size()]), handler);
        }


        public void ungetService(Bundle bundle, ServiceRegistration registration, Object service)
        {}

    }

    @Override
    public void serviceChanged(ServiceEvent event)
    {
        if(event.getType()==ServiceEvent.UNREGISTERING)
        {
            ServiceReference reference = event.getServiceReference();
            ExportRegistration registration = exportedServices.get(reference);
            if(registration!=null)
                registration.close();
        }

    }

}



