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

import java.net.URI;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDCreateException;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.core.identity.URIID;

@Component
@Service(value=Namespace.class)
public class HawtNamespace extends Namespace
{
    /** field <code>serialVersionUID</code> */
    private static final long serialVersionUID = -7390682488271441584L;
    public static final String NAME = "ecf.namespace.hawt";
    public static HawtNamespace INSTANCE;

    public HawtNamespace() {
        super(NAME, "Hawt Namespace");
        INSTANCE = this;
    }

    @Override
    public ID createInstance(Object[] parameters) throws IDCreateException {
        try {
            URI uri = null;
            if (parameters[0] instanceof URI)
                uri = (URI) parameters[0];
            else if (parameters[0] instanceof String)
                uri = URI.create((String) parameters[0]);
            if (uri == null)
                throw new IllegalArgumentException("the first parameter must be of type String or URI");
            return new URIID(INSTANCE, uri);
        } catch (Exception e) {
            throw new IDCreateException("Could not create Hawt ID", e); //$NON-NLS-1$
        }
    }

    @Override
    public String getScheme() {
        return "ecf.hawt";
    }
}



