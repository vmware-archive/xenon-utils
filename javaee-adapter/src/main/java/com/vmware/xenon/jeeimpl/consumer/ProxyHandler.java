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

package com.vmware.xenon.jeeimpl.consumer;

import static java.util.Arrays.asList;

import static com.vmware.xenon.common.UriUtils.extendUri;
import static com.vmware.xenon.common.UriUtils.extendUriWithQuery;


import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceRequestSender;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.jee.consumer.ErrorContext;
import com.vmware.xenon.jee.consumer.OperationInterceptor;
import com.vmware.xenon.jee.exception.Constants;
import com.vmware.xenon.jee.exception.ServiceException;
import com.vmware.xenon.jee.exception.WebErrorResponse;
import com.vmware.xenon.jeeimpl.AnnotationProxyHandler;
import com.vmware.xenon.jeeimpl.query.ODataPagedQueryAnnotationHandlerImpl;
import com.vmware.xenon.jeeimpl.query.ODataQueryAnnotationHandlerImpl;
import com.vmware.xenon.jeeimpl.reflect.MethodInfo;
import com.vmware.xenon.jeeimpl.reflect.MethodInfoBuilder;
import com.vmware.xenon.jeeimpl.reflect.ParamMetadata;

/**
 * Handles the method invocation on contract interfaces.
 * <p>
 * <pre>
 * Capable of handling
 *      1. methods annotated with JAX-RS annotations
 *      2. methods annotated with ODataQuery & ODataPagedQuery
 *      3. default methods
 *      4. methods with annotations, whose annotation handler are explicitly added
 * </pre>
 * Convert jax-rs method invocation to operation and post back results
 */
public class ProxyHandler implements InvocationHandler {

    private Class<?> resourceInterface;
    private String referrer = "/jaxrs/xenon/client";
    private List<MethodInfo> httpMethods = Collections.emptyList();
    private List<AnnotationProxyHandler> customAnnotationHanders = asList(
            new ODataQueryAnnotationHandlerImpl(),
            new ODataPagedQueryAnnotationHandlerImpl());
    private Map<String, Class<?>> typeResolution = Collections.emptyMap();
    private Supplier<URI> baseUriSupplier;
    private ServiceRequestSender client;
    private BiFunction<MethodInfo, Object[], Operation> opBuilder = this::buildOperation;
    private BiConsumer<ErrorContext, CompletableFuture<?>> errorHandler = this::defaultErrorHandler;
    private BiFunction<Operation, MethodInfo, Object> responseDecoder = this::operationDecoder;
    private OperationInterceptor interceptor = new OperationInterceptor() {
    };

    Operation buildOperation(MethodInfo httpMethod, Object[] args) {
        String methodUri = httpMethod.getUriPath();
        List<String> queryUri = new ArrayList<>();
        Operation op = new Operation();
        if (op.getCookies() == null) {
            op.setCookies(new HashMap<>());
        }
        for (ParamMetadata paramMetadata : httpMethod.getParameters()) {
            if (args[paramMetadata.getParameterIndex()] == null) {
                continue;
            }
            String paramValue = String.valueOf(args[paramMetadata.getParameterIndex()]);
            if (paramMetadata.getType() == ParamMetadata.Type.PATH) {
                String regex = Pattern.quote("{" + paramMetadata.getName() + "}");
                methodUri = methodUri.replaceAll(regex, paramValue);
            } else if (paramMetadata.getType() == ParamMetadata.Type.QUERY) {
                queryUri.add(paramMetadata.getName());
                queryUri.add(paramValue);
            } else if (paramMetadata.getType() == ParamMetadata.Type.BODY) {
                op.setBody(args[paramMetadata.getParameterIndex()]);
            } else if (paramMetadata.getType() == ParamMetadata.Type.HEADER) {
                op.addRequestHeader(paramMetadata.getName(), paramValue);
            } else if (paramMetadata.getType() == ParamMetadata.Type.COOKIE) {
                op.getCookies().put(paramMetadata.getName(), paramValue);
            } else if (paramMetadata.getType() == ParamMetadata.Type.PRAGMA) {
                if (args[paramMetadata.getParameterIndex()] instanceof String[]) {
                    for (String pragma : (String[]) (args[paramMetadata.getParameterIndex()])) {
                        op.addPragmaDirective(pragma);
                    }
                } else {
                    op.addPragmaDirective(paramValue);
                }
            }
        }
        op.setUri(extendUriWithQuery(extendUri(this.baseUriSupplier.get(), methodUri),
                queryUri.toArray(new String[] {})));
        op.setAction(httpMethod.getAction());
        op.setReferer(this.referrer);
        return op;
    }

