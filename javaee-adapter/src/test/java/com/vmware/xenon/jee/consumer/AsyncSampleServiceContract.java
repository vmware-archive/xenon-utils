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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import com.vmware.xenon.jee.annotations.OperationBody;
import com.vmware.xenon.jee.annotations.PATCH;

@Path("/vrbc/xenon/util/test")
interface AsyncSampleServiceContract {

    @GET
    @Path("/get/{path}")
    CompletableFuture<Map<String, String>> getAction(@QueryParam("query") String query,
            @PathParam("path") String path);

    @GET
    @Path("/get/genericReturn")
    CompletableFuture<Map<String, SampleServiceContract.SuccessResponse>> getActionWithGenericReturn();

    @PATCH
    @Path("/patch")
    CompletableFuture<Map<String, String>> patchAction(@OperationBody List<Integer> contents);

    @PATCH
    @Path("/patch/voidReturn")
    CompletableFuture<Void> patchActionWithGenericReturn(@OperationBody List<Integer> unused);

    @POST
    @Path("/post")
    CompletableFuture<Void> postAction(@OperationBody List<String> contents);

    @POST
    @Path("/post/auth")
    CompletableFuture<List<String>> postActionWithAuthInfo(@HeaderParam("header") String header,
            @CookieParam("cookie") String cookie, @OperationBody List<Integer> contents);

}
