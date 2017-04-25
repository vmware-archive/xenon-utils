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

/**
 * POJO to hold info about method params
 */
public class ParamMetadata {

    public enum Type {
        PATH, QUERY, BODY, OPERATION, HEADER, COOKIE, PRAGMA
    }

    private String name;
    private int parameterIndex;
    private Type type;
    private Class<?> paramterType;

    private Object defaultValue;

    public Object getDefaultValue() {
        return this.defaultValue;
    }

    public String getName() {
        return this.name;
    }

    public int getParameterIndex() {
        return this.parameterIndex;
    }

    public Class<?> getParamterType() {
        return this.paramterType;
    }

    public Type getType() {
        return this.type;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setParameterIndex(int parameterIndex) {
        this.parameterIndex = parameterIndex;
    }

    public void setParamterType(Class<?> paramterType) {
        this.paramterType = paramterType;
    }

    public void setType(Type type) {
        this.type = type;
    }

}