    private void defaultErrorHandler(ErrorContext completed, CompletableFuture<?> response) {
        Throwable actual = completed.getError();
        Operation completedOp = completed.getCompletedOperation();
        try {
            WebErrorResponse errorResponse = completedOp.getBody(WebErrorResponse.class);
            ServiceException wrapper;
            if (errorResponse != null && errorResponse.getErrorCode() != 0) {
                wrapper = errorResponse.toError(actual);
            } else {
                String msg = String.format("Unable to complete %s on %s. Status code is %s",
                        completedOp.getAction(), completedOp.getUri(), completedOp.getStatusCode());
                if (completedOp.getBodyRaw() != null) {
                    msg = msg + ". ResponseBody is " + completedOp.getBodyRaw().toString();
                }
                wrapper = new ServiceException(actual, completedOp.getStatusCode(),
                        ServiceException.DEFAULT_ERROR_CODE, msg);
            }
            wrapper.addToContext(Constants.URI, completedOp.getUri())
                    .addToContext(Constants.ACTION, completedOp.getAction())
                    .addToContext(Constants.RESPONSE_HEADERS, completedOp.getResponseHeaders())
                    .addToContext(Constants.RETRY_COUNT, completedOp.getRetryCount())
                    .addToContext(Constants.RETRIES_REMAINING, completedOp.getRetriesRemaining())
                    .addToContext(Constants.COOKIES, completedOp.getCookies())
                    .addToContext(Constants.CONTEXT_ID, completedOp.getContextId())
                    .addToContext(Constants.EXPIRATION_MICROS, completedOp.getExpirationMicrosUtc())
                    .addToContext(Constants.REFERER, completedOp.getReferer());
            actual = wrapper;
        } catch (Exception e) {
            // don't care. The body is of different format. Cascade the failure
            if (completedOp.getBodyRaw() != null) {
                actual = new RuntimeException(completedOp.getBodyRaw().toString(), actual);
            }
        } finally {
            response.completeExceptionally(actual);
        }
    }

    private CompletableFuture<Object> executeOp(MethodInfo httpMethod, Operation op)
            throws Throwable {
        CompletableFuture<Operation> future = new CompletableFuture<>();
        op.setCompletion(((completedOp, failure) -> {
            try {
                Map.Entry<Operation, Throwable> result = this.interceptor.interceptAfterComplete(op,
                        new AbstractMap.SimpleEntry<>(completedOp, failure));
                if (result.getValue() == null) {
                    future.complete(result.getKey());
                } else {
                    this.errorHandler.accept(
                            ErrorContext.of(op, result.getKey(), result.getValue()),
                            future);
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }));
        op.sendWith(this.client);
        return future.thenApply(completedOp -> getValidReturnValue(completedOp, httpMethod));
    }

    private Object getValidReturnValue(Operation completedOp, MethodInfo httpMethod) {
        Class<?> returnType = httpMethod.getReturnType();
        if (Void.class.equals(returnType) || Void.TYPE.equals(returnType)) {
            return null;
        } else if (completedOp.hasBody()) {
            return this.responseDecoder.apply(completedOp, httpMethod);
        } else if (List.class.isAssignableFrom(returnType)) {
            return Collections.emptyList();
        } else if (Set.class.isAssignableFrom(returnType)) {
            return Collections.emptySet();
        } else if (Map.class.isAssignableFrom(returnType)) {
            return Collections.emptyMap();
        } else {
            return null;
        }
    }

    public void init() {
        this.httpMethods = MethodInfoBuilder.parseInterfaceForJaxRsInfo(this.resourceInterface,
                this.typeResolution);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) {
            Class<?> declaringClass = method.getDeclaringClass();
            Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            field.setAccessible(true);
            MethodHandles.Lookup lookup = (MethodHandles.Lookup) field.get(null);
            return lookup.unreflectSpecial(method, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        }
        // is there any AnnotationProxyHandler to handle current method invocation
        for (AnnotationProxyHandler handler : this.customAnnotationHanders) {
            if (handler.canHandle(method)) {
                return handler.handle(proxy, method, args);
            }
        }

        // get the interface describing the resource
        Class<?> proxyIfc = proxy.getClass().getInterfaces()[0];
        if (proxyIfc.equals(this.resourceInterface)) {
            Optional<MethodInfo> first = this.httpMethods.stream()
                    .filter(httpMethod -> httpMethod.getMethod().equals(method))
                    .findFirst();
            MethodInfo methodInfo = first.orElseGet(() -> MethodInfoBuilder
                    .generateMethodInfo(new Method[] { method }, this.typeResolution).get(0));
            Operation op = this.opBuilder.apply(methodInfo, args);
            op = this.interceptor.interceptBeforeComplete(op);
            CompletableFuture<Object> executeOp = executeOp(methodInfo, op);
            if (methodInfo.isAsyncApi()) {
                return executeOp;
            } else {
                return executeOp.join();
            }
        } else {
            throw new IllegalStateException("Proxy interface is not same as service interface");
        }
    }

    Object operationDecoder(Operation completedOp, MethodInfo httpMethod) {
        if (httpMethod.getType() instanceof ParameterizedType) {
            String json = Utils.toJson(completedOp.getBodyRaw());
            return Utils.fromJson(json, httpMethod.getType());
        } else {
            return completedOp.getBody(httpMethod.getReturnType());
        }
    }

    public void setBaseUriSupplier(Supplier<URI> baseUriSupplier) {
        this.baseUriSupplier = baseUriSupplier;
    }

    public void setClient(ServiceRequestSender client) {
        this.client = client;
    }

    public void setErrorHandler(BiConsumer<ErrorContext, CompletableFuture<?>> errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void setInterceptor(OperationInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public void setOpBuilder(BiFunction<MethodInfo, Object[], Operation> opBuilder) {
        this.opBuilder = opBuilder;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public void setResourceInterface(Class<?> resourceInterface) {
        this.resourceInterface = resourceInterface;
    }

    public void setResponseDecoder(BiFunction<Operation, MethodInfo, Object> responseDecoder) {
        this.responseDecoder = responseDecoder;
    }

    public void setTypeResolution(Map<String, Class<?>> typeResolution) {
        this.typeResolution = typeResolution;
    }
}
