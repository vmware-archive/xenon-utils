/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.failsafe;

import java.io.IOException;
import java.net.URI;

import org.junit.Test;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

public class FailsafeServiceTest extends FailsafeTest {
    @Override
    public ServiceHost startHost(String[] stringArgs, ServiceHost.Arguments args) throws Throwable {
        // start the FailserverServiceHost to host the FailsafeService
        FailsafeServiceHost.clearRules();
        ServiceHost h = FailsafeServiceHost.startHost(stringArgs, args);
        h.startService(new FailsafeTestPingService());
        return h;
    }

    FailsafeService.Rule defaultRule = new FailsafeService.Rule();

    @Test
    public void canSendWithoutBreaker() throws IOException {
        FailsafeService.Rule rule = new FailsafeService.Rule();
        rule.prefix = "http";
        rule.breakerEnabled = false;
        Operation postOp = Operation
                .createPost(URI.create(xenonUri + "/core/failsafe"));
        postOp.setBody(rule);
        sender.sendAndWait(postOp);

        testXenonWireMock();
    }

    @Test
    public void canSendWithoutRetry() throws IOException {
        FailsafeService.Rule rule = new FailsafeService.Rule();
        rule.prefix = "http";
        rule.retryMaxCount = 0;
        Operation postOp = Operation
                .createPost(URI.create(xenonUri + "/core/failsafe"));
        postOp.setBody(rule);
        sender.sendAndWait(postOp);

        testXenonWireMock();
    }

    @Test
    public void canSendWithoutBackoff() throws IOException {
        FailsafeService.Rule rule = new FailsafeService.Rule();
        rule.prefix = "http";
        rule.retryMaxCount = 0;
        Operation postOp = Operation.createPost(URI.create(xenonUri + "/core/failsafe"));
        postOp.setBody(rule);
        sender.sendAndWait(postOp);

        testXenonWireMock();
    }

    @Test
    public void canMatchRule() throws IOException {
        FailsafeService.Rule rule = new FailsafeService.Rule();

        rule.prefix = "http";
        rule.retryMaxCount = 0;
        sender.sendAndWait(Operation
                .createPost(URI.create(xenonUri + "/core/failsafe"))
                .setBody(rule));

        rule.prefix = "http://";
        rule.retryMaxCount = 0;
        sender.sendAndWait(Operation
                .createPost(URI.create(xenonUri + "/core/failsafe"))
                .setBody(rule));


        rule.prefix = "http://localhost";
        rule.retryMaxCount = 0;
        sender.sendAndWait(Operation
                .createPost(URI.create(xenonUri + "/core/failsafe"))
                .setBody(rule));

        testXenonWireMock();
    }
}
