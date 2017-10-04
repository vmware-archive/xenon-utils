/*
 * Copyright (c) 2014-2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.distributedtracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.test.TestRequestSender;


public class PropogationTest {

    @Rule
    public ExternalResource isolateConfig = new IsolateConfig();

    @Test
    public void testIncomingTraces() throws Throwable {
        System.setProperty("otTracer.appName", "testservice");
        MockTracer tracer = Helpers.injectTracer();
        List<String> uris = new ArrayList<String>();
        Helpers.runHost((TestTracingHost serviceHost) -> {
            System.out.printf("start test code\n");
            // create request sender using one of the hosts in the nodegroup
            TestRequestSender sender = new TestRequestSender(serviceHost);
            // Populate the stateful service.
            TestStatefulService.State postBody = new TestStatefulService.State();
            postBody.name = "foo";
            postBody.documentSelfLink = "foo";
            Operation post = Operation.createPost(serviceHost, "/stateful").setBody(postBody);
            TestStatefulService.State postResult = sender.sendAndWait(post, TestStatefulService.State.class);
            assertEquals("foo", postResult.name);
            assertEquals("/stateful/foo", postResult.documentSelfLink);
            // we don't care about traces before this point
            tracer.reset();
            System.out.printf("otTracer reset\n");

            // Submit a request to the stateless service, which will allocate a trace id, then make an internal request
            // to the stateless server, which should give us another span under the same traceid if propogation outbound
            // and inbound is working right.
            Operation get = Operation.createGet(serviceHost, "/stateless");
            // The stateless service returns what it read from the stateful service.
            TestStatefulService.State getResult = sender.sendAndWait(get, TestStatefulService.State.class);
            assertEquals("foo", getResult.name);
            assertEquals("/stateful/foo", getResult.documentSelfLink);
            // stateful
            uris.add(Operation.createGet(serviceHost, "/stateful/foo").getUri().toString());
            // stateless
            uris.add(get.getUri().toString());
        });
        List<MockSpan> finishedSpans = tracer.finishedSpans();
        /* Spans can potentially complete out of order */
        Collections.sort(finishedSpans, (e1, e2) -> Long.compare(e1.context().spanId(), e2.context().spanId()));
        // TODO: provide a nice declarative check. e.g. a matcher that takes a yaml expression.
        // We want to check:
        // for each span opname tags, type.
        MockSpan finishedSpan = finishedSpans.get(0);
        long traceId = finishedSpan.context().traceId();
        /* Urls: 0 and 1 are the client and server handling of stateless, 2 and 3 stateful/foo. */
        String stateful = uris.get(0);
        String stateless = uris.get(1);
        assertEquals(stateless, finishedSpans.get(0).tags().get(Tags.HTTP_URL.getKey()));
        assertEquals(stateless, finishedSpans.get(1).tags().get(Tags.HTTP_URL.getKey()));
        assertEquals(stateful, finishedSpans.get(2).tags().get(Tags.HTTP_URL.getKey()));
        assertEquals(stateful, finishedSpans.get(3).tags().get(Tags.HTTP_URL.getKey()));
        /* kinds: 0 and 2 should be outbound CLIENT spans, 1 and 3 inbound SERVER spans. */
        assertEquals(Tags.SPAN_KIND_CLIENT, finishedSpans.get(0).tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals(Tags.SPAN_KIND_SERVER, finishedSpans.get(1).tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals(Tags.SPAN_KIND_CLIENT, finishedSpans.get(2).tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals(Tags.SPAN_KIND_SERVER, finishedSpans.get(3).tags().get(Tags.SPAN_KIND.getKey()));
        for (MockSpan span : finishedSpans) {
            System.out.printf("span %s \n", span.toString());
            assertEquals(String.format("broken trace span %s", span.toString()), traceId, span.context().traceId());
            assertEquals(String.format("trace span %s", span.toString()), "GET", span.tags().get(Tags.HTTP_METHOD.getKey()));
            assertEquals(String.format("trace span %s", span.toString()), "200", span.tags().get(Tags.HTTP_STATUS.getKey()));
        }
        // TODO: test of error paths to ensure cpaturing of status is robust
        // TODO -, operationName should be path
        /* Only one trace expected */
/* TODO: io.opentracing.tag.Tags#PEER_HOSTNAME, io.opentracing.tag.Tags#PEER_PORT */
        assertThat(finishedSpans.toArray(), Matchers.arrayWithSize(4));

    }
}

