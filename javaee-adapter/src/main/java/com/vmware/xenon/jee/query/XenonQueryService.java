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

package com.vmware.xenon.jee.query;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import com.vmware.xenon.common.Utils;
import com.vmware.xenon.jee.annotations.OperationBody;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Contract for Xenon core query tasks and odata query
 */
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public interface XenonQueryService {

    String LE = " le ";
    String OR = " or ";
    String ANY = " any ";
    String ALL = " all ";
    String AND = " and ";
    String GT = " gt ";
    String GE = " ge ";
    String lt = " lt ";
    String EQ = " eq ";
    String NE = " ne ";
    String ASC = " asc ";
    String DESC = " desc ";
    String SPACE = " ";

    int DEFAULT_RESULT_LIMIT = 9999;

    @Path("{pageLink}")
    @GET
    CompletableFuture<QueryTask> fetchPage(@PathParam("pageLink") String pageLink);

    @Path("/core/query-tasks/{id}")
    @GET
    CompletableFuture<QueryTask> getQueryResults(@PathParam("id") String id);

    @Path("/core/odata-queries")
    @GET
    CompletableFuture<QueryTask> oDataPagedQuery(@QueryParam("$filter") String filterCriteria,
            @QueryParam("$limit") int limit);

    @Path("/core/odata-queries")
    @GET
    CompletableFuture<QueryTask> oDataPagedQuery(@QueryParam("$filter") String filterCriteria,
            @QueryParam("$limit") int limit,
            @QueryParam("$orderby") String orderBy);

    @Path("/core/odata-queries")
    @GET
    CompletableFuture<QueryTask> oDataPagedQuery(@QueryParam("$filter") String filterCriteria,
            @QueryParam("$limit") int limit,
            @QueryParam("$orderby") String orderBy, @QueryParam("$orderbytype") String orderByType);

    @Path("/core/odata-queries")
    @GET
    CompletableFuture<QueryTask> oDataQuery(@QueryParam("$filter") String filterCriteria);

    @Path("/core/odata-queries")
    @GET
    CompletableFuture<QueryTask> oDataQuery(@QueryParam("$filter") String filterCriteria,
            @QueryParam("$top") int resultLimit);

    @Path("/core/odata-queries")
    @GET
    CompletableFuture<QueryTask> oDataQuery(@QueryParam("$filter") String filterCriteria,
            @QueryParam("$top") int resultLimit,
            @QueryParam("$orderby") String orderBy);

    @Path("/core/odata-queries")
    @GET
    CompletableFuture<QueryTask> oDataQuery(@QueryParam("$filter") String filterCriteria,
            @QueryParam("$top") int resultLimit,
            @QueryParam("$orderby") String orderBy, @QueryParam("$orderbytype") String orderByType);

    @POST
    @Path("/core/query-tasks")
    CompletableFuture<QueryTask> postQueryTask(@OperationBody QueryTask task);

    @Path("/core/query-tasks")
    @POST
    CompletableFuture<QueryTask> query(@OperationBody QueryTask filterCriteria);

    /**
     * TO be used only with queries returning homogeneous results of a single type
     *
     * @param filterCriteria the actual criteria
     * @param clazz          the return type, usually it will be an array type
     * @param <T>
     * @return
     */
    default <T> CompletableFuture<T> typedODataQuery(String filterCriteria, Class<T> clazz) {
        return typedODataQuery(filterCriteria, DEFAULT_RESULT_LIMIT, clazz);
    }

    default <T> CompletableFuture<T> typedODataQuery(String filterCriteria, int resultLimit,
            Class<T> clazz) {
        return oDataQuery(filterCriteria, resultLimit)
                .thenApply(task -> {
                    Map<String, Object> documents = task.results.documents;
                    if (documents == null) {
                        documents = new HashMap<>();
                    }
                    return Utils.fromJson(documents.values(), clazz);
                });
    }

    /**
     * To be used only with direct task queries returning homogeneous results of a single type
     *
     * @param filterCriteria the actual criteria
     * @param clazz          the return type, usually it will be an array type
     * @param <T>
     * @return
     */
    default <T> CompletableFuture<T> typedQuery(QueryTask filterCriteria, Class<T> clazz) {
        return query(filterCriteria)
                .thenApply(task -> {
                    Map<String, Object> documents = task.results.documents;
                    if (documents == null) {
                        documents = new HashMap<>();
                    }
                    return Utils.fromJson(documents.values(), clazz);
                });
    }

}
