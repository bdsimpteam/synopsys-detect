/**
 * synopsys-detect
 *
 * Copyright (c) 2019 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.detect.workflow.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.synopsys.integration.detect.tool.detector.DetectorToolResult;
import com.synopsys.integration.detect.workflow.codelocation.DetectCodeLocation;
import com.synopsys.integration.detect.workflow.event.Event;
import com.synopsys.integration.detect.workflow.event.EventSystem;
import com.synopsys.integration.detect.workflow.report.writer.InfoLogReportWriter;
import com.synopsys.integration.detect.workflow.report.writer.ReportWriter;
import com.synopsys.integration.detect.workflow.report.writer.TraceLogReportWriter;
import com.synopsys.integration.detector.base.DetectorEvaluation;
import com.synopsys.integration.detector.base.DetectorEvaluationTree;

public class ReportManager {
    // all entry points to reporting
    private final EventSystem eventSystem;

    // Summary, print collections or final groups or information.
    private final SearchSummaryReporter searchSummaryReporter;
    private final PreparationSummaryReporter preparationSummaryReporter;
    private final ExtractionSummaryReporter extractionSummaryReporter;
    private final ErrorSummaryReporter errorSummaryReporter;

    private final ReportWriter logWriter = new InfoLogReportWriter();
    private final ReportWriter traceLogWriter = new TraceLogReportWriter();
    private final ExtractionReporter extractionReporter;

    public static ReportManager createDefault(EventSystem eventSystem) {
        return new ReportManager(eventSystem, new PreparationSummaryReporter(), new ExtractionSummaryReporter(), new SearchSummaryReporter(), new ErrorSummaryReporter(), new ExtractionReporter());
    }

    public ReportManager(final EventSystem eventSystem,
        final PreparationSummaryReporter preparationSummaryReporter, final ExtractionSummaryReporter extractionSummaryReporter, final SearchSummaryReporter searchSummaryReporter, ErrorSummaryReporter errorSummaryReporter, ExtractionReporter extractionReporter) {
        this.eventSystem = eventSystem;
        this.preparationSummaryReporter = preparationSummaryReporter;
        this.extractionSummaryReporter = extractionSummaryReporter;
        this.searchSummaryReporter = searchSummaryReporter;
        this.errorSummaryReporter = errorSummaryReporter;
        this.extractionReporter = extractionReporter;

        eventSystem.registerListener(Event.SearchCompleted, event -> searchCompleted(event));
        eventSystem.registerListener(Event.PreparationsCompleted, event -> preparationsCompleted(event));
        eventSystem.registerListener(Event.DetectorsComplete, event -> bomToolsComplete(event));
        eventSystem.registerListener(Event.CodeLocationsCalculated, event -> codeLocationsCompleted(event.getCodeLocationNames()));

        eventSystem.registerListener(Event.ExtractionCount, event -> exractionCount(event));
        eventSystem.registerListener(Event.ExtractionStarted, event -> exractionStarted(event));
        eventSystem.registerListener(Event.ExtractionEnded, event -> exractionEnded(event));

    }

    // Reports
    public void searchCompleted(final DetectorEvaluationTree rootEvaluation) {
        searchSummaryReporter.print(logWriter, rootEvaluation);
        final DetailedSearchSummaryReporter detailedSearchSummaryReporter = new DetailedSearchSummaryReporter();
        detailedSearchSummaryReporter.print(traceLogWriter, rootEvaluation);
    }

    public void preparationsCompleted(final DetectorEvaluationTree detectorEvaluationTree) {
        preparationSummaryReporter.write(logWriter, detectorEvaluationTree);
    }

    public void exractionCount(final Integer count) {
        extractionReporter.setExtractionCount(count);
    }

    public void exractionStarted(final DetectorEvaluation detectorEvaluation) {
        extractionReporter.extractionStarted(logWriter, detectorEvaluation);
    }

    public void exractionEnded(final DetectorEvaluation detectorEvaluation) {
        extractionReporter.extractionEnded(logWriter, detectorEvaluation);
    }

    private DetectorToolResult detectorToolResult;

    public void bomToolsComplete(DetectorToolResult detectorToolResult) {
        this.detectorToolResult = detectorToolResult;
    }

    public void codeLocationsCompleted(final Map<DetectCodeLocation, String> codeLocationNameMap) {
        if (codeLocationNameMap.size() > 0 && detectorToolResult != null && detectorToolResult.rootDetectorEvaluationTree.isPresent()) {
            extractionSummaryReporter.writeSummary(logWriter, detectorToolResult.rootDetectorEvaluationTree.get(), detectorToolResult.codeLocationMap, codeLocationNameMap);
        } else {
            logWriter.writeLine("There were no extractions to be summarized - no code locations were generated or no detectors were evaluated.");
        }

    }

    public void printDetectorIssues() {
        if (detectorToolResult != null && detectorToolResult.rootDetectorEvaluationTree.isPresent()) {
            errorSummaryReporter.writeSummary(logWriter, detectorToolResult.rootDetectorEvaluationTree.get());
        } else {
            logWriter.writeLine("There were no detector issues to be summarized - detectors did not run or no detectors were evaluated.");
        }
    }
}
