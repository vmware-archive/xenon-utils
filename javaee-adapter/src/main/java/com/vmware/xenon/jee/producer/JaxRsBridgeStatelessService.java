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

package com.vmware.xenon.jee.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer;
import com.vmware.xenon.jee.consumer.JaxRsServiceConsumer.ClientBuilder;

/**
 * Stateless service that adds JAX-RS annotation processing logic using RequestRouter
 * <pre>
 * Only public methods will be considered for processing and following are set of supported annotations
 *  1. GET/POST/DELETE/PUT/DELETE
 *  2. Custom annotation PATCH as jaxrs spec doesn't have one
 *  3. HEADER/COOKIE/ DEFAULT
 *  4. Custom annotation @OperationBody to receive body as a payload in custom type
 *  5. Executes javax validation annotations if custom types are annotated
 * </pre>
 * Note: JAX-RS annotations are not inherited and needs to be declared on each methods.
 * Also see {@link RequestRouterBuilder}
 */
public class JaxRsBridgeStatelessService extends StatelessService {

    protected Logger log = LoggerFactory.getLogger(getClass());
    private Class<?> contractInterface;

    public JaxRsBridgeStatelessService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    protected void addFilters(OperationProcessingChain operationProcessingChain) {
    }

    /**
     * Add JAX-RS Annotation processing logic
     */
    @Override
    public OperationProcessingChain getOperationProcessingChain() {
        if (super.getOperationProcessingChain() != null) {
            return super.getOperationProcessingChain();
        }
        final OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
        RequestRouter requestRouter = RequestRouterBuilder.parseJaxRsAnnotations(this,
                this.contractInterface);
        addFilters(opProcessingChain);
        opProcessingChain.add(requestRouter);
        setOperationProcessingChain(opProcessingChain);
        initializeInstance();
        return opProcessingChain;
    }

    /**
     * Invoked only once and can be used to initialize the instance as part if lifecycle
     */
    protected void initializeInstance() {
    }

    protected <T> T newLocalhostContract(Class<T> clazz) {
        ClientBuilder<T> newBuilder = JaxRsServiceConsumer.newBuilder();
        return newBuilder.withHost(getHost())
                .withResourceInterface(clazz).build();
    }

    /**
     * The contract interface implemented by this service which has public methods with JaxRsAnnotations
     * The passed interface are scanned for JaxRs annotation along with child class.
     * @param iFace
     */
    protected void setContractInterface(Class<?> iFace) {
        this.contractInterface = iFace;
    }
}
