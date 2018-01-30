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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.swagger.models.Info;
import io.swagger.models.Scheme;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.auth.SecuritySchemeDefinition;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route.SupportLevel;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Stateless service that serves a swagger 2.0 compatible descriptor of all factory
 * services started on a host.
 */
public class SwaggerDescriptorService extends StatelessService {
    public static final String SELF_LINK = ServiceUriPaths.SWAGGER;

    private Info info;
    private String[] excludedPrefixes = new String[] { "/core/" };
    private String[] includedPrefixes = null;
    private String[] stripPackagePrefixes = new String[] { };
    private boolean excludeUtilities;
    private Map<String, SecuritySchemeDefinition> securityDefinitions;
    private List<SecurityRequirement> security;
    private List<Scheme> schemes = new ArrayList<Scheme>();

    // default to document only APIs annotated with PUBLIC
    private SupportLevel supportLevel = SupportLevel.PUBLIC;

    private Consumer<Swagger> swaggerPostprocessor;

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
     * Include only services whose URIs start with any of the given prefixes.
     *
     * @param includedPrefixes
     */
    public void setIncludedPrefixes(String... includedPrefixes) {
        this.includedPrefixes = includedPrefixes;
    }

    /**
     * A hook to postprocess swagger after the framework has produced a final API descriptor.
     * Intended to be used in cases where complex customizations are easier to express in an
     * imperative style, for example skip a DELETE operation only on a single service etc.
     *
     * The postprocessor in invoked every time a Swagger instance is built from the the current host.
     * @param swaggerPostprocessor
     */
    public void setSwaggerPostprocessor(Consumer<Swagger> swaggerPostprocessor) {
        this.swaggerPostprocessor = swaggerPostprocessor;
    }

    /**
     * Strip the given package prefixes from type names in generated swagger.
     * <p>
     * This permits swagger definitions to be mapped to simpler type names, e.g.
     * stripping
     *
     * @param stripPackagePrefixes
     */
    public void setStripPackagePrefixes(String... stripPackagePrefixes) {
        this.stripPackagePrefixes = stripPackagePrefixes;
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
     * When supportLevel is set, then only Routes
     * which have the given Route.SpportLevel or above are included
     * in the documentation
     */
    public void setSupportLevel(SupportLevel supportLevel) {
        this.supportLevel = supportLevel;
    }

    /**
     * Set the security definitions in the swagger description.
     * @param securityDefinitions
     */
    public void setSecurityDefinitions(Map<String, SecuritySchemeDefinition> securityDefinitions) {
        this.securityDefinitions = securityDefinitions;
    }

    /**
     * Set the default security for all operations in the swagger description.
     * @param security
     */
    public void setSecurity(List<SecurityRequirement> security) {
        this.security = security;
    }

    /**
     * Set the api schemes (ex. https) in the swagger description.
     * @param schemes
     */
    public void setSchemes(List<Scheme> schemes) {
        this.schemes = schemes;
    }

    @Override
    public void handleStart(Operation start) {
        logInfo("Swagger UI available at: %s", getHost().getUri()
                + getSelfLink() + ServiceHost.SERVICE_URI_SUFFIX_UI);
        start.complete();
    }

    @Override
    public void handleGet(Operation get) {
        Operation op = Operation.createGet(this, "/");
        op.setCompletion((o, e) -> {
            SwaggerAssembler
                    .create(this)
                    .setExcludedPrefixes(this.excludedPrefixes)
                    .setIncludedPrefixes(this.includedPrefixes)
                    .setStripPackagePrefixes(this.stripPackagePrefixes)
                    .setSupportLevel(this.supportLevel)
                    .setInfo(this.info)
                    .setPostprocessor(this.swaggerPostprocessor)
                    .setExcludeUtilities(this.excludeUtilities)
                    .setSecurityDefinitions(this.securityDefinitions)
                    .setSecurity(this.security)
                    .setSchemes(this.schemes)
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
}
