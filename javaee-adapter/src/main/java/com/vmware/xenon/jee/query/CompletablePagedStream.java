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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@link com.vmware.xenon.services.common.QueryTask} with limit clause returns multiple pages of responses.
 * <p>
 * An implementation of this interface provides a Stream like view on all of the results across pages.
 * <p>
 * Wherever possible, perform filter operation using Query Task itself and use this only for map kind of operation.
 */
public interface CompletablePagedStream<D> {

    /**
     * For internal use only
     */
    class ShortCircuitActionException extends RuntimeException {

        static final long serialVersionUID = -1;

        public ShortCircuitActionException(String message) {
            super(message);
        }

    }

    /**
     * Given criteria, filters all the documents by navigating to
     */
    CompletableFuture<List<D>> filter(Predicate<? super D> criteria);

    /**
     * Given action is executed for all the results across all the pages
     */
    CompletableFuture<Void> forEach(Consumer<? super D> action);

    /**
     * Given action is executed for all the results across all the pages until the predicate returns false for the first time.
     */
    CompletableFuture<Void> forEachWhile(Consumer<? super D> action, Predicate<? super D> until);

    /**
     * For each of the document across all the pages, given mapper is executed and mapped out is collected.
     * <p>
     * Return type of mapper can be either be a direct result or CompletableFuture depending on the needs
     */
    <R> CompletableFuture<List<R>> map(Function<? super D, ? extends R> mapper);

    /**
     * For each of the document across all the pages, given mapper is executed whenever the predicate matches and mapped out is collected.
     * <p>
     * Return type of mapper can be either be a direct result or CompletableFuture depending on the needs
     */
    <R> CompletableFuture<List<R>> mapIf(Function<? super D, ? extends R> mapper,
            Predicate<? super D> ifTrue);

    /**
     * For each of the document across all the pages, given mapper is executed until predicate matches
     * ( aborts the stream processing when the predicates returns false for the first time). Mapped out is collected
     * <p>
     * Return type of mapper can be either be a direct result or CompletableFuture depending on the needs
     */
    <R> CompletableFuture<List<R>> mapWhile(Function<? super D, ? extends R> mapper,
            Predicate<? super D> until);
}
