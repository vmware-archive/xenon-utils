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

import static junit.framework.TestCase.assertTrue;

import java.io.IOException;
import java.net.URI;

import org.junit.Test;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

public class FailsafeRulesResetServiceTest extends FailsafeTest {

    @Override
    public ServiceHost startHost(String[] stringArgs, ServiceHost.Arguments args) throws Throwable {
        return FailsafeServiceHost.startHost(stringArgs, args).startService(new FailsafeTestPingService());
    }

    @Test
    public void canResetRules() throws IOException {

        //loading initial ruleset from test/resources/failsafe.json
        Operation resetruleOp = Operation
                .createPost(URI.create(xenonUri + FailsafeRulesResetService.SELF_LINK));
        sender.sendAndWait(resetruleOp);

        FailsafeService.Rule rule = sender.sendGetAndWait(URI.create(xenonUri + "/core/failsafe/default-1"),
                FailsafeService.Rule.class);

        //changing the settings of the rule and updating its state
        int initialRetryCount = rule.retryMaxCount;
        int updatedRetryCount = initialRetryCount+1;

        rule.retryMaxCount = updatedRetryCount;
        Operation updateOp = Operation
                .createPut(URI.create(xenonUri + rule.documentSelfLink))
                .setBody(rule);
        sender.sendAndWait(updateOp);

        //validating if state changed.
        FailsafeService.Rule updatedRule = sender.sendGetAndWait(URI.create(xenonUri + "/core/failsafe/default-1"),
                FailsafeService.Rule.class);
        assertTrue(updatedRetryCount==updatedRule.retryMaxCount);

        //reset the rule to initial settings
        Operation resetruleOp2 = Operation
                .createPost(URI.create(xenonUri + FailsafeRulesResetService.SELF_LINK));
        sender.sendAndWait(resetruleOp2);

        //validating if the reset worked.
        updatedRule = sender.sendGetAndWait(URI.create(xenonUri + "/core/failsafe/default-1"),
                FailsafeService.Rule.class);
        assertTrue(initialRetryCount==updatedRule.retryMaxCount);
    }
}
