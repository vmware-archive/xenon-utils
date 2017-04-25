/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.xenon.jee.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare a Xenon supported OData Query.
 * <p>
 * Returned documents should belong to a single document type.
 * <p>
 * Implementation Notes : These annotations are effective only on sub-interfaces of
 * {@link com.vmware.xenon.jee.query.XenonQueryService} at the moment
 * <p>
 * Why ? Interceptor, auth cookie passing etc., gets auto handled as we stick to XenonQueryService
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ODataQuery {

    String NONE = "__NONE";

    String SPACE = " ";

    /**
     * Defines the expected document return type.
     */
    Class<?> documentKind();

    /**
     * Defines the field by to which order by clause needs to be applied
     * <p>
     * Implementation Notes : This value is passed as $orderby query param
     */
    String orderBy() default NONE;

    /**
     * Define the data type using which the values will be ordered
     */
    String orderByType() default NONE;

    /**
     * Request to return the very first document alone.
     * <p>
     * Even if multiple results are returned, returns the first document.
     * Return type of such method should be documentKind
     */
    boolean pickFirst() default false;

    /**
     * Defines the top n rows result limit
     * <p>
     * Implementation Notes : This value is passed as $top query param
     */
    int top() default 9999;

    /**
     * Defines the OData query to be executed when the annotated method is called.
     * <p>
     * Implementation Notes : This value is passed as $filter query param to factory service
     */
    String value();

}
