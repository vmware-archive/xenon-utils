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

import {
    Component,
    OnInit,
    ContentChildren,
    QueryList,
    Directive,
    Input
} from '@angular/core';

import {Rule} from './rule';
import {RuleService} from './rule.service';

@Component({
    selector: 'rules',
    templateUrl: './rule.component.html',
    providers: [RuleService],
    styleUrls: ['./rule.component.css']
})

export class RuleComponent implements OnInit {
    errorMessage: string;
    infoMessage: string;
    activeRule: Rule; // for shared dialogs
    backupRule: Rule; // for cancel edit
    rules: Rule[];

    // We don't call the get method in the constructor
    constructor(public ruleService: RuleService) {}

    // new rules created in the UI have these settings by default
    ruleTemplate(prefix: String): Rule {
        return new Rule(
                    prefix, // prefix
                    false,   // retry enabled
                    3,      // retry count
                    30000,  // retry max ms
                    100,    // retry backoff
                    250,    // retry exp backoff
                    5000,   // retry exp backoff max
                    100,    // retry jitter
                    false,   // breaker enabled
                    5,      // breaker fails
                    250,    // breaker open time
                    5,      // breaker success
                    30000,  // breaker timeout
                    null,   // self link
                    0       // circuitresettimestamp
                )
    }

    // Fetching the records in the onInit lifecycle method makes the application easier to debug
    ngOnInit() {
        this.activeRule = this.ruleTemplate('');
        this.getRules();
    }

    resetMessages() {
        this.infoMessage = null;
        this.errorMessage = null;
    }

    getRules() {
        this.resetMessages();
        this.ruleService.getRules()
            .subscribe(
                rules => this.rules = rules,
                error => this.errorMessage = <any>error
            )
    }

    createRule(prefix: String) {
        this.resetMessages();
        if (!prefix) {
            this.errorMessage = "API Prefix is required";
            return;
        }
        this.ruleService
            .addRule(this.ruleTemplate(prefix))
            .subscribe(
                newRule => { this.infoMessage = "Rule added"; this.rules = [newRule, ...this.rules] },
                error => this.errorMessage = <any>error
            )
    }

    updateRule(rule: Rule) {
        this.resetMessages();
        this.ruleService
            .updateRule(rule)
            .subscribe(
                updatedRule => {
                    this.infoMessage = "Rule updated";
                    this.rules = [updatedRule, ...this.rules.filter(
                        r => ! (r.documentSelfLink === updatedRule.documentSelfLink)
                )]},
                error => {
                    this.cancelRuleUpdate();
                    this.errorMessage = <any>error
                }
            )
    }

    deleteRule(rule: Rule) {
        this.resetMessages();
        this.ruleService
            .deleteRule(rule)
            .subscribe(
                deletedRule => this.rules = this.rules.filter(
                    r => {
                        this.infoMessage = "Rule deleted";
                        return ! (r.documentSelfLink === deletedRule.documentSelfLink);
                    }
                ),
                error => this.errorMessage = <any>error
            )
    }

    setActive(rule: Rule) {
        this.activeRule = rule;
        this.backupRule = Object.assign({} , this.activeRule);
    }

    cancelRuleUpdate() {
        for (const k of Object.keys(this.backupRule)) {
            this.activeRule[k] = this.backupRule[k];
        }
    }

    resetRule(rule: Rule) {
        this.resetMessages();
        this.ruleService.resetRuleState(rule)
            .subscribe(
                resetRule => {
                    this.infoMessage = "State set to close";
                    },
                error => {
                    this.errorMessage = <any>error
                }
            )
    }

    resetAllRules() {
        this.resetMessages();
        this.errorMessage = "unimplemented";
        // TODO - reset the rule circuit breaker
    }
}
