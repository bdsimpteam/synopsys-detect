/**
 * detector
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
package com.synopsys.integration.detector.evaluation;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.detectable.Detectable;
import com.synopsys.integration.detectable.DetectableEnvironment;
import com.synopsys.integration.detectable.Extraction;
import com.synopsys.integration.detectable.ExtractionEnvironment;
import com.synopsys.integration.detectable.detectable.exception.DetectableException;
import com.synopsys.integration.detectable.detectable.result.DetectableResult;
import com.synopsys.integration.detectable.detectable.result.ExceptionDetectableResult;
import com.synopsys.integration.detector.base.DetectorEvaluation;
import com.synopsys.integration.detector.base.DetectorEvaluationTree;
import com.synopsys.integration.detector.result.DetectableDetectorResult;
import com.synopsys.integration.detector.result.DetectorResult;
import com.synopsys.integration.detector.rule.DetectorRule;

public class DetectorEvaluator {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final DetectorRuleSetEvaluator detectorRuleSetEvaluator = new DetectorRuleSetEvaluator();
    private DetectorEvaluatorListener detectorEvaluatorListener;

    private final DetectorEvaluationOptions evaluationOptions;

    public DetectorEvaluator(final DetectorEvaluationOptions evaluationOptions) {
        this.evaluationOptions = evaluationOptions;
    }

    //Unfortunately, currently search and applicable are tied together due to Search needing to know about previous detectors that applied.
    //So Search and then Applicable must be evaluated of Detector 1 before the next Search can be evaluated of Detector 2.
    public void searchAndApplicableEvaluation(final DetectorEvaluationTree detectorEvaluationTree, final Set<DetectorRule> appliedInParent) {
        logger.trace("Determining applicable detectors on the directory: " + detectorEvaluationTree.getDirectory().toString());

        final Set<DetectorRule> appliedSoFar = new HashSet<>();

        for (final DetectorEvaluation detectorEvaluation : detectorEvaluationTree.getOrderedEvaluations()) {
            getDetectorEvaluatorListener().ifPresent(it -> it.applicableStarted(detectorEvaluation));

            final DetectorRule detectorRule = detectorEvaluation.getDetectorRule();
            logger.trace("Evaluating detector: " + detectorRule.getDescriptiveName());
            final SearchEnvironment searchEnvironment = new SearchEnvironment(detectorEvaluationTree.getDepthFromRoot(), evaluationOptions.getDetectorFilter(), evaluationOptions.isForceNested(), appliedInParent, appliedSoFar);
            detectorEvaluation.setSearchEnvironment(searchEnvironment);
            final DetectorResult searchableResult = detectorRuleSetEvaluator.evaluateSearchable(detectorEvaluationTree.getDetectorRuleSet(), detectorEvaluation.getDetectorRule(), searchEnvironment);
            detectorEvaluation.setSearchable(searchableResult);

            if (detectorEvaluation.isSearchable()) {
                logger.trace("Searchable passed, will continue evaluating.");
                //TODO: potential todo, this could be invoked as part of the rule - ie we make a DetectableEnvironmentCreatable and the file could be given to the creatable (detectorRule.createEnvironment(file)
                final DetectableEnvironment detectableEnvironment = new DetectableEnvironment(detectorEvaluationTree.getDirectory());
                detectorEvaluation.setDetectableEnvironment(detectableEnvironment);

                final Detectable detectable = detectorRule.createDetectable(detectableEnvironment);
                detectorEvaluation.setDetectable(detectable);

                final DetectorResult applicableResult = new DetectableDetectorResult(detectable.applicable());
                detectorEvaluation.setApplicable(applicableResult);

                if (detectorEvaluation.isApplicable()) {
                    logger.trace("Found applicable detector: " + detectorRule.getDescriptiveName());
                    appliedSoFar.add(detectorRule);
                } else {
                    logger.trace("Applicable did not pass: " + detectorEvaluation.getApplicabilityMessage());
                }
            } else {
                logger.trace("Searchable did not pass: " + detectorEvaluation.getSearchabilityMessage());
            }

            getDetectorEvaluatorListener().ifPresent(it -> it.applicableEnded(detectorEvaluation));
        }

        if (appliedSoFar.size() > 0){
            logger.info("Found (" + appliedSoFar.size() + ") applicable detectors in: " + detectorEvaluationTree.getDirectory().toString()); //TODO: Perfect log level also matters here. To little and we may appear stuck, but we may also be flooding the logs.
        }

        final Set<DetectorRule> nextAppliedInParent = new HashSet<>();
        nextAppliedInParent.addAll(appliedInParent);
        nextAppliedInParent.addAll(appliedSoFar);

        for (final DetectorEvaluationTree childDetectorEvaluationTree : detectorEvaluationTree.getChildren()) {
            searchAndApplicableEvaluation(childDetectorEvaluationTree, nextAppliedInParent);
        }
    }

    public void extractableEvaluation(final DetectorEvaluationTree detectorEvaluationTree) {
        logger.trace("Determining extractable detectors in the directory: " + detectorEvaluationTree.getDirectory().toString());
        for (final DetectorEvaluation detectorEvaluation : detectorEvaluationTree.getOrderedEvaluations()) {
            if (detectorEvaluation.isSearchable() && detectorEvaluation.isApplicable()) {

                getDetectorEvaluatorListener().ifPresent(it -> it.extractableStarted(detectorEvaluation));

                logger.trace("Detector was searchable and applicable, will check extractable: " + detectorEvaluation.getDetectorRule().getDescriptiveName());
                final Detectable detectable = detectorEvaluation.getDetectable();
                DetectableResult detectableExtractableResult;
                try {
                    detectableExtractableResult = detectable.extractable();
                } catch (final DetectableException e) {
                    detectableExtractableResult = new ExceptionDetectableResult(e);
                }
                final DetectorResult extractableResult = new DetectableDetectorResult(detectableExtractableResult);
                detectorEvaluation.setExtractable(extractableResult);
                if (detectorEvaluation.isExtractable()) {
                    logger.trace("Extractable passed. Done evaluating for now.");
                } else {
                    logger.trace("Extractable did not pass: " + detectorEvaluation.getExtractabilityMessage());
                }

                getDetectorEvaluatorListener().ifPresent(it -> it.extractableEnded(detectorEvaluation));
            }
        }

        for (final DetectorEvaluationTree childDetectorEvaluationTree : detectorEvaluationTree.getChildren()) {
            extractableEvaluation(childDetectorEvaluationTree);
        }
    }

    public void extractionEvaluation(final DetectorEvaluationTree detectorEvaluationTree, final Function<DetectorEvaluation, ExtractionEnvironment> extractionEnvironmentProvider) {
        logger.trace("Extracting detectors in the directory: " + detectorEvaluationTree.getDirectory().toString());
        for (final DetectorEvaluation detectorEvaluation : detectorEvaluationTree.getOrderedEvaluations()) {
            if (detectorEvaluation.isSearchable() && detectorEvaluation.isApplicable() && detectorEvaluation.isExtractable()) {

                logger.trace("Detector was searchable, applicable and extractable, will perform extraction: " + detectorEvaluation.getDetectorRule().getDescriptiveName());
                final Detectable detectable = detectorEvaluation.getDetectable();
                final ExtractionEnvironment extractionEnvironment = extractionEnvironmentProvider.apply(detectorEvaluation);
                detectorEvaluation.setExtractionEnvironment(extractionEnvironment);

                getDetectorEvaluatorListener().ifPresent(it -> it.extractionStarted(detectorEvaluation));

                try {
                    final Extraction extraction = detectable.extract(extractionEnvironment);
                    detectorEvaluation.setExtraction(extraction);
                }catch (Exception e){
                    detectorEvaluation.setExtraction(new Extraction.Builder().exception(e).build());
                }

                getDetectorEvaluatorListener().ifPresent(it -> it.extractionEnded(detectorEvaluation));

                logger.trace("Extraction result: " + detectorEvaluation.wasExtractionSuccessful());
            }
        }

        for (final DetectorEvaluationTree childDetectorEvaluationTree : detectorEvaluationTree.getChildren()) {
            extractionEvaluation(childDetectorEvaluationTree, extractionEnvironmentProvider);
        }
    }

    public Optional<DetectorEvaluatorListener> getDetectorEvaluatorListener() {
        return Optional.ofNullable(detectorEvaluatorListener);
    }

    public void setDetectorEvaluatorListener(final DetectorEvaluatorListener detectorEvaluatorListener) {
        this.detectorEvaluatorListener = detectorEvaluatorListener;
    }
}
