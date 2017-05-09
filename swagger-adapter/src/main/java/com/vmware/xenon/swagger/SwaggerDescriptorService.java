/*
 * Copyright (c) 2014-2016 VMware, Inc. All Rights Reserved.
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

import java.nio.ByteBuffer;

import io.swagger.models.Info;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Stateless service that serves a swagger 2.0 compatible descriptor of all factory
 * services started on a host.
 */
public class SwaggerDescriptorService extends StatelessService {
    public static final String SELF_LINK = ServiceUriPaths.SWAGGER;

    private static final String DEFAULT_QUERY_PARAMS = "?includes=ALL";
    private static final String PUBLIC_QUERY_PARAMS = "?includes=PUBLIC";

    private Info info;
    private String[] excludedPrefixes;
    private boolean excludeUtilities;
    private String queryParams = DEFAULT_QUERY_PARAMS;

    public SwaggerDescriptorService() {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.HTML_USER_INTERFACE, true);
        toggleOption(ServiceOption.CONCURRENT_GET_HANDLING, true);
    }

    /**
     * Exclude services whose URIs start with any of the given prefixes.
     *
     * @param excludedPrefixes
     */
    public void setExcludedPrefixes(String... excludedPrefixes) {
        this.excludedPrefixes = excludedPrefixes;
    }

    /**
     * Users can provide general information about the swagger endpoint.
     *
     * @param info
     */
    public void setInfo(Info info) {
        this.info = info;
    }

    /**
     * Excludes utility service from the swagger description.
     * @param excludeUtilities
     */
    public void setExcludeUtilities(boolean excludeUtilities) {
        this.excludeUtilities = excludeUtilities;
    }

    /**
     * When publicOnly is set, then only services with the PUBLIC
     * service option are included in documentation
     * @param publicOnly
     */
    public void setPublicOnly(boolean publicOnly) {
        if (publicOnly) {
            this.queryParams = PUBLIC_QUERY_PARAMS;
        } else {
            this.queryParams = DEFAULT_QUERY_PARAMS;
        }
    }

    @Override
    public void handleStart(Operation start) {
        logInfo("Swagger UI available at: %s", getHost().getPublicUri()
                + ServiceUriPaths.SWAGGER
                + ServiceHost.SERVICE_URI_SUFFIX_UI);
        start.complete();
    }

    @Override
    public void handleGet(Operation get) {
        String acceptEncoding = get.getRequestHeader(Operation.ACCEPT_ENCODING_HEADER);
        if (acceptEncoding != null && acceptEncoding.contains(Operation.CONTENT_ENCODING_GZIP)) {
            addCompressHandler(get);
        }

        Operation op = Operation.createGet(this, "/" + this.queryParams);
        op.setCompletion((o, e) -> {
            SwaggerAssembler
                    .create(this)
                    .setExcludedPrefixes(this.excludedPrefixes)
                    .setInfo(this.info)
                    .setExcludeUtilities(this.excludeUtilities)
                    .setQueryResult(o.getBody(ServiceDocumentQueryResult.class))
                    .build(get);
        });

        op.sendWith(this);
    }

    private void addCompressHandler(Operation get) {
        get.nestCompletion((o, e) -> {
            String content = o.getBody(String.class);
            ByteBuffer compressed;
            try {
                compressed = Utils.compressGZip(content);
            } catch (Exception ex) {
                get.fail(ex);
                return;
            }

            get.addResponseHeader(Operation.CONTENT_ENCODING_HEADER,
                    Operation.CONTENT_ENCODING_GZIP);
            get.setBodyNoCloning(compressed.array());
            get.setContentLength(compressed.limit());

            get.complete();
        });
    }

}
