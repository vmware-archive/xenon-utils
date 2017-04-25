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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.vmware.xenon.jee.annotations.ODataPagedQuery;
import com.vmware.xenon.jee.annotations.ODataQuery;
import com.vmware.xenon.jee.annotations.Param;
import com.vmware.xenon.jee.query.CompletablePagedStream;
import com.vmware.xenon.jee.query.XenonQueryService;
import com.vmware.xenon.services.common.ExampleService.ExampleServiceState;

public interface ExampleODataXenonQueryService extends XenonQueryService {

    @ODataQuery(value = "name eq :name", documentKind = ExampleServiceState.class, orderBy = "counter desc", orderByType = "LONG")
    CompletableFuture<List<ExampleServiceState>> findByNameAndOrderDescByCounter(
            @Param("name") String name);

    @ODataQuery(value = "name eq :name and required eq :required", documentKind = ExampleServiceState.class, pickFirst = true)
    CompletableFuture<ExampleServiceState> findByNameAndRequired(@Param("name") String name,
            @Param("required") String required);

    @ODataQuery(value = "name eq :name", documentKind = ExampleServiceState.class)
    CompletableFuture<ExampleServiceState[]> findByNameReturnAsArray(@Param("name") String name);

    @ODataQuery(value = "name eq :name", documentKind = ExampleServiceState.class)
    CompletableFuture<Collection<ExampleServiceState>> findByNameReturnAsCollection(
            @Param("name") String name);

    @ODataQuery(value = "name eq :name", documentKind = ExampleServiceState.class)
    CompletableFuture<List<ExampleServiceState>> findByNameReturnAsList(@Param("name") String name);

    @ODataQuery(value = "name eq :name", documentKind = ExampleServiceState.class)
    CompletableFuture<Set<ExampleServiceState>> findByNameReturnAsSet(@Param("name") String name);

    @ODataPagedQuery(value = "name eq :name", documentKind = ExampleServiceState.class)
    CompletablePagedStream<ExampleServiceState> findByNameWithDynamicLimit(
            @Param("name") String name, @Param("$limit") int limit);

    @ODataPagedQuery(value = "name eq :name", limit = 5, documentKind = ExampleServiceState.class)
    CompletablePagedStream<ExampleServiceState> findByNameWithLimit(@Param("name") String name);

    @ODataPagedQuery(value = "name eq :name", limit = 5, documentKind = ExampleServiceState.class, orderBy = "sortedCounter desc")
    CompletablePagedStream<ExampleServiceState> findByNameWithLimitOrderBy(
            @Param("name") String name);

    @ODataPagedQuery(value = "name eq :name", limit = 5, documentKind = ExampleServiceState.class, orderBy = "sortedCounter desc", orderByType = "LONG")
    CompletablePagedStream<ExampleServiceState> findByNameWithLimitOrderByAndLongType(
            @Param("name") String name);

    @ODataQuery(value = "name eq :name", documentKind = ExampleServiceState.class, top = 1)
    CompletableFuture<ExampleServiceState> findFirstByName(@Param("name") String name);

    CompletableFuture<Collection<ExampleServiceState>> notAQueryMethod(@Param("name") String name);

}
