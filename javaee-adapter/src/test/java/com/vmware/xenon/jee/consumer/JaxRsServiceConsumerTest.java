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

package com.vmware.xenon.jee.consumer;

import static java.util.Arrays.asList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer.ClientBuilder;
import com.vmware.xenon.jee.exception.ServiceException;

public class JaxRsServiceConsumerTest {

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule();

    private static SampleServiceContract clientService;
    private static AsyncSampleServiceContract asyncClientService;

    @BeforeClass
    public static void initJaxRsClientInvoker() {
        ClientBuilder<SampleServiceContract> newBuilder = JaxRsServiceConsumer.newBuilder();
        clientService = newBuilder
                .withBaseUri("http://localhost:" + wireMockRule.port())
                .withResourceInterface(SampleServiceContract.class)
                .build();
        ClientBuilder<AsyncSampleServiceContract> newAsyncBuilder = JaxRsServiceConsumer.newBuilder();
        asyncClientService = newAsyncBuilder
                .withBaseUri("http://localhost:" + wireMockRule.port())
                .withResourceInterface(AsyncSampleServiceContract.class)
                .build();
        WireMock.reset();
    }

    String successResp() {
        Map<String, String> map = new HashMap<>();
        map.put("result", "success");
        return new Gson().toJson(map);
    }

    @Test(expected = Exception.class)
    public void testAsyncGetActionWithReturnTypeException() {
        stubFor(get(urlEqualTo("/vrbc/xenon/util/test/get/pathValue1?query=queryValue1"))
                .willReturn(aResponse()
                        .withBody("string body instead of map")));

        asyncClientService.getAction("queryValue1", "pathValue1").join();
        fail("Shouldn't reach here");
    }

