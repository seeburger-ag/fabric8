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
package io.fabric8.dosgi;

import static io.fabric8.dosgi.zookeeper.Constants.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Dictionary;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.dosgi.impl.Manager;
import io.fabric8.dosgi.util.Configurer;
import io.fabric8.dosgi.util.DefaultConfigurer;
import io.fabric8.dosgi.zookeeper.ManagedCuratorFrameworkAvailable;

@Component(name = "io.fabric8.dosgi.manager", label = "Fabric8 DOSGi Manager Activation", policy = ConfigurationPolicy.OPTIONAL, immediate = true, metatype = true)
@Service(ConnectionStateListener.class)
@Properties(
        {
                @Property(name = DOSGI_PORT, label = "dosgi port", description = "Server Port for DOSGi communication", value = "3000"),
                @Property(name = DOSGI_BIND_HOST, label = "dosgi bind address", description = "Bind address for the DOSGi socket", value = "0.0.0.0"),
                @Property(name = DOSGI_TIMEOUT, label = "dosgi timeout", description = "Connection timeout in ms", value = "300000"),
        }
)
public class DOSGiActivation implements ConnectionStateListener {

    private BundleContext bundleContext;
    private Manager manager;
    private String uri;
    private String exportedAddress;
    private long timeout = TimeUnit.MINUTES.toMillis(5);
    private CuratorFramework curator;
	private Configurer configurer = new DefaultConfigurer();
	private static final Logger LOG = LoggerFactory.getLogger(DOSGiActivation.class);

	@Activate
	public void activate(ComponentContext ctx)
	{
	    configure(ctx);
	}

	@Modified
    public void modified(ComponentContext ctx)
	{
	    configure(ctx);
	}

    @Deactivate
    public void deactivate() throws Exception {
        onDisconnected();
    }

    private void configure(ComponentContext ctx)
    {
        bundleContext = ctx.getBundleContext();
        Dictionary properties = ctx.getProperties();
        try
        {
            Map<String,String> settings = configurer.configure(properties, null);
            String port = settings.getOrDefault(DOSGI_PORT, "3000");
            String bindHost = settings.getOrDefault(DOSGI_BIND_HOST, "0.0.0.0");
            timeout = Long.parseLong(settings.getOrDefault(DOSGI_TIMEOUT, "300000"));
            uri = "tcp://"+bindHost+":"+port;
        }
        catch (Exception e)
        {
            LOG.error("Failed to update configuration",e);
        }


    }
//    public void setBundleContext(BundleContext bundleContext) {
//        this.bundleContext = bundleContext;
//    }
//
//    public void setUri(String uri) {
//        this.uri = uri;
//    }
//
//    public void setExportedAddress(String exportedAddress) {
//        this.exportedAddress = exportedAddress;
//    }
//
//    public void setTimeout(long timeout) {
//        this.timeout = timeout;
//    }

    public void destroy() {
        destroyManager();
        curator = null;
    }

    protected void destroyManager() {
        if (manager != null) {
            Manager mgr = manager;
            manager = null;
            try {
                mgr.destroy();
            } catch (IOException e) {
                //ignore
            }
        }
    }


    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        switch (newState) {
            case CONNECTED:
            case RECONNECTED:
                this.curator = client;
                onConnected();
                break;
            default:
                onDisconnected();
        }
    }

    public void onConnected() {
        destroyManager();
        try {
            manager = new Manager(this.bundleContext, curator, uri, InetAddress.getLocalHost().getCanonicalHostName(), timeout);
            manager.init();
        } catch (Exception e) {
            throw new RuntimeException("Unable to start DOSGi service: " + e.getMessage(), e);
        }
    }

    public void onDisconnected() {
        destroyManager();
    }


}
