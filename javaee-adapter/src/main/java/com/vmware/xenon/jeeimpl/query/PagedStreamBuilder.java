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

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.jee.query.CompletablePagedStream;
import com.vmware.xenon.jee.query.XenonQueryService;
import com.vmware.xenon.services.common.QueryTask;

/**
 */
public class PagedStreamBuilder<D> {

    public static <D> PagedStreamBuilder<D> newBuilder() {
        return new PagedStreamBuilder<>();
    }

    public static <D> CompletablePagedStream<D> newPagedStream(
            CompletableFuture<QueryTask> executed, XenonQueryService querySvc,
            Class<D> documentKind) {
        PagedStreamBuilder<D> newBuilder = newBuilder();
        return newBuilder.withDocumentKind(documentKind)
                .withExecutedQuery(executed)
                .withXenonQueryService(querySvc)
                .build();
    }

    public static <D> CompletablePagedStream<D> newPagedStream(
            CompletableFuture<QueryTask> executed, XenonQueryService querySvc,
            Function<ServiceDocumentQueryResult, List<D>> converter) {
        PagedStreamBuilder<D> newBuilder = newBuilder();
        return newBuilder.withConverter(converter)
                .withExecutedQuery(executed)
                .withXenonQueryService(querySvc)
                .build();
    }

    public static <D> CompletablePagedStream<D> newPagedStream(QueryTask toBeExecuted,
            XenonQueryService querySvc, Class<D> documentKind) {
        PagedStreamBuilder<D> newBuilder = newBuilder();
        return newBuilder.withDocumentKind(documentKind)
                .withQueryToBeExecuted(toBeExecuted)
                .withXenonQueryService(querySvc)
                .build();
    }

    private QueryTask toBeExecuted;

    private CompletableFuture<QueryTask> executed;

    private XenonQueryService querySvc;

    private Function<ServiceDocumentQueryResult, List<D>> converter;

    public CompletablePagedStream<D> build() {
        if (this.toBeExecuted == null && this.executed == null) {
            throw new IllegalArgumentException("QueryTask is mandatory.");
        }
        requireNonNull(this.querySvc, "Query Service is mandatory");
        requireNonNull(this.converter,
                "Need documentKind or ServiceDocumentQueryResult to POJO converter");
        if (nonNull(this.toBeExecuted)) {
            this.executed = this.querySvc.query(this.toBeExecuted);
        }
        return new CompletablePagedStreamImpl<>(
                this.executed.thenApply(
                        qTask -> new PagedResultsImpl<>(qTask, this.converter, this.querySvc)));
    }

    /**
     * Converter to convert ServiceDocumentQueryResult to desired pojo
     * prefer using {@link ConverterUtil}
     */
    public PagedStreamBuilder<D> withConverter(
            Function<ServiceDocumentQueryResult, List<D>> converter) {
        this.converter = converter;
        return this;
    }

    public PagedStreamBuilder<D> withDocumentKind(Class<D> documentKind) {
        this.converter = ConverterUtil.fromDocuments(documentKind);
        return this;
    }

    /**
     * QueryTask already executed which would have nextPageLink readily available in ServiceDocumentQueryResult
     */
    public PagedStreamBuilder<D> withExecutedQuery(CompletableFuture<QueryTask> executed) {
        this.executed = executed;
        return this;
    }

    /**
     * Constructed query task that needs to be executed
     */
    public PagedStreamBuilder<D> withQueryToBeExecuted(QueryTask toBeExecuted) {
        this.toBeExecuted = toBeExecuted;
        return this;
    }

    /**
     * XenonQueryService to be used for querying successive pages
     */
    public PagedStreamBuilder<D> withXenonQueryService(XenonQueryService querySvc) {
        this.querySvc = querySvc;
        return this;
    }

}
