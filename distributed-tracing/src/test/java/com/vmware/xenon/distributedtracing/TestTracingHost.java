/*
 * Copyright (c) 2014-2015 VMware, Inc. All Rights Reserved.
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

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;
import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.ThreadLocalActiveSpan;

import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Our entry point, spawning a host that run/showcase examples we can play with.
 */
public class TestTracingHost extends ServiceHost {

    private Tracer tracer;

    public TestTracingHost() throws Throwable {
        super();
        this.tracer = DTracer.getTracer();
    }

    public static void main(String[] args) throws Throwable {
        TestTracingHost h = new TestTracingHost();
        h.initialize(args);
        LuceneDocumentIndexService documentIndexService = new LuceneDocumentIndexService();
        documentIndexService.toggleOption(ServiceOption.INSTRUMENTATION, true);
        h.setDocumentIndexingService(documentIndexService);
        h.toggleDebuggingMode(true);
        h.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            h.log(Level.WARNING, "Host stopping ...");
            h.stop();
            h.log(Level.WARNING, "Host is stopped");
        }));
    }

    public Tracer getTracer() {
        return this.tracer;
    }

    @Override
    @SuppressWarnings("try")
    public ServiceHost start() throws Throwable {
        // Trace the startup process
        try (ActiveSpan activeSpan = this.tracer.buildSpan("ServiceHost.start").startActive()) {
            super.start();

            // Start core services, must be done once - note that this traces internally
            startDefaultCoreServicesSynchronously();
            // Start the root namespace service: this will list all available factory services for
            // queries to the root (/)

            try (ActiveSpan nsSpan = this.tracer.buildSpan("startNamespace").startActive()) {
                super.startService(new RootNamespaceService());
            }
            try (ActiveSpan exampleSpan = this.tracer.buildSpan("startExampleServices").startActive()) {
                // Start example services
                //startFactory(new ExampleService());
                //startFactory(new ExampleTaskService());
                startFactory(new TestStatefulService());
                startService(new TestStatelessService());
            }

        }
        if (this.tracer.activeSpan() != null) {
            throw new Exception(String.format("leaked span %s", this.mock_span_info(this.tracer.activeSpan())));
        }
        return this;
    }

    @Override
    @SuppressWarnings("try")
    public void startDefaultCoreServicesSynchronously() throws Throwable {
        try (ActiveSpan coreSpan = this.tracer.buildSpan("startDefaultCore").startActive()) {
            super.startDefaultCoreServicesSynchronously();
        }
    }

    @Override
    @SuppressWarnings("try")
    public boolean handleRequest(Service service, Operation inboundOp) {
        /* NettyHttpServiceClient dispatches work back into this host within the current context
           - so we
         */
        if (inboundOp == null && service != null) {
            inboundOp = service.dequeueRequest();
        }

        if (inboundOp == null) {
            return true;
        }
        Operation op = inboundOp;
        // Debugging: check that on exit the same span is active as on entrance.
        ActiveSpan callerSpan = this.tracer.activeSpan();
        try {
            SpanContext extractedContext = this.tracer.extract(
                Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(inboundOp.getRequestHeaders()));
            System.out.printf("{prop-recv} %s\n", contextToString((MockSpan.MockContext) extractedContext));
            ActiveSpan span = this.tracer.buildSpan(op.getAction().name())
                    // By definition this is a new request, so we don't want to use any active span (e.g. due to
                    // fastpathing) as a parent.
                    .ignoreActiveSpan()
                    .asChildOf(extractedContext)
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                    .startActive();
            try {
                if (((MockSpan.MockContext) span.context()).traceId() == 16) {
                    System.out.printf("mde span %s", 16);
                }
                // All the work until we return from this function should be under this span.
                LateContinuation cont = new LateContinuation();
                System.out.printf("{recv} newspan %s %s\n", mock_span_info(span), op.getUri());
                op.nestCompletion((o, e) -> {
                    if (cont.continuation == null) {
                        cont.continuation = span.capture();
                    }
                    try (ActiveSpan contspan = cont.continuation.activate()) {
                        if (e == null) {
                            o.complete();
                        } else {
                            o.fail(e);
                        }
                        System.out.printf("{recv} byespan %s %s %s\n", mock_span_info(span), mock_span_info(contspan), o.getUri());
                    }
                });
                try {
                    return super.handleRequest(service, inboundOp);
                } finally {
                    if (cont.continuation == null) {
                        cont.continuation = span.capture();
                    }
                }
            } finally {
                span.close();
            }
        } finally {
            if (callerSpan != this.tracer.activeSpan()) {
                System.out.printf("Different exit Span was %s now %s\n", mock_span_info(callerSpan), mock_span_info(this.tracer.activeSpan()));
                int X = 1/0;
            }
        }
    }

    private static class LateContinuation {
        public ActiveSpan.Continuation continuation;
    }

    private int completions = 0;

    // TODO also broadcast requests.
    @Override
    public void sendRequest(Operation op) {
        // Debugging
        op.setExpiration(Utils.fromNowMicrosUtc(100000000));
        // Overlap with the base class because we add a completion ourselves preventing this from working in
        // prepareRequest.
        if (op.getCompletion() == null) {
            op.setCompletion((o, e) -> {
                if (e == null) {
                    return;
                }
                if (op.isFailureLoggingDisabled()) {
                    return;
                }
                log(Level.WARNING, "%s (ctx id:%s) to %s, from %s failed: %s", o.getAction(),
                        o.getContextId(),
                        o.getUri(),
                        o.getReferer(),
                        e.getMessage());
            });
        }
        if (true /* minimising failure */) {
            op.nestCompletion((o, e) -> {
                System.out.printf("Completing %s %s %s\n", o.getAction(), o.getUri().getPath(), e);
                if (e == null) {
                    o.complete();
                } else {
                    o.fail(e);
                }
            });
            super.sendRequest(op);
            return;
        }
        // Create a separate tracer to track the request
        ActiveSpan span = this.tracer.buildSpan(op.getAction().name())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .startActive();
        // refcount 1
        try {
            System.out.printf("{send} made span %s %s\n", mock_span_info(span), op.getUri());
            tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS,
                    new TextMapInjectAdapter(op.getRequestHeaders()));

            // work spawned in the process of sending should use this span as a parent,

            // but we can't capture it directly after creating the completion - and we have to submit the
            // completion before calling sendRequest, because the request can get fast-pathed.
            LateContinuation cont = new LateContinuation();
            // We should have the span present when any new work is created in callbacks.
            // NB: Callbacks can manage their own inner spans too, of course.
            if (this.completions > 0  && false) {
                System.out.printf("Skipping completion path for Request %s %s\n", op.getAction(), op.getUri().getPath());
                super.sendRequest(op);
                return;
            } else
                this.completions += 1;
            op.nestCompletion((o, e) -> {
                System.out.printf("Completing %s %s %s\n", o.getAction(), o.getUri().getPath(), e);
                if (cont.continuation == null) {
                    cont.continuation = span.capture(); // ref +1 =2
                }
                try (ActiveSpan contspan = cont.continuation.activate()) {
                    if (e == null) {
                        o.complete();
                    } else {
                        o.fail(e);
                    }
                    //    System.out.printf("{send} clos span %s %s %s\n", mock_span_info(span), mock_span_info(contspan), o.getUri());
                    // ref -1
                }
            });
            System.out.printf("Sending Request %s %s\n", op.getAction(), op.getUri().getPath());
            super.sendRequest(op);
            // Work that happens after we return should not.
            if (cont.continuation == null) {
                cont.continuation = span.capture(); // ref +1 = 2
            }
        } finally {
            span.close();
            // refcount 0
        }
/*
        // We want to complete the span when op completes - success or fail.

        super.sendRequest(op); */
    }

    // debugging
    String mock_span_info(ActiveSpan span) {
        try {
        Field field = ThreadLocalActiveSpan.class.getDeclaredField("wrapped");
        field.setAccessible(true);
        return field.get(span).toString();
        } catch (IllegalAccessException|NoSuchFieldException e) {
            return "unknown span config";
        }/*
        ((ThreadLocalActiveSpan)span).wrapped.toString();
        MockSpan.MockContext ctx = (MockSpan.MockContext) span.context();
        return contextToString(ctx);*/

    }

    String contextToString(MockSpan.MockContext ctx) {
        if (ctx == null) {
            return "Not a Mock Context";
        }
        return String.format("Span[%s:%s]", ctx.traceId(), ctx.spanId());
    }

}
