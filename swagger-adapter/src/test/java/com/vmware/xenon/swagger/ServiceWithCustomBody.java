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
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.swagger.CarService.Car;

public class ServiceWithCustomBody extends StatelessService {

    public static final String SELF_LINK = "/test/custom-body";

    @RouteDocumentation(description = "post with a car", requestBodyType = Car.class)
    @Override
    public void handlePost(Operation post) {
        post.complete();
    }

    @RouteDocumentation(description = "put with a token", requestBodyType = TokenService.UserToken.class)
    @Override
    public void handlePut(Operation put) {
        put.complete();
    }

    @RouteDocumentation(description = "patch without specifying the body type")
    @Override
    public void handlePatch(Operation patch) {
        patch.complete();
    }
}
