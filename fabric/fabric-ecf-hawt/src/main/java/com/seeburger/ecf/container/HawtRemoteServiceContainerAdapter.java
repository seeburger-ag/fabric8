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


import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ecf.remoteservice.IExtendedRemoteServiceRegistration;
import org.eclipse.ecf.remoteservice.IRegistrationListener;
import org.eclipse.ecf.remoteservice.RemoteServiceContainerAdapterImpl;
import org.eclipse.ecf.remoteservice.RemoteServiceRegistrationImpl;
import org.eclipse.ecf.remoteservice.RemoteServiceRegistryImpl;

import com.seeburger.ecf.io.ServerInvoker;
import com.seeburger.ecf.io.ServerInvoker.ServiceFactory;


public class HawtRemoteServiceContainerAdapter extends RemoteServiceContainerAdapterImpl
{

    private ServerInvoker invoker;

    public HawtRemoteServiceContainerAdapter(HawtServerContainer container, ServerInvoker invoker)
    {
        super(container);
        this.invoker = invoker;
    }


    protected RemoteServiceRegistrationImpl createRegistration()
    {
        return new CustomRemoteServiceRegistration();
    }

    @Override
    protected HawtServerContainer getContainer()
    {
        return (HawtServerContainer)super.getContainer();
    }

    class CustomRemoteServiceRegistration extends RemoteServiceRegistrationImpl implements IExtendedRemoteServiceRegistration
    {


        public CustomRemoteServiceRegistration()
        {
            super(new IRegistrationListener()
            {
                public void unregister(RemoteServiceRegistrationImpl registration)
                {
                    invoker.unregisterService(registration.getID().getName());
                    handleServiceUnregister(registration);
                }
            });
        }

        @Override
        public void publish(RemoteServiceRegistryImpl registry, Object svc, String[] clzzes, Dictionary props)
        {
            ServiceFactory serviceFactory = new ServerInvoker.ServiceFactory() {
                public Object get() {
                    return svc;
                }
                public void unget() {

                }
            };

            super.publish(registry, svc, clzzes, props);
            invoker.registerService(String.valueOf(getID().getContainerRelativeID()), serviceFactory, svc.getClass().getClassLoader());
        }

        @Override
        public Map<String, Object> getExtraProperties()
        {
            Map<String, Object> result = new HashMap<String, Object>();

//            result.put("ecf.endpoint.id", invoker.getConnectAddress());
            return result;
        }
    }
}
