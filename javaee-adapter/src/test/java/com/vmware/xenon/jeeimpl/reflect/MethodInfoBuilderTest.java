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

package com.vmware.xenon.jeeimpl.reflect;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.xenon.common.UriUtils.buildUriPath;
import static com.vmware.xenon.jeeimpl.reflect.ParamMetadata.Type.BODY;
import static com.vmware.xenon.jeeimpl.reflect.ParamMetadata.Type.COOKIE;
import static com.vmware.xenon.jeeimpl.reflect.ParamMetadata.Type.HEADER;
import static com.vmware.xenon.jeeimpl.reflect.ParamMetadata.Type.OPERATION;
import static com.vmware.xenon.jeeimpl.reflect.ParamMetadata.Type.PATH;
import static com.vmware.xenon.jeeimpl.reflect.ParamMetadata.Type.PRAGMA;
import static com.vmware.xenon.jeeimpl.reflect.ParamMetadata.Type.QUERY;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.jee.annotations.OperationBody;
import com.vmware.xenon.jee.annotations.PATCH;
import com.vmware.xenon.jee.annotations.PragmaDirective;

@RunWith(MockitoJUnitRunner.class)
public class MethodInfoBuilderTest {

    /**
     * A test class with JaxRs annotations to facilitate testing
     */
    public static class MockWithJaxRsParams<S1, S2> {

        public class SuccessResponse {
            private int code;
            private String message;

            public int getCode() {
                return this.code;
            }

            public String getMessage() {
                return this.message;
            }

            public void setCode(int code) {
                this.code = code;
            }

            public void setMessage(String message) {
                this.message = message;
            }
        }

        public CompletableFuture<String[]> asyncMethodWithArrayReturnType() {
            return null;
        }

        public CompletableFuture<S2> asyncMethodWithGenericDynamicReturnType() {
            return null;
        }

        public CompletableFuture<Map<String, SuccessResponse>> asyncMethodWithGenericReturnType() {
            return null;
        }

        //used in test async API

        public CompletableFuture<?> asyncMethodWithNonGenericReturnType() {
            return null;
        }

        // to test return type

        public CompletableFuture<?> asyncMethodWithWildCardReturnType() {
            return null;
        }

        public void methodWithCookieAndHeader(@CookieParam("cookie") String cookie,
                @HeaderParam("header") String header) {

        }

        public S1 methodWithGenericDynamicReturnType() {
            return null;
        }

        // methods to test generic return type
        public Map<String, SuccessResponse> methodWithGenericReturnType() {
            return null;
        }

        public void methodWithPathParamAndBody(@PathParam("path") String path,
                @OperationBody Object customObject) {

        }

        public void methodWithPathParamOnly(@PathParam("path") String path) {

        }

        public void methodWithPragmaDirective(@PragmaDirective String[] pragma) {

        }

        public void methodWithQueryAndOperation(@QueryParam("query") String path, Operation op) {

        }

        //used in testExtractParamsWithIncorrectConfig

        //used in testExtractParams
        public void methodWithQueryAndPathParam(@QueryParam("query") String query,
                @PathParam("path") String path) {

        }

        public void methodWithQueryAndPathParams(@QueryParam("query1") String query1,
                @QueryParam("query2") String query2,
                @PathParam("path1") String path1,
                @PathParam("path2") String path2) {

        }

        public void methodWithQueryParamOnly(@QueryParam("query") String query) {

        }

        public SuccessResponse methodWithReturnType() {
            return null;
        }

        public void methodWithUnannotatedArgAndIncorrectType(@QueryParam("query") String path,
                Object someBody) {

        }

        public void methodWithUnannotatedArgs(@QueryParam("query") String path, Operation op,
                Object someBody) {

        }

        public void methodWithUnsupportedAnnotation(@MatrixParam("matrix") String param,
                Operation op) {

        }

        @DELETE
        @Path("/delete/{path}")
        public List<String> testDeleteAction(@PathParam("path") String path) {
            return null;
        }

        //used in  testParseAction & testParseJaxRsMethodInfo
        @GET
        public void testGetAction(@QueryParam("query") String query, @PathParam("path") String path,
                Operation op) {

        }

        @GET
        @Path("/get/{path}")
        public CompletableFuture<String> testGetWithDefaultValues(
                @DefaultValue("odata") @PathParam("path") String query,
                @DefaultValue("sampleFilter") @QueryParam("query") List<String> path) {
            return CompletableFuture.completedFuture("");
        }

        @PATCH
        @Path("/patch/{path}")
        public void testPatchAction(@PathParam("path") String path,
                @OperationBody List<String> contents, Operation op) {

        }

