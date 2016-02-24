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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.remoteservice.IRemoteCall;
import org.eclipse.ecf.remoteservice.client.AbstractClientService;
import org.eclipse.ecf.remoteservice.client.IRemoteCallable;
import org.eclipse.ecf.remoteservice.client.RemoteServiceClientRegistration;
import org.osgi.framework.ServiceException;

public class HawtClientRemoteService extends AbstractClientService
{

    public HawtClientRemoteService(HawtClientContainer container, RemoteServiceClientRegistration registration)
    {
        super(container, registration);
    }

    @Override
    protected Object invokeRemoteCall(IRemoteCall call, IRemoteCallable callable) throws ECFException
    {
        if (call instanceof HawtRemoteCalls)
        {

            HawtRemoteCalls theCall = (HawtRemoteCalls)call;
            Method method = theCall.getJavaMethod();

            String uri = getClientContainer().getConnectedID().getName();
            String service = String.valueOf(getRemoteServiceID().getContainerRelativeID());

            InvocationHandler realHandler = getClientContainer().getInvoker().getProxy(uri,service, method.getDeclaringClass().getClassLoader());
            try
            {
                return realHandler.invoke(null, method, call.getParameters());
            }
            catch (Throwable e)
            {
                throw new ECFException(e.getMessage(),e);
            }

        }
        throw new ECFException("The RemoteCall must be a HawtRemoteCall: "+call);
    }

    protected Object invokeSync(IRemoteCall remoteCall) throws ECFException {
        return invokeRemoteCall(remoteCall, null);

    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        // methods declared by Object
        try {
            // If the method is from Class Object, or from IRemoteServiceProxy
            // then return result
            Object resultObject = invokeObject(proxy, method, args);
            if (resultObject != null)
                return resultObject;
            if (isAsync(proxy, method, args))
                //TODO: async is broken
                return invokeAsync(method, args);
            // else call synchronously/block and return result
            final String callMethod = getCallMethodNameForProxyInvoke(method, args);
            final Object[] callParameters = getCallParametersForProxyInvoke(callMethod, method, args);
            final long callTimeout = getCallTimeoutForProxyInvoke(callMethod, method, args);
            final IRemoteCall remoteCall = createRemoteCall(method, callParameters, callTimeout);
            return invokeSync(remoteCall);
        } catch (Throwable t) {
            if (t instanceof ServiceException)
                throw t;
            // rethrow as service exception
            throw new ServiceException("Service exception on remote service proxy rsid=" + getRemoteServiceID(), ServiceException.REMOTE, t); //$NON-NLS-1$
        }
    }


    private IRemoteCall createRemoteCall(Method method, Object[] callParameters, long callTimeout)
    {
        return new HawtRemoteCalls(method, callParameters, callTimeout);
    }

    @Override
    protected HawtClientContainer getClientContainer()
    {
        return (HawtClientContainer)super.getClientContainer();
    }

    private static class HawtRemoteCalls implements IRemoteCall
    {

        private Method method;
        private Object[] callParameters;
        private long callTimeout;

        public HawtRemoteCalls(Method method, Object[] callParameters, long callTimeout)
        {
            super();
            this.method = method;
            this.callParameters = callParameters;
            this.callTimeout = callTimeout;
        }

        @Override
        public String getMethod()
        {
            return method.getName();
        }

        @Override
        public Object[] getParameters()
        {
            return callParameters;
        }

        @Override
        public long getTimeout()
        {
            return callTimeout;
        }

        public Method getJavaMethod()
        {
            return method;
        }

    }

}



