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

import org.osgi.service.remoteserviceadmin.ExportReference;

public class ExportRegistrationImpl implements org.osgi.service.remoteserviceadmin.ExportRegistration {

    final ExportReference exportedReference;
    boolean closed;
    private RemoteServiceAdminImpl admin;

    public ExportRegistrationImpl(ExportReference exportedReference, RemoteServiceAdminImpl admin) {
        this.exportedReference = exportedReference;
        this.admin = admin;
    }


    public void close() {
        admin.unExportService(this.getExportReference().getExportedService());
        closed = true;
    }

    @Override
    public ExportReference getExportReference()
    {
        return closed ? null : exportedReference;
    }

    @Override
    public Throwable getException()
    {
        return null;
    }

}
