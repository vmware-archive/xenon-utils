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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Iterator kind of view for Xenon paginated query task results.
 * <p>
 * Implementation has to be async and non-blocking.
 */
public interface PagedResults<D> extends Iterator<CompletableFuture<PagedResults<D>>> {

    /**
     * Returns the documents in the current page
     * Note that first page (index 0) will always have no documents
     */
    List<D> documents();

    @Override
    default boolean hasNext() {
        return hasNextPage();
    }

    /**
     * Returns true is next page is available
     */
    boolean hasNextPage();

    /**
     * Returns true is previous page is available
     */
    boolean hasPreviousPage();

    /**
     * Return next page's PagedResults
     *
     * @throws NullPointerException when there is no next page
     */
    @Override
    CompletableFuture<PagedResults<D>> next();

    /**
     * No of documents returned in the current page
     */
    int pageSize();

    /**
     * Return previous page's PagedResults
     *
     * @throws NullPointerException when there is no previous page
     */
    CompletableFuture<PagedResults<D>> previous();

    /**
     * Total no of documents matching the query
     */
    CompletableFuture<Long> totalCount();

}
