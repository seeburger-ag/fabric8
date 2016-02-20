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
package com.seeburger.ecf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.core.provider.BaseRemoteServiceContainerInstantiator;
import org.eclipse.ecf.core.provider.IRemoteServiceContainerInstantiator;

public class HawtInstantiator extends BaseRemoteServiceContainerInstantiator implements IRemoteServiceContainerInstantiator
{
    public static final String HAWT_CONFIG_TYPE = "hawt";

    @Override
    public String[] getSupportedConfigs(ContainerTypeDescription description)
    {
        return super.getSupportedConfigs(description);
    }

    public static final String CONFIG_PARAM = "configuration";

    protected static final String[] hawtIntents = new String[] { "hawt" };

    protected HawtInstantiator(String serverConfigTypeName) {
//        this.exporterConfigs.add(serverConfigTypeName);
    }

    protected HawtInstantiator(String serverConfigTypeName, String clientConfigTypeName) {
        this(serverConfigTypeName);
//        this.exporterConfigToImporterConfigs.put(serverConfigTypeName,
//                Arrays.asList(new String[] { clientConfigTypeName }));
    }

    public String[] getSupportedIntents(ContainerTypeDescription description) {
        List<String> results = new ArrayList<String>(Arrays.asList(super.getSupportedIntents(description)));
        results.addAll(Arrays.asList(hawtIntents));
        return (String[]) results.toArray(new String[results.size()]);
    }

//    protected Configuration getConfigurationFromParams(ContainerTypeDescription description,
//            Map<String, ?> parameters) {
//        return getParameterValue(parameters, CONFIG_PARAM, Configuration.class, null);
//    }


//    @Override
//    public IContainer createInstance(ContainerTypeDescription description, Map<String, ?> parameters) {
////        return createInstance(description, parameters, getConfigurationFromParams(description, parameters));
//    }
}



