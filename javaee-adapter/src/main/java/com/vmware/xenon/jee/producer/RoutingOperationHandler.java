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

import static java.lang.String.format;
import static java.util.Collections.emptyMap;

import static com.vmware.xenon.common.UriUtils.URI_PATH_CHAR;
import static com.vmware.xenon.common.UriUtils.normalizeUriPath;
import static com.vmware.xenon.common.UriUtils.parseUriQueryParams;
import static com.vmware.xenon.jeeimpl.reflect.TypeConverter.asCollection;
import static com.vmware.xenon.jeeimpl.reflect.TypeConverter.convertToPrimitiveOrEnum;


import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.jee.exception.ServiceException;
import com.vmware.xenon.jee.exception.WebErrorResponse;
import com.vmware.xenon.jeeimpl.reflect.MethodInfo;
import com.vmware.xenon.jeeimpl.reflect.ParamMetadata;

/**
 * For internal consumption only
 * Responsible for processing Operation to fetch query & path parameters and to invoke the actual method
 */
public class RoutingOperationHandler implements Consumer<Operation> {

    public static class Context {

        private Operation op;
        private Object[] inputs;

        public Context(Operation op) {
            this.op = op;
        }

        public void finish() {
            // indicates completion
        }

        public void start() {
            // indicates start
        }

        public Object[] getInputs() {
            return Arrays.copyOf(this.inputs, this.inputs.length);
        }

        public Operation getOp() {
            return this.op;
        }

        public void setInputs(Object[] inputs) {
            this.inputs = Arrays.copyOf(inputs, inputs.length);
        }

    }

    private static Map<Integer, String> parsePathValues(URI uri) {
        Map<Integer, String> pathValues = new HashMap<>();
        String path = normalizeUriPath(uri.getPath());
        String[] tokens = path.split(URI_PATH_CHAR);
        for (int i = 0; i < tokens.length; i++) {
            pathValues.put(i, tokens[i]);
        }
        return pathValues;
    }

    private final String path;
    private final MethodInfo httpMethod;
    private final Service service;
    /**
     * Method receives Operation as an argument. When operation completes will be handled by the method itself
     */
    boolean hasOperationAsAnArgument = false;
    /**
     * holds true for non-void methods
     */
    boolean hasValidReturnType = false;
    private Logger log = LoggerFactory.getLogger(getClass());

    private Validator validator;

    private int resourcePathOffset;

    RoutingOperationHandler(String path, MethodInfo publicMethod, Service service) {
        this.path = path;
        this.httpMethod = publicMethod;
        this.service = service;
    }

    @Override
    public void accept(Operation operation) {
        try {
            doLogging(operation);
            Context ctx = getContext(operation);
            ctx.start();
            Map<String, String> queryParams = parseUriQueryParams(operation.getUri());
            Map<Integer, String> pathValues = parsePathValues(operation.getUri());
            Object[] methodInputs;
            try {
                methodInputs = findMethodInputs(operation, queryParams, pathValues);
                ctx.setInputs(methodInputs);
            } catch (ServiceException error) {
                operation.fail(error, WebErrorResponse.from(error));
                return; // model failure occurred. Skip actual API call
            }
            if (this.hasOperationAsAnArgument) {
                // do not invoke operation.complete(). User takes care of this.
                invokeMethodAndSetBody(ctx);
            } else {
                // we have to invoke operation.complete() or operation.fail() in all possible scenarios
                if (this.httpMethod.isAsyncApi()) {
                    invokeMethodAndHandleOperationAsync(ctx);
                } else {
                    invokeMethodAndHandleOperationSync(ctx);
                }
            }
        } catch (Exception e) { // handles exception in synchronous invocation / method execution
            this.log.error("Unable to invoke the " + this.httpMethod.getName(), e);
            operation.fail(e, format("Failed to execute %s handler on %s ", operation.getAction(),
                    operation.getUri().getPath()));
        }

    }

    protected Context getContext(Operation operation) {
        return new Context(operation);
    }

    private void doLogging(Operation operation) {
        long startTime = System.nanoTime();
        this.log.trace("Performing {} on {}", operation.getAction(), operation.getUri());
        operation.nestCompletion((completedOp, failure) -> {
            if (failure == null) {
                this.log.debug("Operation {} on {} Succeeded. It took {}",
                        new Object[] { operation.getAction(), operation.getUri(),
                                System.nanoTime() - startTime });
                operation.complete();
            } else {
                this.log.warn("Operation {} on {} failed. It took {}",
                        new Object[] { operation.getAction(), operation.getUri(),
                                System.nanoTime() - startTime });
                operation.fail(failure);
            }
        });
    }

