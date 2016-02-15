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
package io.fabric8.dosgi.impl;

import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ImportReference;

public class ImportReferenceImpl implements ImportReference
{

    private ServiceReference reference;
    private EndpointDescription description;



    public ImportReferenceImpl(ServiceReference reference, EndpointDescription description)
    {
        super();
        this.reference = reference;
        this.description = description;
    }


    @Override
    public ServiceReference getImportedService()
    {
        return reference;
    }


    @Override
    public EndpointDescription getImportedEndpoint()
    {
        return description;
    }

}



