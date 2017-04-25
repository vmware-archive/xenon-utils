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

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.xenon.jee.util.ServiceConsumerUtil.newLocalStatefulSvcContract;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.jee.annotations.ODataQuery;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.consumer.StatefulServiceContract;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.ExampleService.ExampleServiceState;

@RunWith(MockitoJUnitRunner.class)
public class ODataQueryAnnotationHandlerImplTest extends BasicReusableHostTestCase {

    private static List<Method> allMethods;

    @BeforeClass
    public static void init() {
        allMethods = asList(ExampleODataXenonQueryService.class.getMethods());
    }

    private ODataQueryAnnotationHandlerImpl testClass;
    private StatefulServiceContract<ExampleServiceState> testSvc;

    private ExampleODataXenonQueryService oDataQueryService;

    @Test
    public void canHandle() throws Exception {
        Assert.assertTrue(this.testClass.canHandle(findMethodWithName("findByNameReturnAsArray")));
        Assert.assertTrue(this.testClass.canHandle(findMethodWithName("findFirstByName")));
        Assert.assertTrue(
                this.testClass.canHandle(findMethodWithName("findByNameAndOrderDescByCounter")));
        Assert.assertTrue(this.testClass.canHandle(findMethodWithName("findByNameAndRequired")));
        Assert.assertTrue(this.testClass.canHandle(findMethodWithName("findByNameReturnAsList")));
        Assert.assertTrue(this.testClass.canHandle(findMethodWithName("findByNameReturnAsSet")));
        Assert.assertTrue(
                this.testClass.canHandle(findMethodWithName("findByNameReturnAsCollection")));
        Assert.assertFalse(this.testClass.canHandle(findMethodWithName("notAQueryMethod")));
        Assert.assertFalse(this.testClass.canHandle(findMethodWithName("findByNameWithLimit")));
        Assert.assertFalse(
                this.testClass.canHandle(findMethodWithName("findByNameWithDynamicLimit")));
        Assert.assertFalse(
                this.testClass.canHandle(findMethodWithName("findByNameWithLimitOrderBy")));
        Assert.assertFalse(
                this.testClass
                        .canHandle(findMethodWithName("findByNameWithLimitOrderByAndLongType")));
    }

    @Test
    public void filterCriteria() throws Exception {
        Method method = findMethodWithName("findFirstByName");
        ODataQuery annotation = method.getDeclaredAnnotation(ODataQuery.class);
        String filterCriteria = this.testClass.getFilterCriteria(method, annotation,
                new Object[] { "Ram" });
        System.out.println(filterCriteria);
        assertTrue(filterCriteria.contains(
                "documentKind eq " + Utils.toDocumentKind(annotation.documentKind()) + " "));
        assertTrue(filterCriteria.contains("name eq 'Ram'"));

    }

    private Method findMethodWithName(String name) {
        return allMethods.stream().filter(each -> each.getName().equals(name)).findFirst().get();
    }

    @Test
    public void handle() throws Exception {
        // test data
        ExampleServiceState state = new ExampleServiceState();
        String name = "ExampleName";
        String required = "Some mandatory value";
        state.name = name;
        state.counter = -1L;
        state.required = required;
        this.testSvc.post(state).join();
        for (int i = 0; i < 10; i++) {
            state.required = required + i;
            state.counter = (long) i;
            this.testSvc.post(state).get();
        }
        assertEquals(11, this.oDataQueryService.findByNameReturnAsCollection(name).join().size());
        assertEquals(11, this.oDataQueryService.findByNameReturnAsList(name).join().size());
        assertEquals(11, this.oDataQueryService.findByNameReturnAsSet(name).join().size());
        assertEquals(11, this.oDataQueryService.findByNameReturnAsArray(name).join().length);
        ExampleServiceState join = this.oDataQueryService.findFirstByName(name).join();
        assertNotNull(join);
        assertEquals(name, join.name);

        join = this.oDataQueryService.findByNameAndRequired(name, required).join();
        assertNotNull(join);
        assertEquals(name, join.name);
        assertEquals(required, join.required);

        List<ExampleServiceState> states = this.oDataQueryService
                .findByNameAndOrderDescByCounter(name)
                .join();
        assertEquals(11, states.size());
        //verify whether order is preserved
        ExampleServiceState firstRecord = states.get(0);
        System.out.println(firstRecord.required);
        ExampleServiceState lastRecord = states.get(10);
        System.out.println(lastRecord.required);
        assertEquals(Long.valueOf(9), firstRecord.counter);
        assertEquals(Long.valueOf(-1), lastRecord.counter);
        //                Thread.sleep(TimeUnit.MINUTES.toMillis(10));
    }

    @Before
    public void setUp() throws Exception {
        this.testClass = new ODataQueryAnnotationHandlerImpl();

        this.host.startFactory(new ExampleService());
        CountDownLatch latch = new CountDownLatch(1);
        this.host.registerForServiceAvailability(((completedOp, failure) -> latch.countDown()),
                ExampleService.FACTORY_LINK);
        latch.await(500, TimeUnit.MILLISECONDS);
        this.testSvc = newLocalStatefulSvcContract(this.host, ExampleService.FACTORY_LINK,
                ExampleServiceState.class);
        this.oDataQueryService = JaxRsServiceConsumer.newProxy(ExampleODataXenonQueryService.class, this.host);
        System.out.println(this.host.getPort());
    }

    @Test
    public void toArrayReturnType() throws Exception {
        Method method = findMethodWithName("findByNameReturnAsArray");
        ODataQuery annotation = method.getDeclaredAnnotation(ODataQuery.class);
        List<ExampleServiceState> results = asList(new ExampleServiceState());
        ExampleServiceState[] expected = (ExampleServiceState[]) this.testClass.toReturnType(
                annotation,
                method, results);
        assertEquals(1, expected.length);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void toCollectionReturnType() throws Exception {
        Method method = findMethodWithName("findByNameReturnAsCollection");
        ODataQuery annotation = method.getDeclaredAnnotation(ODataQuery.class);
        List<ExampleServiceState> results = asList(new ExampleServiceState());
        Collection<ExampleServiceState> expected = (Collection<ExampleServiceState>) this.testClass
                .toReturnType(annotation, method, results);
        assertEquals(1, expected.size());
    }

    @Test
    public void toDocumentKindReturnType() throws Exception {
        Method method = findMethodWithName("findFirstByName");
        ODataQuery annotation = method.getDeclaredAnnotation(ODataQuery.class);
        List<ExampleServiceState> results = asList(new ExampleServiceState());
        ExampleServiceState expected = (ExampleServiceState) this.testClass.toReturnType(annotation,
                method, results);
        assertNotNull(expected);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void toListReturnType() throws Exception {
        Method method = findMethodWithName("findByNameReturnAsList");
        ODataQuery annotation = method.getDeclaredAnnotation(ODataQuery.class);
        List<ExampleServiceState> results = asList(new ExampleServiceState());
        List<ExampleServiceState> expected = (List<ExampleServiceState>) this.testClass
                .toReturnType(annotation, method, results);
        assertEquals(1, expected.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void toSetReturnType() throws Exception {
        Method method = findMethodWithName("findByNameReturnAsSet");
        ODataQuery annotation = method.getDeclaredAnnotation(ODataQuery.class);
        List<ExampleServiceState> results = asList(new ExampleServiceState());
        Set<ExampleServiceState> expected = (Set<ExampleServiceState>) this.testClass
                .toReturnType(annotation, method, results);
        assertEquals(1, expected.size());
    }

}
