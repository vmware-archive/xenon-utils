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

import { Injectable } from '@angular/core';
import { Inject } from '@angular/core';
import { Http, Response } from '@angular/http';
import { Headers, RequestOptions } from '@angular/http';
import { DOCUMENT } from '@angular/common';
import { Observable } from 'rxjs/Observable';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';

import { Rule } from './rule';

export interface ServiceStatsTimeSeries {
    bins: {[key: string]: any};
}

export interface ServiceStats {
    kind: string;
    entries: { [key: string]: ServiceStatsEntry | any };
}

export interface ServiceStatsEntry {
        name: string;
        latestValue: number;
        accumulatedValue: number;
        version: number;
        lastUpdateMicrosUtc: number;
        kind: string;
        unit?: string;
        sourceTimeMicrosUtc?: number;
        serviceReference?: string;
        logHistogram?: any;
        timeSeriesStats?: ServiceStatsTimeSeries;
}

@Injectable()
export class RuleService {
    public baseUrl: string = '';
    public failsafeFactory: string = '/core/failsafe';
    public failsafeResetState: string = '/core/failsafe/circuit/reset';

    constructor(private http: Http, @Inject(DOCUMENT) private document: any) {
        let parser = document.createElement('a');
        parser.href = this.document.location.href;
        this.baseUrl = parser.protocol + '//' + parser.hostname + ':' + parser.port;
        // for development, enable CORS in FailsafeServiceHost also
        // this.baseUrl = 'http://localhost:8000'
    }

    private getHeaders() {
        let headers = new Headers();
        headers.append('Accept', 'application/json');
        return headers;
    }

    private getOptions() {
        let headers = this.getHeaders();
        return new RequestOptions({ headers });
    }

    getRules (): Observable<Rule[]> {
        return this.http.get(this.baseUrl + this.failsafeFactory + '?expand')
            .map(this.parseRules)
            .catch(this.handleError);
    }

    getRuleStats (rule: Rule): Observable<ServiceStatsEntry[]> {
        return this.http.get(this.baseUrl + rule.documentSelfLink + '/stats')
            .map(this.parseStats)
            .catch(this.handleError);
    }

    addRule (rule: Rule): Observable<Rule> {
        return this.http.post(this.baseUrl + this.failsafeFactory, rule, this.getOptions())
            .map(this.parseRule)
            .catch(this.handleError);
    }

    updateRule (rule: Rule): Observable<Rule> {
        return this.http.put(this.baseUrl + rule.documentSelfLink, rule, this.getOptions())
            .map(this.parseRule)
            .catch(this.handleError);
    }

    deleteRule (rule: Rule): Observable<Rule> {
        return this.http.delete(this.baseUrl + rule.documentSelfLink, this.getOptions())
            .map(this.parseRule)
            .catch(this.handleError);
    }

    resetRuleState (rule: Rule): Observable<Rule> {
        rule.circuitCloseTimestamp = Date.now()
        return this.http.put(this.baseUrl + rule.documentSelfLink, rule, this.getOptions())
            .map(this.parseRule)
            .catch(this.handleError);
    }

    private parseRules(res: Response)  {
        let docs = res.json().documents;
        return Object.keys(docs).map(function(id){
            return docs[id]
        });
    }

    private parseStats(res: Response)  {
        let stats = res.json().entries;
        return Object.keys(stats).map(function(id){
            return stats[id]
        });
    }

    private parseRule(res: Response)  {
        return res.json() || [];
    }

    // Displays the error message
    private handleError(error: Response | any) {
        let errorMessage: string;
        errorMessage = error.message ? error.message : error.toString();

        // This returns another Observable for the observer to subscribe to
        return Observable.throw(errorMessage);
    }
}
