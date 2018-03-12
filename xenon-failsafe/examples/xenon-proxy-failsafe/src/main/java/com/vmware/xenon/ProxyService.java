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

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatelessService;

public class ProxyService extends StatelessService {
    public static final String SELF_LINK = "/proxy";
    public static final String FACTORY_LINK = "/proxy";

    public static class Proxy extends ServiceDocument {
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String url;
    }

    public ProxyService() {
        super(ProxyService.Proxy.class);
        toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePost(Operation post) {
        Proxy proxy = post.getBody(Proxy.class);
        String url = proxy.url;

        super.sendRequest(Operation
            .createGet(URI.create(url))
            .forceRemote()
            .nestCompletion(
                (o,e) -> {
                    if (e != null) {
                        post.fail(e);
                    } else {
                        post.setBodyNoCloning(o.getBodyRaw());
                        post.complete();
                    }
                }
            )
        );
    }
}
