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

import static java.time.temporal.ChronoUnit.MILLIS;
import static junit.framework.TestCase.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.test.TestContext;

/**
 * Created by toddc on 9/28/17.
 */
public class FailsafeServiceHostTest extends FailsafeTest {

    @Override
    public ServiceHost startHost(String[] stringArgs, ServiceHost.Arguments args) throws Throwable {
        ServiceHost h = FailsafeServiceHost.startHost(stringArgs, args);
        h.startService(new FailsafeTestPingService());
        return h;
    }

    @Test
    public void canSendOperation() {
        AtomicReference<Operation> opRef = new AtomicReference<>();
        TestContext waitContext = new TestContext(1, Duration.of(25000, MILLIS));

        Operation getOp = Operation.createGet(this.clientHost, "core/management/stats").forceRemote();
        getOp.setCompletion((op,err) -> {
            opRef.set(op);
            waitContext.complete();
        });
        sender.sendRequest(getOp);
        waitContext.await();

        assertTrue(opRef.get().getContentLength() > 0);
    }
}
