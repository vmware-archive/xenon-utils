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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.jee.query.PagedResults;
import com.vmware.xenon.jee.query.XenonQueryService;
import com.vmware.xenon.services.common.QueryTask;

public class PagedResultsImpl<T> implements PagedResults<T> {

    private QueryTask executedTask;
    private XenonQueryService queryService;
    private Function<ServiceDocumentQueryResult, List<T>> toDocuments;

    public PagedResultsImpl(QueryTask executedTask, Class<T> documentKind,
            XenonQueryService queryService) {
        this(executedTask, ConverterUtil.fromDocuments(documentKind), queryService);
    }

    public PagedResultsImpl(QueryTask executedTask,
            Function<ServiceDocumentQueryResult, List<T>> convertToDocs,
            XenonQueryService queryService) {
        this.executedTask = executedTask;
        this.queryService = queryService;
        this.toDocuments = convertToDocs;
    }

    @Override
    public List<T> documents() {
        return this.toDocuments.apply(this.executedTask.results);
    }

    private CompletableFuture<PagedResults<T>> getPagedResults(String page) {
        return this.queryService.fetchPage(page)
                .thenApply(queryTask -> new PagedResultsImpl<>(queryTask, this.toDocuments,
                        this.queryService));
    }

    @Override
    public boolean hasNextPage() {
        return this.executedTask.results.nextPageLink != null;
    }

    @Override
    public boolean hasPreviousPage() {
        return this.executedTask.results.prevPageLink != null;
    }

    @Override
    public CompletableFuture<PagedResults<T>> next() {
        return getPagedResults(this.executedTask.results.nextPageLink);
    }

    @Override
    public int pageSize() {
        return this.executedTask.querySpec.resultLimit;
    }

    @Override
    public CompletableFuture<PagedResults<T>> previous() {
        return getPagedResults(this.executedTask.results.prevPageLink);
    }

    @Override
    public CompletableFuture<Long> totalCount() {
        QueryTask.Query query = this.executedTask.querySpec.query;
        QueryTask task = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.COUNT)
                .setQuery(query)
                .build();
        return this.queryService.query(task)
                .thenApply(queryTask -> queryTask.results.documentCount);
    }
}
