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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.jee.annotations.OperationBody;
import com.vmware.xenon.jee.annotations.PATCH;
import com.vmware.xenon.jee.exception.ServiceException;
import com.vmware.xenon.jee.exception.WebErrorResponse;
import com.vmware.xenon.jeeimpl.reflect.MethodInfo;
import com.vmware.xenon.jeeimpl.reflect.MethodInfoBuilder;

@RunWith(MockitoJUnitRunner.class)
public class RoutingOperationHandlerTest {

    interface MockXenonServiceWithJaxRsAnnotations extends Service {

        @DELETE
        @Path("/delete/{path}")
        List<String> testDeleteAction(@PathParam("path") String path,
                @HeaderParam("header") String header,
                @CookieParam("cookie") String cookie);

        @GET
        void testGetAction(@QueryParam("query") String query, @PathParam("path") String path,
                Operation op);

        @PATCH
        @Path("/patch/{path}")
        void testPatchAction(@PathParam("path") String path, @OperationBody List<String> contents,
                Operation op);

        @POST
        @Path("/post/{path}")
        CompletableFuture<List<String>> testPostAction(@PathParam("path") String path,
                @OperationBody List<String> contents);

    }

    @Mock
    private Operation op;

    @Mock
    private MockXenonServiceWithJaxRsAnnotations service;

    @Mock
    private List<String> mockedBody;

    @Test
    public void testOperationHandlerForDeleteWithOutOperation() throws Exception {
        //given
        Method deleteMethod = MockXenonServiceWithJaxRsAnnotations.class.getMethod(
                "testDeleteAction",
                String.class, String.class, String.class);
        String path = "/resource/delete/{path}";

        List<MethodInfo> methodInfos = MethodInfoBuilder
                .generateMethodInfo(new Method[] { deleteMethod }, Collections.emptyMap());

        RoutingOperationHandler testClass = new RoutingOperationHandler(path, methodInfos.get(0),
                this.service);
        testClass.init();
        when(this.op.getUri()).thenReturn(new URI("/resource/delete/PathValue1/PathValue2"));
        when(this.op.getRequestHeader("header")).thenReturn("headerValue");
        when(this.service.testDeleteAction("PathValue1", "headerValue", null))
                .thenReturn(this.mockedBody);
        //test
        testClass.accept(this.op);
        //verify
        verify(this.service).testDeleteAction("PathValue1", "headerValue", null);
        assertTrue(testClass.hasValidReturnType);
        assertFalse(testClass.hasOperationAsAnArgument);
        verify(this.op).setBody(this.mockedBody);
        verify(this.op).complete();
    }

    @Test
    public void testOperationHandlerForGetAndPostParam() throws Exception {
        //given
        Method getMethod = MockXenonServiceWithJaxRsAnnotations.class.getMethod("testGetAction",
                String.class, String.class, Operation.class);

        List<MethodInfo> methodInfos = MethodInfoBuilder
                .generateMethodInfo(new Method[] { getMethod }, Collections.emptyMap());

        String path = "/resource/get/{path}";
        RoutingOperationHandler testClass = new RoutingOperationHandler(path, methodInfos.get(0),
                this.service);
        testClass.init();
        when(this.op.getUri()).thenReturn(new URI("/resource/get/PathValue1?query=QueryValue1"));
        //test
        testClass.accept(this.op);
        //verify
        verify(this.service).testGetAction("QueryValue1", "PathValue1", this.op);
        assertFalse(testClass.hasValidReturnType);
        assertTrue(testClass.hasOperationAsAnArgument);
    }

    @Test
    public void testOperationHandlerForPatchWithBodyParam() throws Exception {
        //given
        Method patchMethod = MockXenonServiceWithJaxRsAnnotations.class.getMethod("testPatchAction",
                String.class, List.class, Operation.class);
        String path = "/resource/patch/{path}";

        List<MethodInfo> methodInfos = MethodInfoBuilder
                .generateMethodInfo(new Method[] { patchMethod }, Collections.emptyMap());
        RoutingOperationHandler testClass = new RoutingOperationHandler(path, methodInfos.get(0),
                this.service);

        testClass.init();
        when(this.op.getUri()).thenReturn(new URI("/resource/patch/PathValue1/PathValue2"));
        when(this.op.getBody(List.class)).thenReturn(this.mockedBody);
        //test
        testClass.accept(this.op);
        //verify
        verify(this.service).testPatchAction("PathValue1", this.mockedBody, this.op);
        assertFalse(testClass.hasValidReturnType);
        assertTrue(testClass.hasOperationAsAnArgument);
        //        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
    }

