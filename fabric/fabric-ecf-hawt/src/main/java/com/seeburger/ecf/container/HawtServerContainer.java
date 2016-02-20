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
package com.seeburger.ecf.container;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.ecf.core.AbstractContainer;
import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.core.security.IConnectContext;
import org.eclipse.ecf.remoteservice.IRemoteServiceContainerAdapter;
import org.fusesource.hawtdispatch.Dispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.seeburger.ecf.HawtNamespace;
import com.seeburger.ecf.io.ServerInvoker;
import com.seeburger.ecf.tcp.ServerInvokerImpl;

public class HawtServerContainer extends AbstractContainer
{

    private ID serverId;
    private String port;
    private HawtRemoteServiceContainerAdapter remoteAdapter;
    private ServerInvoker invoker;
    private static final Logger LOG = LoggerFactory.getLogger(HawtServerContainer.class);

    public HawtServerContainer(Map<String, ? > options) {

        // Create serverID
        this.serverId = HawtNamespace.INSTANCE.createInstance(new Object[] { URI.create("uuid:" + java.util.UUID.randomUUID().toString()) });
        invoker = createInvoker(options);
        remoteAdapter = new HawtRemoteServiceContainerAdapter(this, invoker);
    }

    private ServerInvoker createInvoker(Map<String, ? > options)
    {
        try
        {
            invoker = new ServerInvokerImpl("tcp://0.0.0.0:9001", Dispatch.createQueue(), new ConcurrentHashMap<>());
            invoker.start();
        }
        catch (Exception e)
        {
            LOG.error("Failed to start server invoker",e);
        }
        return invoker;
    }

    @Override
    public void connect(ID targetID, IConnectContext connectContext) throws ContainerConnectException
    {
        throw new ContainerConnectException("Cannot connect to HawtServerContainer");
    }

    @Override
    public ID getConnectedID()
    {
        return null;
    }

    @Override
    public Namespace getConnectNamespace()
    {
        return serverId.getNamespace();
    }

    @Override
    public void disconnect()
    {

    }

    @Override
    public ID getID()
    {
        return serverId;
    }

    @Override
    public void dispose()
    {
        invoker.stop();
        invoker = null;
        super.dispose();
    }

    @Override
    public Object getAdapter(Class serviceType)
    {
        if(serviceType==IRemoteServiceContainerAdapter.class)
            return remoteAdapter;
        return super.getAdapter(serviceType);
    }

}



