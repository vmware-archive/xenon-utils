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

package com.vmware.xenon.jeeimpl.query;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.jee.annotations.ODataPagedQuery;
import com.vmware.xenon.jee.query.CompletablePagedStream;
import com.vmware.xenon.jee.query.XenonQueryService;
import com.vmware.xenon.jeeimpl.AnnotationProxyHandler;
import com.vmware.xenon.services.common.QueryTask;

/**

 * <p>
 * Handles {@link com.vmware.xenon.jee.annotations.ODataPagedQuery} annotation
 * on sub-classes of {@link com.vmware.xenon.jee.query.XenonQueryService}
 * <p>
 * Return type of the method should be CompletablePagedStream
 */
public class ODataPagedQueryAnnotationHandlerImpl implements AnnotationProxyHandler {

    private static final Logger log = LoggerFactory
            .getLogger(ODataQueryAnnotationHandlerImpl.class);

    @Override
    public boolean canHandle(Method method) {
        ODataPagedQuery declaredAnnotation = method.getDeclaredAnnotation(ODataPagedQuery.class);
        if (declaredAnnotation == null) {
            return false;
        }

        if (!CompletablePagedStream.class.equals(method.getReturnType())) {
            log.info(
                    "ODataPagedQuery annotation is honoured on methods whose return type is of type CompletablePagedStream "
                            +
                            "Skipping {} with return type {}",
                    method.getName(), method.getReturnType());
            return false;
        }

        Class<?>[] interfaces = method.getDeclaringClass().getInterfaces();
        for (Class<?> anInterface : interfaces) {
            if (anInterface.equals(XenonQueryService.class)) {
                return true;
            }
        }
        log.info(
                "ODataPagedQuery annotation is honoured only on the child classes of XenonQueryService. "
                        +
                        "Method {} with ODataQuery annotation is skipped",
                method.getName());
        return false;
    }

    @Override
    public Object handle(Object proxy, Method method, Object[] args) {
        ODataPagedQuery oData = method.getDeclaredAnnotation(ODataPagedQuery.class);
        XenonQueryService queryService = (XenonQueryService) proxy;
        String filterCriteria = ODataQueryUtil.getFilterCriteria(method, oData.documentKind(),
                oData.value(), args);
        Optional<Integer> optionalLimit = ODataQueryUtil.getIntParamWithName(method, "$limit",
                args);
        int limit = optionalLimit.orElse(oData.limit());
        String orderBy = oData.orderBy();
        String orderByType = oData.orderByType();
        CompletableFuture<QueryTask> executedQuery;

        if (ODataPagedQuery.NONE.equals(orderBy)) {
            executedQuery = queryService.oDataPagedQuery(filterCriteria, limit);
        } else if (ODataPagedQuery.NONE.equals(orderByType)) {
            executedQuery = queryService.oDataPagedQuery(filterCriteria, limit, orderBy);
        } else {
            executedQuery = queryService.oDataPagedQuery(filterCriteria, limit, orderBy,
                    orderByType);
        }
        return PagedStreamBuilder.newPagedStream(executedQuery, queryService, oData.documentKind());
    }

}
