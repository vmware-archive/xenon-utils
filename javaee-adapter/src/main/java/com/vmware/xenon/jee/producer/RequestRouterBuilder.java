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

package com.vmware.xenon.jee.producer;

import static com.vmware.xenon.common.Service.ServiceOption.URI_NAMESPACE_OWNER;
import static com.vmware.xenon.common.UriUtils.URI_PATH_CHAR;
import static com.vmware.xenon.common.UriUtils.buildUriPath;
import static com.vmware.xenon.common.UriUtils.normalizeUriPath;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.jeeimpl.reflect.MethodInfo;
import com.vmware.xenon.jeeimpl.reflect.MethodInfoBuilder;

/**
 * Creates com.vmware.xenon.common.RequestRouter to route requests to appropriate methods
 * identified by jax-rs annotations
 * <p>
 * Each public method needs to be annotated with HTTP verb (sub-class of HTTPMethod annotation ie.,
 * GET, POST, PUT, DELETE, PATCH etc.,) and can be annotated with @Path URI relative to the
 * self-link of the service and can make use of @QueryParam and @PathParam to extract values
 * from URI.
 * </p><p>
 * Such methods can have at max one parameter which is not annotated and the parameter type should be Operation.
 * If Operation is received as an argument, then the method is assumed to hold responsibility of setting appropriate
 * response body and invoke operation.complete().
 * </p>
 * If method doesn't gets Operation as an input argument then Operation.complete
 * is invoked automatically. If such method returns non-void type, the return value is set as body.
 * <p>Methods can return CompletableFuture and actual Operation will be completed upon the future completion by the framework</p>
 * <p>
 * Methods which do not have HTTPMethod annotations are skipped. </p>
 */
public class RequestRouterBuilder {

    private static class DynamicPathParamMatcher implements Predicate<Operation> {

        private String[] expectedPathTokens;

        /**
         * Matches path with path param i.e., curly braces
         *
         * @param pathWithParams Normalized path param
         */
        DynamicPathParamMatcher(String pathWithParams) {
            this.expectedPathTokens = pathWithParams.split(URI_PATH_CHAR);
        }

        @Override
        public boolean test(Operation operation) {
            String path = operation.getUri().getPath();
            String[] actualTokens = path.split(URI_PATH_CHAR);
            if (actualTokens.length != this.expectedPathTokens.length) {
                return false;
            }

            for (int i = 0; i < actualTokens.length; i++) {
                // if path don't match, it has to be path param
                String pathParam = this.expectedPathTokens[i];
                if (!pathParam.equals(actualTokens[i])) {
                    if (pathParam.length() > 0
                            && pathParam.charAt(0) == '{'
                            && pathParam.charAt(pathParam.length() - 1) == '}') {
                        continue;
                    }
                    return false;
                }
            }
            return true;
        }

    }

    private static Logger log = LoggerFactory.getLogger(RequestRouterBuilder.class);

    static Predicate<Operation> newUriMatcher(String pathWithParams) {
        if (pathWithParams.contains("{") && pathWithParams.contains("}")) { //URI with path param
            return new DynamicPathParamMatcher(pathWithParams);
        } else {
            return operation -> normalizeUriPath(operation.getUri().getPath())
                    .equals(pathWithParams);
        }

    }

    /**
     * IllegalArgumentException if HTTPMethod is not mappable to Xenon Service.Action
     *
     * @param xenonService
     * @return
     */
    public static RequestRouter parseJaxRsAnnotations(Service xenonService,
            Class<?>... interfaces) {
        if (!xenonService.hasOption(URI_NAMESPACE_OWNER)) {
            throw new IllegalArgumentException("URI_NAMESPACE_OWNER option needs to be enabled");
        }
        List<MethodInfo> httpMethods = MethodInfoBuilder
                .parseServiceForJaxRsInfo(xenonService.getClass(), Collections.emptyMap());
        RequestRouter router = new RequestRouter();
        registerRoutes(router, xenonService, httpMethods);
        Stream.of(interfaces)
                .filter(Objects::nonNull)
                .filter(iFace -> iFace.isAssignableFrom(xenonService.getClass()))
                .forEach(iFace -> registerRoutes(router, xenonService, MethodInfoBuilder
                        .parseInterfaceForJaxRsInfo(iFace, Collections.emptyMap())));
        return router;
    }

    private static void registerRoutes(RequestRouter router, Service xenonService,
            List<MethodInfo> httpMethods) {
        httpMethods.forEach(methodInfo -> {
            String path = buildUriPath(xenonService.getSelfLink(), methodInfo.getUriPath());
            Predicate<Operation> predicate = newUriMatcher(path);
            RoutingOperationHandler routingOperationHandler = getRoutingOperationHandler(xenonService, methodInfo, path);
            routingOperationHandler.init();
            router.register(methodInfo.getAction(), predicate, routingOperationHandler,
                    "JaxRs annotation based Router");
            log.info("Registered {} on {} to {}",
                    new Object[] { methodInfo.getAction(), path, methodInfo.getName() });
        });
    }

    protected static RoutingOperationHandler getRoutingOperationHandler(Service xenonService, MethodInfo methodInfo, String path) {
        return new RoutingOperationHandler(path,methodInfo, xenonService);
    }
}
