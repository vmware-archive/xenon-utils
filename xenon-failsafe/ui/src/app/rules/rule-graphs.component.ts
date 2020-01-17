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
    Input,
    ViewChild
} from '@angular/core';

import {Rule} from './rule';
import {RuleService, ServiceStatsEntry, ServiceStatsTimeSeries} from './rule.service';
import {Chart} from 'chart.js';
import * as _ from 'lodash';
import * as numeral from 'numeral';
import * as moment from 'moment';

@Component({
    selector: 'rule-graph',
    templateUrl: './rule-graphs.component.html',
    providers: [RuleService],
    styleUrls: ['./rule.component.css']
})

export class RuleGraphComponent implements OnInit {
    @Input('rule') rule: Rule;
    @ViewChild('canvas1') canvas1;
    @ViewChild('canvas2') canvas2;
    chart1: Chart;
    chart2: Chart;
    chartData: {[key: string]: any};
    stats: ServiceStatsEntry[];
    // We don't call the get method in the constructor
    constructor(public ruleService: RuleService) {}

    // Fetching the records in the onInit lifecycle method makes the application easier to debug
    ngOnInit() {
        this.ruleService.getRuleStats(this.rule)
            .subscribe(
                stats => { this.stats = stats; this.onStatsLoaded(); },
                error => { }
            );
    }

    onStatsLoaded() {
        // transform into chart datasets
        let bins: number[] = [];
        let dataSets: object = {0: [], 1: [], 2: []};
        let j: number[] = [0, 0];

        // construct bin index map
        for (let i = 0; i < this.stats.length; i++) {
            if (this.stats[i].timeSeriesStats !== undefined) {
                bins = _.union(bins, Object.keys(this.stats[i].timeSeriesStats.bins));
            }
        }

        // fill in missing bins
        bins = bins.sort();
        let min: number = Number(bins[0]);
        let max: number = Number(bins[bins.length - 1]);
        bins = _.invert(bins);
        let k: number = 0;
        for (let i: number = min; i <= max; i = ( i + 60000 ) ) {
            bins[i] = k++;
        }

        // get the stats dataset chart object and settings for all timeseries stats
        for (let i = 0; i < this.stats.length; i++) {
            if (this.stats[i].timeSeriesStats !== undefined) {
                let chartIndex: number = this.chartForDataset(this.stats[i].name);
                dataSets[chartIndex][j[chartIndex]++] = this.getChartDataset(
                    this.stats[i].timeSeriesStats,
                    this.labelForDataset(this.stats[i].name),
                    this.colorForDataset(this.stats[i].name),
                    bins
                );
            }
        }

        // convert timestamps to time labels
        let labels: string[] = this.getChartLabels(bins);

        this.chart1 = new Chart(this.canvas1.nativeElement.getContext("2d"), this.getChartOptions(dataSets[0], labels));
        this.chart2 = new Chart(this.canvas2.nativeElement.getContext("2d"), this.getChartOptions(dataSets[1], labels));
    }

    // determine which chart each stat goes to
    private chartForDataset(statLabel: string) {
        switch (statLabel) {
            case 'failCount':
                return 1;
            case 'circuitOpenCount':
                return 1;
            case 'remoteRequestCount':
                return 0;
            case 'sentRequestCount':
                return 0;
            case 'successCount':
                return 1;
            default:
                return 2; // no chart
        }
    }

    private colorForDataset(statLabel: string) {
        switch (statLabel) {
            case 'failCount':
                return '#E62700';
            case 'circuitOpenCount':
                return '#9B56BB';
            case 'remoteRequestCount':
                return '#318700';
            case 'sentRequestCount':
                return '#FF9C32';
            case 'successCount':
                return '#007CBB';
            default:
                return '#000000';
        }
    }

    private labelForDataset(statLabel: string) {
        switch (statLabel) {
            case 'failCount':
                return 'failure';
            case 'circuitOpenCount':
                return 'circuit open';
            case 'remoteRequestCount':
                return 'calls made';
            case 'sentRequestCount':
                return 'requests sent';
            case 'successCount':
                return 'success';
            default:
                return 'unknown';
         }
    }

