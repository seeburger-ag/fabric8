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

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.server.ZooKeeperServer;

import io.fabric8.dosgi.impl.Manager;
import io.fabric8.dosgi.util.ZookeeperBootstrap;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements ConnectionStateListener, BundleActivator {

    private BundleContext bundleContext;
    private Manager manager;
    private String uri;
    private String exportedAddress;
    private long timeout = TimeUnit.MINUTES.toMillis(5);
    private CuratorFramework curator;
	private ZooKeeperServer server;
	private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setExportedAddress(String exportedAddress) {
        this.exportedAddress = exportedAddress;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

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
    	LOG.info("Received connect");
        destroyManager();
        try {
            manager = new Manager(this.bundleContext, curator, uri, exportedAddress, timeout);
            manager.init();
        } catch (Exception e) {
            throw new RuntimeException("Unable to start DOSGi service: " + e.getMessage(), e);
        }
    }

    public void onDisconnected() {
    	LOG.info("Received disconnect");
        destroyManager();
    }

	@Override
	public void start(BundleContext context) throws Exception {
		setBundleContext(context);
		int serverPort = 2555;//new Random().nextInt(50000)+1024;
		setUri("tcp://0.0.0.0:"+serverPort);
		
		try {
			server = new ZookeeperBootstrap().activate(serverPort, serverPort+1);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		CuratorFramework client = CuratorFrameworkFactory.newClient(uri, new RetryNTimes(100, 100));
		client.getConnectionStateListenable().addListener(this);
		
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		onDisconnected();
		if(server!=null)
			server.shutdown();
	}
}
