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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.xenon.common.Utils.fromJson;
import static com.vmware.xenon.jee.util.ServiceConsumerUtil.newLocalStatefulSvcContract;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.spotify.futures.CompletableFutures;
import org.eclipse.collections.impl.test.Verify;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.consumer.StatefulServiceContract;
import com.vmware.xenon.jee.query.CompletablePagedStream;
import com.vmware.xenon.jee.query.PagedResults;
import com.vmware.xenon.jee.query.XenonQueryService;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.ExampleService.ExampleServiceState;
import com.vmware.xenon.services.common.QueryTask;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PagedDataQueryTest extends BasicReusableHostTestCase {

    static class ExampleServiceStateWrapper {

        private ExampleServiceState state;
        private String link;

        public ExampleServiceStateWrapper(ExampleServiceState state, String link) {
            this.state = state;
            this.link = link;
        }

    }

    static StatefulServiceContract<ExampleServiceState> statefulSvc;

    private XenonQueryService xenonQueryService;

    @Before
    public void initializeHost() throws Throwable {
        this.host.startFactory(new ExampleService());
        CountDownLatch latch = new CountDownLatch(1);
        this.host.registerForServiceAvailability(((completedOp, failure) -> latch.countDown()),
                ExampleService.FACTORY_LINK);
        latch.await(500, TimeUnit.MILLISECONDS);
        statefulSvc = newLocalStatefulSvcContract(this.host, ExampleService.FACTORY_LINK,
                ExampleServiceState.class);
        this.xenonQueryService = JaxRsServiceConsumer.newProxy(XenonQueryService.class, this.host);
    }

    private CompletablePagedStream<ExampleServiceState> queryToPagedStream(
            CompletableFuture<QueryTask> query) {
        return PagedStreamBuilder.newPagedStream(query, this.xenonQueryService,
                ExampleServiceState.class);
    }

    @Test
    public void setupData() throws Exception {
        for (int i = 0; i < 1000; i++) {
            ExampleServiceState state = new ExampleServiceState();
            state.name = "Name_" + (i % 100);
            state.required = "Required for " + i;
            state.sortedCounter = (long) i;
            state.keyValues = Maps.newHashMap();
            state.keyValues.put("key", "value");
            state.keyValues.put("count", "" + i);
            statefulSvc.post(state).join();
        }
    }

    @Test
    public void testPaginatedQuery() throws Exception {
        /* fire a paged query */
        QueryTask resp = this.xenonQueryService
                .oDataPagedQuery("name eq 'Name_54'", 5, "sortedCounter desc", "LONG").join();
        String nextPageLink = resp.results.nextPageLink;
        List<String> docs = new ArrayList<>();
        /* use fetchPage API and fetch next page until none available */
        do {
            resp = this.xenonQueryService.fetchPage(nextPageLink).join();
            System.out.println(resp.results.documentCount);
            nextPageLink = resp.results.nextPageLink;
            docs.addAll(resp.results.documentLinks);
        } while (nextPageLink != null);
        /* in order to verify, query all the documents at one and compare results */
        resp = this.xenonQueryService
                .oDataQuery("name eq 'Name_54'", 1000, "sortedCounter desc", "LONG")
                .join();
        assertEquals(resp.results.documentLinks.size(), docs.size());
        Verify.assertListsEqual(resp.results.documentLinks, docs);
    }

    /**
     * Test method assumes that setupData() & testPaginatedQuery() method executed successfully
     */
    @Test
    public void testPaginatedQueryUsingPagedResults() throws Exception {
        // issue a pagination query
        CompletableFuture<QueryTask> query = this.xenonQueryService.oDataPagedQuery(
                "name eq 'Name_54'",
                4, "sortedCounter desc", "LONG");
        //convert to paged results
        CompletableFuture<PagedResults<ExampleServiceState>> firstPage = query
                .thenApply(queryTask -> new PagedResultsImpl<>(queryTask, ExampleServiceState.class,
                        this.xenonQueryService));

        //verify
        assertEquals(Long.valueOf(10), firstPage.join().totalCount().join());
        assertEquals(4, firstPage.join().pageSize());
        assertTrue(firstPage.join().hasNextPage());
        assertFalse(firstPage.join().hasPreviousPage());
        assertTrue(firstPage.join().documents().isEmpty());

        //  traverse to second page
        CompletableFuture<PagedResults<ExampleServiceState>> secondPage = firstPage
                .thenCompose(PagedResults::next);

        //verify
        assertEquals(4, secondPage.join().pageSize());
        assertTrue(secondPage.join().hasNextPage());
        assertFalse(firstPage.join().hasPreviousPage());
        assertEquals(4, secondPage.join().documents().size());

        //  traverse to third page
        CompletableFuture<PagedResults<ExampleServiceState>> thirdPage = secondPage
                .thenCompose(PagedResults::next);

        //verify
        assertEquals(4, thirdPage.join().pageSize());
        assertTrue(thirdPage.join().hasNextPage());
        assertTrue(thirdPage.join().hasPreviousPage());
        assertEquals(4, thirdPage.join().documents().size());

        //  traverse to fourth page
        CompletableFuture<PagedResults<ExampleServiceState>> fourthPage = thirdPage
                .thenCompose(PagedResults::next);

        //verify
        assertEquals(4, fourthPage.join().pageSize());
        assertEquals(2, fourthPage.join().documents().size());
        assertFalse(fourthPage.join().hasNextPage());
        assertTrue(fourthPage.join().hasPreviousPage());

    }

    /**
     * Test method assumes that setupData() & testPaginatedQuery() method executed successfully
     */
    @Test
    public void testPaginatedQueryUsingPagedStream() throws Exception {
        verifyMap();
        verifyMapIf();
        verifyMapWhile();
        verifyFilter();
        verifyForEach();
        verifyForEachWhile();

        verifyResultConverter();

    }

    private void verifyExceptionInPagedResults(Supplier<?> streamAction) {
        try {
            streamAction.get();
            fail("Shouldn't reach here");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof RuntimeException);
            assertEquals("Wont process any more", e.getCause().getMessage());
        }
    }

    private void verifyFilter() {
        String filterCriteria = "name eq 'Name_5'";
        CompletableFuture<QueryTask> query = this.xenonQueryService.oDataPagedQuery(filterCriteria,
                4,
                "sortedCounter desc", "LONG");
        CompletablePagedStream<ExampleServiceState> pagedStream = queryToPagedStream(query);
        List<String> docLinks = pagedStream
                .filter(exampleServiceState -> exampleServiceState.sortedCounter < 500).join()
                .stream().map(state -> state.documentSelfLink).collect(Collectors.toList());
        verifyPagedResults(filterCriteria + " and sortedCounter lt 500", docLinks);

        //what happens when lambda throws exception
        verifyExceptionInPagedResults(() -> pagedStream.filter(state -> {
            if (state.sortedCounter > 250) {
                throw new RuntimeException("Wont process any more");
            } else {
                return true;
            }
        })
                .join());
    }

    private void verifyForEach() {
        String filterCriteria = "name eq 'Name_5'";
        CompletableFuture<QueryTask> query = this.xenonQueryService.oDataPagedQuery(filterCriteria,
                3,
                "sortedCounter desc", "LONG");
        CompletablePagedStream<ExampleServiceState> pagedStream = queryToPagedStream(query);
        List<String> actualLinks = new ArrayList<>();
        pagedStream.forEach(ex -> actualLinks.add(ex.documentSelfLink)).join();

        verifyPagedResults(filterCriteria, actualLinks);

        //what happens when lambda throws exception
        verifyExceptionInPagedResults(() -> pagedStream.forEach(state -> {
            if (state.sortedCounter > 250) {
                throw new RuntimeException("Wont process any more");
            }
        })
                .join());
    }

    private void verifyForEachWhile() {
        String filterCriteria = "name eq 'Name_5'";
        CompletableFuture<QueryTask> query = this.xenonQueryService.oDataPagedQuery(filterCriteria,
                2,
                "sortedCounter desc", "LONG");
        CompletablePagedStream<ExampleServiceState> pagedStream = queryToPagedStream(query);
        List<String> actualLinks = new ArrayList<>();
        pagedStream.forEachWhile(ex -> actualLinks.add(ex.documentSelfLink),
                ex -> ex.sortedCounter > 500)
                .join();

        verifyPagedResults(filterCriteria + " and sortedCounter gt 500", actualLinks);
    }

    private void verifyMap() {
        // issue a pagination query
        String filterCriteria = "name eq 'Name_54'";
        String nameSuffix = "_UPDATED";
        QueryTask expectedResp = this.xenonQueryService
                .oDataQuery(filterCriteria, 1000, "sortedCounter desc", "LONG").join();
        CompletableFuture<QueryTask> query = this.xenonQueryService.oDataPagedQuery(filterCriteria,
                4,
                "sortedCounter desc", "LONG");
        CompletablePagedStream<ExampleServiceState> pagedStream = queryToPagedStream(query);

        List<String> actualDocLinks = new ArrayList<>();
        List<ExampleServiceState> allDocs = pagedStream.map(state -> {
            actualDocLinks.add(state.documentSelfLink);
            state.name = state.name + nameSuffix;
            return statefulSvc.putBySelfLink(state.documentSelfLink, state);
        })
                .thenCompose(CompletableFutures::allAsList).join();

        assertEquals(expectedResp.results.documentLinks.size(), actualDocLinks.size());
        assertEquals(expectedResp.results.documentLinks.size(), allDocs.size());
        Verify.assertListsEqual(expectedResp.results.documentLinks, actualDocLinks);
        Verify.assertAllSatisfy(allDocs, each -> each.name.contains(nameSuffix));

        //what happens when lambda throws exception
        verifyExceptionInPagedResults(
                () -> pagedStream.map(state -> {
                    if (state.sortedCounter > 500) {
                        throw new RuntimeException("Wont process any more");
                    } else {
                        return state.documentSelfLink;
                    }
                }).join());
    }

    private void verifyMapIf() {
        String filterCriteria = "name eq 'Name_5'";
        CompletableFuture<QueryTask> query = this.xenonQueryService.oDataPagedQuery(filterCriteria,
                4,
                "sortedCounter desc", "LONG");
        CompletablePagedStream<ExampleServiceState> pagedStream = queryToPagedStream(query);
        List<String> docLinks = pagedStream
                .mapIf(ex -> ex.documentSelfLink, ex -> ex.sortedCounter > 500).join();

        verifyPagedResults(filterCriteria + " and sortedCounter gt 500", docLinks);
    }

    private void verifyMapWhile() {
        String filterCriteria = "name eq 'Name_5'";
        CompletableFuture<QueryTask> query = this.xenonQueryService.oDataPagedQuery(filterCriteria,
                4,
                "sortedCounter desc", "LONG");
        CompletablePagedStream<ExampleServiceState> pagedStream = queryToPagedStream(query);
        List<String> docLinks = pagedStream.mapWhile(ex -> {
            System.out.println(ex.documentSelfLink + " : " + ex.sortedCounter);
            return ex.documentSelfLink;
        }, ex -> ex.sortedCounter > 500).join();

        verifyPagedResults(filterCriteria + " and sortedCounter gt 500", docLinks);
    }

    private void verifyPagedResults(String criteria, List<String> actualLinks) {
        QueryTask expectedResp = this.xenonQueryService
                .oDataQuery(criteria, 1000, "sortedCounter desc", "LONG").join();
        assertEquals(expectedResp.results.documentLinks.size(), actualLinks.size());
        Verify.assertListsEqual(expectedResp.results.documentLinks, actualLinks);
    }

    private void verifyResultConverter() {
        String filterCriteria = "name eq 'Name_5'";
        CompletableFuture<QueryTask> query = this.xenonQueryService.oDataPagedQuery(filterCriteria,
                4,
                "sortedCounter desc", "LONG");
        Function<ServiceDocumentQueryResult, List<ExampleServiceStateWrapper>> converter = queryResult -> {
            List<ExampleServiceStateWrapper> documents = new ArrayList<>();
            queryResult.documentLinks.forEach(link -> {
                ExampleServiceState e = fromJson(queryResult.documents.get(link),
                        ExampleServiceState.class);
                documents.add(new ExampleServiceStateWrapper(e, e.documentSelfLink));
            });
            return documents;
        };
        CompletablePagedStream<ExampleServiceStateWrapper> pagedStream = PagedStreamBuilder
                .newPagedStream(query, this.xenonQueryService, converter);
        List<String> docLinks = new ArrayList<>();
        pagedStream.forEach(wrapper -> docLinks.add(wrapper.link)).join();
        verifyPagedResults(filterCriteria, docLinks);
    }

}