    /**
     * Finds the actual arguments to be passed to the method during invocation
     *
     * @param operation   Operation from which request body needs to be extracted
     * @param queryParams all the query params required by the method
     * @param pathValues  all the path params required by the method
     * @return method parameters
     * @throws ServiceException if method body type has validation annotations and model breaches constraints
     */
    private Object[] findMethodInputs(Operation operation, Map<String, String> queryParams,
            Map<Integer, String> pathValues) throws ServiceException {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        Map<String, String> cookies = operation.getCookies() == null ? emptyMap()
                : operation.getCookies();
        Object[] arguments = this.httpMethod.getParameters().stream()
                .map(paramMetadata -> {
                    switch (paramMetadata.getType()) {

                    case QUERY:
                        return toValue(queryParams.get(paramMetadata.getName()), paramMetadata);

                    case HEADER:
                        return toValue(operation.getRequestHeader(paramMetadata.getName()),
                                paramMetadata);

                    case COOKIE:
                        return toValue(cookies.get(paramMetadata.getName()), paramMetadata);

                    case PATH:
                        // -1 so that length gets converted to index
                        Integer index = this.httpMethod.getPathParamsVsUriIndex()
                                .getOrDefault(paramMetadata.getName(), -1);
                        return toValue(pathValues.get(index + this.resourcePathOffset),
                                paramMetadata);

                    case OPERATION:
                        return operation;

                    case BODY:
                        Object body = operation.getBody(paramMetadata.getParamterType());
                        violations.addAll(this.validator.validate(body));
                        return body;

                    default:
                        return null;
                    }
                }).toArray();

        if (!violations.isEmpty()) {
            this.log.warn("Operation {} on {} has constraints violations. Rejecting the request",
                    operation.getAction(), operation.getUri());
            violations.forEach(violation -> this.log.warn("Invalid Value {}, Violation Message {} ",
                    violation.getInvalidValue(), violation.getMessage()));
            ServiceException error = new ServiceException(400,
                    "Constraint violations occurred while validating input request");
            Map<String, Object> context = new HashMap<>();
            context.put("Constraints", violations);
            error.setContext(context);
            throw error;
        }
        return arguments;
    }

    void init() {
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
        this.hasOperationAsAnArgument = this.httpMethod.getParameters().stream().anyMatch(
                paramMetadata -> paramMetadata.getType().equals(ParamMetadata.Type.OPERATION));
        this.hasValidReturnType = !this.httpMethod.getMethod().getReturnType().equals(Void.TYPE);
        // http method provides path index using @PATH at method level only. The service class will have the path prefix.
        // this offset is to capture the no of forward slashes at service level
        this.resourcePathOffset = this.path.split(URI_PATH_CHAR).length
                - (this.httpMethod.getUriPath() == null ? 0
                        : this.httpMethod.getUriPath().split(URI_PATH_CHAR).length);
    }

    private void invokeMethodAndHandleOperationAsync(Context ctx) throws Exception {
        Object invocationResult = this.httpMethod.getMethod().invoke(this.service, ctx.getInputs());
        CompletableFuture<?> future = (CompletableFuture<?>) invocationResult;
        future.exceptionally(throwable -> {
            WebErrorResponse errorResponse;
            Throwable cause;
            if (throwable instanceof CompletionException
                    || throwable instanceof ExecutionException) {
                cause = throwable.getCause();
            } else {
                cause = throwable;
            }
            errorResponse = WebErrorResponse.from(cause);
            if (errorResponse != null) {
                //if < 400 will be replaced to 500 during failing Operation
                ctx.getOp().setStatusCode(errorResponse.statusCode);
            }
            ctx.getOp().fail(cause, errorResponse);
            return null;
        });
        // this won't be invoked if future got completed exceptionally
        future.thenAccept(resp -> {
            ctx.getOp().setBody(resp);
            ctx.getOp().complete();
            ctx.finish();
        });

    }

    private void invokeMethodAndHandleOperationSync(Context ctx) throws Exception {
        Object invocationResult = this.httpMethod.getMethod().invoke(this.service, ctx.getInputs());
        if (this.hasValidReturnType) {
            ctx.getOp().setBody(invocationResult);
        }
        ctx.getOp().complete();
        ctx.finish();
    }

    private void invokeMethodAndSetBody(Context ctx) throws Exception {
        Object invocationResult = this.httpMethod.getMethod().invoke(this.service, ctx.getInputs());
        if (this.hasValidReturnType) {
            ctx.getOp().setBody(invocationResult);
        }
        ctx.finish();
    }

    private Object toValue(String obj, ParamMetadata metadata) {
        if (obj == null) {
            return metadata.getDefaultValue();
        } else if (String.class.equals(metadata.getParamterType())) {
            return obj;
        } else {
            Object converted = convertToPrimitiveOrEnum(obj, metadata.getParamterType());
            if (metadata.getParamterType().isPrimitive()
                    || metadata.getParamterType().isInstance(converted)) {
                return converted;
            }
            converted = asCollection(obj, metadata.getParamterType());
            if (metadata.getParamterType().isInstance(converted)) {
                return converted;
            }
        }
        return obj;
    }

}
