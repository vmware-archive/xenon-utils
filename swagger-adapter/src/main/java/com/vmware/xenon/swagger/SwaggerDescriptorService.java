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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.zip.GZIPOutputStream;

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

    private static final String ACCEPT_ENCODING_HEADER = "accept-encoding";

    private Info info;
    private String[] excludedPrefixes;
    private boolean excludeUtilities;

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

    @Override
    public void handleStart(Operation start) {
        logInfo("Swagger UI available at: %s", getHost().getPublicUri()
                + ServiceUriPaths.SWAGGER
                + ServiceHost.SERVICE_URI_SUFFIX_UI);
        start.complete();
    }

    @Override
    public void handleGet(Operation get) {
        String acceptEncoding = get.getRequestHeader(ACCEPT_ENCODING_HEADER);
        if (acceptEncoding != null && acceptEncoding.contains(Operation.CONTENT_ENCODING_GZIP)) {
            addCompressHandler(get);
        }

        Operation op = Operation.createGet(this, "/");
        op.setCompletion((o, e) -> {
            SwaggerAssembler
                    .create(this)
                    .setExcludedPrefixes(this.excludedPrefixes)
                    .setInfo(this.info)
                    .setExcludeUtilities(this.excludeUtilities)
                    .setQueryResult(o.getBody(ServiceDocumentQueryResult.class))
                    .build(get);
        });

        getHost().queryServiceUris(
                // all services
                EnumSet.noneOf(ServiceOption.class),
                true,
                op,
                // exclude factory items
                EnumSet.of(ServiceOption.FACTORY_ITEM));
    }

    private void addCompressHandler(Operation get) {
        get.nestCompletion((o, e) -> {
            String content = o.getBody(String.class);
            ByteBuffer compressed;
            try {
                compressed = compressGZip(content);
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

    /**
     * Compresses text to gzip byte buffer.
     */
    private ByteBuffer compressGZip(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zos = new GZIPOutputStream(out)) {
            byte[] bytes = text.getBytes(Utils.CHARSET);
            zos.write(bytes, 0, bytes.length);
        }

        return ByteBuffer.wrap(out.toByteArray());
    }
}
