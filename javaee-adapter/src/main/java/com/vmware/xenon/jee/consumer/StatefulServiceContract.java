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

package com.vmware.xenon.jee.consumer;

import static com.vmware.xenon.jee.util.ServiceConsumerUtil.selfLinkToId;

import java.util.concurrent.CompletableFuture;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.vmware.xenon.jee.annotations.OperationBody;
import com.vmware.xenon.jee.annotations.PATCH;
import com.vmware.xenon.jee.annotations.PragmaDirective;

/**
 * Contract applicable for all stateful services.
 * Note that, not all services implement all types of HTTP operation.
 */
public interface StatefulServiceContract<T> {

    @DELETE
    @Path("{id}")
    CompletableFuture<T> delete(@PathParam("id") String id);

    @DELETE
    @Path("{id}")
    @Deprecated
    CompletableFuture<T> delete(@PathParam("id") String id, @OperationBody T deleteBody);

    /**
     * @param docLink the document self link
     * @return updated service doc
     */
    default CompletableFuture<T> deleteBySelfLink(String docLink) {
        return delete(selfLinkToId(docLink));
    }

    @GET
    @Path("{id}")
    CompletableFuture<T> get(@PathParam("id") String id);

    default CompletableFuture<T> getBySelfLink(String docLink) {
        return get(selfLinkToId(docLink));
    }

    @PATCH
    @Path("{id}")
    CompletableFuture<T> patch(@PathParam("id") String id, @OperationBody T patchBody);

    /**
     * @param docLink   the document self link
     * @param patchBody Partial body of the service document which the actual service expects
     * @return updated service doc
     */
    default CompletableFuture<T> patchBySelfLink(String docLink, T patchBody) {
        return patch(selfLinkToId(docLink), patchBody);
    }

    @POST
    CompletableFuture<T> post(@OperationBody T postBody);

    @POST
    CompletableFuture<T> postWithPragma(@OperationBody T postBody,
            @PragmaDirective String... pragma);

    @PUT
    @Path("{id}")
    CompletableFuture<T> put(@PathParam("id") String id, @OperationBody T putBody);

    /**
     * @param docLink   the document self link
     * @param patchBody Complete body of the service document which the actual service expects
     * @return updated service doc
     */
    default CompletableFuture<T> putBySelfLink(String docLink, T patchBody) {
        return put(selfLinkToId(docLink), patchBody);
    }
}
