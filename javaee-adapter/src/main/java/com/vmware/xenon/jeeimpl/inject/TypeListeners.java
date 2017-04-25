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

package com.vmware.xenon.jeeimpl.inject;

import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.common.UriUtils.extendUri;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

import com.google.inject.MembersInjector;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.jee.consumer.InterceptorChain;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer.ClientBuilder;
import com.vmware.xenon.jee.consumer.StatefulServiceContract;
import com.vmware.xenon.jee.inject.InjectLogger;
import com.vmware.xenon.jee.inject.InjectRestProxy;
import com.vmware.xenon.jee.inject.InjectStatefulProxy;
import com.vmware.xenon.jee.util.PropertyManager;

/**
 * Bootstraps Guice with Annotation listeners
 */
public class TypeListeners {

    static class Slf4JTypeListener implements TypeListener {

        @Override
        public <T> void hear(TypeLiteral<T> typeLiteral, TypeEncounter<T> typeEncounter) {
            Class<?> clazz = typeLiteral.getRawType();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.getType() == Logger.class && field.isAnnotationPresent(InjectLogger.class)) {
                        typeEncounter.register(new XenonSlf4jLogInjector<T>(field));
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
    }


    static class ServiceProxyAnnotationListener implements TypeListener {

        @Inject
        private Provider<ServiceHost> host;

        @Inject
        private Provider<Map<String, InterceptorChain>> allInterceptors;

        @Override
        public <T> void hear(TypeLiteral<T> typeLiteral, TypeEncounter<T> typeEncounter) {
            Class<?> clazz = typeLiteral.getRawType();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(InjectRestProxy.class)) {
                        typeEncounter.register(new ServiceProxyInjector<>(field, this.host, this.allInterceptors));
                    } else if (field.isAnnotationPresent(InjectStatefulProxy.class)) {
                        typeEncounter.register(new StatefulServiceProxyInjector<>(field, this.host, this.allInterceptors));
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
    }

    static class StatefulServiceProxyInjector<T> implements MembersInjector<T> {
        private final Field field;
        private final Provider<ServiceHost> host;
        private final Provider<Map<String, InterceptorChain>> allInterceptors;

        StatefulServiceProxyInjector(Field field, Provider<ServiceHost> host, Provider<Map<String, InterceptorChain>> allInterceptors) {
            this.field = field;
            this.host = host;
            this.allInterceptors = allInterceptors;
            this.field.setAccessible(true);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void injectMembers(T instance) {
            try {
                ClientBuilder<StatefulServiceContract<?>> newBuilder = JaxRsServiceConsumer.newBuilder();
                InjectStatefulProxy annotation = this.field.getAnnotation(InjectStatefulProxy.class);
                Class<StatefulServiceContract<?>> type = (Class<StatefulServiceContract<?>>) this.field.getType();
                newBuilder.withHost(this.host.get())
                        .withResourceInterface(type)
                        .withGenericTypeResolution("T", annotation.documentKind());
                String strUri = annotation.baseUri();
                if (strUri.length() > 0) {
                    URI baseUri = buildUri(PropertyManager.getProperty(strUri, strUri));
                    newBuilder.withBaseUri(extendUri(baseUri, annotation.serviceUri()));
                } else {
                    newBuilder.withBaseUri(extendUri(this.host.get().getUri(), annotation.serviceUri()));
                }
                String interceptorChain = annotation.interceptorName();
                if (interceptorChain.length() > 0) {
                    if (this.allInterceptors.get().containsKey(interceptorChain)) {
                        newBuilder.withInterceptor(this.allInterceptors.get().get(interceptorChain));
                    } else {
                        throw new RuntimeException("No Interceptor chain found with name " + interceptorChain);
                    }
                }
                this.field.set(instance, newBuilder.build());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class ServiceProxyInjector<T> implements MembersInjector<T> {
        private final Field field;
        private final Provider<ServiceHost> host;
        private final Provider<Map<String, InterceptorChain>> allInterceptors;

        ServiceProxyInjector(Field field, Provider<ServiceHost> host, Provider<Map<String, InterceptorChain>> allInterceptors) {
            this.field = field;
            this.host = host;
            this.allInterceptors = allInterceptors;
            this.field.setAccessible(true);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void injectMembers(T instance) {
            try {
                ClientBuilder<T> newBuilder = JaxRsServiceConsumer.newBuilder();
                InjectRestProxy injectRestProxy = this.field.getAnnotation(InjectRestProxy.class);
                Class<T> type = (Class<T>) this.field.getType();
                if (type.isInstance(StatefulServiceContract.class)) {
                    throw new RuntimeException("Please use @InjectStatefulProxy annotation for dealing with StatefulServiceContract");
                }
                newBuilder.withHost(this.host.get()).withResourceInterface(type);
                String baseUri = injectRestProxy.baseUri();
                if (baseUri.length() > 0) {
                    newBuilder.withBaseUri(PropertyManager.getProperty(baseUri, baseUri));
                }
                String interceptorChain = injectRestProxy.interceptorName();
                if (interceptorChain.length() > 0) {
                    if (this.allInterceptors.get().containsKey(interceptorChain)) {
                        newBuilder.withInterceptor(this.allInterceptors.get().get(interceptorChain));
                    } else {
                        throw new RuntimeException("No Interceptor chain found with name " + interceptorChain);
                    }
                }
                this.field.set(instance, newBuilder.build());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }


    static class XenonSlf4jLogInjector<T> implements MembersInjector<T> {
        private final Field field;
        private final Logger logger;

        XenonSlf4jLogInjector(Field field) {
            this.field = field;
            this.logger = LoggerFactory.getLogger(field.getDeclaringClass());
            field.setAccessible(true);
        }

        @Override
        public void injectMembers(T instance) {
            try {
                this.field.set(instance, this.logger);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
