/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
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

import static io.fabric8.dosgi.util.ZooKeeperUtils.*;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.*;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLStreamException;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.dosgi.api.Dispatched;
import io.fabric8.dosgi.api.SerializationStrategy;
import io.fabric8.dosgi.capset.CapabilitySet;
import io.fabric8.dosgi.io.ClientInvoker;
import io.fabric8.dosgi.io.ServerInvoker;
import io.fabric8.dosgi.tcp.ClientInvokerImpl;
import io.fabric8.dosgi.tcp.ServerInvokerImpl;
import io.fabric8.dosgi.util.Utils;

//@Component
//@Service
public class Manager implements ServiceListener, /*, ListenerHook, EventHook, FindHook, */TreeCacheListener, Dispatched, EndpointListener {



    private static final Logger LOGGER = LoggerFactory.getLogger(Manager.class);
    private static final String DOSGI_REGISTRY = "/fabric/dosgi";


    private final BundleContext bundleContext;

//    @Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY)
//    RemoteServiceAdmin remoteServiceAdmin;

//    @Reference(referenceInterface=EndpointListener.class,cardinality=ReferenceCardinality.OPTIONAL_MULTIPLE,bind="addEndpointListener",unbind="removeEndpointListener")
//    private List<EndpointListener> endpointListeners;

    private ServiceTracker<RemoteServiceAdmin,RemoteServiceAdmin> adminTracker;
    private ServiceTracker<EndpointListener,EndpointListener> endpointListenerTracker;
    private Map<String, ImportRegistration> endpointToImportRegistration;

    //
    // Discovery part
    //

    // The zookeeper client
    private final CuratorFramework curator;
    // The tracked zookeeper tree
    private TreeCache tree;
    // Remote endpoints
    private final CapabilitySet<EndpointDescription> remoteEndpoints;

    //
    // Internal data structures
    //
    private final DispatchQueue queue;

    private final Map<String, SerializationStrategy> serializationStrategies;


    private String uuid;

    private final URI uri;

    private final String exportedAddress;

    private final long timeout;

    private ClientInvoker client;

    private ServerInvoker server;

    private ServiceRegistration clientService;
    private ServiceRegistration serverService;

    private static final String EXPORTED_SERVICE_FILTER = "(" + RemoteConstants.SERVICE_EXPORTED_INTERFACES + "=*)";


    public Manager(BundleContext context, CuratorFramework curator) throws Exception {
        this(context, curator, "tcp://0.0.0.0:2543", null, TimeUnit.SECONDS.toMillis(30));
    }

