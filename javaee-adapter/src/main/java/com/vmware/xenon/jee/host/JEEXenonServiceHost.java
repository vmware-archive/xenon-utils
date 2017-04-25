/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.xenon.jee.host;

import com.google.inject.Injector;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

/**
 * Initializes the Service Host with JEE constructs like Injector etc.,
 */
public class JEEXenonServiceHost extends ServiceHost implements InjectableHost {

    private Injector injector;
    private ServiceHost underlyer;

    public JEEXenonServiceHost() {
    }

    protected JEEXenonServiceHost(ServiceHost underlyer) {
        this.underlyer = underlyer;
    }

    public Injector getInjector() {
        return this.injector;
    }

    @Override
    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ServiceHost getHost() {
        if (this.underlyer == null) {
            return this;
        }
        return this.underlyer;
    }

    @Override
    public void addPrivilegedService(Class<? extends Service> serviceType) {
        super.addPrivilegedService(serviceType);
    }
}
