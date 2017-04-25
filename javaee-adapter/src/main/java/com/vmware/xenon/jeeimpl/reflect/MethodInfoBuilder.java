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

package com.vmware.xenon.jeeimpl.reflect;

import static java.lang.reflect.Modifier.isPublic;
import static java.util.stream.Collectors.toList;

import static com.vmware.xenon.common.UriUtils.URI_PATH_CHAR;
import static com.vmware.xenon.jeeimpl.reflect.TypeConverter.asCollection;
import static com.vmware.xenon.jeeimpl.reflect.TypeConverter.convertToPrimitiveOrEnum;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.jee.annotations.OperationBody;
import com.vmware.xenon.jee.annotations.PragmaDirective;

/**
 * Parses and creates MethodInfo of public declared method given a JaxRs annotated interfaces
 */
public class MethodInfoBuilder {

    private static Logger log = LoggerFactory.getLogger(MethodInfoBuilder.class);

    static List<ParamMetadata> extractParamMetadatas(Method publicMethod) {
        Parameter[] parameters = publicMethod.getParameters();
        return IntStream.range(0, parameters.length)
                .mapToObj(parameterIndex -> {
                    Parameter parameter = parameters[parameterIndex];
                    ParamMetadata param = new ParamMetadata();
                    param.setParamterType(parameter.getType());
                    if (parameter.isAnnotationPresent(QueryParam.class)) {
                        QueryParam annotation = parameter.getAnnotation(QueryParam.class);
                        param.setName(annotation.value());
                        param.setType(ParamMetadata.Type.QUERY);
                    } else if (parameter.isAnnotationPresent(PathParam.class)) {
                        PathParam annotation = parameter.getAnnotation(PathParam.class);
                        param.setName(annotation.value());
                        param.setType(ParamMetadata.Type.PATH);
                    } else if (parameter.isAnnotationPresent(HeaderParam.class)) {
                        HeaderParam annotation = parameter.getAnnotation(HeaderParam.class);
                        param.setName(annotation.value());
                        param.setType(ParamMetadata.Type.HEADER);
                    } else if (parameter.isAnnotationPresent(CookieParam.class)) {
                        CookieParam annotation = parameter.getAnnotation(CookieParam.class);
                        param.setName(annotation.value());
                        param.setType(ParamMetadata.Type.COOKIE);
                    } else if (parameter.isAnnotationPresent(PragmaDirective.class)) {
                        param.setType(ParamMetadata.Type.PRAGMA);
                    } else if (parameter.isAnnotationPresent(OperationBody.class)) {
                        param.setType(ParamMetadata.Type.BODY);
                    } else if (parameter.getType().equals(Operation.class)) {
                        param.setType(ParamMetadata.Type.OPERATION);
                    } else {
                        throw new IllegalArgumentException(
                                "Unable to understand Parameter " + parameter.getName()
                                        + " . It neither has supported annotations nor of type Operation ");
                    }
                    param.setParameterIndex(parameterIndex);

                    // populate default value
                    populateDefaultValue(parameter, param);
                    return param;
                })
                .collect(toList());
    }

    public static List<MethodInfo> generateMethodInfo(Method[] methods,
            Map<String, Class<?>> genericTypeHints) {
        List<MethodInfo> httpMethods = IntStream.range(0, methods.length)
                .mapToObj(idx -> new MethodInfo(methods[idx]))
                .filter(mInfo -> isPublic(mInfo.getMethod().getModifiers()))
                .filter(mInfo -> {
                    Service.Action action = parseAction(mInfo.getMethod());
                    mInfo.setAction(action);
                    if (action == null) {
                        log.debug("Skipping method {} as it has no HTTP Method annotation",
                                mInfo.getName());
                        return false;
                    }
                    Path pathAnnotation = mInfo.getMethod().getAnnotation(Path.class);
                    mInfo.setUriPath(pathAnnotation == null ? null : pathAnnotation.value());
                    return true;
                }).collect(toList());

        httpMethods.forEach(mInfo -> {
            mInfo.setPathParamsVsUriIndex(parsePathParams(mInfo.getUriPath()));
            mInfo.setParameters(extractParamMetadatas(mInfo.getMethod()));
            parseReturnTypes(mInfo, genericTypeHints);
            mInfo.getParameters().sort(Comparator.comparing(ParamMetadata::getParameterIndex));
        });
        return httpMethods;
    }

    private static String getHttpMethodName(AnnotatedElement element) {
        HttpMethod httpMethod = element.getAnnotation(HttpMethod.class);
        return httpMethod == null ? null : httpMethod.value();
    }

