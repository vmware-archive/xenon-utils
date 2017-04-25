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

package com.vmware.xenon.jee.producer;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import static com.vmware.xenon.common.Service.ServiceOption.URI_NAMESPACE_OWNER;
import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.common.UriUtils.buildUriPath;
import static com.vmware.xenon.common.Utils.toJson;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

@RunWith(MockitoJUnitRunner.class)
public class RequestRouterBuilderTest extends BasicReusableHostTestCase {

    @Mock
    private Operation op;

    @Mock
    private Service service;

    private MockedStatelessService statelessService = new MockedStatelessService();

    @Test(expected = IllegalArgumentException.class)
    public void buildJaxRsRouterShouldFailWhenNamespaceOwnerOperationsIsNotSet() throws Exception {
        when(this.service.hasOption(URI_NAMESPACE_OWNER)).thenReturn(false);
        RequestRouterBuilder.parseJaxRsAnnotations(this.service);
    }

    public void startService() throws Throwable {
        this.statelessService.log = LoggerFactory.getLogger(MockedStatelessService.class);
        this.host.startService(this.statelessService);
        this.host.waitForServiceAvailable(MockedStatelessService.SELF_LINK);
    }

    public void stopService() throws Throwable {
        this.host.stopService(this.statelessService);
    }

    @Test
    public void testActualService() throws Throwable {
        startService();
        testGetWithQueryAndPathAndReturn();
        testSimpleGet();
        testSimplePatch();
        testGetWithQueryAndPath();
        testPostWithQueryAndPath();
        testPutWithQueryAndPathAndReturn();
        testPostWithModelValidationOnBody();
        stopService();
    }

