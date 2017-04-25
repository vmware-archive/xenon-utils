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

package com.vmware.xenon.jee.consumer;

import static java.util.Objects.requireNonNull;

import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.common.UriUtils.extendUri;
import static com.vmware.xenon.common.Utils.DEFAULT_THREAD_COUNT;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;
import com.vmware.xenon.jeeimpl.consumer.ProxyHandler;
import com.vmware.xenon.jeeimpl.reflect.MethodInfo;

/**
 * Builder class to provide a proxy instance of the contract interface ( interface annotated with JAX-RS annotations)
 * <p>
 * Proxy instance is capable of converting method class to Xenon Operation and sends the Operation with the provided service host.
 */
public class JaxRsServiceConsumer {

    /**
     * Builder class to set various options
     */
    public static class ClientBuilder<R> {

        private Class<R> resourceInterface;
        private URI baseUri;
        private ServiceClient serviceClient;
        private ServiceHost host;
        private OperationInterceptor interceptor;
        private Map<String, Class<?>> typeResolution = null;
        private Supplier<URI> baseUriSupplier;
        private BiFunction<MethodInfo, Object[], Operation> opBuilder;
        private BiConsumer<ErrorContext, CompletableFuture<?>> errorHandler;
        private BiFunction<Operation, MethodInfo, Object> responseDecoder;

        private Logger log = LoggerFactory.getLogger(getClass());

        private ClientBuilder() {
        }

        @SuppressWarnings("unchecked")
        public R build() {
            requireNonNull(this.resourceInterface,
                    "Interface capturing the Service API is required");
            // need baseUriSupplier
            if (this.baseUri == null && this.baseUriSupplier == null) {
                requireNonNull(this.host, "Base URI is required");
                this.baseUri = this.host.getUri();
            }
            if (this.host == null) {
                this.log.warn(
                        "Need Xenon service host used to bootstrap the process. Proceeding with default service client sender");
                this.serviceClient = createServiceClient();
            } else {
                this.serviceClient = this.host.getClient();
            }

            if (this.baseUriSupplier == null) {
                this.baseUriSupplier = () -> addPathFromAnnotation(this.resourceInterface,
                        this.baseUri);
            }

            /*if (baseUriSupplier == null && serviceInfo == null) {
                baseUriSupplier = () -> addPathFromAnnotation(resourceInterface, baseUri);
            } else if (baseUriSupplier == null) {
                baseUriSupplier = () -> extendUri(baseUri, serviceInfo.serviceLink());
            }*/
            ProxyHandler proxyHandler = new ProxyHandler();
            proxyHandler.setResourceInterface(this.resourceInterface);
            proxyHandler.setBaseUriSupplier(this.baseUriSupplier);
            proxyHandler.setClient(this.serviceClient);
            if (this.host != null) {
                proxyHandler.setReferrer(this.host.getPublicUri().toString());
            }
            if (this.errorHandler != null) {
                proxyHandler.setErrorHandler(this.errorHandler);
            }
            if (this.responseDecoder != null) {
                proxyHandler.setResponseDecoder(this.responseDecoder);
            }
            if (this.opBuilder != null) {
                proxyHandler.setOpBuilder(this.opBuilder);
            }
            if (this.typeResolution != null) {
                proxyHandler.setTypeResolution(this.typeResolution);
            }
            if (this.interceptor != null) {
                proxyHandler.setInterceptor(this.interceptor);
            }

            proxyHandler.init();

            return (R) Proxy.newProxyInstance(this.resourceInterface.getClassLoader(),
                    new Class<?>[] { this.resourceInterface }, proxyHandler);
        }

        /**
         * Base URI of the service
         *
         * @param baseUri
         * @return
         */
        public ClientBuilder<R> withBaseUri(String baseUri) {
            this.baseUri = buildUri(baseUri);
            return this;
        }

        public ClientBuilder<R> withBaseUri(Supplier<URI> baseUriSupplier) {
            this.baseUriSupplier = baseUriSupplier;
            return this;
        }

        /**
         * Base URI of the service
         *
         * @param baseUri
         * @return
         */
        public ClientBuilder<R> withBaseUri(URI baseUri) {
            this.baseUri = baseUri;
            return this;
        }

