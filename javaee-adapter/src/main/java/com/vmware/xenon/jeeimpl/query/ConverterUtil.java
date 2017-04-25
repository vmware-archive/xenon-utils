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

package com.vmware.xenon.jeeimpl.query;

import static com.vmware.xenon.common.Utils.fromJson;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.vmware.xenon.common.ServiceDocumentQueryResult;

/**

 * <p>
 * Utility to convert {@link ServiceDocumentQueryResult} to a List of documents
 */
public class ConverterUtil {

    /**
     * Utility helps to convert ServiceDocumentQueryResult.documents containing homogeneous results of a particular document kind to a List
     *
     * @param documentKind
     */
    public static <D> Function<ServiceDocumentQueryResult, List<D>> fromDocuments(
            Class<D> documentKind) {
        return queryResult -> {
            List<D> documents = new ArrayList<>();
            queryResult.documentLinks.forEach(
                    link -> documents.add(fromJson(queryResult.documents.get(link), documentKind)));
            return documents;
        };
    }

    /**
     * Utility helps to convert ServiceDocumentQueryResult.selectedDocuments containing homogeneous results of a particular selected document kind to a List
     */
    public static <S> Function<ServiceDocumentQueryResult, List<S>> fromSelectedDocuments(
            Class<S> selectedDocumentKind) {
        return queryResult -> {
            List<S> documents = new ArrayList<>();
            queryResult.selectedDocuments.values()
                    .forEach(obj -> documents.add(fromJson(obj, selectedDocumentKind)));
            return documents;
        };
    }

}