    @Test(expected = Exception.class)
    public void testFailureOfPostWithModelValidationOnBody() throws Throwable {
        startService();
        this.host.testStart(1);
        // try creating instance
        URI uri = buildUri(this.host,
                MockedStatelessService.SELF_LINK + "/validation/path/value_of_path_param",
                "queryParam=value_of_query_param");
        //direct asList fails in Kyro serialization
        EmployeePojo payload = new EmployeePojo();
        payload.setAge(15);
        Operation op = Operation
                .createPost(uri)
                .setBody(payload)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        return;
                    }
                    try {
                        fail("Shouldn't reach here. Should have failed with ConstraintViolation");
                    } finally {
                        this.host.completeIteration();
                    }
                });
        this.host.send(op);
        this.host.testWait();
        stopService();
    }

    public void testGetWithQueryAndPath() throws Throwable {
        this.host.testStart(1);
        // try creating instance
        URI uri = buildUri(this.host,
                MockedStatelessService.SELF_LINK + "/path/value_of_path_param/query",
                "queryParam=value_of_query_param");
        Operation get = Operation
                .createGet(uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        fail("Shouldn't reach here");
                        return;
                    }
                    try {
                        Map<String,String> body = getMap(o);
                        assertEquals("success", body.get("result"));
                        assertEquals("value_of_path_param", body.get("pathParam"));
                        assertEquals("value_of_query_param", body.get("queryParam"));
                    } finally {
                        this.host.completeIteration();
                    }
                });
        this.host.send(get);
        this.host.testWait();
    }

    public void testGetWithQueryAndPathAndReturn() throws Throwable {
        this.host.testStart(1);
        // try creating instance
        URI uri = buildUri(this.host,
                MockedStatelessService.SELF_LINK + "/complete/path/value_of_path_param",
                "queryParam=value_of_query_param");
        Operation op = Operation
                .createGet(uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        fail("Shouldn't reach here");
                        return;
                    }
                    try {
                        Map<String, String> body = getMap(o);
                        assertEquals("success", body.get("result"));
                        assertEquals("value_of_path_param", body.get("pathParam"));
                        assertEquals("value_of_query_param", body.get("queryParam"));
                    } finally {
                        this.host.completeIteration();
                    }
                });
        this.host.send(op);
        this.host.testWait();
    }

    @Test
    public void testNewURIMatcherWithOutPathParam() throws Exception {
        String path = buildUriPath("/vrbc/parent/", "/child/");
        Predicate<Operation> matcher = RequestRouterBuilder.newUriMatcher(path);
        when(this.op.getUri())
                .thenReturn(new URI("/vrbc/parent/child"))
                .thenReturn(new URI("/vrbc/parent/childs"));
        assertTrue(matcher.test(this.op));
        assertFalse(matcher.test(this.op));

        path = buildUriPath("/vrbc/parent/1/", "/child/");
        matcher = RequestRouterBuilder.newUriMatcher(path);
        when(this.op.getUri()).thenReturn(new URI("/vrbc/parent/child"));
        assertFalse(matcher.test(this.op));
    }

    @Test
    public void testNewURIMatcherWithPathParam() throws Exception {
        String path = buildUriPath("/vrbc/parent/", "/child/{pathParam}");
        Predicate<Operation> matcher = RequestRouterBuilder.newUriMatcher(path);
        when(this.op.getUri())
                .thenReturn(new URI("/vrbc/parent/child/pathParamValue"))
                .thenReturn(new URI("/vrbc/parent/child/pathParamValue/2"))
                .thenReturn(new URI("/vrbc/parent/1/child/pathParamValue"));
        assertTrue(matcher.test(this.op));
        assertFalse(matcher.test(this.op));
        assertFalse(matcher.test(this.op));

        path = buildUriPath("/vrbc/parent/", "/{firstPathParam}/child/{secondPathParam}");
        matcher = RequestRouterBuilder.newUriMatcher(path);
        when(this.op.getUri())
                .thenReturn(new URI("/vrbc/parent/param1/child/param2"))
                .thenReturn(new URI("/vrbc/parent/child/child/child"))
                .thenReturn(new URI("/vrbc/parent/child/child/child/child"));
        assertTrue(matcher.test(this.op));
        assertTrue(matcher.test(this.op));
        assertFalse(matcher.test(this.op));
    }

    public void testPostWithModelValidationOnBody() throws Throwable {
        this.host.testStart(1);
        // try creating instance
        URI uri = buildUri(this.host,
                MockedStatelessService.SELF_LINK + "/validation/path/value_of_path_param",
                "queryParam=value_of_query_param");
        //direct asList fails in Kyro serialization
        EmployeePojo payload = new EmployeePojo();
        payload.setAge(25);
        payload.setName("Ram");
        Operation op = Operation
                .createPost(uri)
                .setBody(payload)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        fail("Shouldn't reach here");
                        return;
                    }
                    try {
                        Map<String,String> body = getMap(o);
                        assertEquals("success", body.get("result"));
                        assertEquals("value_of_path_param", body.get("pathParam"));
                        assertEquals("value_of_query_param", body.get("queryParam"));
                        assertEquals(toJson(payload), body.get("body"));
                    } finally {
                        this.host.completeIteration();
                    }
                });
        this.host.send(op);
        this.host.testWait();
    }

    public void testPostWithQueryAndPath() throws Throwable {
        this.host.testStart(1);
        // try creating instance
        URI uri = buildUri(this.host,
                MockedStatelessService.SELF_LINK + "/path/value_of_path_param/query",
                "queryParam=value_of_query_param");
        //direct asList fails in Kyro serialization
        List<String> payload = new ArrayList<>(asList("One", "Two", "Three"));
        Operation op = Operation
                .createPost(uri)
                .setBody(payload)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        fail("Shouldn't reach here");
                        return;
                    }
                    try {
                        Map<String,String> body = getMap(o);
                        assertEquals("success", body.get("result"));
                        assertEquals("value_of_path_param", body.get("pathParam"));
                        assertEquals("value_of_query_param", body.get("queryParam"));
                        assertEquals(toJson(payload), body.get("body"));
                    } finally {
                        this.host.completeIteration();
                    }
                });
        this.host.send(op);
        this.host.testWait();
    }

    @SuppressWarnings("unchecked")
    private Map<String,String> getMap(Operation o) {
        return o.getBody(Map.class);
    }

    public void testPutWithQueryAndPathAndReturn() throws Throwable {
        this.host.testStart(1);
        // try creating instance
        URI uri = buildUri(this.host,
                MockedStatelessService.SELF_LINK + "/complete/path/value_of_path_param",
                "queryParam=value_of_query_param");
        //direct asList fails in Kyro serialization
        List<String> payload = new ArrayList<>(asList("One", "Two", "Three"));
        Operation op = Operation
                .createPut(uri)
                .setBody(payload)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        fail("Shouldn't reach here");
                        return;
                    }
                    try {
                        Map<String,String> body = getMap(o);
                        assertEquals("success", body.get("result"));
                        assertEquals("value_of_path_param", body.get("pathParam"));
                        assertEquals("value_of_query_param", body.get("queryParam"));
                        assertEquals(toJson(payload), body.get("body"));
                    } finally {
                        this.host.completeIteration();
                    }
                });
        this.host.send(op);
        this.host.testWait();
    }

    public void testSimpleGet() throws Throwable {
        this.host.testStart(1);
        // try creating instance
        URI uri = buildUri(this.host, MockedStatelessService.SELF_LINK + "/simple");
        Operation get = Operation
                .createGet(uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        fail("Shouldn't reach here");
                        return;
                    }
                    try {
                        Map<String,String> body = getMap(o);
                        assertEquals("success", body.get("result"));
                    } finally {
                        this.host.completeIteration();
                    }
                });
        this.host.send(get);
        this.host.testWait();
    }

    public void testSimplePatch() throws Throwable {
        this.host.testStart(1);
        // try creating instance
        URI uri = buildUri(this.host, MockedStatelessService.SELF_LINK + "/simple");
        Operation op = Operation
                .createPatch(uri)
                .setBody("Some payload")
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        fail("Shouldn't reach here");
                        return;
                    }
                    try {
                        Map<String,String> body = getMap(o);
                        assertEquals("success", body.get("result"));
                    } finally {
                        this.host.completeIteration();
                    }
                });
        this.host.send(op);
        this.host.testWait();

    }

}
