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
import java.util.Hashtable;
import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.remoteservice.provider.IRemoteServiceDistributionProvider;
import org.eclipse.ecf.remoteservice.provider.RemoteServiceDistributionProvider;

import com.seeburger.ecf.HawtNamespace;


@Component(enabled=true,immediate=true)
@Service(value=IRemoteServiceDistributionProvider.class)
public class HawtRemoteServiceDistributionProvider extends RemoteServiceDistributionProvider
{

    public static final String HAWT_SERVER_CONFIG_NAME = HawtRemoteServiceDistributionProviderClient.SERVER_PROVIDER_NAME;//"ecf.hawt";
    private Map<String, ? > properties;



    public HawtRemoteServiceDistributionProvider()
    {
        super();
    }


    @Activate
    public void activate(Map<String, ?> properties)
    {
        setName(HawtRemoteServiceDistributionProviderClient.SERVER_PROVIDER_NAME);
        setServer(true);
        setHidden(false);
        setNamespace(HawtNamespace.INSTANCE);
//        ContainerTypeDescription typeDescription = getContainerTypeDescription();
//        System.out.println(typeDescription);
        setInstantiator(new HawtContainerInstantiator(HawtRemoteServiceDistributionProviderClient.SERVER_PROVIDER_NAME));
        setDescription("Fabric Hawt.io Provider");
        this.properties = properties;


    }


    @Override
    public Dictionary<String, ? > getContainerTypeDescriptionProperties()
    {
        return new Hashtable<>(properties);
    }
}
