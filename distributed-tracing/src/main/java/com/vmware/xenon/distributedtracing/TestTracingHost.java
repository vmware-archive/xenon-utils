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

import java.util.logging.Level;
import java.util.logging.Logger;

import io.opentracing.ActiveSpan;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;

/**
 * Our entry point, spawning a host that run/showcase examples we can play with.
 */
public class TestTracingHost extends ServiceHost {

    private Logger logger = Logger.getLogger(getClass().getName());

    public Logger getLogger() {
        return this.logger;
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

    @Override
    @SuppressWarnings("try")
    public ServiceHost start() throws Throwable {
        // Trace the local startup process
        try (ActiveSpan activeSpan = this.getTracer().buildSpan("TestTracingHost.start").startActive()) {
            // Will nest under our span
            super.start();

            // Start core services, must be done once - note that this traces internally
            startDefaultCoreServicesSynchronously();

            // Start the root namespace service: this will list all available factory services for
            // queries to the root (/)
            try (ActiveSpan nsSpan = this.getTracer().buildSpan("startNamespace").startActive()) {
                super.startService(new RootNamespaceService());
            }
            try (ActiveSpan exampleSpan = this.getTracer().buildSpan("startExampleServices").startActive()) {
                // Start example services
                //startFactory(new ExampleService());
                //startFactory(new ExampleTaskService());
                startFactory(new TestStatefulService());
                startService(new TestStatelessService());
            }
        }
        return this;
    }
}
