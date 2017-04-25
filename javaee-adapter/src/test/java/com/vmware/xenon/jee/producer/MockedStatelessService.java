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

import static com.vmware.xenon.common.Utils.toJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.jee.annotations.OperationBody;
import com.vmware.xenon.jee.annotations.PATCH;

public class MockedStatelessService extends JaxRsBridgeStatelessService implements ExampleContract {

    public static final String SELF_LINK = "/vrbc/common/routing/test";

    Logger log = LoggerFactory.getLogger(getClass());

    public MockedStatelessService() {
        setContractInterface(ExampleContract.class);
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public CompletableFuture<List<String>> getWithDefaults(String query, int intVal,
            Integer intDefaultParam) {
        List<String> resp = new ArrayList<>();
        resp.add(query);
        resp.add(String.valueOf(intVal));
        resp.add(String.valueOf(intDefaultParam));
        return CompletableFuture.completedFuture(resp);
    }

    @Path("/path/{pathParam}/query")
    @GET
    public void getWithQueryAndPath(@PathParam("pathParam") String pathValue,
            @QueryParam("queryParam") String query, Operation get) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        help.put("pathParam", pathValue);
        help.put("queryParam", query);
        get.setBody(help);
        get.complete();
    }

    @Override
    @Path("/complete/path/{pathParam}")
    @GET
    public Map<String, String> getWithQueryAndPathAndReturn(
            @PathParam("pathParam") String pathValue, @QueryParam("queryParam") String query) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        help.put("pathParam", pathValue);
        help.put("queryParam", query);
        this.log.info("getWithQueryAndPathAndReturn invoked with pathparam {} & query param {}",
                pathValue, query);
        return help;
    }

    @Override
    public CompletableFuture<Map<String, String>> getWithQueryAndPathAndReturnAsync(
            String pathValue, String query) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        help.put("pathParam", pathValue);
        help.put("queryParam", query);
        return CompletableFuture.completedFuture(help);
    }

    @Path("/validation/path/{pathParam}")
    @POST
    public Map<String, String> postWithModelValidationOnBody(
            @PathParam("pathParam") String pathValue,
            @QueryParam("queryParam") String query,
            @OperationBody EmployeePojo payload) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        help.put("pathParam", pathValue);
        help.put("queryParam", query);
        help.put("body", toJson(payload));
        return help;
    }

    @Path("/path/{pathParam}/query")
    @POST
    public void postWithQueryAndPath(@PathParam("pathParam") String pathValue,
            @QueryParam("queryParam") String query,
            @OperationBody List<String> payload,
            Operation get) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        help.put("pathParam", pathValue);
        help.put("queryParam", query);
        help.put("body", toJson(payload));
        get.setBody(help);
        get.complete();
    }

    @Override
    public CompletableFuture<Map<String, String>> postWithQueryAndPathAndReturnAsync(String query,
            List<String> payload) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        help.put("queryParam", query);
        help.put("body", toJson(payload));
        return CompletableFuture.completedFuture(help);
    }

    @Path("/complete/path/{pathParam}")
    @PUT
    public Map<String, String> putWithQueryAndPathAndReturn(
            @PathParam("pathParam") String pathValue,
            @QueryParam("queryParam") String query,
            @OperationBody List<String> payload) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        help.put("pathParam", pathValue);
        help.put("queryParam", query);
        help.put("body", toJson(payload));
        return help;
    }

    @Path("/simple")
    @GET
    public void simpleGet(final Operation get) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        get.setBody(help);
        get.complete();
    }

    @Path("/simple")
    @PATCH
    public void simplePatch(final Operation get) {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("result", "success");
        get.setBody(help);
        get.complete();
    }
}
