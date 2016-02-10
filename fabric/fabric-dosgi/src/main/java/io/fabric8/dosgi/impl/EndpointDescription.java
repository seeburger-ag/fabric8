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
package io.fabric8.dosgi.impl;

import java.util.Map;

import org.osgi.framework.ServiceReference;

import io.fabric8.dosgi.capset.Attribute;
import io.fabric8.dosgi.capset.Capability;

/**
 * A description of an endpoint that provides sufficient information for a
 * compatible distribution provider to create a connection to this endpoint
 *
 * An Endpoint Description is easy to transfer between different systems because
 * it is property based where the property keys are strings and the values are
 * simple types. This allows it to be used as a communications device to convey
 * available endpoint information to nodes in a network.
 *
 * An Endpoint Description reflects the perspective of an <i>importer</i>. That
 * is, the property keys have been chosen to match filters that are created by
 * client bundles that need a service. Therefore the map must not contain any
 * <code>service.exported.*</code> property and must contain the corresponding
 * <code>service.imported.*</code> ones.
 *
 * The <code>service.intents</code> property must contain the intents provided
 * by the service itself combined with the intents added by the exporting
 * distribution provider. Qualified intents appear fully expanded on this
 * property.
 *
 * @Immutable
 * @version $Revision: 8645 $
 */

public class EndpointDescription extends org.osgi.service.remoteserviceadmin.EndpointDescription implements Capability {



    public EndpointDescription(Map<String, Object> properties)
    {
        super(properties);
    }

    public EndpointDescription(ServiceReference reference, Map<String, Object> properties)
    {
        super(reference, properties);
    }

    public Attribute getAttribute(String name) {
        Object val = getProperties().get(name);
        return val != null ? new Attribute( name, val ) : null;
    }
}