        /**
         * Use Custom error handler instead of default error handler which wraps failures to HTTPError
         *
         * @param errorHandler
         * @return
         */
        public ClientBuilder<R> withErrorHandler(
                BiConsumer<ErrorContext, CompletableFuture<?>> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Hint to resolve generic type
         *
         * @param typeName
         * @param type
         * @return
         */
        public ClientBuilder<R> withGenericTypeResolution(String typeName, Class<?> type) {
            if (this.typeResolution == null) {
                this.typeResolution = new HashMap<>();
            }
            this.typeResolution.put(typeName, type);
            return this;
        }

        public ClientBuilder<R> withHost(ServiceHost host) {
            this.host = host;
            return this;
        }

        /**
         * Interceptor to be invoked before and after the API (Operation) invocation
         *
         * @param interceptor
         * @return
         */
        public ClientBuilder<R> withInterceptor(OperationInterceptor interceptor) {
            this.interceptor = interceptor;
            return this;
        }

        public ClientBuilder<R> withOperationBuilder(
                BiFunction<MethodInfo, Object[], Operation> opBuilder) {
            this.opBuilder = opBuilder;
            return this;
        }

        public ClientBuilder<R> withResourceInterface(Class<R> resourceInterface) {
            this.resourceInterface = resourceInterface;
            return this;
        }

        /**
         * Use custom response decoder instead of default gson decoder
         */
        public ClientBuilder<R> withResponseDecoder(
                BiFunction<Operation, MethodInfo, Object> responseDecoder) {
            this.responseDecoder = responseDecoder;
            return this;
        }

        public ClientBuilder<R> withSystemAuthContextFrom(Service svc) {
            return withInterceptor(new Interceptors.AuthContextPopulator(svc));
        }
    }

    private static Logger log = LoggerFactory.getLogger(JaxRsServiceConsumer.class);

    public static URI addPathFromAnnotation(AnnotatedElement element, URI parent) {
        String pathToBeAdded = parsePath(element);
        return extendUri(parent, pathToBeAdded);
    }

    public static SSLContext createAcceptAllSslContext() throws Exception {
        TrustManager[] byPassTrustManagers = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        SSLContext sslContext = SSLContext.getInstance(ServiceClient.TLS_PROTOCOL_NAME);
        sslContext.init(null, byPassTrustManagers, new SecureRandom());
        return sslContext;

    }

    public static ServiceClient createServiceClient() {
        try {
            ServiceClient serviceClient = NettyHttpServiceClient.create(
                    JaxRsServiceConsumer.class.getSimpleName(),
                    Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT),
                    Executors.newScheduledThreadPool(DEFAULT_THREAD_COUNT));
            serviceClient.setSSLContext(createAcceptAllSslContext());
            serviceClient.start();
            return serviceClient;
        } catch (URISyntaxException illegalUri) {
            throw new RuntimeException(
                    "Unable to create ServiceClient. Is this because of HTTP PROXY settings ?",
                    illegalUri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builder for accessing ProxyHandler
     *
     * @return
     */
    public static <R> ClientBuilder<R> newBuilder() {
        return new ClientBuilder<>();
    }

    /**
     * @param resourceInterface interface holding the contract
     * @param host              Service host hosting the service
     * @param <R>               An instance of given interface is provided
     * @return
     */
    public static <R> R newProxy(Class<R> resourceInterface, ServiceHost host) {
        ClientBuilder<R> clientBuilder = JaxRsServiceConsumer.newBuilder();
        return clientBuilder
                .withHost(host)
                .withResourceInterface(resourceInterface)
                .build();
    }

    /**
     * @param resourceInterface interface holding the contract
     * @param baseUri           URI pointing to the service hosted outside of current host
     * @param host              Service host hosting the service
     * @param <R>               An instance of given interface is provided
     * @return
     */
    public static <R> R newProxy(Class<R> resourceInterface, String baseUri, ServiceHost host) {
        ClientBuilder<R> clientBuilder = JaxRsServiceConsumer.newBuilder();
        return clientBuilder
                .withHost(host)
                .withBaseUri(baseUri)
                .withResourceInterface(resourceInterface)
                .build();
    }

    /**
     * Use the variant which accepts service host instead of baseUri
     * <p>
     * This is recommended to be used only in test cases
     *
     * @param resourceInterface interface holding the contract
     * @param baseUri           base URI pointing to the host and port.
     *                          Note that, if @PATH annotation on interface is found as a child path of baseUri, path value will be ignored
     * @param <R>               An instance of given interface is provided
     * @return
     */
    @Deprecated
    public static <R> R newProxy(Class<R> resourceInterface, URI baseUri) {
        ClientBuilder<R> newBuilder = JaxRsServiceConsumer.newBuilder();
        return newBuilder
                .withBaseUri(baseUri)
                .withResourceInterface(resourceInterface)
                .build();
    }

    private static String parsePath(AnnotatedElement element) {
        Path path = element.getAnnotation(Path.class);
        if (path != null) {
            return path.value();
        }
        return null;
    }

}
