/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.fail;

import static com.vmware.xenon.jee.util.ServiceConsumerUtil.newLocalStatefulSvcContract;

import java.lang.reflect.Method;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.consumer.StatefulServiceContract;
import com.vmware.xenon.jee.query.CompletablePagedStream;
import com.vmware.xenon.services.common.ExampleService;

@RunWith(MockitoJUnitRunner.class)
public class ODataPagedQueryAnnotationHandlerImplTest extends BasicReusableHostTestCase {

    private static ImmutableList<Method> allMethods;

    @BeforeClass
    public static void init() {
        allMethods = ImmutableList.copyOf(ExampleODataXenonQueryService.class.getMethods());
    }

    private ODataPagedQueryAnnotationHandlerImpl testClass;
    private StatefulServiceContract<ExampleService.ExampleServiceState> exampleSvc;

    private ExampleODataXenonQueryService querySvc;

    @Test
    public void canHandle() throws Exception {
        Assert.assertTrue(this.testClass.canHandle(findMethodWithName("findByNameWithLimit")));
        Assert.assertTrue(
                this.testClass.canHandle(findMethodWithName("findByNameWithLimitOrderBy")));
        Assert.assertTrue(
                this.testClass
                        .canHandle(findMethodWithName("findByNameWithLimitOrderByAndLongType")));

        Assert.assertFalse(
                this.testClass.canHandle(findMethodWithName("findByNameReturnAsCollection")));
        Assert.assertFalse(this.testClass.canHandle(findMethodWithName("notAQueryMethod")));
    }

    private Method findMethodWithName(String name) {
        return allMethods.stream().filter(each -> each.getName().equals(name)).findFirst().get();
    }

    @Test
    public void handle() throws Exception {
        ExampleService.ExampleServiceState state = new ExampleService.ExampleServiceState();
        String name = "ExampleName";
        String required = "Some mandatory value";
        state.name = name;
        state.sortedCounter = -1L;
        state.required = required;
        this.exampleSvc.post(state).join();

        for (int i = 0; i < 10; i++) {
            state.required = required + i;
            state.sortedCounter = (long) i;
            this.exampleSvc.post(state).join();
        }

        CompletablePagedStream<ExampleService.ExampleServiceState> join = this.querySvc
                .findByNameWithLimit(name);
        MutableInt count = new MutableInt(0);
        join.forEach(each -> count.increment()).join();
        assertEquals(11, count.intValue());

        count.setValue(0);
        this.querySvc.findByNameWithLimitOrderByAndLongType(name).forEach(each -> count.increment())
                .join();
        assertEquals(11, count.intValue());

        count.setValue(0);
        this.querySvc.findByNameWithDynamicLimit(name, 100).forEach(each -> count.increment())
                .join();
        assertEquals(11, count.intValue());

        try {
            this.querySvc.findByNameWithLimitOrderBy(name).forEach(each -> count.increment())
                    .join();
            fail("In correct query. Should have failed");
        } catch (Exception e) {
            // no-op
        }
    }

    @Before
    public void setUp() throws Exception {
        this.testClass = new ODataPagedQueryAnnotationHandlerImpl();
        this.host.startFactory(new ExampleService());
        this.host.waitForServiceAvailable(ExampleService.FACTORY_LINK);
        this.exampleSvc = newLocalStatefulSvcContract(this.host, ExampleService.FACTORY_LINK,
                ExampleService.ExampleServiceState.class);
        this.querySvc = JaxRsServiceConsumer.newProxy(ExampleODataXenonQueryService.class, this.host);
    }

}
