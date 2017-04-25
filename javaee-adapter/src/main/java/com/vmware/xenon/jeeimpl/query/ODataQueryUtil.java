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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.Utils;
import com.vmware.xenon.jee.annotations.Param;
import com.vmware.xenon.jee.query.XenonQueryService;

/**

 * <p>
 * Intended for internal use only
 */
class ODataQueryUtil {

    private static final Logger log = LoggerFactory.getLogger(ODataQueryUtil.class);

    static String getFilterCriteria(Method method, Class<?> documentKind, String filter,
            Object[] args) {
        Parameter[] parameters = method.getParameters();
        String filterCriteria = "documentKind" + XenonQueryService.EQ
                + Utils.toDocumentKind(documentKind);
        if (parameters.length > 0) {
            filterCriteria = filterCriteria + XenonQueryService.AND + filter;
        } else {
            filterCriteria = filterCriteria + XenonQueryService.SPACE + filter;
        }
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Param param = parameter.getDeclaredAnnotation(Param.class);
            if (param == null) {
                continue;
            }
            String variableName = param.value();
            Object val = args[i];
            if (val instanceof String) {
                val = "'" + val + "'";
            }
            filterCriteria = filterCriteria.replaceAll(":" + variableName, String.valueOf(val));
        }
        log.debug("Filter criteria for method {} is {}", method.getName(), filterCriteria);
        return filterCriteria;
    }

    static Optional<Integer> getIntParamWithName(Method method, String paramName, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Param param = parameter.getDeclaredAnnotation(Param.class);
            if (param == null) {
                continue;
            }
            if (paramName.equals(param.value())) {
                Object val = args[i];
                try {
                    return Optional.of(Integer.valueOf(String.valueOf(val)));
                } catch (NumberFormatException e) {
                    // no-op
                }
            }
        }
        return Optional.empty();
    }

}
