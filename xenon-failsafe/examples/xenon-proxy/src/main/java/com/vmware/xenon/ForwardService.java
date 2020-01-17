/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon;

import java.net.URI;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;

public class ForwardService extends StatelessService {
    public static final String SELF_LINK = "/forward";
    public static final String FACTORY_LINK = "/forward";

    public static AtomicInteger failPercent = new AtomicInteger(1);
    public static AtomicReference<String> forwardUrl = new AtomicReference<>("http://localhost:8000/fail");
    Random random = new Random();

    public static class Update extends ServiceDocument {
        public String forwardUrl;
        public int failPercent;
    }

    public ForwardService() {
        super(ForwardService.Update.class);
    }

    @Override
    public void handlePost(Operation post) {
        ForwardService.Update update = post.getBody(ForwardService.Update.class);
        ForwardService.failPercent.set(update.failPercent);
        ForwardService.forwardUrl.set(update.forwardUrl);
        post.complete();
    }

    /**
     * fail with an expected exception
     * @param op
     */
    @Override
    public void handleGet(Operation op) {
        if (random.nextInt(100) < ForwardService.failPercent.get()) {
            op.fail(503, new Exception("Forwarding Failure"), null);
        } else {
            super.sendRequest(Operation
                    .createGet(URI.create(forwardUrl.get()))
                    .forceRemote()
                    .nestCompletion(
                            (o,e) -> {
                                if (e != null) {
                                    op.fail(e);
                                } else {
                                    op.setBodyNoCloning(this.getHost().getUri() + "->" + o.getBodyRaw());
                                    op.complete();
                                }
                            }
                    )
            );
        }

    }
}
