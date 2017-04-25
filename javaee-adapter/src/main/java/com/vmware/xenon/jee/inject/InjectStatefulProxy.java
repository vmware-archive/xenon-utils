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

package com.vmware.xenon.jee.inject;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

import com.vmware.xenon.common.ServiceDocument;

/**
 * Injects a Proxy handler corresponding to {@link com.vmware.xenon.jee.consumer.StatefulServiceContract} capable of converting method annotations to Xenon Operation.
 * This replaces the need for using {@link com.vmware.xenon.jee.consumer.JaxRsServiceConsumer} in most of the places if not all.
 */
@BindingAnnotation
@Target({FIELD})
@Retention(RUNTIME)
public @interface InjectStatefulProxy {

    /**
     * @return the base URI pointing to the service host where the resource is running.
     * This value can either be
     *  1. an absolute value like http://host-ip:8000 or
     *  2. a system property / env value like EXTERNAL_SVC( in shortThis should be resolvable via {@link com.vmware.xenon.jee.util.PropertyManager} )
     *
     *  Default is current (local host) host.
     *
     *  Note that, before starting the InjectableHost, PropertyManager needs to be properly initialized.
     */
    String baseUri() default "";

    /**
     * @return {@link com.vmware.xenon.jee.consumer.OperationInterceptor} bean name which needs to be wired during proxy method invocation
     *
     * Interceptors needs to be registered using MapBinder like example shown below.
     * Specify a single bean name which needs to be used while creating a proxy instance
     *
     * <pre>
     * <code>
     *     MapBinder&lt;String, InterceptorChain&gt; mapbinder = MapBinder.newMapBinder(binder(), String.class, InterceptorChain.class);
     *     mapbinder.addBinding(OPERATION_EXPIRY_IN_2_MINS).toInstance(interceptorChain);
     * </code>
     * </pre>
     */
    String interceptorName() default "";

    /**
     *  the document type used in stateful service
     */
    Class<? extends ServiceDocument> documentKind();

    /**
     *  Stateful service URI / factory link that needs to be appended with base uri to interact with stateful service
     */
    String serviceUri();
}
