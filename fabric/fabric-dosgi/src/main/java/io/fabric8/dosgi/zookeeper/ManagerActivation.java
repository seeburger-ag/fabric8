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
package io.fabric8.dosgi.zookeeper;

import static io.fabric8.dosgi.zookeeper.Constants.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.dosgi.impl.Manager;

/**
 * ManagerActivation.
 * <p>
 * handles the lifecycle of the DOSGi Manager instance
 *
 * @author utzig
 */
public class ManagerActivation implements ConnectionStateListener
{

    private static final Logger LOG = LoggerFactory.getLogger(ManagerActivation.class);
    private CuratorFramework curator;
    private BundleContext bundleContext;
    private Manager manager;
    private Map<String, String> configuration;
    private long timeout;
    private String uri;

    public ManagerActivation()
    {
        this.bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
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

    private void onConnected()
    {
        destroyManager();
        try {
            manager = new Manager(this.bundleContext, curator, uri, InetAddress.getLocalHost().getCanonicalHostName(), timeout);
            manager.init();
        } catch (Exception e) {
            throw new RuntimeException("Unable to start DOSGi service: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void activate(Map configuration) {
        destroyManager();
        this.configuration = configuration;
        try
        {
            String port = (String)configuration.getOrDefault(DOSGI_PORT, "3000");
            String bindHost = (String)configuration.getOrDefault(DOSGI_BIND_HOST, "0.0.0.0");
            timeout = Long.parseLong((String)configuration.getOrDefault(DOSGI_TIMEOUT, "10000")); //10 seconds
            uri = "tcp://"+bindHost+":"+port;
        }
        catch (Exception e)
        {
            LOG.error("Failed to update configuration",e);
        }
    }

    public void onDisconnected() {
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

}



