package com.seeburger.cxf;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.cxf.dosgi.dsw.handlers.ConfigurationTypeHandler;
import org.apache.cxf.dosgi.dsw.handlers.ExportResult;
import org.apache.cxf.dosgi.dsw.qos.IntentUnsatisfiedException;
import org.apache.cxf.dosgi.dsw.qos.IntentUtils;
import org.apache.cxf.dosgi.dsw.util.OsgiUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.transport.Destination;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.seeburger.cxf.api.SerializationStrategy;
import com.seeburger.cxf.io.ClientInvoker;
import com.seeburger.cxf.io.ServerInvoker;
import com.seeburger.cxf.io.ServerInvoker.ServiceFactory;
import com.seeburger.cxf.tcp.ClientInvokerImpl;
import com.seeburger.cxf.tcp.ServerInvokerImpl;

@Component(enabled=true)
@Service
public class HawtConfigurationTypeHandler implements ConfigurationTypeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HawtConfigurationTypeHandler.class);

    private static final String FABRIC_ADDRESS = "fabric.address";

    private ServerInvoker server;
    private ClientInvoker client;
    private DispatchQueue queue;
    private ConcurrentHashMap<String, SerializationStrategy> serializationStrategies;
    private BundleContext bundleContext;

    @Activate
    public void activate(Map<String, ?> config) {
        this.bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        this.queue = Dispatch.createQueue();
        this.serializationStrategies = new ConcurrentHashMap<String, SerializationStrategy>();
        Random r = new Random();
        int port = r.nextInt(55000);
        port+=10000;

        try {
            String host = Inet4Address.getLocalHost().getCanonicalHostName();
            server = new ServerInvokerImpl("tcp://"+host+":"+port, queue, serializationStrategies);
            server.start();
            client = new ClientInvokerImpl(queue, serializationStrategies);
            client.start();
        } catch (Exception e) {
            LOG.error("Failed to start the tcp server",e);
        }
    }

    @Deactivate
    public void deactivate(Map<String, ?> config) {
        server.stop();
        client.stop();
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[] { "hawt.io" };
    }

    @Override
    public ExportResult createServer(ServiceReference serviceReference, BundleContext dswContext,
            BundleContext callingContext, Map<String, Object> sd, Class<?> iClass, final Object serviceBean) {
            Map<String, Object> endpointProps = createEndpointProps(sd, iClass, getSupportedTypes(), server.getConnectAddress(), new String[0]); //TODO: handle intents properly
            ServiceFactory factory = new ServiceFactory() {

                @Override
                public void unget() {
                    // TODO Auto-generated method stub

                }

                @Override
                public Object get() {
                    return serviceBean;
                }
            };
            server.registerService(endpointProps.get(RemoteConstants.ENDPOINT_ID).toString(), factory, serviceBean.getClass().getClassLoader());
            return new ExportResult(endpointProps, new ServerWrapper(server,serviceBean,endpointProps));
    }

    @Override
    public Object createProxy(ServiceReference serviceReference, BundleContext dswContext, BundleContext callingContext,
            Class<?> iClass, EndpointDescription endpoint) throws IntentUnsatisfiedException {
        InvocationHandler invocationHandler = client.getProxy((String) endpoint.getProperties().get(FABRIC_ADDRESS), endpoint.getProperties().get(RemoteConstants.ENDPOINT_ID).toString(), iClass.getClassLoader());
        return Proxy.newProxyInstance(iClass.getClassLoader(), new Class[] {iClass},invocationHandler);
    }

//
//    protected Object getProxy(Object serviceProxy, Class<?> iType) {
//        return Proxy.newProxyInstance(iType.getClassLoader(), new Class[] {
//            iType
//        }, new ServiceInvocationHandler(serviceProxy, iType));
//    }

    protected Map<String, Object> createEndpointProps(Map<String, Object> sd, Class<?> iClass,
                                                      String[] importedConfigs, String address, String[] intents) {
        Map<String, Object> props = new HashMap<String, Object>();

        copyEndpointProperties(sd, props);

        String[] sa = new String[] {
            iClass.getName()
        };
        String pkg = iClass.getPackage().getName();

        props.remove(org.osgi.framework.Constants.SERVICE_ID);
        props.put(org.osgi.framework.Constants.OBJECTCLASS, sa);
        props.put(RemoteConstants.ENDPOINT_SERVICE_ID, sd.get(org.osgi.framework.Constants.SERVICE_ID));
        props.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, OsgiUtils.getUUID(bundleContext));
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, importedConfigs);
        props.put(RemoteConstants.ENDPOINT_PACKAGE_VERSION_ + pkg, OsgiUtils.getVersion(iClass, bundleContext));


        String[] allIntents = IntentUtils.mergeArrays(intents, IntentUtils.getIntentsImplementedByTheService(sd));
        props.put(RemoteConstants.SERVICE_INTENTS, allIntents);
        String fabricAddress = server.getConnectAddress();
        props.put(FABRIC_ADDRESS, fabricAddress);
        String endpointID = fabricAddress+"/"+UUID.randomUUID().toString();
        props.put(RemoteConstants.ENDPOINT_ID, endpointID);
        return props;
    }

    private void copyEndpointProperties(Map<String, Object> sd, Map<String, Object> endpointProps) {
        Set<Map.Entry<String, Object>> keys = sd.entrySet();
        for (Map.Entry<String, Object> entry : keys) {
            try {
                String skey = entry.getKey();
                if (!skey.startsWith(".")) {
                    endpointProps.put(skey, entry.getValue());
                }
            } catch (ClassCastException e) {
                LOG.warn("ServiceProperties Map contained non String key. Skipped " + entry + "   "
                         + e.getLocalizedMessage());
            }
        }
    }

    class ServerWrapper implements Server, ServiceFactory
    {

        public ServerWrapper(ServerInvoker server, Object serviceBean, Map<String, Object> endpointProps) {
            // TODO Auto-generated constructor stub
        }

        @Override
        public void start() {
            // TODO Auto-generated method stub

        }

        @Override
        public void stop() {
            // TODO Auto-generated method stub

        }

        @Override
        public void destroy() {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean isStarted() {
            // TODO Auto-generated method stub
            return true;
        }

        @Override
        public Destination getDestination() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Endpoint getEndpoint() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Object get() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void unget() {
            // TODO Auto-generated method stub

        }

    }
}

