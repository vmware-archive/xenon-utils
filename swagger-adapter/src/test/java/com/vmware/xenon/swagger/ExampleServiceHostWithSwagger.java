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

import static com.vmware.xenon.swagger.TestSwaggerDescriptorService.INFO_DESCRIPTION;
import static com.vmware.xenon.swagger.TestSwaggerDescriptorService.INFO_TERMS_OF_SERVICE;

import java.util.logging.Level;

import io.swagger.models.Info;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.RootNamespaceService;
import com.vmware.xenon.ui.UiService;

public class ExampleServiceHostWithSwagger extends ServiceHost {
    public static void main(String[] args) throws Throwable {
        ServiceHost h = new ExampleServiceHostWithSwagger()
                .initialize(args)
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            h.log(Level.WARNING, "Host stopping ...");
            h.stop();
            h.log(Level.WARNING, "Host is stopped");
        }));
    }

    @Override
    public ServiceHost start() throws Throwable {
        SwaggerDescriptorService swagger = new SwaggerDescriptorService();
        Info info = new Info();
        info.setDescription(INFO_DESCRIPTION);
        info.setTermsOfService(INFO_TERMS_OF_SERVICE);
        info.setTitle("title");
        info.setVersion("version");

        swagger.setExcludeUtilities(true);
        swagger.setExcludedPrefixes(
                "/core/synch-tasks",
                "/core/callbacks",
                "/core/netty-maint-proxies",
                "/core/query-page-forwarding",
                "/core/service-context-index");
        swagger.setInfo(info);
        super.start();

        super.startDefaultCoreServicesSynchronously();

        // Start the root namespace factory: this will respond to the root URI (/) and list all
        // the factory services.
        super.startService(new RootNamespaceService());

        this.startService(swagger);

        this.startService(
                Operation.createPost(UriUtils.buildFactoryUri(this, ExampleService.class)),
                ExampleService.createFactory());

        this.startService(Operation.createPost(UriUtils.buildFactoryUri(this, CarService.class)),
                CarService.createFactory());

        this.startService(Operation.createPost(UriUtils.buildUri(this, UiService.class)),
                new UiService());

        this.startService(
                Operation.createPost(UriUtils.buildFactoryUri(this, ExampleService.class)),
                new ExampleService());

        this.startService(Operation.createPost(UriUtils.buildUri(this, TokenService.class)),
                new TokenService());

        this.startService(Operation.createPost(UriUtils.buildUri(this, NsOwnerService.class)),
                new NsOwnerService());

        return this;
    }
}
