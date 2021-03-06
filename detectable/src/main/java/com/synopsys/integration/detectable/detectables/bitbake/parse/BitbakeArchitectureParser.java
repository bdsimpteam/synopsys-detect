/**
 * detectable
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
package com.synopsys.integration.detectable.detectables.bitbake.parse;

import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

public class BitbakeArchitectureParser {
    public Optional<String> architectureFromOutput(String standardOutput) {
        return Arrays.stream(standardOutput.split(System.lineSeparator()))
                   .map(this::architectureFromLine)
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .findFirst();
    }

    public Optional<String> architectureFromLine(final String line) {
        if (line.trim().startsWith("TARGET_SYS")){
            return Optional.of(StringUtils.substringBetween(line, "\"").trim());
        } else {
            return Optional.empty();
        }
    }
}
