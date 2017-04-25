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

package com.vmware.xenon.jee.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare a Xenon supported OData Query with limits.
 * Returned documents should belong to a single document type.
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ODataPagedQuery {

    String NONE = "__NONE";

    String SPACE = " ";

    /**
     * Defines the expected document return type.
     * Implementation Notes : Document Kind filter criteria is added for this type.
     * Response object is also assumed to be of this type
     */
    Class<?> documentKind();

    /**
     * Defines the top n rows result limit
     * <p>
     * Implementation Notes : This value is passed as $limit query param
     * <p>
     * Dynamic value can be provided by passing method parameter on annotated method as
     * {@literal @}Param("$limit") int limit
     */
    int limit() default 10000;

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
     * Request to find out the total no. of document matching results.
     */
    boolean populateTotalCount() default true;

    /**
     * Defines the OData query to be executed when the annotated method is called.
     * <p>
     * Implementation Notes : This value is passed as $filter query param
     */
    String value();

}
