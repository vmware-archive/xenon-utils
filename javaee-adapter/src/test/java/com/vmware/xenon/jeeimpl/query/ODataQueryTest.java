/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.jeeimpl.query;

import static org.junit.Assert.assertEquals;

import static com.vmware.xenon.jee.util.ServiceConsumerUtil.newLocalStatefulSvcContract;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.consumer.StatefulServiceContract;
import com.vmware.xenon.jee.query.XenonQueryService;
import com.vmware.xenon.services.common.ExampleService;

public class ODataQueryTest extends BasicReusableHostTestCase {

    static StatefulServiceContract<ExampleService.ExampleServiceState> testSvc;
    private XenonQueryService xenonQueryService;

    @Test
    public void execute() throws Exception {
        // test data
        ExampleService.ExampleServiceState state = new ExampleService.ExampleServiceState();
        state.name = "ExampleName";
        state.required = "Some mandatory value";
        for (int i = 0; i < 10; i++) {
            testSvc.post(state).get();
        }

        ExampleService.ExampleServiceState[] states = this.xenonQueryService
                .typedODataQuery("name eq ExampleName", ExampleService.ExampleServiceState[].class)
                .join();

        assertEquals(10, states.length);

        Stream.of(states)
                .forEach(s -> assertEquals("ExampleName", s.name));

        states = this.xenonQueryService
                .typedODataQuery("name eq randomName2", ExampleService.ExampleServiceState[].class)
                .join();

        assertEquals(0, states.length);

        states = this.xenonQueryService
                .typedODataQuery("name eq ExampleName", 5,
                        ExampleService.ExampleServiceState[].class)
                .join();

        assertEquals(5, states.length);

    }

    @Before
    public void initializeHost() throws Throwable {
        this.host.startFactory(new ExampleService());
        CountDownLatch latch = new CountDownLatch(1);
        this.host.registerForServiceAvailability(((completedOp, failure) -> latch.countDown()),
                ExampleService.FACTORY_LINK);
        latch.await(500, TimeUnit.MILLISECONDS);
        testSvc = newLocalStatefulSvcContract(this.host, ExampleService.FACTORY_LINK,
                ExampleService.ExampleServiceState.class);
        this.xenonQueryService = JaxRsServiceConsumer.newProxy(XenonQueryService.class, this.host);
    }

}
