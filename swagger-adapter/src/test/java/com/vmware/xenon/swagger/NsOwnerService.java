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

package com.vmware.xenon.swagger;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.PathParam;
import com.vmware.xenon.common.StatelessService;

public class NsOwnerService extends StatelessService {
    public static final String SELF_LINK = "/calculate";

    public NsOwnerService() {
        toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    /**
     * a badly designed rest api for a calculator.
     * However it demonstrates the use of repeated annotations for routes.
     * @param get
     */
    @RouteDocumentation(
            path = "/{a}/ADD/{b}",
            pathParams = {
                    @PathParam(name = "a", type = "number", description = "first operand"),
                    @PathParam(name = "b", type = "number", description = "second operand"),
            },
            description = "performs addition",
            produces = { "application/json" })
    @RouteDocumentation(
            path = "/{a}/MULT/{b}",
            pathParams = {
                    @PathParam(name = "a", description = "first operand"),
                    @PathParam(name = "b", description = "second operand"),
            },
            description = "performs multiplication",
            produces = { "application/json" })
    @Override
    public void handleGet(Operation get) {
        // /calculate/2/MULT/7 must return 14
        String uri = get.getUri().getPath();
        String[] parts = uri.split("/");

        String op1 = parts[2];
        String action = parts[3];
        String op2 = parts[4];

        double d1 = Double.parseDouble(op1);
        double d2 = Double.parseDouble(op2);
        double res = 0;
        switch (action) {
        case "MULT":
            res = d1 * d2;
            break;
        case "ADD":
            res = d1 + d2;
            break;
        case "DIV":
            res = d1 / d2;
            break;
        case "SUB":
            res = d1 - d2;
            break;
        default:
        }

        get.setBody(res);
        get.complete();
    }
}