        @POST
        @Path("/post/{path}")
        public CompletableFuture<List<String>> testPostAction(@PathParam("path") String path,
                @OperationBody List<String> contents) {
            return null;
        }

    }

    private Map<String, Class<?>> typeResolution;

    private MethodInfo findMethodByName(List<MethodInfo> httpMethods, String name) {
        return httpMethods.stream().filter(method -> method.getName().equals(name)).findFirst()
                .get();
    }

    @Before
    public void init() {
        this.typeResolution = new HashMap<>();
        this.typeResolution.put("S1", String.class);
        this.typeResolution.put("S2", Long.class);
    }

    @Test
    public void testAsyncMethodReturnType() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class
                .getDeclaredMethod("asyncMethodWithGenericReturnType");
        MethodInfo mInfo = new MethodInfo(publicMethod);
        MethodInfoBuilder.parseReturnTypes(mInfo, this.typeResolution);
        assertEquals(Map.class, mInfo.getReturnType());
        assertEquals(Map.class, ((ParameterizedType) mInfo.getType()).getRawType());

        publicMethod = MockWithJaxRsParams.class
                .getDeclaredMethod("asyncMethodWithWildCardReturnType");
        mInfo = new MethodInfo(publicMethod);
        MethodInfoBuilder.parseReturnTypes(mInfo, this.typeResolution);
        assertEquals(Object.class, mInfo.getReturnType());

        publicMethod = MockWithJaxRsParams.class
                .getDeclaredMethod("asyncMethodWithArrayReturnType");
        mInfo = new MethodInfo(publicMethod);
        MethodInfoBuilder.parseReturnTypes(mInfo, this.typeResolution);
        assertEquals(String[].class, mInfo.getReturnType());

        publicMethod = MockWithJaxRsParams.class
                .getDeclaredMethod("asyncMethodWithNonGenericReturnType");
        mInfo = new MethodInfo(publicMethod);
        MethodInfoBuilder.parseReturnTypes(mInfo, this.typeResolution);
        assertEquals(Object.class, mInfo.getReturnType());

