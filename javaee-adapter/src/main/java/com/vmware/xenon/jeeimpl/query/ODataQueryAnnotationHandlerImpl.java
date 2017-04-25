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

package com.vmware.xenon.jeeimpl.query;

import static com.vmware.xenon.jeeimpl.query.ConverterUtil.fromDocuments;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.jee.annotations.ODataQuery;
import com.vmware.xenon.jee.query.XenonQueryService;
import com.vmware.xenon.jeeimpl.AnnotationProxyHandler;
import com.vmware.xenon.services.common.QueryTask;

/**

 * <p>
 * Handles {@link ODataQuery} annotation
 * on sub-classes of {@link XenonQueryService}
 */
public class ODataQueryAnnotationHandlerImpl implements AnnotationProxyHandler {

    private static final Logger log = LoggerFactory
            .getLogger(ODataQueryAnnotationHandlerImpl.class);

    @Override
    public boolean canHandle(Method method) {
        ODataQuery declaredAnnotation = method.getDeclaredAnnotation(ODataQuery.class);
        if (declaredAnnotation == null) {
            return false;
        }
        Class<?>[] interfaces = method.getDeclaringClass().getInterfaces();
        for (Class<?> anInterface : interfaces) {
            if (anInterface.equals(XenonQueryService.class) &&
                    CompletableFuture.class.equals(method.getReturnType())) {
                return true;
            }
        }
        log.info(
                "ODataQuery annotation is honoured only on the child classes of XenonQueryService. "
                        +
                        "Method {} with ODataQuery annotation is skipped",
                method.getName());
        return false;
    }

    private CompletableFuture<Object> formResponse(Method method, ODataQuery oData,
            CompletableFuture<QueryTask> executedQuery) {
        return executedQuery.thenApply(queryTask -> {
            List<?> documents = fromDocuments(oData.documentKind()).apply(queryTask.results);
            return toReturnType(oData, method, documents);
        });
    }

    String getFilterCriteria(Method method, ODataQuery oData, Object[] args) {
        return ODataQueryUtil.getFilterCriteria(method, oData.documentKind(), oData.value(), args);
    }

    @Override
    public Object handle(Object proxy, Method method, Object[] args) {
        ODataQuery oData = method.getDeclaredAnnotation(ODataQuery.class);
        XenonQueryService queryService = (XenonQueryService) proxy;
        String filterCriteria = getFilterCriteria(method, oData, args);
        int top = oData.top();
        String orderBy = oData.orderBy();
        String orderByType = oData.orderByType();
        CompletableFuture<QueryTask> executedQuery;
        if (ODataQuery.NONE.equals(orderBy)) {
            executedQuery = queryService.oDataQuery(filterCriteria, top);
        } else if (ODataQuery.NONE.equals(orderByType)) {
            executedQuery = queryService.oDataQuery(filterCriteria, top, orderBy);
        } else {
            executedQuery = queryService.oDataQuery(filterCriteria, top, orderBy, orderByType);

        }

        return formResponse(method, oData, executedQuery);
    }

    Object toReturnType(ODataQuery declaredAnnotation, Method method, List<?> results) {

        if (declaredAnnotation.pickFirst() || declaredAnnotation.top() == 1) {
            return results.isEmpty() ? null : results.get(0);
        }

        Type genericReturnType = method.getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) genericReturnType;
            Type typeArgument = type.getActualTypeArguments()[0];
            if (typeArgument instanceof ParameterizedType) {
                Class<?> rawType = (Class<?>) ((ParameterizedType) typeArgument).getRawType();
                if (List.class.equals(rawType)) {
                    return results;
                } else if (Set.class.equals(rawType)) {
                    return new HashSet<>(results);
                } else if (Collection.class.equals(rawType)) {
                    return results;
                }
            } else if (typeArgument.getTypeName().endsWith("[]")) {
                return toTypedArray(declaredAnnotation.documentKind(), results);
            }
        }
        log.warn("Unable to detect the return type for method {}. Returning as List. ",
                method.getName());
        return results;
    }

    @SuppressWarnings("unchecked")
    private <T> T[] toTypedArray(Class<T> clazz, List<?> items) {
        T[] array = (T[]) Array.newInstance(clazz, items.size());
        items.toArray(array);
        return array;
    }

}
