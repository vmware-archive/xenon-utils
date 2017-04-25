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

package com.vmware.xenon.jee.producer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import com.vmware.xenon.jee.annotations.OperationBody;

@Path(MockedStatelessService.SELF_LINK)
public interface ExampleContract {

    @Path("/async/defaults")
    @GET
    CompletableFuture<List<String>> getWithDefaults(
            @DefaultValue("default") @QueryParam("strParam") String query,
            @QueryParam("intParam") int intVal,
            @DefaultValue("2") @QueryParam("intDefaultParam") Integer intDefaultParam);

    @Path("/async/path/{pathParam}")
    @GET
    Map<String, String> getWithQueryAndPathAndReturn(@PathParam("pathParam") String pathValue,
            @QueryParam("queryParam") String query);

    @Path("/async/get/{pathParam}")
    @GET
    CompletableFuture<Map<String, String>> getWithQueryAndPathAndReturnAsync(
            @PathParam("pathParam") String pathValue, @QueryParam("queryParam") String query);

    @Path("/async/post")
    @POST
    CompletableFuture<Map<String, String>> postWithQueryAndPathAndReturnAsync(
            @DefaultValue("defaultQueryParamVal") @QueryParam("queryParam") String query,
            @OperationBody List<String> values);

}
