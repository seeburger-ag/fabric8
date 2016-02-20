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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.provider.IRemoteServiceContainerInstantiator;
import org.eclipse.ecf.remoteservice.provider.RemoteServiceContainerInstantiator;


public class HawtContainerInstantiator extends RemoteServiceContainerInstantiator implements IRemoteServiceContainerInstantiator
{

    public static final String CONFIG_PARAM = "configuration";

    protected static final String[] intents = new String[]{"ecf.hawt",HawtRemoteServiceDistributionProvider.HAWT_SERVER_CONFIG_NAME,HawtRemoteServiceDistributionProviderClient.SERVER_PROVIDER_NAME,HawtRemoteServiceDistributionProviderClient.CLIENT_PROVIDER_NAME};


    protected HawtContainerInstantiator(String serverConfigTypeName)
    {
        this.exporterConfigs.add(serverConfigTypeName);
    }


    protected HawtContainerInstantiator(String serverConfigTypeName, String clientConfigTypeName)
    {
        this(serverConfigTypeName);
        this.exporterConfigToImporterConfigs.put(serverConfigTypeName, Arrays.asList(new String[]{clientConfigTypeName}));
    }


    public String[] getSupportedIntents(ContainerTypeDescription description)
    {
//        List<String> results = new ArrayList<String>(Arrays.asList(super.getSupportedIntents(description)));
//        results.addAll(Arrays.asList(intents));
//        return (String[])results.toArray(new String[results.size()]);

        List<String> results = new ArrayList<String>();
        String[] genericIntents = super.getSupportedIntents(description);
        for (int i = 0; i < genericIntents.length; i++)
            results.add(genericIntents[i]);
        for (int i = 0; i < intents.length; i++)
            results.add(intents[i]);
        return (String[]) results.toArray(new String[] {});
    }

    @Override
    public IContainer createInstance(ContainerTypeDescription type, Map<String, ? > options) throws ContainerCreateException
    {
        if(type.isServer())
            return new HawtServerContainer(options);
        return new HawtClientContainer();
    }
}
