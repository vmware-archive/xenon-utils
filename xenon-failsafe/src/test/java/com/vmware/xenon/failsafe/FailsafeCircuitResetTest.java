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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

public class FailsafeCircuitResetTest  extends FailsafeTest{

    @Override
    public ServiceHost startHost(String[] stringArgs, ServiceHost.Arguments args) throws Throwable {
        FailsafeServiceHost.clearRules();
        return FailsafeServiceHost.startHost(stringArgs, args).startService(new FailsafeTestPingService());
    }

    @Test
    public void canResetBreaker() throws IOException {

        FailsafeService.Rule rule = new FailsafeService.Rule();
        rule.prefix = mockUri + "/testreset";
        rule.breakerEnabled = true;
        rule.retryMaxCount =2;
        rule.retryBackoffMaxMs = 300;
        rule.breakerFailureThreshold=2;

        rule = sender.sendPostAndWait(URI.create(xenonUri + "/core/failsafe"),rule, FailsafeService.Rule.class);

        String resource = "/testreset/failsafe/circuitbreaker1";
        mockGetFail(resource);
        //retries retryMaxCount times which is also breakerFailureThreshold.
        //opens circuit
        xenonGetRequest(resource);

        resource = "/testreset/failsafe/circuitbreaker2";
        String resultBody = "testreset failsafe";
        mockGet(resource, resultBody);

        //closes circuit
        rule.circuitCloseTimestamp = Utils.getNowMicrosUtc();
        Operation resetOp = Operation
                .createPut(URI.create(xenonUri + rule.documentSelfLink))
                .setBody(rule);
        sender.sendAndWait(resetOp);

        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (Exception e){}

        //checks if circuit is closed and request is fulfilled
        assertTrue(xenonGetRequest(resource).getBodyRaw().equals(resultBody));
    }
}
