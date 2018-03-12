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

package com.vmware.xenon.failsafe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;

import com.google.gson.Gson;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class FailsafeRulesResetService extends StatelessService {
    public static final String SELF_LINK = ServiceUriPaths.CORE + "/failsafe/rules/reset";

    public void handlePost(Operation op) {
        try {
            //load all the rules from failsafe.json in resources
            Gson gson = new Gson();
            BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/failsafe.json")));
            FailsafeService.Rule[] rules= gson.fromJson(br, FailsafeService.Rule[].class);
            Collection<Operation> rulesCollection = new ArrayList<>();
            for (int i=0 ; i< rules.length; i++){
                FailsafeService.Rule rule = rules[i];
                String url = FailsafeService.FACTORY_LINK + "/default-"+(i+1);
                //post the rule if it doesnot exist in rules map.
                if (FailsafeServiceHost.rules.containsKey(rule.prefix)) {
                    rulesCollection.add(Operation.createPut(getHost(), url)
                            .setBody(rule));
                } else {
                    rule.documentSelfLink = url;
                    rulesCollection.add(Operation.createPost(getHost(), FailsafeService.SELF_LINK)
                            .setBody(rule));
                }
            }

            OperationJoin.create(rulesCollection).setCompletion((o, e) -> {
                op.complete();
            }).sendWith(this);

        } catch (Exception ex ){
            op.fail(ex);
        }
    }
}
