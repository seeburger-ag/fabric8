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
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.remoteservice.IRemoteCall;
import org.eclipse.ecf.remoteservice.IRemoteService;
import org.eclipse.ecf.remoteservice.IRemoteServiceCallPolicy;
import org.eclipse.ecf.remoteservice.IRemoteServiceReference;
import org.eclipse.ecf.remoteservice.IRemoteServiceRegistration;
import org.eclipse.ecf.remoteservice.client.AbstractClientContainer;
import org.eclipse.ecf.remoteservice.client.IRemoteCallable;
import org.eclipse.ecf.remoteservice.client.RemoteCallableFactory;
import org.eclipse.ecf.remoteservice.client.RemoteServiceClientRegistration;
import org.fusesource.hawtdispatch.Dispatch;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.seeburger.ecf.HawtNamespace;
import com.seeburger.ecf.io.ClientInvoker;
import com.seeburger.ecf.tcp.ClientInvokerImpl;

public class HawtClientContainer extends AbstractClientContainer
{

    private ClientInvoker invoker;
    private static final Logger LOG = LoggerFactory.getLogger(HawtClientContainer.class);
    private IRemoteCallable callable;

    public HawtClientContainer()
    {
        super(HawtNamespace.INSTANCE.createInstance(new Object[] { URI.create("uuid:" + java.util.UUID.randomUUID().toString()) }));
        this.callable = RemoteCallableFactory.createCallable(getID().getName());
        invoker = new ClientInvokerImpl(Dispatch.createQueue(), new ConcurrentHashMap<>());
        try
        {
            invoker.start();
        }
        catch (Exception e)
        {
            LOG.error("Failed to start client invoker",e);
        }
    }

    //no clue what this is for
    @Override
    public IRemoteServiceReference[] getRemoteServiceReferences(ID target, ID[] idFilter, String clazz, String filter)
            throws InvalidSyntaxException, ContainerConnectException {
        IRemoteServiceReference[] refs = super.getRemoteServiceReferences(target, idFilter, clazz, filter);
        if (refs == null) {
            IRemoteServiceRegistration registration = registerCallables(new String[] { clazz }, new IRemoteCallable[][] { { callable } }, null);
            return new IRemoteServiceReference[]{registration.getReference()};
//            refs = super.getRemoteServiceReferences(target, idFilter, clazz, filter);
        }
        return refs;
    }

    @Override
    public IRemoteServiceReference[] getRemoteServiceReferences(ID[] idFilter, String clazz, String filter) throws InvalidSyntaxException
    {
        // TODO Auto-generated method stub
        return super.getRemoteServiceReferences(idFilter, clazz, filter);
    }


    @Override
    public boolean setRemoteServiceCallPolicy(IRemoteServiceCallPolicy arg0)
    {
        return false;
    }

    @Override
    public Namespace getConnectNamespace()
    {
        return HawtNamespace.INSTANCE;
    }

    @Override
    protected IRemoteService createRemoteService(RemoteServiceClientRegistration arg0)
    {
        return new HawtClientRemoteService(this, arg0);
    }

    @Override
    protected String prepareEndpointAddress(IRemoteCall arg0, IRemoteCallable arg1)
    {
        return null;
    }

    public ClientInvoker getInvoker()
    {
        return invoker;
    }

    @Override
    public void dispose()
    {
        invoker.stop();
        super.dispose();
    }

}



