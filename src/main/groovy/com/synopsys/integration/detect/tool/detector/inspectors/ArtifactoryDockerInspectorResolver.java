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
package com.synopsys.integration.detect.tool.detector.inspectors;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.detect.exception.DetectUserFriendlyException;
import com.synopsys.integration.detect.workflow.ArtifactResolver;
import com.synopsys.integration.detect.workflow.ArtifactoryConstants;
import com.synopsys.integration.detect.workflow.file.AirGapManager;
import com.synopsys.integration.detect.workflow.file.DirectoryManager;
import com.synopsys.integration.detectable.detectable.exception.DetectableException;
import com.synopsys.integration.detectable.detectable.file.FileFinder;
import com.synopsys.integration.detectable.detectables.docker.DockerDetectableOptions;
import com.synopsys.integration.detectable.detectables.docker.DockerInspectorInfo;
import com.synopsys.integration.detectable.detectables.docker.DockerInspectorResolver;
import com.synopsys.integration.exception.IntegrationException;

public class ArtifactoryDockerInspectorResolver implements DockerInspectorResolver {
    private static final String IMAGE_INSPECTOR_FAMILY = "blackduck-imageinspector";
    private static final List<String> inspectorNames = Arrays.asList("ubuntu", "alpine", "centos");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final static String DOCKER_SHARED_DIRECTORY_NAME = "docker";

    private final DirectoryManager directoryManager;
    private final AirGapManager airGapManager;
    private final FileFinder fileFinder;
    private final ArtifactResolver artifactResolver;
    private final DockerDetectableOptions dockerDetectableOptions;

    private DockerInspectorInfo resolvedInfo;

    public ArtifactoryDockerInspectorResolver(final DirectoryManager directoryManager, final AirGapManager airGapManager, final FileFinder fileFinder, final ArtifactResolver artifactResolver,
        final DockerDetectableOptions dockerDetectableOptions) {
        this.directoryManager = directoryManager;
        this.airGapManager = airGapManager;
        this.fileFinder = fileFinder;
        this.artifactResolver = artifactResolver;
        this.dockerDetectableOptions = dockerDetectableOptions;
    }

    @Override
    public DockerInspectorInfo resolveDockerInspector() throws DetectableException {
        try {
            if (resolvedInfo == null) {
                resolvedInfo = install();
            }
            return resolvedInfo;
        } catch (final Exception e) {
            throw new DetectableException(e);
        }
    }

    private DockerInspectorInfo install() throws IntegrationException, IOException, DetectUserFriendlyException {
        final Optional<File> airGapDockerFolder = airGapManager.getDockerInspectorAirGapFile();
        final String providedJarPath = dockerDetectableOptions.getDockerInspectorPath();

        if (StringUtils.isNotBlank(providedJarPath)) {
            logger.info("Docker tool will attempt to use the provided docker inspector.");
            return findProvidedJar(providedJarPath);
        } else if (airGapDockerFolder.isPresent()) {
            logger.info("Docker tool will attempt to use the air gapped docker inspector.");
            final Optional<DockerInspectorInfo> airGapInspector = findAirGapInspector();
            return airGapInspector.orElse(null);
        } else {
            logger.info("Docker tool will attempt to download or find docker inspector.");
            return findOrDownloadJar();
        }
    }

    private Optional<DockerInspectorInfo> findAirGapInspector() {
        return getAirGapJar().map(dockerInspectorJar1 -> new DockerInspectorInfo(dockerInspectorJar1, getAirGapInspectorImageTarfiles()));
    }

    private List<File> getAirGapInspectorImageTarfiles() {
        final List<File> airGapInspectorImageTarfiles;
        airGapInspectorImageTarfiles = new ArrayList<>();
        final String dockerInspectorAirGapPath = airGapManager.getDockerInspectorAirGapPath();
        for (final String inspectorName : inspectorNames) {
            final File osImage = new File(dockerInspectorAirGapPath, IMAGE_INSPECTOR_FAMILY + "-" + inspectorName + ".tar");
            airGapInspectorImageTarfiles.add(osImage);
        }
        return airGapInspectorImageTarfiles;
    }

    private DockerInspectorInfo findProvidedJar(final String providedJarPath) {
        logger.debug("Checking for user-specified disk-resident docker inspector jar file");
        File providedJar = null;
        if (StringUtils.isNotBlank(providedJarPath)) {
            logger.debug(String.format("Using user-provided docker inspector jar path: %s", providedJarPath));
            final File providedJarCandidate = new File(providedJarPath);
            if (providedJarCandidate.isFile()) {
                logger.debug(String.format("Found user-specified jar: %s", providedJarCandidate.getAbsolutePath()));
                providedJar = providedJarCandidate;
            }
        }
        return new DockerInspectorInfo(providedJar);
    }

    private Optional<File> getAirGapJar() {
        final Optional<File> airGapDirPath = airGapManager.getDockerInspectorAirGapFile();
        if (!airGapDirPath.isPresent()) {
            return Optional.empty();
        }

        logger.debug(String.format("Checking for air gap docker inspector jar file in: %s", airGapDirPath));
        try {
            final List<File> possibleJars = fileFinder.findFiles(airGapDirPath.get(), "*.jar", 1);
            if (possibleJars == null || possibleJars.size() == 0) {
                logger.error("Unable to locate air gap jar.");
                return Optional.empty();
            } else {
                final File airGapJarFile = possibleJars.get(0);
                logger.info(String.format("Found air gap docker inspector: %s", airGapJarFile.getAbsolutePath()));
                return Optional.of(airGapJarFile);
            }
        } catch (final Exception e) {
            logger.debug(String.format("Did not find a docker inspector jar file in the airgap dir: %s", airGapDirPath));
            return Optional.empty();
        }
    }

    private DockerInspectorInfo findOrDownloadJar() throws IntegrationException, IOException, DetectUserFriendlyException {
        logger.info("Determining the location of the Docker inspector.");
        final String dockerVersion = dockerDetectableOptions.getDockerInspectorVersion();
        final Optional<String> location = artifactResolver.resolveArtifactLocation(ArtifactoryConstants.ARTIFACTORY_URL, ArtifactoryConstants.DOCKER_INSPECTOR_REPO, ArtifactoryConstants.DOCKER_INSPECTOR_PROPERTY, dockerVersion,
            ArtifactoryConstants.DOCKER_INSPECTOR_VERSION_OVERRIDE);
        if (location.isPresent()) {
            logger.info("Finding or downloading the docker inspector.");
            final File dockerDirectory = directoryManager.getPermanentDirectory(DOCKER_SHARED_DIRECTORY_NAME);
            logger.debug(String.format("Downloading docker inspector from '%s' to '%s'.", location.get(), dockerDirectory.getAbsolutePath()));
            final File jarFile = artifactResolver.downloadOrFindArtifact(dockerDirectory, location.get());
            logger.info("Found online docker inspector: " + jarFile.getAbsolutePath());
            return new DockerInspectorInfo(jarFile);
        } else {
            throw new DetectableException("Unable to find Docker version from artifactory.");
        }
    }
}
