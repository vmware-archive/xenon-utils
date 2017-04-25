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

import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import com.vmware.xenon.jee.annotations.OperationBody;
import com.vmware.xenon.jee.annotations.PATCH;

@Path("/vrbc/xenon/util/test")
interface SampleServiceContract {

    class SuccessResponse {
        private int code;
        private String message;

        public int getCode() {
            return this.code;
        }

        public String getMessage() {
            return this.message;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    @DELETE
    @Path("/delete/{path}")
    List<String> deleteAction(@PathParam("path") String path);

    @GET
    @Path("/get/{path}")
    Map<String, String> getAction(@QueryParam("query") String query,
            @PathParam("path") String path);

    @GET
    @Path("/get/genericReturn")
    Map<String, SuccessResponse> getActionWithGenericReturn();

    @PATCH
    @Path("/patch")
    Map<String, String> patchAction(@OperationBody List<Integer> contents);

    @PATCH
    @Path("/patch/voidReturn")
    void patchActionWithGenericReturn(@OperationBody List<Integer> unused);

    @POST
    @Path("/post")
    Map<String, String> postAction(@OperationBody List<String> contents);

    @POST
    @Path("/post/auth")
    List<String> postActionWithAuthInfo(@HeaderParam("header") String header,
            @CookieParam("cookie") String cookie, @OperationBody List<Integer> contents);
}