    public Manager(BundleContext context, CuratorFramework curator, String uri, String exportedAddress, long timeout) throws Exception {
        this.queue = Dispatch.createQueue();
        this.serializationStrategies = new ConcurrentHashMap<String, SerializationStrategy>();
        this.remoteEndpoints = new CapabilitySet<EndpointDescription>(
                Arrays.asList(Constants.OBJECTCLASS, ENDPOINT_FRAMEWORK_UUID), false);
        this.bundleContext = context;
        this.curator = curator;
        this.uri = URI.create(uri);
        this.exportedAddress = exportedAddress;
        this.timeout = timeout;
        this.endpointToImportRegistration = new ConcurrentHashMap<>();
    }

//    @Activate
    public void init() throws Exception {
        uuid = Utils.getUUID(bundleContext);
        // Create client and server
        this.client = new ClientInvokerImpl(queue, timeout, serializationStrategies);
        this.server = new ServerInvokerImpl(uri.toString(), queue, serializationStrategies);
        this.client.start();
        this.server.start();
        // ZooKeeper tracking
        try {
            create(curator, DOSGI_REGISTRY, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException e) {
            // The node already exists, that's fine
        }
        this.tree = new TreeCache(curator,  DOSGI_REGISTRY);
        this.tree.getListenable().addListener(this);
        this.tree.start();
        endpointListenerTracker = new ServiceTracker<>(bundleContext, EndpointListener.class, null);
        endpointListenerTracker.open(false);


        // Service registration
//        this.registration = this.bundleContext.registerService(new String[] { ListenerHook.class.getName(), EventHook.class.getName(), FindHook.class.getName() }, this, null);
        this.bundleContext.registerService(new String[] { EndpointListener.class.getName()}, this, null);
        bundleContext.addServiceListener(this, EXPORTED_SERVICE_FILTER);
        clientService = bundleContext.registerService(ClientInvoker.class.getName(), client, null);
        serverService = bundleContext.registerService(ServerInvoker.class.getName(), server, null);

        new Thread(() -> {
            /*
             *  Check existing services
             */
            try
            {
                adminTracker = new ServiceTracker<>(bundleContext, RemoteServiceAdmin.class, null);
                adminTracker.open(false);
                ServiceReference[] references = this.bundleContext.getServiceReferences((String) null, EXPORTED_SERVICE_FILTER);
                Map<String,Object> props = new HashMap<>();
                String serverAddress = uri.getScheme() + "://" + exportedAddress + ":" + uri.getPort();
                props.put(RemoteServiceAdminImpl.FABRIC_ADDRESS, serverAddress);
                if (references != null) {
                    for (ServiceReference reference : references) {
                        adminTracker.getService().exportService(reference,props);
                    }
                }
            }
            catch (InvalidSyntaxException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }).start();

    }

//    @Deactivate
    public void destroy() throws IOException {

        this.server.stop();
        this.client.stop();
        if(tree!=null)
        {
            this.tree.close();
        }
        if (clientService != null) {
            this.clientService.unregister();
        }
        if (serverService != null) {
            this.serverService.unregister();
        }
        adminTracker.close();
        endpointListenerTracker.close();
//        this.bundleContext.removeServiceListener(this);
    }

//    public void addEndpointListener(EndpointListener listener)
//    {
//        endpointListeners.remove(listener);
//    }
//
//    public void removeEndpointListener(EndpointListener listener)
//    {
//        endpointListeners.remove(listener);
//    }



    //
    // EventHook
    //

//    @SuppressWarnings("unchecked")
//    public void event(ServiceEvent event, Collection collection) {
//        // Our imported services are exported from within the importing bundle and should only be visible it
//        ServiceReference reference = event.getServiceReference();
//        if (reference.getProperty(SERVICE_IMPORTED) != null && reference.getProperty(FABRIC_ADDRESS) != null) {
//            Collection<BundleContext> contexts = (Collection<BundleContext>) collection;
//            for (Iterator<BundleContext> iterator = contexts.iterator(); iterator.hasNext();) {
//                BundleContext context = iterator.next();
//                if (context != reference.getBundle().getBundleContext() && context != this.bundleContext) {
//                    iterator.remove();
//                }
//            }
//        }
//    }
//
//    //
//    // FindHook
//    //
//
//    @SuppressWarnings("unchecked")
//    public void find(BundleContext context, String name, String filter, boolean allServices, Collection collection) {
//        // Our imported services are exported from within the importing bundle and should only be visible it
//        Collection<ServiceReference> references = (Collection<ServiceReference>) collection;
//        for (Iterator<ServiceReference> iterator = references.iterator(); iterator.hasNext();) {
//            ServiceReference reference = iterator.next();
//            if (reference.getProperty(SERVICE_IMPORTED) != null && reference.getProperty(FABRIC_ADDRESS) != null) {
//                if (context != reference.getBundle().getBundleContext() && context != this.bundleContext) {
//                    iterator.remove();
//                }
//            }
//        }
//    }


    //
    // Export logic
    //

//    protected void unExportService(final ServiceReference reference) {
//        try {
//            ExportRegistrationImpl registration = exportedServices.remove(reference);
//            if (registration != null) {
//                server.unregisterService(registration.getExportReference().getExportedEndpoint().getId());
//                delete(curator, DOSGI_REGISTRY + "/" + registration.getExportReference().getExportedEndpoint().getId());
//            }
//        } catch (Exception e) {
//            LOGGER.info("Error when unexporting endpoint", e);
//        }
//    }

    public DispatchQueue queue() {
        return queue;
    }


    @Override
    public void childEvent(CuratorFramework curatorFramework, TreeCacheEvent event) throws Exception
    {
        switch (event.getType())
        {
            case NODE_ADDED:
            {
                if (event.getData().getData() != null)
                {
                    EndpointDescription endpoint = Utils.getEndpointDescription(new String(event.getData().getData()));
                    importEndpoint(endpoint);
                    // Check existing listeners
//                    for (Map.Entry<ListenerInfo, SimpleFilter> entry : listeners.entrySet())
//                    {
//                        if (CapabilitySet.matches(endpoint, entry.getValue()))
//                        {
//                            doImportService(endpoint, entry.getKey());
//                        }
//                    }
                }
            }
                break;
            case NODE_UPDATED:
            {
                if (event.getData().getData() != null)
                {
                    EndpointDescription endpoint = Utils.getEndpointDescription(new String(event.getData().getData()));
                    unimportEndpoint(endpoint);
                    importEndpoint(endpoint);
//                    Map<Long, ImportRegistrationImpl> registrations = importedServices.get(endpoint);
//                    if (registrations != null)
//                    {
//                        for (ImportRegistrationImpl reg : registrations.values())
//                        {
//                            reg.importedService.setProperties(new Hashtable<String, Object>(endpoint.getProperties()));
//                        }
//                    }
                }
            }
                break;
            case NODE_REMOVED:
            {
                if (event.getData().getPath() != null)
                {
                    String path = event.getData().getPath();
                    String endpointId = path.substring(path.lastIndexOf('/')+1, path.length());
                    Collection<ImportReference> endpoints = new ArrayList<>(adminTracker.getService().getImportedEndpoints());
                    for (ImportReference importReference : endpoints)
                    {
                        if(importReference.getImportedEndpoint().getId().equals(endpointId))
                        {
                            EndpointDescription endpoint = importReference.getImportedEndpoint();
                            unimportEndpoint(endpoint);
                        }
                    }
                }
            }
                break;
        }

    }


    protected void importEndpoint(EndpointDescription endpoint)
    {
        if (!uuid.equals(endpoint.getFrameworkUUID()))
        {
            ImportRegistration registration = adminTracker.getService().importService(endpoint);
            if (registration != null)
            {
                // otherwise the registration failed
                if (registration.getException() == null)
                {
                    remoteEndpoints.addCapability(endpoint);
                    endpointToImportRegistration.put(endpoint.getId(), registration);
                }
            }
        }
        fireEndpointAdded(endpoint);
    }


    protected void unimportEndpoint(EndpointDescription endpoint)
    {
        if (!uuid.equals(endpoint.getFrameworkUUID()))
        {
            ImportRegistration registration = endpointToImportRegistration.remove(endpoint.getId());
            if (registration != null)
            {
                remoteEndpoints.removeCapability(endpoint);
                registration.close();
            }
        }
        fireEndpointRemoved(endpoint);
    }


    // endpoint listener
    @Override
    public void endpointAdded(EndpointDescription endpoint, String matchedFilter)
    {

        try
        {
            String descStr = Utils.getEndpointDescriptionXML(endpoint);
            // Publish in ZooKeeper
            final String nodePath = create(curator, DOSGI_REGISTRY + "/" + endpoint.getId(), descStr, CreateMode.EPHEMERAL);
        }
        catch (XMLStreamException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }


    @Override
    public void endpointRemoved(EndpointDescription endpoint, String matchedFilter)
    {
        server.unregisterService(endpoint.getId());
        try
        {
            delete(curator, DOSGI_REGISTRY + "/" + endpoint.getId());
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to delete endpoint in zooper "+endpoint);
        }

    }

    protected void fireEndpointAdded(EndpointDescription endpoint)
    {
        if(!uuid.equals(endpoint.getFrameworkUUID()))
        {
            for (Object endpointListener : endpointListenerTracker.getServices())
            {
                //TODO: evaluate scope filter
                if(endpointListener!=this)
                    ((EndpointListener)endpointListener).endpointAdded(endpoint, "TODO");
            }
        }
    }

    protected void fireEndpointRemoved(EndpointDescription endpoint)
    {
        for (Object endpointListener : endpointListenerTracker.getServices())
        {
            // TODO: evaluate scope filter
            if(endpointListener!=this)
                ((EndpointListener)endpointListener).endpointRemoved(endpoint, "TODO");
        }
    }

    //service listener
    public void serviceChanged(final ServiceEvent event) {
        final ServiceReference reference = event.getServiceReference();
        if(reference.getProperty(SERVICE_EXPORTED_INTERFACES)==null)
            return; //only remote services are interesting
        RemoteServiceAdmin admin = adminTracker.getService();
        Map<String,Object> props = new HashMap<>();

        String serverAddress = uri.getScheme() + "://" + exportedAddress + ":" + uri.getPort();
        props.put(RemoteServiceAdminImpl.FABRIC_ADDRESS, serverAddress);
        switch (event.getType()) {
            case ServiceEvent.REGISTERED:
                admin.exportService(reference, props);
                break;
            case ServiceEvent.MODIFIED:
                Collection<ExportReference> exportedServices = admin.getExportedServices();
                for (ExportReference exportReference : exportedServices)
                {
                     ServiceReference service = exportReference.getExportedService();
                     if(exportReference.getExportedService().compareTo(reference)==0)
                     {
                         fireEndpointRemoved(exportReference.getExportedEndpoint());
                         admin.exportService(reference, props);
                     }
                }
//                remoteServiceAdmin.exportService(reference, null);
//                updateService(reference);
                break;
            case ServiceEvent.UNREGISTERING:
                exportedServices = admin.getExportedServices();
                for (ExportReference exportReference : exportedServices)
                {
                     ServiceReference service = exportReference.getExportedService();
                     if(exportReference.getExportedService().compareTo(reference)==0)
                     {
                         fireEndpointRemoved(exportReference.getExportedEndpoint());
//                         admin.exportService(reference, null);
                     }
                }
                break;
        }
    }
}
