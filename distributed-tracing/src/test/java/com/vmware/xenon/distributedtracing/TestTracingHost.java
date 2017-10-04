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

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vmware.xenon.common.opentracing.TracerFactory;
import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;


import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;

/**
 * Our entry point, spawning a host that run/showcase examples we can play with.
 */
public class TestTracingHost extends ServiceHost {

    public Tracer tracer;
    private Logger logger = Logger.getLogger(getClass().getName());

    public Logger getLogger() {
        return this.logger;
    }

    public TestTracingHost() throws Throwable {
        super();
        this.tracer = TracerFactory.factory.create(this);
    }

    public static void main(String[] args) throws Throwable {
        TestTracingHost h = new TestTracingHost();
        h.initialize(args);
        LuceneDocumentIndexService documentIndexService = new LuceneDocumentIndexService();
        documentIndexService.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
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
            throw new Exception(String.format("leaked span %s", this.tracer.activeSpan().toString()));
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
        SpanContext extractedContext = this.tracer.extract(
                Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(inboundOp.getRequestHeaders()));
        ActiveSpan span = this.tracer.buildSpan(op.getUri().getPath().toString())
                // By definition this is a new request, so we don't want to use any active span (e.g. due to
                // fastpathing) as a parent.
                .ignoreActiveSpan()
                .asChildOf(extractedContext)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .startActive();
        this.addOperationTags(span, op);
        try {
            // All the work until we return from this function should be under this span.
            LateContinuation cont = new LateContinuation();
            this.nestCompletion(op, (o, e) -> {
                if (cont.continuation == null) {
                    cont.continuation = span.capture();
                }
                try (ActiveSpan contspan = cont.continuation.activate()) {
                    contspan.setTag(Tags.HTTP_STATUS.getKey(), Integer.toString(o.getStatusCode()));
                    if (e == null) {
                        o.complete();
                    } else {
                        o.fail(e);
                    }
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
    }

    private static class LateContinuation {
        public ActiveSpan.Continuation continuation;
    }

    public void addOperationTags(ActiveSpan span, Operation op) {
        span.setTag(Tags.HTTP_METHOD.getKey(), op.getAction().toString());
        span.setTag(Tags.HTTP_URL.getKey(), op.getUri().toString());
    }

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
        // Create a separate tracer to track the request
        ActiveSpan span = this.tracer.buildSpan(op.getUri().getPath().toString())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .startActive();
        this.addOperationTags(span, op);
        try {
            this.tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS,
                    new TextMapInjectAdapter(op.getRequestHeaders()));

            // work spawned in the process of sending should use this span as a parent,

            // but we can't capture it directly after creating the completion - and we have to submit the
            // completion before calling sendRequest, because the request can get fast-pathed.
            LateContinuation cont = new LateContinuation();
            // We should have the span present when any new work is created in callbacks.
            // NB: Callbacks can manage their own inner spans too, of course.
            this.nestCompletion(op, (o, e) -> {
                if (cont.continuation == null) {
                    cont.continuation = span.capture();
                }
                try (ActiveSpan contspan = cont.continuation.activate()) {
                    contspan.setTag(Tags.HTTP_STATUS.getKey(), Integer.toString(o.getStatusCode()));
                    if (e == null) {
                        o.complete();
                    } else {
                        o.fail(e);
                    }
                }
            });
            super.sendRequest(op);
            // Work that happens after we return should not.
            if (cont.continuation == null) {
                cont.continuation = span.capture();
            }
        } finally {
            span.close();
        }
    }

    /* Preserve trace context over 'run' */
    @Override
    public void run(Runnable task) {
        this.run(this.getExecutor(), task);
    }

    public void executeRunnableSafe(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log(Level.SEVERE, "Unhandled exception executing task: %s", Utils.toString(e));
        }
    }

    @Override
    public void run(ExecutorService executor, Runnable task) {
        if (executor == null || task == null) {
            throw new IllegalStateException("Valid executor/task must be provided");
        }
        if (executor.isShutdown()) {
            throw new IllegalStateException("Stopped");
        }
        OperationContext origContext = OperationContext.getOperationContext();
        ActiveSpan span = this.tracer.activeSpan();
        ActiveSpan.Continuation cont;
        if (span != null) {
            cont = span.capture();
        } else {
            cont = null;
        }
        try {
            this.getExecutor().execute(() -> {
                ActiveSpan contspan = null != cont ? cont.activate() : null;
                try {
                    OperationContext.setFrom(origContext);
                    executeRunnableSafe(task);
                } finally {
                    if (contspan != null) {
                        contspan.close();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            if (cont != null) {
                cont.activate().close();
            }
            throw e;
        }
    }

    @Override
    public void queueOrScheduleRequestInternal(Service s, Operation op) {
        if (!s.queueRequest(op)) {
            ActiveSpan span = this.tracer.activeSpan();
            ActiveSpan.Continuation cont = null != span ? span.capture() : null;
            Runnable r = () -> {
                OperationContext opCtx = OperationContext.getOperationContext();
                OperationContext.setFrom(op);
                try {
                    ActiveSpan contspan = null != cont ? cont.activate() : null;
                    try {
                        s.handleRequest(op);
                    } finally {
                        if (contspan != null) {
                            contspan.close();
                        }
                    }
                } catch (Exception e) {
                    if (!Utils.isValidationError(e)) {
                        this.log(Level.SEVERE, "Uncaught exception in service %s: %s", s.getUri(),
                                Utils.toString(e));
                    } else if (this.getLogger().isLoggable(Level.FINE)) {
                        this.log(Level.FINE, "Validation Error in service %s: %s", s.getUri(), Utils.toString(e));
                    }
                    op.fail(e);
                } finally {
                    OperationContext.setFrom(opCtx);
                }
            };
            try {
                this.getExecutor().execute(r);
            } catch (RejectedExecutionException e) {
                if (cont != null) {
                    cont.activate().close();
                }
                throw e;
            }

        }

    }

    /* These three methods workaround https://www.pivotaltracker.com/story/show/151798288 */
    private static Operation.CompletionHandler getCompletion(Operation op) {
        try {
            Field field = Operation.class.getDeclaredField("completion");
            field.setAccessible(true);
            return (Operation.CompletionHandler)field.get(op);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException("Couldn't read completion");
        }
    }

    private static void setCompletion(Operation op, Operation.CompletionHandler c) {
        try {
            Field field = Operation.class.getDeclaredField("completion");
            field.setAccessible(true);
            field.set(op, c);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException("Couldn't set completion");
        }

    }

    public void nestCompletion(Operation op, Operation.CompletionHandler h) {
        Operation.CompletionHandler existing = getCompletion(op);
        setCompletion(op, (o, e) -> {
            op.setStatusCode(o.getStatusCode());
            setCompletion(o, existing);
            h.handle(o, e);
        });
    }
}
