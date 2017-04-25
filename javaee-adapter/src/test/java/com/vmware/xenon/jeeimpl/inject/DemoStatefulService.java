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

package com.vmware.xenon.jeeimpl.inject;

import static com.vmware.xenon.jeeimpl.inject.DemoStatelessService.REMOTE_SVC_URI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.ws.rs.Path;

import org.slf4j.Logger;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.jee.inject.InjectLogger;
import com.vmware.xenon.jee.inject.InjectRestProxy;

@Path(DemoStatefulService.FACTORY_LINK) public class DemoStatefulService extends StatefulService {

    public static final String FACTORY_LINK = "/example/inject/stateful";

    @InjectRestProxy(baseUri = REMOTE_SVC_URI)
    private DemoStatelessService svcContract;

    @InjectLogger
    private Logger log;

    public static class StatefulDoc extends ServiceDocument {
        public List<String> values = new ArrayList<>();
    }

    public DemoStatefulService() {
        super(StatefulDoc.class);
    }

    @Override
    public void handleCreate(Operation post) {
        CompletableFuture<List<String>> response = this.svcContract.sayHello();
        response.thenAccept(contents -> {
            StatefulDoc body = post.getBody(StatefulDoc.class);
            body.values.addAll(contents);
            setState(post, body);
            this.log.info("Stateful service injected with logger and some rest proxy contract. Contract returned {}", contents);
            post.complete();
        })
                .exceptionally(throwable -> {
                    Operation.fail(post, 500, 500, throwable);
                    return null;
                });
    }
}
