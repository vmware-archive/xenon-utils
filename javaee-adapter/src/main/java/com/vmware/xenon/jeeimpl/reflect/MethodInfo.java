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

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vmware.xenon.common.Service;

/**
 * Method info corresponding to a JaxRs Annotated HTTP method
 */
public class MethodInfo {

    private Method method;
    private String name;
    private List<ParamMetadata> parameters = new ArrayList<>();
    private String uriPath;
    private Service.Action action;
    private Map<String, Integer> pathParamsVsUriIndex;
    private boolean asyncApi;
    private Class<?> returnType;
    private Type type;

    public MethodInfo(Method method) {
        this.method = method;
        this.name = method.getName();
    }

    public Service.Action getAction() {
        return this.action;
    }

    public Method getMethod() {
        return this.method;
    }

    public String getName() {
        return this.name;
    }

    public List<ParamMetadata> getParameters() {
        return this.parameters;
    }

    public Map<String, Integer> getPathParamsVsUriIndex() {
        return this.pathParamsVsUriIndex;
    }

    public Class<?> getReturnType() {
        return this.returnType;
    }

    public Type getType() {
        return this.type;
    }

    public String getUriPath() {
        return this.uriPath;
    }

    public boolean isAsyncApi() {
        return this.asyncApi;
    }

    public void setAction(Service.Action action) {
        this.action = action;
    }

    public void setAsyncApi(boolean asyncApi) {
        this.asyncApi = asyncApi;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public void setParameters(List<ParamMetadata> parameters) {
        this.parameters = parameters;
    }

    public void setPathParamsVsUriIndex(Map<String, Integer> pathParamsVsUriIndex) {
        this.pathParamsVsUriIndex = pathParamsVsUriIndex;
    }

    public void setReturnType(Class<?> returnType) {
        this.returnType = returnType;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setUriPath(String uriPath) {
        this.uriPath = uriPath;
    }
}
