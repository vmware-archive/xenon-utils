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

package com.vmware.xenon.jee.inject;

import javax.inject.Provider;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * A factory service with ability to get stateful service instance from CDI provider.
 */
public abstract class CDIEnabledFactoryService extends FactoryService {

    private Provider<? extends StatefulService> statefulSvcProvider;

    public CDIEnabledFactoryService(Class<? extends ServiceDocument> childServiceDocumentType) {
        super(childServiceDocumentType);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        if (this.statefulSvcProvider == null) {
            return defaultInstance();
        }
        return this.statefulSvcProvider.get();
    }

    /**
     * com.vmware.xenon.common.FactoryService has logic in constructor which needs an instance of the service.
     * This can't be skipped and due to which needs a dummy instance at the least.
     *
     * @return a service instance which is used only by xenon framework while starting up the service.
     * Wont be used for any of the actual service document.
     */
    public abstract Service defaultInstance();

    public void setStatefulSvcProvider(Provider<? extends StatefulService> statefulSvcProvider) {
        this.statefulSvcProvider = statefulSvcProvider;
    }
}
