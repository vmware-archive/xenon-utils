/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
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

import java.net.URI;
import java.util.Map;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.ApiResponse;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.QueryParam;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;


/**
 */
public class CarService extends StatefulService {
    public static final String FACTORY_LINK = "/cars";

    public CarService() {
        super(Car.class);
    }

    public static Service createFactory() {
        return FactoryService.create(CarService.class, Car.class);
    }

    @RouteDocumentation(description = "@CAR",
            queryParams = {
                @QueryParam(description = "@TEAPOT",
                        example = "false", name = "teapot", required = false, type = "boolean")
            },
            consumes = { "application/json", "app/json" },
            produces = { "application/json", "app/json" },
            responses = {
                @ApiResponse(statusCode = 200, description = "OK"),
                @ApiResponse(statusCode = 404, description = "Not Found"),
                @ApiResponse(statusCode = 418, description = "I'm a teapot!")
            })
    @Override
    public void handlePut(Operation put) {
        boolean teapot = false;
        if (put.getUri().getQuery() != null) {
            Map<String,String> queryParams = UriUtils.parseUriQueryParams(put.getUri());
            teapot = Boolean.parseBoolean(queryParams.get("teapot"));
        }
        if (teapot) {
            put.setStatusCode(418); // I'm a teapot
        } else {
            this.setState(put, put.getBody(Car.class));
        }
        put.complete();
    }

    public enum FuelType {
        GASOLINE, DIESEL, ELECTRICITY
    }

    public static class EngineInfo {
        public FuelType fuel;
        public double power;
    }

    public static class Car extends ServiceDocument {
        public URI manufacturerHomePage;
        public double length;
        public double weight;
        @Documentation(description = "@MAKE", exampleString = "BMW")
        public String make;
        @Documentation(description = "License plate of the car", exampleString = "XXXAAAA")
        public String licensePlate;
        public EngineInfo engineInfo;
    }
}