    private getChartDataset(data: ServiceStatsTimeSeries, label: string, color: string, bins: number[]) {
        let rgbColor: string = this.convertHexToRgb(color);

        return {
            label: label,
            data: this.getChartData(data, bins),
            fill: false,
            lineTension: 0.1,
            borderColor: `rgba(${rgbColor}, 1)`,
            borderJoinStyle: 'round',
            borderCapStyle: 'butt',
            borderWidth: 2,
            pointRadius: 1,
            pointHitDetectionRadius : 10,
            pointBackgroundColor: `rgba(${rgbColor}, 1)`,
            pointBorderWidth: 1,
            pointHoverRadius: 4,
            pointHoverBackgroundColor: `rgba(${rgbColor}, 1)`,
            pointHoverBorderColor: `rgba(${rgbColor}, 1)`,
            pointHoverBorderWidth: 3
        }
    }

    /**
     * Returns options line charts.
     */
    private getChartOptions(dataSets: any[], labels: string[]): any {
        return {
            type: 'line',
            data: {
                labels: labels,
                datasets: dataSets
            },
            options: {
                scales: {
                    xAxes: [{
                        ticks: {
                            maxTicksLimit: 5,
                            maxRotation: 0
                        },
                        gridLines: {
                            display: false
                        }
                    }],
                    yAxes: [{
                        display: true,
                        ticks: {
                            beginAtZero: true
                        }
                    }]
                },
                tooltips: {
                    callbacks: {
                        label: (tooltip: any, d: any) => {
                            let dataIndex: number = tooltip.index;
                            let datasetIndex: number = tooltip.datasetIndex;
                            let formattedValue: string =
                                this.formatNumber(d.datasets[datasetIndex].data[dataIndex], false);

                            return `${formattedValue}`;
                        }
                    }
                }
            }
        };
    }

    /**
     * Format a number with commas as thousands separators.
     *
     * @param {number} n - an integer that represents disk space in bytes.
     * @param {boolean} usePrefix - whether to use metric prefix for the number. If true, 1000 will be 1.0k.
     */
    formatNumber(n: number, usePrefix: boolean = false): string {
        let numeralValue = numeral(n);

        if (n < 1000 || !usePrefix) {
            return numeralValue.format('0,0');
        }

        if (n >= 1000 && n < 10000) {
            return numeralValue.format('0.0a');
        }

        return '10k+';
    }

    private convertHexToRgb(colorInHex: string): string {
        // Expand shorthand form (e.g. "03F") to full form (e.g. "0033FF")
        let shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
        let hex = colorInHex.replace(shorthandRegex, function(m, r, g, b) {
            return r + r + g + g + b + b;
        });

        let rgb = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
        return rgb ? `${parseInt(rgb[1], 16)}, ${parseInt(rgb[2], 16)}, ${parseInt(rgb[3], 16)}` : '';
    }

    /**
     * Returns labels for the chart
     */
    private getChartLabels(bins: number[]): string[] {
        return _.map(_.sortBy(_.keys(bins), (timestamp: string) => {
                return +timestamp;
            }), (timestamp: string) => {
                // Don't show date when displaying per hour stats
                return this.getTimeStamp((+timestamp) * 1000,
                    true);
            });
    }

    /**
     * Returns data for the chart
     */
    private getChartData(timeSeriesStats: ServiceStatsTimeSeries, bins: number[]): number[] {
        if (_.isUndefined(timeSeriesStats)) {
            return [];
        }

        let returnData: number[] = [];

        Object.keys(bins).forEach(function(b) {
            returnData[bins[b]] = 0;
        });

        _.keys(timeSeriesStats.bins).forEach((timestamp: string) => {
            returnData[bins[+timestamp]] = timeSeriesStats.bins[timestamp].sum
        });

        return returnData;
    }

    getTimeStamp(timeInMicroseconds: number, timeOnly: boolean = false): string {
        if (timeInMicroseconds === 0) {
            return 'Never';
        }

        let format: string = timeOnly ? 'hh:mm A' : 'M/D/YY hh:mm A';
        return moment(timeInMicroseconds / 1000).local().format(format);
    }
}
