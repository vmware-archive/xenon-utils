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
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;

import java.util.logging.Level;

/**
 * Our entry point, spawning a host that run/showcase examples we can play with.
 */
public class TestTracingHostMinimal extends ServiceHost {

    public static void main(String[] args) throws Throwable {
        TestTracingHostMinimal h = new TestTracingHostMinimal();
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

    @Override
    public ServiceHost start() throws Throwable {
        super.start();
        // Start core services, must be done once - note that this traces internally
        startDefaultCoreServicesSynchronously();
        return this;
    }

    @Override
    public void sendRequest(Operation op) {
        // Overlap with the base class because we add a completion ourselves preventing this from working in
        // prepareRequest.
        if (op.getCompletion() == null) {
            op.setCompletion((o, e) -> {
                if (e == null) {
                    return;
                }
                if (o.isFailureLoggingDisabled()) {
                    return;
                }
                log(Level.WARNING, "%s (ctx id:%s) to %s, from %s failed: %s", o.getAction(),
                        o.getContextId(),
                        o.getUri(),
                        o.getReferer(),
                        e.getMessage());
            });
        }

        if (op.getId() > 11) {
            System.out.printf("skipping nest on op %s %s %s %s\n", op.getId(), op.getAction(), op.getUri().getPath(), op.getStatusCode());
            super.sendRequest(op);
            return;
        }

        /* XXX: comment out this, or change the getId check to > 10, and host startup completes normally. */

        op.nestCompletion((o, e) -> {
            System.out.printf("Completing %s %s %s %s %s\n", o.getId(), o.getAction(), o.getUri().getPath(), o.getStatusCode(), e);
            if (e == null) {
                o.complete();
            } else {
                o.fail(e);
            }
        });
        System.out.printf("sending %s %s %s %s\n", op.getId(), op.getAction(), op.getUri().getPath(), op.getStatusCode());
        super.sendRequest(op);
    }
}
