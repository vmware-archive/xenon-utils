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

import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.jee.inject.InjectUtils.extractFactoryUri;
import static com.vmware.xenon.jee.inject.InjectUtils.extractUri;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.Arguments;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.jee.inject.InjectUtils;
import com.vmware.xenon.jeeimpl.inject.BaseXenonModule;

/**
 * Host that supports Inject / CDI
 */
public interface InjectableHost {

    Injector getInjector();

    void setInjector(Injector injector);

    ServiceHost getHost();

    static HostBuilder newBuilder() {
        return new HostBuilder();
    }

    class HostBuilder {
        private List<Class<? extends Service>> privilegedServices = new ArrayList<>();
        private String[] cmdArgs = new String[0];
        private Arguments args;
        private List<Module> cdiModule = new ArrayList<>();
        private Map<String, FactoryService> factoryServices = new HashMap<>();
        private Map<String, StatelessService> statelessServices = new HashMap<>();
        private Map<String, StatefulService> statefulServices = new HashMap<>();
        private ServiceHost underlyer;

        public HostBuilder withArguments(String[] args) {
            this.cmdArgs = Arrays.copyOf(args, args.length);
            return this;
        }

        public HostBuilder withArguments(ServiceHost.Arguments args) {
            this.args = args;
            return this;
        }

        /**
         * Usually required only when you have test case.
         * Host should be preferably sub class of {@link JEEXenonServiceHost} or VerificationHost in case of test cases
         */
        public HostBuilder wrapHost(ServiceHost host) {
            this.underlyer = host;
            return this;
        }

        /**
         * Configures a service as a privileged service. Note that the service needs to be started using withService method
         */
        public HostBuilder asPrivilegedService(Class<? extends Service> serviceType) {
            this.privilegedServices.add(serviceType);
            return this;
        }

        /**
         * Custom Guice module which needs to configured
         */
        public HostBuilder withCdiModule(Module guiceModule) {
            this.cdiModule.add(guiceModule);
            return this;
        }

        /**
         * Configure a stateless Service with CDI
         */
        public HostBuilder withStatelessService(StatelessService service) {
            this.statelessServices.put(extractUri(service.getClass()), service);
            return this;
        }

        /**
         * Configure a Stateful Service with CDI
         */
        public HostBuilder withStatefulService(StatefulService service) {
            this.statefulServices.put(extractUri(service.getClass()), service);
            return this;
        }

        /**
         * Configure a custom factory service with out implicit CDI
         */
        public HostBuilder withFactory(FactoryService service) {
            this.factoryServices.put(extractFactoryUri(service.getClass()), service);
            return this;
        }

        public InjectableHost buildAndStart() throws Throwable {
            JEEXenonServiceHost injectableHost;
            ServiceHost host;
            if (this.underlyer == null) { // this should be default scenario
                injectableHost = new JEEXenonServiceHost();
            } else {
                injectableHost = new JEEXenonServiceHost(this.underlyer);
            }
            host = injectableHost.getHost();
            if (host.isStarted()) {
                Utils.log(InjectableHost.class, InjectableHost.class.getSimpleName(), Level.INFO,
                        "Host already started. Skipping initialization & starting default core services");
            } else {
                if (this.args != null) {
                    host.initialize(this.args);
                }
                if (this.cmdArgs != null) {
                    host.initialize(this.cmdArgs);
                }
                host.start();
                host.startDefaultCoreServicesSynchronously();
            }

            // we will not be having thousands of module. This should be fine
            this.cdiModule.add(0, new BaseXenonModule(injectableHost.getHost()));
            Injector injector = Guice.createInjector(this.cdiModule);
            injectableHost.setInjector(injector);

            startPrivilegedSvc(injectableHost);
            this.statelessServices.forEach((uri, statelessService) -> {
                Operation post = Operation.createPost(buildUri(host, uri));
                StatelessService instance = injector.getInstance(statelessService.getClass());
                host.startService(post, instance);
            });
            this.statefulServices.forEach((uri, statefulService) -> {
                Operation post = Operation.createPost(buildUri(host, uri));
                FactoryService cdiFactory = InjectUtils.createCDIFactory(statefulService, injectableHost);
                host.startService(post, cdiFactory);
            });
            this.factoryServices.forEach((uri, factoryService) -> {
                Operation post = Operation.createPost(buildUri(host, uri));
                host.startService(post, factoryService);
            });

            return injectableHost;
        }

        private void startPrivilegedSvc(JEEXenonServiceHost injectableHost) {
            if (injectableHost.getHost() instanceof JEEXenonServiceHost) {
                this.privilegedServices.forEach(injectableHost::addPrivilegedService);
            } else {
                Method[] methods = injectableHost.getHost().getClass().getMethods();
                Stream.of(methods).filter(method -> method.getName().equals("addPrivilegedService")).findFirst().ifPresent(method -> {
                    if (method.isAccessible()) {
                        this.privilegedServices.forEach(svc -> {
                            try {
                                method.invoke(injectableHost.getHost(), svc);
                            } catch (Exception e) {
                                Utils.log(InjectableHost.class, InjectableHost.class.getSimpleName(), Level.WARNING, "Unable to add privileged service %s to service host due to %s", svc.getName(), Utils.toString(e));
                            }
                        });
                    } else {
                        this.privilegedServices.forEach(svc -> {
                            Utils.log(InjectableHost.class, InjectableHost.class.getSimpleName(), Level.WARNING, "Unable to add privileged service %s to service host. Does it have a public method to add privilege service like VerificationHost or JEEXenonServiceHost", svc.getName());
                        });
                    }
                });
            }
        }
    }
}
