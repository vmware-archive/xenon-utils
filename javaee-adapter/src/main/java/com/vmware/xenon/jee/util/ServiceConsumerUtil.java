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

package com.vmware.xenon.jee.util;

import static java.lang.Thread.sleep;

import static com.vmware.xenon.common.UriUtils.extendUri;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.validation.constraints.NotNull;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer.ClientBuilder;
import com.vmware.xenon.jee.consumer.OperationInterceptor;
import com.vmware.xenon.jee.consumer.StatefulServiceContract;
import com.vmware.xenon.jee.query.XenonQueryService;

/**
 * Utility class exposing few functions useful for a service consumer
 */
public class ServiceConsumerUtil {

    /**
     * Returns a future that gets completed after the delay. Value to be returned is provided by supplier
     */
    public static <T> CompletableFuture<T> getDelayFuture(long delayTime,
            Supplier<T> resultSupplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                sleep(delayTime);
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
            }
            future.complete(resultSupplier.get());
        }).start();
        return future;
    }

    /**
     * Returns a proxy instance of StatefulServiceContract pointing to the host (localhost) and suffix URI provided.
     * Result will be converted to given type
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends ServiceDocument> StatefulServiceContract<T> newLocalStatefulSvcContract(
            ServiceHost host, String factoryLink, Class<T> clazz) {
        ClientBuilder<StatefulServiceContract<T>> newBuilder = JaxRsServiceConsumer.newBuilder();
        Class<StatefulServiceContract<T>> resourceClass = (Class)StatefulServiceContract.class;
        return newBuilder
                .withHost(host)
                .withBaseUri(extendUri(host.getPublicUri(), factoryLink))
                .withResourceInterface(resourceClass)
                .withGenericTypeResolution("T", clazz)
                .build();
    }

    /**
     * Returns a proxy instance of XenonQueryService pointing to the localhost
     */
    public static XenonQueryService newQueryServiceClient(ServiceHost host) {
        ClientBuilder<XenonQueryService> newBuilder = JaxRsServiceConsumer.newBuilder();
        return newBuilder
                .withHost(host)
                .withResourceInterface(XenonQueryService.class)
                .build();
    }

    /**
     * Returns a proxy instance of XenonQueryService pointing to the localhost with all operations intercepted by given interceptor.
     * * See {@link com.vmware.xenon.jee.consumer.Interceptors} for default interceptors
     */
    public static XenonQueryService newQueryServiceClient(ServiceHost host,
                                                          OperationInterceptor interceptor) {
        ClientBuilder<XenonQueryService> newBuilder = JaxRsServiceConsumer.newBuilder();
        return newBuilder
                .withHost(host)
                .withInterceptor(interceptor)
                .withResourceInterface(XenonQueryService.class)
                .build();
    }

    /**
     * Returns a proxy instance of XenonQueryService pointing to the Base URI with given interceptor.
     * Interceptors could be added to invoke query service from System context for example.
     * See {@link com.vmware.xenon.jee.consumer.Interceptors} for more info
     *
     */
    public static XenonQueryService newQueryServiceClient(URI baseUri, ServiceHost host,
                                                          OperationInterceptor interceptor) {
        ClientBuilder<XenonQueryService> newBuilder = JaxRsServiceConsumer.newBuilder();
        return newBuilder
                .withBaseUri(baseUri)
                .withHost(host)
                .withResourceInterface(XenonQueryService.class)
                .withInterceptor(interceptor)
                .build();
    }

    /**
     * Returns a random available port
     *
     * @return available port
     * @throws IOException
     */
    public static int randomAvailablePort() throws IOException {

        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * Returns the last segment after forward slash. Useful to get a document id from the whole self link
     *
     * @param selfLink usually a documentSelfLink
     * @return
     */
    public static String selfLinkToId(@NotNull String selfLink) {
        return selfLink.substring(selfLink.lastIndexOf("/") + 1, selfLink.length());
    }

}