    @Test
    public void testOperationHandlerForPost() throws Exception {
        //given
        Method postMethod = MockXenonServiceWithJaxRsAnnotations.class.getMethod("testPostAction",
                String.class, List.class);
        String path = "/resource/post/{path}";

        List<MethodInfo> methodInfos = MethodInfoBuilder
                .generateMethodInfo(new Method[] { postMethod }, Collections.emptyMap());

        RoutingOperationHandler testClass = new RoutingOperationHandler(path, methodInfos.get(0),
                this.service);
        testClass.init();
        when(this.op.getUri()).thenReturn(new URI("/resource/post/PathValue1"));
        CompletableFuture<List<String>> successFuture = new CompletableFuture<>();
        when(this.service.testPostAction("PathValue1", this.mockedBody))
                .thenReturn(successFuture);
        when(this.op.getBody(List.class)).thenReturn(this.mockedBody);
        //test
        testClass.accept(this.op);
        List<String> result = asList("1", "2");
        successFuture.complete(result);

        //verify
        verify(this.service).testPostAction("PathValue1", this.mockedBody);
        assertTrue(testClass.hasValidReturnType);
        assertFalse(testClass.hasOperationAsAnArgument);
        verify(this.op).setBody(result);
        verify(this.op).complete();
        //        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
    }

    @Test
    public void testOperationHandlerForPostWithExceptionFlow() throws Exception {
        //given
        Method postMethod = MockXenonServiceWithJaxRsAnnotations.class.getMethod("testPostAction",
                String.class, List.class);
        String path = "/resource/post/{path}";

        List<MethodInfo> methodInfos = MethodInfoBuilder
                .generateMethodInfo(new Method[] { postMethod }, Collections.emptyMap());

        RoutingOperationHandler testClass = new RoutingOperationHandler(path, methodInfos.get(0),
                this.service);
        testClass.init();
        when(this.op.getUri()).thenReturn(new URI("/resource/post/PathValue1"));
        CompletableFuture<List<String>> failureFuture1 = new CompletableFuture<>();
        CompletableFuture<List<String>> failureFuture2 = new CompletableFuture<>();
        when(this.service.testPostAction("PathValue1", this.mockedBody))
                .thenReturn(failureFuture1)
                .thenReturn(failureFuture2);
        when(this.op.getBody(List.class)).thenReturn(this.mockedBody);
        //test
        testClass.accept(this.op);
        failureFuture1.completeExceptionally(new ServiceException(100));

        //verify
        verify(this.service).testPostAction("PathValue1", this.mockedBody);
        assertTrue(testClass.hasValidReturnType);
        assertFalse(testClass.hasOperationAsAnArgument);
        verify(this.op).fail(any(ServiceException.class), any(WebErrorResponse.class));

        //setup
        failureFuture2.completeExceptionally(new CompletionException(new NullPointerException()));
        //test
        testClass.accept(this.op);
        //verify
        verify(this.service, times(2)).testPostAction("PathValue1", this.mockedBody);
        assertTrue(testClass.hasValidReturnType);
        assertFalse(testClass.hasOperationAsAnArgument);
        verify(this.op, times(2)).fail(any(NullPointerException.class),
                any(WebErrorResponse.class));
    }

    @Test
    public void testOperationHandlerForPostWithExceptionFlow2() throws Exception {
        //given
        Method postMethod = MockXenonServiceWithJaxRsAnnotations.class.getMethod("testPostAction",
                String.class, List.class);
        String path = "/resource/post/{path}";

        List<MethodInfo> methodInfos = MethodInfoBuilder
                .generateMethodInfo(new Method[] { postMethod }, Collections.emptyMap());

        RoutingOperationHandler testClass = new RoutingOperationHandler(path, methodInfos.get(0),
                this.service);
        testClass.init();
        when(this.op.getUri()).thenReturn(new URI("/resource/post/PathValue1"));

        when(this.service.testPostAction("PathValue1", this.mockedBody))
                .thenThrow(new NullPointerException());
        when(this.op.getBody(List.class)).thenReturn(this.mockedBody);

        //test
        testClass.accept(this.op);

        //verify
        verify(this.service).testPostAction("PathValue1", this.mockedBody);
        assertTrue(testClass.hasValidReturnType);
        assertFalse(testClass.hasOperationAsAnArgument);
        verify(this.op).fail(any(NullPointerException.class), any(String.class));
    }

}