        publicMethod = MockWithJaxRsParams.class
                .getDeclaredMethod("asyncMethodWithGenericDynamicReturnType");
        mInfo = new MethodInfo(publicMethod);
        MethodInfoBuilder.parseReturnTypes(mInfo, this.typeResolution);
        assertEquals(Long.class, mInfo.getReturnType());
    }

    @Test
    public void testAyncApi() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("testPostAction", String.class,
                List.class);
        List<MethodInfo> infos = MethodInfoBuilder.generateMethodInfo(new Method[] { publicMethod },
                this.typeResolution);
        assertEquals(1, infos.size());
        assertTrue(infos.get(0).isAsyncApi());
        assertEquals(List.class, ((ParameterizedType) infos.get(0).getType()).getRawType());
        assertEquals(List.class, infos.get(0).getReturnType());
    }

    @Test
    public void testDefaultValuePopulation() {
        List<MethodInfo> httpMethods = MethodInfoBuilder
                .parseInterfaceForJaxRsInfo(MockWithJaxRsParams.class, this.typeResolution);
        assertEquals(5, httpMethods.size());
        MethodInfo defaultValues = findMethodByName(httpMethods, "testGetWithDefaultValues");
        assertEquals("odata", defaultValues.getParameters().get(0).getDefaultValue());
        assertEquals(asList("sampleFilter"),
                defaultValues.getParameters().get(1).getDefaultValue());
    }

    @Test
    public void testExtractParams() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class
                .getDeclaredMethod("methodWithQueryAndPathParam", String.class, String.class);
        List<ParamMetadata> params = MethodInfoBuilder.extractParamMetadatas(publicMethod);
        assertEquals(2, params.size());
        params.sort(Comparator.comparing(ParamMetadata::getParameterIndex));
        assertEquals("query", params.get(0).getName());
        assertEquals(QUERY, params.get(0).getType());
        assertEquals("path", params.get(1).getName());
        assertEquals(PATH, params.get(1).getType());
    }

    @Test
    public void testExtractParamsForMethodWithCookieAndHeader() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("methodWithCookieAndHeader",
                String.class, String.class);
        List<ParamMetadata> params = MethodInfoBuilder.extractParamMetadatas(publicMethod);
        assertEquals(2, params.size());
        ParamMetadata paramMetadata = params.get(1);
        assertEquals(1, paramMetadata.getParameterIndex());
        assertEquals(HEADER, paramMetadata.getType());
        assertEquals("header", paramMetadata.getName());
        paramMetadata = params.get(0);
        assertEquals(0, paramMetadata.getParameterIndex());
        assertEquals(COOKIE, paramMetadata.getType());
        assertEquals("cookie", paramMetadata.getName());
    }

    @Test
    public void testExtractParamsForMethodWithPathParamAndBody() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("methodWithPathParamAndBody",
                String.class, Object.class);
        List<ParamMetadata> params = MethodInfoBuilder.extractParamMetadatas(publicMethod);
        assertEquals(2, params.size());
        assertEquals(1, params.get(1).getParameterIndex());
        assertEquals(BODY, params.get(1).getType());
        assertEquals(Object.class, params.get(1).getParamterType());
    }

    @Test
    public void testExtractParamsForMethodWithPathParamOnly() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("methodWithPathParamOnly",
                String.class);
        List<ParamMetadata> params = MethodInfoBuilder.extractParamMetadatas(publicMethod);
        assertEquals(1, params.size());
        assertEquals("path", params.get(0).getName());
        assertEquals(PATH, params.get(0).getType());
    }

    @Test
    public void testExtractParamsForMethodWithPragmaDirective() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("methodWithPragmaDirective",
                String[].class);
        List<ParamMetadata> params = MethodInfoBuilder.extractParamMetadatas(publicMethod);
        assertEquals(1, params.size());
        ParamMetadata paramMetadata = params.get(0);
        assertEquals(0, paramMetadata.getParameterIndex());
        assertEquals(PRAGMA, paramMetadata.getType());
    }

    @Test
    public void testExtractParamsForMethodWithQueryAndOperation() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("methodWithQueryAndOperation",
                String.class, Operation.class);
        List<ParamMetadata> params = MethodInfoBuilder.extractParamMetadatas(publicMethod);
        assertEquals(2, params.size());
        assertEquals(1, params.get(1).getParameterIndex());
        assertEquals(OPERATION, params.get(1).getType());
    }

    @Test
    public void testExtractParamsFOrMethodWithQueryParamOnly() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("methodWithQueryParamOnly",
                String.class);
        List<ParamMetadata> params = MethodInfoBuilder.extractParamMetadatas(publicMethod);
        assertEquals(1, params.size());
        assertEquals("query", params.get(0).getName());
        assertEquals(QUERY, params.get(0).getType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractParamsForMethodWithUnannotatedArgAndIncorrectType() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getDeclaredMethod(
                "methodWithUnannotatedArgAndIncorrectType", String.class, Object.class);
        MethodInfoBuilder.extractParamMetadatas(publicMethod);
        fail("Shouldn't reach here");

    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractParamsForMethodWithUnannotatedArgs() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getDeclaredMethod(
                "methodWithUnannotatedArgs", String.class,
                Operation.class, Object.class);
        MethodInfoBuilder.extractParamMetadatas(publicMethod);
        fail("Shouldn't reach here");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractParamsForMethodWithUnsupportedAnnotation() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getDeclaredMethod(
                "methodWithUnsupportedAnnotation", String.class, Operation.class);
        MethodInfoBuilder.extractParamMetadatas(publicMethod);
        fail("Shouldn't reach here");
    }

    @Test
    public void testExtractParamsOnMethodWithQueryAndPathParams() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("methodWithQueryAndPathParams",
                String.class, String.class, String.class, String.class);
        List<ParamMetadata> params = MethodInfoBuilder.extractParamMetadatas(publicMethod);
        assertEquals(4, params.size());
        params.sort(Comparator.comparing(ParamMetadata::getParameterIndex));
        assertEquals("query1", params.get(0).getName());
        assertEquals(QUERY, params.get(0).getType());
        assertEquals("query2", params.get(1).getName());
        assertEquals(QUERY, params.get(1).getType());
        assertEquals("path1", params.get(2).getName());
        assertEquals(PATH, params.get(2).getType());
        assertEquals("path2", params.get(3).getName());
        assertEquals(PATH, params.get(3).getType());
    }

    @Test
    public void testMethodWithGenericReturnType() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class
                .getDeclaredMethod("methodWithGenericReturnType");
        MethodInfo mInfo = new MethodInfo(publicMethod);
        MethodInfoBuilder.parseReturnTypes(mInfo, this.typeResolution);
        assertEquals(Map.class, mInfo.getReturnType());
        assertEquals(Map.class, ((ParameterizedType) mInfo.getType()).getRawType());

        publicMethod = MockWithJaxRsParams.class.getDeclaredMethod("methodWithReturnType");
        mInfo = new MethodInfo(publicMethod);
        MethodInfoBuilder.parseReturnTypes(mInfo, this.typeResolution);
        assertEquals(MockWithJaxRsParams.SuccessResponse.class, mInfo.getReturnType());

        publicMethod = MockWithJaxRsParams.class
                .getDeclaredMethod("methodWithGenericDynamicReturnType");
        mInfo = new MethodInfo(publicMethod);
        MethodInfoBuilder.parseReturnTypes(mInfo, this.typeResolution);
        assertEquals(String.class, mInfo.getReturnType());

    }

    @Test
    public void testParseAction() throws Exception {
        Method publicMethod = MockWithJaxRsParams.class.getMethod("testGetAction", String.class,
                String.class, Operation.class);
        Service.Action action = MethodInfoBuilder.parseAction(publicMethod);
        assertEquals(Service.Action.GET, action);

        publicMethod = MockWithJaxRsParams.class.getMethod("testPatchAction", String.class,
                List.class, Operation.class);
        action = MethodInfoBuilder.parseAction(publicMethod);
        assertEquals(Service.Action.PATCH, action);

        publicMethod = MockWithJaxRsParams.class.getMethod("testDeleteAction", String.class);
        action = MethodInfoBuilder.parseAction(publicMethod);
        assertEquals(Service.Action.DELETE, action);
    }

    @Test
    public void testParseDeleteApi() throws Exception {
        List<MethodInfo> httpMethods = MethodInfoBuilder
                .parseInterfaceForJaxRsInfo(MockWithJaxRsParams.class, Collections.emptyMap());
        assertEquals(5, httpMethods.size());

        MethodInfo testDeleteAction = findMethodByName(httpMethods, "testDeleteAction");
        assertEquals(1, testDeleteAction.getParameters().size());
        assertEquals("path", testDeleteAction.getParameters().get(0).getName());
        assertEquals(PATH, testDeleteAction.getParameters().get(0).getType());
        assertEquals("/delete/{path}", testDeleteAction.getUriPath());
        assertEquals(Service.Action.DELETE, testDeleteAction.getAction());
        assertFalse(testDeleteAction.getPathParamsVsUriIndex().isEmpty());
        assertTrue(testDeleteAction.getPathParamsVsUriIndex().containsKey("path"));
    }

    @Test
    public void testParseGetApiWithPathAndQueryParam() throws Exception {
        List<MethodInfo> httpMethods = MethodInfoBuilder
                .parseInterfaceForJaxRsInfo(MockWithJaxRsParams.class, this.typeResolution);
        assertEquals(5, httpMethods.size());

        MethodInfo testGetAction = findMethodByName(httpMethods, "testGetAction");
        assertEquals(3, testGetAction.getParameters().size());
        assertEquals("path", testGetAction.getParameters().get(1).getName());
        assertEquals(PATH, testGetAction.getParameters().get(1).getType());
        assertEquals("query", testGetAction.getParameters().get(0).getName());
        assertEquals(QUERY, testGetAction.getParameters().get(0).getType());
        assertEquals(OPERATION, testGetAction.getParameters().get(2).getType());
        assertEquals(Service.Action.GET, testGetAction.getAction());
        assertTrue(testGetAction.getPathParamsVsUriIndex().isEmpty());
    }

    @Test
    public void testParsePatchApi() throws Exception {
        List<MethodInfo> httpMethods = MethodInfoBuilder
                .parseInterfaceForJaxRsInfo(MockWithJaxRsParams.class, this.typeResolution);
        assertEquals(5, httpMethods.size());

        MethodInfo testPatchAction = findMethodByName(httpMethods, "testPatchAction");
        assertEquals(3, testPatchAction.getParameters().size());
        assertEquals("path", testPatchAction.getParameters().get(0).getName());
        assertEquals(PATH, testPatchAction.getParameters().get(0).getType());
        assertEquals(BODY, testPatchAction.getParameters().get(1).getType());
        assertEquals(OPERATION, testPatchAction.getParameters().get(2).getType());
        assertEquals("/patch/{path}", testPatchAction.getUriPath());
        assertEquals(Service.Action.PATCH, testPatchAction.getAction());
        assertFalse(testPatchAction.getPathParamsVsUriIndex().isEmpty());
        assertTrue(testPatchAction.getPathParamsVsUriIndex().containsKey("path"));
    }

    @Test
    public void testParsePathParams() throws Exception {
        Map<String, Integer> pathParams = MethodInfoBuilder
                .parsePathParams(buildUriPath("/vrbc/parent/", "/child/{pathParam}"));
        assertEquals(1, pathParams.size());
        assertEquals(Integer.valueOf(4), pathParams.get("pathParam"));

        pathParams = MethodInfoBuilder
                .parsePathParams(buildUriPath("/vrbc/parent/", "/child/{pathParam1}/{pathParam2}"));
        assertEquals(2, pathParams.size());
        assertEquals(Integer.valueOf(4), pathParams.get("pathParam1"));
        assertEquals(Integer.valueOf(5), pathParams.get("pathParam2"));
    }
}
