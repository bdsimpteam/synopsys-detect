/**
 * detectable
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
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
package com.synopsys.integration.detectable.detectables.clang.packagemanager;

import java.io.File;
import java.util.Set;

public class PackageDetailsResult {
    private final Set<PackageDetails> foundPackages;
    private final Set<File> unmanagedDependencies;

    public PackageDetailsResult(final Set<PackageDetails> foundPackages, final Set<File> unmanagedDependencies) {
        this.foundPackages = foundPackages;
        this.unmanagedDependencies = unmanagedDependencies;
    }

    public Set<File> getUnmanagedDependencies() {
        return unmanagedDependencies;
    }

    public Set<PackageDetails> getFoundPackages() {
        return foundPackages;
    }
}