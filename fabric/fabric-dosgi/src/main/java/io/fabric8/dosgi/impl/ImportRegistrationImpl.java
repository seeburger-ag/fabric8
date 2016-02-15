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

import java.util.HashSet;
import java.util.Set;

import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.remoteserviceadmin.ImportReference;


public class ImportRegistrationImpl implements org.osgi.service.remoteserviceadmin.ImportRegistration {

    final ServiceRegistration importedService;
    ImportReference importedReference;
    final Set<ListenerHook.ListenerInfo> listeners;
    boolean closed;
    private RemoteServiceAdminImpl admin;

    public ImportRegistrationImpl(ServiceRegistration importedService, ImportReference importedReference, RemoteServiceAdminImpl admin) {
        this.listeners = new HashSet<ListenerHook.ListenerInfo>();
        this.importedService = importedService;
        this.importedReference = importedReference;
        this.admin = admin;
    }

    public ServiceRegistration getImportedService() {
        return closed ? null : importedService;
    }


    public void close() {
        admin.unImportService(this);
        importedService.unregister();
        closed = true;
    }

    @Override
    public ImportReference getImportReference()
    {
        return closed ? null : importedReference;
    }

    @Override
    public Throwable getException()
    {
        return null;
    }

}
