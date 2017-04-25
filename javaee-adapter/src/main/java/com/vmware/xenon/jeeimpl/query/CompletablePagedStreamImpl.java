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

package com.vmware.xenon.jeeimpl.query;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.vmware.xenon.jee.query.CompletablePagedStream;
import com.vmware.xenon.jee.query.PagedResults;

/**
 */
public class CompletablePagedStreamImpl<D> implements CompletablePagedStream<D> {

    private CompletableFuture<PagedResults<D>> pages;

    public CompletablePagedStreamImpl(CompletableFuture<PagedResults<D>> pages) {
        this.pages = pages;
    }

    protected <R> Consumer<Throwable> completeByAccumulatingOrFail(
            CompletableFuture<List<R>> response, List<R> accumulate) {
        return (Throwable exception) -> {
            if (exception == null) {
                response.complete(accumulate);
            } else {
                response.completeExceptionally(exception);
            }
        };
    }

    protected <R> Consumer<Throwable> completeByAccumulatingUntilOrFail(
            CompletableFuture<List<R>> response, List<R> accumulate) {
        return (Throwable exception) -> {
            if (exception == null || exception instanceof ShortCircuitActionException) {
                response.complete(accumulate);
            } else {
                response.completeExceptionally(exception);
            }
        };
    }

    @Override
    public CompletableFuture<List<D>> filter(Predicate<? super D> criteria) {
        CompletableFuture<List<D>> response = new CompletableFuture<>();
        List<D> accumulate = new ArrayList<>();
        Consumer<PagedResults<D>> forEachPage = page -> page.documents().forEach(doc -> {
            if (criteria.test(doc)) {
                accumulate.add(doc);
            }
        });
        processAllPages(this.pages, forEachPage,
                completeByAccumulatingOrFail(response, accumulate));
        return response;
    }

    @Override
    public CompletableFuture<Void> forEach(Consumer<? super D> action) {
        CompletableFuture<Void> response = new CompletableFuture<>();
        Consumer<PagedResults<D>> forEachPage = page -> page.documents().forEach(action);
        Consumer<Throwable> _finally = e -> {
            if (e == null) {
                response.complete(null);
            } else {
                response.completeExceptionally(e);
            }
        };
        processAllPages(this.pages, forEachPage, _finally);
        return response;
    }

    @Override
    public CompletableFuture<Void> forEachWhile(Consumer<? super D> action,
            Predicate<? super D> until) {
        CompletableFuture<Void> response = new CompletableFuture<>();
        Consumer<PagedResults<D>> forEachPage = page -> page.documents().forEach(doc -> {
            if (until.test(doc)) {
                action.accept(doc);
            } else {
                throw new ShortCircuitActionException("LoopNoMore");
            }
        });
        Consumer<Throwable> _finally = e -> {
            if (e == null || e instanceof ShortCircuitActionException) {
                response.complete(null);
            } else {
                response.completeExceptionally(e);
            }
        };
        processAllPages(this.pages, forEachPage, _finally);
        return response;
    }

    @Override
    public <R> CompletableFuture<List<R>> map(Function<? super D, ? extends R> mapper) {
        CompletableFuture<List<R>> response = new CompletableFuture<>();
        List<R> accumulate = new ArrayList<>();
        Consumer<PagedResults<D>> forEachPage = page -> page.documents()
                .forEach(doc -> accumulate.add(mapper.apply(doc)));
        processAllPages(this.pages, forEachPage,
                completeByAccumulatingOrFail(response, accumulate));
        return response;
    }

    @Override
    public <R> CompletableFuture<List<R>> mapIf(Function<? super D, ? extends R> mapper,
            Predicate<? super D> ifTrue) {
        CompletableFuture<List<R>> response = new CompletableFuture<>();
        List<R> accumulate = new ArrayList<>();
        Consumer<PagedResults<D>> forEachPage = page -> page.documents().forEach(doc -> {
            if (ifTrue.test(doc)) {
                accumulate.add(mapper.apply(doc));
            }
        });
        processAllPages(this.pages, forEachPage,
                completeByAccumulatingOrFail(response, accumulate));
        return response;
    }

    @Override
    public <R> CompletableFuture<List<R>> mapWhile(Function<? super D, ? extends R> mapper,
            Predicate<? super D> ifTrue) {
        CompletableFuture<List<R>> response = new CompletableFuture<>();
        List<R> accumulate = new ArrayList<>();
        Consumer<PagedResults<D>> forEachPage = page -> page.documents().forEach(doc -> {
            if (ifTrue.test(doc)) {
                accumulate.add(mapper.apply(doc));
            } else {
                throw new ShortCircuitActionException("LoopNoMore");
            }
        });
        processAllPages(this.pages, forEachPage,
                completeByAccumulatingUntilOrFail(response, accumulate));
        return response;
    }

    private void processAllPages(CompletableFuture<PagedResults<D>> pages,
            Consumer<PagedResults<D>> processPage, Consumer<Throwable> _finally) {
        pages.thenAccept(page -> {
            try {
                processPage.accept(page);
                if (page.hasNextPage()) {
                    processAllPages(page.next(), processPage, _finally);
                } else {
                    _finally.accept(null);
                }
            } catch (Exception e) {
                _finally.accept(e);
            }
        });
        pages.exceptionally(throwable -> {
            _finally.accept(throwable);
            return null;
        });
    }
}