    /**
     * special case : For CompletableFuture return type
     * 1. mark method as async
     * 2. set return type as generic argument ie., for CompletableFuture &lt String &gt set return type as String
     */
    private static void handleCompletableFutureReturnType(MethodInfo mInfo,
            Map<String, Class<?>> genericTypeHints) {
        mInfo.setAsyncApi(true);
        Type genericReturnType = mInfo.getMethod().getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) genericReturnType;
            Type[] typeArguments = type.getActualTypeArguments();
            // We support only completable future for async API, which means we will get only one generic argument
            Type typeArgument = typeArguments[0];
            mInfo.setType(typeArgument);
            if (typeArgument instanceof ParameterizedType) {
                mInfo.setReturnType((Class<?>) ((ParameterizedType) typeArgument).getRawType());
            } else if (typeArgument instanceof WildcardType) {
                mInfo.setReturnType(Object.class);
            } else if (typeArgument instanceof Class) {
                mInfo.setReturnType((Class<?>) typeArgument);
            } else if (typeArgument instanceof TypeVariable) {
                mInfo.setReturnType(genericTypeHints
                        .getOrDefault(((TypeVariable) typeArgument).getName(), Object.class));
            } else {
                mInfo.setReturnType(Object.class);
            }
        } else {
            // method has no generic type info
            mInfo.setReturnType(Object.class);
            mInfo.setType(genericReturnType);
        }
    }

    /**
     * set return type
     */
    private static void handleReturnType(MethodInfo mInfo, Map<String, Class<?>> genericTypeHints) {
        mInfo.setReturnType(mInfo.getMethod().getReturnType());
        Type genericReturnType = mInfo.getMethod().getGenericReturnType();
        mInfo.setType(genericReturnType);
        if (genericReturnType instanceof TypeVariable) {
            mInfo.setReturnType(genericTypeHints
                    .getOrDefault(((TypeVariable) genericReturnType).getName(), Object.class));
        }
    }

    /**
     * Given a method, finds the HTTP action
     *
     * @param publicMethod
     * @return
     */
    static Service.Action parseAction(Method publicMethod) {
        Service.Action action = null;
        for (Annotation ann : publicMethod.getAnnotations()) {
            String httpMethod = getHttpMethodName(ann.annotationType());
            if (httpMethod != null) {
                action = Service.Action.valueOf(httpMethod);
                break;
            }
        }
        return action;
    }

    public static List<MethodInfo> parseInterfaceForJaxRsInfo(Class<?> httpResource,
            Map<String, Class<?>> returnTypeResolution) {
        Method[] methods = httpResource.getMethods();
        return generateMethodInfo(methods, returnTypeResolution);
    }

    /**
     * Parse path params given the URI
     *
     * @param uri
     * @return
     */
    static Map<String, Integer> parsePathParams(String uri) {
        if (uri != null && uri.contains("{") && uri.contains("}")) {
            Map<String, Integer> pathParams = new HashMap<>();
            String[] tokens = uri.split(URI_PATH_CHAR);
            for (int i = 0; i < tokens.length; i++) {
                String curToken = tokens[i];
                if (curToken.length() > 0 &&
                        curToken.charAt(0) == '{'
                        && curToken.charAt(curToken.length() - 1) == '}') {
                    pathParams.put(curToken.substring(1, curToken.length() - 1), i);
                }
            }
            return pathParams;
        } else {
            return Collections.emptyMap();
        }
    }

    static void parseReturnTypes(MethodInfo mInfo, Map<String, Class<?>> genericTypeHints) {
        if (CompletableFuture.class.equals(mInfo.getMethod().getReturnType())) {
            mInfo.setAsyncApi(true);
            handleCompletableFutureReturnType(mInfo, genericTypeHints);
        } else {
            mInfo.setAsyncApi(false);
            log.info("Avoid using synchronous API. Looks like {} is a synchronous API",
                    mInfo.getMethod());
            handleReturnType(mInfo, genericTypeHints);
        }
    }

    public static List<MethodInfo> parseServiceForJaxRsInfo(Class<?> httpResource,
            Map<String, Class<?>> returnTypeResolution) {
        Method[] methods = httpResource.getDeclaredMethods();
        return generateMethodInfo(methods, returnTypeResolution);
    }

    private static void populateDefaultValue(Parameter parameter, ParamMetadata param) {
        if (parameter.isAnnotationPresent(DefaultValue.class)) {
            Object init = parameter.getAnnotation(DefaultValue.class).value();
            if (parameter.getType().isInstance(init)) {
                param.setDefaultValue(init);
            } else {
                Object converted = convertToPrimitiveOrEnum(String.valueOf(init),
                        parameter.getType());
                if (parameter.getType().isInstance(converted)) {
                    param.setDefaultValue(converted);
                } else {
                    converted = asCollection(String.valueOf(init), parameter.getType());
                    if (parameter.getType().isInstance(converted)) {
                        param.setDefaultValue(converted);
                    }
                }
            }
        }
    }

}
