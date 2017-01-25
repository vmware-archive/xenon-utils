/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocument.UsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class TokenService extends StatelessService {
    public static final String SELF_LINK = "/tokens";

    static class Token {
        public String token;
    }

    static class UserToken {
        public static final String FIELD_NAME_INTERNAL_ID = "internalId";

        public String user;
        public String token;
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String internalId;
    }

    @Override
    public OperationProcessingChain getOperationProcessingChain() {
        RequestRouter requestRouter = new RequestRouter();

        // Post with activate action activates the pipeline
        requestRouter.register(
                Action.GET,
                new RequestRouter.RequestUriMatcher("type=short"),
                this::handleGetForShort, "Short version");
        // Post with execute action executes the pipeline
        requestRouter.register(
                Action.GET,
                new RequestRouter.RequestUriMatcher("type=long"),
                this::handleGet, "Long version");

        requestRouter.register(Action.PATCH,
                new RequestRouter.RequestBodyMatcher<>(UserToken.class, "token",
                        "34bf4c10-e122-11e6-bf01-fe55135034f3"),
                this::handlePatch, "Patch Token 1");

        requestRouter.register(Action.PATCH,
                new RequestRouter.RequestBodyMatcher<>(UserToken.class, "token",
                        "34bf4c10-e122-11e6-bf01-fe55135034f1"),
                this::handlePatch, "Patch Token 2");

        OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
        opProcessingChain.add(requestRouter);
        setOperationProcessingChain(opProcessingChain);
        return opProcessingChain;
    }


    @Override
    public void handleGet(Operation op) {
        Token response = new Token();
        response.token = UUID.randomUUID().toString();
        op.setBody(response).complete();
    }

    @Override
    public void handlePost(Operation post) {
        UserToken body = post.getBody(UserToken.class);
        body.internalId = UUID.randomUUID().toString();
        log(Level.INFO, "user:%s token:%s internal", body.user, body.token);
        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        UserToken body = put.getBody(UserToken.class);
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(put.getUri());
        String appendValue = queryParams.get("append");

        UserToken currentState = new UserToken();
        currentState.token = body.token;
        currentState.user = body.user;
        if (Boolean.valueOf(appendValue)) {
            currentState.internalId = body.user + body.internalId;
        }
        log(Level.INFO, "user:%s token:%s internal:%s", currentState.user, currentState.token,
                currentState.internalId);
        put.setBody(currentState).complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        UserToken body = patch.getBody(UserToken.class);
        UserToken currentState = new UserToken();
        currentState.token = body.token;
        currentState.user = body.user;
        currentState.internalId = body.internalId;
        log(Level.INFO, "user:%s token:%s internal:%s", currentState.user, currentState.token,
                currentState.internalId);
        patch.setBody(currentState).complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();

        if (d.documentDescription.serviceRequestRoutes == null) {
            d.documentDescription.serviceRequestRoutes = new HashMap<>();
        }
        d.documentDescription.name = "Custom Tag Name";
        d.documentDescription.description = "Custom Service Description";

        Route route = new Route();
        route.action = Action.POST;
        route.description = "Creates user-token mapping";
        route.requestType = UserToken.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));

        route = new Route();
        route.action = Action.PUT;
        route.description = "Replace user-token mapping";
        route.requestType = UserToken.class;

        route.parameters = new ArrayList<>();
        RequestRouter.Parameter parameter1 = new RequestRouter.Parameter("append", "Append Id",
                "string", false, "true", RequestRouter.ParamDef.QUERY);
        RequestRouter.Parameter parameter2 = new RequestRouter.Parameter("token", "Token value",
                "string", false, "34bf4c10-e122-11e6-bf01-fe55135034f3", RequestRouter.ParamDef.BODY);
        RequestRouter.Parameter parameter3 = new RequestRouter.Parameter("user", "Update user",
                "string", true, "user1", RequestRouter.ParamDef.BODY);
        route.parameters.add(parameter1);
        route.parameters.add(parameter2);
        route.parameters.add(parameter3);

        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }

    private void handleGetForShort(Operation get) {
        Token response = new Token();
        response.token = String.valueOf(UUID.randomUUID().getMostSignificantBits());
        get.setBody(response).complete();
    }
}