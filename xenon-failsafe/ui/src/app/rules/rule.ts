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

import {Timestamp} from "rxjs/Rx";

export class Rule {
    constructor (
        public prefix: String,
        public retryEnabled: Boolean,
        public retryMaxCount: Number,
        public retryMaxDurationMs: Number,
        public retryDelayMs: Number,
        public retryBackoffMs: Number,
        public retryBackoffMaxMs: Number,
        public retryJitterMs: Number,
        public breakerEnabled: Boolean,
        public breakerFailureThreshold: Number,
        public breakerOpenTimeMs: Number,
        public breakerSuccessThreshold: Number,
        public breakerTimeout: Number,
        public documentSelfLink: String,
        public circuitCloseTimestamp: Number,
    ) { }
}
