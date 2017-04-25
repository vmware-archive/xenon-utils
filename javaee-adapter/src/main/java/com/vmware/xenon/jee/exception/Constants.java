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

package com.vmware.xenon.jee.exception;

/**
 * Class holding constants used in ServerErrorResponse context map
 */
public class Constants {

    public static final String URI = "URI";
    public static final String ACTION = "Action";
    public static final String RESPONSE_HEADERS = "ResponseHeaders";
    public static final String RETRY_COUNT = "RetryCount";
    public static final String RETRIES_REMAINING = "RetriesRemaining";
    public static final String COOKIES = "Cookies";
    public static final String CONTEXT_ID = "ContextId";
    public static final String EXPIRATION_MICROS = "ExpirationMicros";
    public static final String REFERER = "Referer";
    public static final String RESPONSE_STATUS_CODE = "StatusCode";

}
