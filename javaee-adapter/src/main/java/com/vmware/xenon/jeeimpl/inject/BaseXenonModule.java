/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.jeeimpl.inject;

import java.util.concurrent.TimeUnit;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.MapBinder;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.jee.consumer.InterceptorChain;
import com.vmware.xenon.jee.consumer.Interceptors;
import com.vmware.xenon.jee.inject.BeanNames;

/**
 * Intended to configure JaxRs Client based on custom annotations.
 *
 * Configures Listeners for {@link com.vmware.xenon.jee.inject.InjectLogger} and
 * {@link com.vmware.xenon.jee.inject.InjectRestProxy} annotation at the moment
 */
public class BaseXenonModule extends AbstractModule {

    private final ServiceHost host;

    public BaseXenonModule(ServiceHost host) {
        this.host = host;
    }

    @Override
    protected void configure() {
        bind(ServiceHost.class).toInstance(this.host);
        this.operationInterceptor();
        bindListener(Matchers.any(), new TypeListeners.Slf4JTypeListener());
        TypeListeners.ServiceProxyAnnotationListener listener = new TypeListeners.ServiceProxyAnnotationListener();
        requestInjection(listener);
        bindListener(Matchers.any(), listener);
    }

    protected MapBinder<String, InterceptorChain> operationInterceptor() {
        InterceptorChain interceptorChain = InterceptorChain.newBuilder().with(Interceptors.expireOperationIn(TimeUnit.MINUTES, 2))
                .build();
        MapBinder<String, InterceptorChain> mapbinder = MapBinder.newMapBinder(binder(), String.class, InterceptorChain.class);
        mapbinder.addBinding(BeanNames.OPERATION_EXPIRY_IN_2_MINS).toInstance(interceptorChain);
        return mapbinder;
    }
}