    @Test(expected = ServiceException.class)
    public void testAyncGetActionWithXenonErrorResponse() throws Throwable {
        stubFor(get(urlEqualTo("/vrbc/xenon/util/test/get/pathValue1?query=queryValue1"))
                .willReturn(aResponse()
                        .withBody(xenonServiceErrorResp())
                        .withStatus(500)));

        try {
            asyncClientService.getAction("queryValue1", "pathValue1").join();
            fail("Shouldn't reach here");
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testDeleteAction() throws Exception {
        stubFor(delete(urlEqualTo("/vrbc/xenon/util/test/delete/delete_path_param_1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[ \"success\" ] ")));

        List<String> action = clientService.deleteAction("delete_path_param_1");
        assertNotNull(action);
        assertEquals("success", action.get(0));

    }

    @Test
    public void testGetAction() {
        stubFor(get(urlEqualTo("/vrbc/xenon/util/test/get/pathValue1?query=queryValue1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(successResp())));

        Map<String, String> action = clientService.getAction("queryValue1", "pathValue1");
        assertNotNull(action);
        assertEquals("success", action.get("result"));

        action = asyncClientService.getAction("queryValue1", "pathValue1").join();
        assertNotNull(action);
        assertEquals("success", action.get("result"));
    }

    @Test
    public void testGetActionWithGenericReturn() {
        Map<String, Object> outer = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("code", 20);
        inner.put("message", "success");
        outer.put("result", inner);
        String successResp = new Gson().toJson(outer);

        stubFor(get(urlEqualTo("/vrbc/xenon/util/test/get/genericReturn"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withHeader("custom-header", "Custom-Value")
                        .withBody(successResp)));

        Map<String, SampleServiceContract.SuccessResponse> genericReturn = clientService
                .getActionWithGenericReturn();
        assertNotNull(genericReturn);
        assertEquals(1, genericReturn.size());
        assertTrue(genericReturn.containsKey("result"));
        assertEquals("success", genericReturn.get("result").getMessage());
        assertEquals(20, genericReturn.get("result").getCode());

        genericReturn = asyncClientService.getActionWithGenericReturn().join();
        assertNotNull(genericReturn);
        assertEquals(1, genericReturn.size());
        assertTrue(genericReturn.containsKey("result"));
        assertEquals("success", genericReturn.get("result").getMessage());
        assertEquals(20, genericReturn.get("result").getCode());
    }

    @Test
    public void testGetActionWithNoBody() {
        stubFor(get(urlEqualTo("/vrbc/xenon/util/test/get/pathValue1?query=queryValue1"))
                .willReturn(aResponse()));

        Map<String, String> action = clientService.getAction("queryValue1", "pathValue1");
        assertNotNull(action);
        assertTrue(action.isEmpty());

        action = asyncClientService.getAction("queryValue1", "pathValue1").join();
        assertNotNull(action);
        assertTrue(action.isEmpty());
    }

    @Test(expected = Exception.class)
    public void testGetActionWithReturnTypeException() {
        stubFor(get(urlEqualTo("/vrbc/xenon/util/test/get/pathValue1?query=queryValue1"))
                .willReturn(aResponse()
                        .withBody("string body instead of map")));

        clientService.getAction("queryValue1", "pathValue1");
        fail("Shouldn't reach here");
    }

    @Test(expected = CompletionException.class)
    public void testGetActionWithServiceException() {
        stubFor(get(urlEqualTo("/vrbc/xenon/util/test/get/pathValue1?query=queryValue1"))
                .willReturn(aResponse()
                        .withStatus(404)));

        clientService.getAction("queryValue1", "pathValue1");
        fail("Shouldn't reach here");
    }

    @Test(expected = CompletionException.class)
    public void testGetActionWithXenonErrorResponse() {
        stubFor(get(urlEqualTo("/vrbc/xenon/util/test/get/pathValue1?query=queryValue1"))
                .willReturn(aResponse()
                        .withBody(xenonServiceErrorResp())
                        .withStatus(500)));

        clientService.getAction("queryValue1", "pathValue1");
        fail("Shouldn't reach here");
    }

    @Test
    public void testPatchAction() throws Exception {
        List<Integer> contents = asList(1, 2, 3);
        stubFor(patch(urlEqualTo("/vrbc/xenon/util/test/patch"))
                .withRequestBody(equalToJson(new Gson().toJson(contents)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(successResp())));

        Map<String, String> action = clientService.patchAction(new ArrayList<>(contents));
        assertNotNull(action);
        assertEquals("success", action.get("result"));

        action = asyncClientService.patchAction(new ArrayList<>(contents)).join();
        assertNotNull(action);
        assertEquals("success", action.get("result"));
    }

    @Test
    public void testPatchActionWithVoidReturn() {
        List<Integer> contents = asList(1, 2, 3);
        stubFor(patch(urlEqualTo("/vrbc/xenon/util/test/patch/voidReturn"))
                .withRequestBody(equalToJson(new Gson().toJson(contents)))
                .willReturn(aResponse()
                        .withBody("[ \"success\" ] ")));

        clientService.patchActionWithGenericReturn(new ArrayList<>(contents));

        assertTrue("Was able to invoke a void method properly", true);
    }

    @Test
    public void testPostActionAsync() {
        List<String> contents = asList("POST_BODY_1", "POST_BODY_2");
        stubFor(post(urlEqualTo("/vrbc/xenon/util/test/post"))
                .withRequestBody(equalToJson(new Gson().toJson(contents)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(successResp())));

        asyncClientService.postAction(new ArrayList<>(contents)).join();
        assertTrue("Void method works fine", true);
        verify(postRequestedFor(urlEqualTo("/vrbc/xenon/util/test/post"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test(expected = CompletionException.class)
    public void testPostActionAsyncFailure() {
        List<String> contents = asList("POST_BODY_1", "POST_BODY_2");
        stubFor(post(urlEqualTo("/vrbc/xenon/util/test/post"))
                .withRequestBody(equalToJson(new Gson().toJson(contents)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(successResp())));

        asyncClientService.postAction(new ArrayList<>()).join();
        fail("Shouldn't reach here");
    }

    @Test(expected = Exception.class)
    public void testPostActionFailure() {
        List<String> contents = asList("POST_BODY_1", "POST_BODY_2");
        stubFor(post(urlEqualTo("/vrbc/xenon/util/test/post"))
                .withRequestBody(equalToJson(new Gson().toJson(contents)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(successResp())));

        Map<String, String> action = clientService.postAction(new ArrayList<>());
        assertNotNull(action);
        assertEquals("success", action.get("result"));
    }

    @Test
    public void testPostActionWithAuthInfo() {
        List<Integer> contents = asList(1, 2, 3);
        stubFor(post(urlEqualTo("/vrbc/xenon/util/test/post/auth"))
                .withRequestBody(equalToJson(new Gson().toJson(contents)))
                .withCookie("cookie", equalTo("auth-cookie-value"))
                .withHeader("header", equalTo("auth-header-value"))
                .willReturn(aResponse()
                        .withBody("[ \"success\" ] ")));

        List<String> action = clientService.postActionWithAuthInfo("auth-header-value",
                "auth-cookie-value", new ArrayList<>(contents));
        assertNotNull(action);
        assertEquals("success", action.get(0));

        action = asyncClientService.postActionWithAuthInfo("auth-header-value",
                "auth-cookie-value", new ArrayList<>(contents)).join();
        assertNotNull(action);
        assertEquals("success", action.get(0));
    }

    @Test
    public void testPostActionWithMapReturn() {
        List<String> contents = asList("POST_BODY_1", "POST_BODY_2");
        stubFor(post(urlEqualTo("/vrbc/xenon/util/test/post"))
                .withRequestBody(equalToJson(new Gson().toJson(contents)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(successResp())));

        Map<String, String> action = clientService.postAction(new ArrayList<>(contents));
        assertNotNull(action);
        assertEquals("success", action.get("result"));
    }

    String xenonServiceErrorResp() {
        ServiceErrorResponse errorRsp = ServiceErrorResponse
                .create(new IllegalArgumentException("bad request"), 404);
        return new Gson().toJson(errorRsp);
    }

}
