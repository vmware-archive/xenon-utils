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
import static org.hamcrest.Matchers.greaterThan;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.test.TestRequestSender;

import java.util.HashMap;
import java.util.List;

public class PropogationTest {

    @Rule
    public ExternalResource isolateConfig = new IsolateConfig();

    @Test
    public void testIncomingTraces() throws Throwable {
        System.setProperty("tracer.appName", "testservice");
        MockTracer tracer = Helpers.injectTracer();
        Helpers.runHost((TestTracingHostMinimal serviceHost) -> {
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

            // Submit a request to the stateless service, which will allocate a trace id, then make an internal request
            // to the stateless server, which should give us another span under the same traceid if propogation outbound
            // and inbound is working right.
            Operation get = Operation.createGet(serviceHost, "/stateless");
            // The stateless service returns what it read from the stateful service.
            TestStatefulService.State getResult = sender.sendAndWait(get, TestStatefulService.State.class);
            assertEquals("foo", getResult.name);
            assertEquals("/stateful/foo", getResult.documentSelfLink);
        });
        List<MockSpan> finishedSpans = tracer.finishedSpans();
        // TODO: provide a nice declarative check. e.g. a matcher that takes a yaml expression.
        // We want to check:
        // for each span opname tags, type.
        MockSpan finishedSpan = finishedSpans.get(0);
        for (MockSpan span : finishedSpans) {
            System.out.printf("span %s \n", span.toString());
        }
        // TODO - Tags.HTTP_METHOD, Tags.HTTP_URL, Tags.HTTP_STATUS (response), operationName should be path
        /* Only one trace expected */
        // TODO on client spans:
/*
     * Adds standard tags: {@link io.opentracing.tag.Tags#SPAN_KIND},
     * {@link io.opentracing.tag.Tags#PEER_HOSTNAME}, {@link io.opentracing.tag.Tags#PEER_PORT},
     * {@link io.opentracing.tag.Tags#HTTP_METHOD}, {@link io.opentracing.tag.Tags#HTTP_URL} and
                * {@link io.opentracing.tag.Tags#HTTP_STATUS}


                // and
        Tags.HTTP_STATUS.set(span, responseContext.getStatus());
*/
        assertThat(finishedSpans.toArray(), Matchers.arrayWithSize(1));

    }
}

