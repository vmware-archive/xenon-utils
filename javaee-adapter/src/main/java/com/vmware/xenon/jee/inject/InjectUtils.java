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

import static com.vmware.xenon.common.UriUtils.FIELD_NAME_FACTORY_LINK;
import static com.vmware.xenon.common.UriUtils.FIELD_NAME_SELF_LINK;
import static com.vmware.xenon.common.UriUtils.buildUri;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import javax.inject.Provider;
import javax.ws.rs.Path;

import com.google.inject.Injector;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.jee.host.InjectableHost;

/**
 * Util class related to CDI / Inject
 */
public class InjectUtils {

    public static FactoryService createCDIFactory(StatefulService svc, InjectableHost host) {
        return createCDIFactory(svc, host.getInjector().getProvider(svc.getClass()));
    }

    public static FactoryService createCDIFactory(StatefulService svc, Provider<? extends StatefulService> statefulSvcProvider) {
        CDIEnabledFactoryService factoryService = new CDIEnabledFactoryService(svc.getStateType()) {
            @Override
            public Service defaultInstance() {
                return svc;
            }
        };
        factoryService.setStatefulSvcProvider(statefulSvcProvider);
        return factoryService;
    }

    public static FactoryService createCDIFactory(StatefulService svc, Injector injector) {
        return createCDIFactory(svc, injector.getProvider(svc.getClass()));
    }


    public static FactoryService startCDIFactoryService(StatefulService svc, Injector injector, ServiceHost host) {
        return startCDIFactoryService(svc, injector.getProvider(svc.getClass()), host);
    }


    public static FactoryService startCDIFactoryService(StatefulService svc, Provider<? extends StatefulService> statefulSvcProvider, ServiceHost host) {
        FactoryService cdiFactory = createCDIFactory(svc, statefulSvcProvider);
        String value = extractFactoryUri(svc.getClass());
        return startCDIFactoryService(cdiFactory, value, host);
    }

    public static String extractPath(AnnotatedElement element) {
        Path path = element.getAnnotation(Path.class);
        if (path != null) {
            return path.value();
        }
        return null;
    }

    public static String extractUri(Class<? extends Service> svc) {
        Path pathAnnotation = getPathAnnotation(svc);
        if (pathAnnotation == null) {
            try {
                Field f = svc.getField(FIELD_NAME_SELF_LINK);
                return (String) f.get(null);
            } catch (Exception e) {
                throw new RuntimeException("Unable to find URI from service instance. Neither Path annotation is present nor " + FIELD_NAME_FACTORY_LINK + " is present");
            }
        } else {
            return pathAnnotation.value();
        }
    }

    private static Path getPathAnnotation(Class<? extends Service> svc) {
        Path annotation = svc.getAnnotation(Path.class);
        if (annotation == null) {
            Class<?>[] classes = svc.getInterfaces();
            for (int cnt = 0; annotation == null && cnt < classes.length; cnt++) {
                annotation = classes[cnt].getAnnotation(Path.class);
            }
        }
        return annotation;
    }

    public static String extractFactoryUri(Class<? extends Service> svc) {
        Path pathAnnotation = getPathAnnotation(svc);
        if (pathAnnotation == null) {
            try {
                Field f = svc.getField(FIELD_NAME_FACTORY_LINK);
                return (String) f.get(null);
            } catch (Exception e) {
                throw new RuntimeException("Unable to find URI from service instance. Neither Path annotation is present nor " + FIELD_NAME_FACTORY_LINK + " is present");
            }
        } else {
            return pathAnnotation.value();
        }
    }

    public static FactoryService startCDIFactoryService(FactoryService svc, String URI, ServiceHost host) {
        host.startService(Operation.createPost(buildUri(host, URI)), svc);
        return svc;
    }
}
