/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.helper.CommandWithHelp
import com.here.ort.helper.common.minimize
import com.here.ort.helper.common.replaceScopeExcludes
import com.here.ort.helper.common.sortScopeExcludes
import com.here.ort.helper.common.writeAsYaml
import com.here.ort.model.OrtResult
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.ScopeExclude
import com.here.ort.model.config.ScopeExcludeReason
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(
    commandNames = ["generate-scope-excludes"],
    commandDescription = "Generate scope excludes based on common default for the package managers. " +
        "The output is written to the given repository configuration file."
)
internal class GenerateScopeExcludesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The input ORT file from which the rule violations are read."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = true,
        order = PARAMETER_ORDER_OPTIONAL,
        description = "Override the repository configuration contained in the given input ORT file."
    )
    private lateinit var repositoryConfigurationFile: File

    override fun runCommand(jc: JCommander): Int {
        val ortResult = ortResultFile.readValue<OrtResult>()
        val scopeExcludes = ortResult.generateScopeExcludes()

        repositoryConfigurationFile
            .readValue<RepositoryConfiguration>()
            .replaceScopeExcludes(scopeExcludes)
            .sortScopeExcludes()
            .writeAsYaml(repositoryConfigurationFile)

        return 0
    }
}

private fun OrtResult.generateScopeExcludes(): List<ScopeExclude> {
    val projectScopes = getProjects().flatMap { project ->
        project.scopes.map { it.name }
    }

    return getProjects().flatMap { project ->
        getScopeExcludesForPackageManager(project.id.type)
    }.minimize(projectScopes)
}

private fun getScopeExcludesForPackageManager(packageManagerName: String): List<ScopeExclude> =
    when (packageManagerName) {
        "Bower" -> listOf(
            ScopeExclude(
                pattern = "devDependencies",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Development dependencies."
            )
        )
        "Bundler" -> listOf(
            ScopeExclude(
                pattern = "test",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Test dependencies."
            )
        )
        "Cargo" -> listOf(
            ScopeExclude(
                pattern = "build-dependencies",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Build dependencies."
            ),
            ScopeExclude(
                pattern = "dev-dependencies",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Development dependencies."
            )
        )
        "GoMod" -> listOf(
            ScopeExclude(
                pattern = "all",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Dependencies used to build all targets including non-released artifacts like tests."
            )
        )
        "Gradle" -> listOf(
            ScopeExclude(
                pattern = "checkstyle",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Checkstyle dependencies."
            ),
            ScopeExclude(
                pattern = "detekt",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Detekt dependencies."
            ),
            ScopeExclude(
                pattern = "findbugs",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Findbugs dependencies."
            ),
            ScopeExclude(
                pattern = "jacocoAgent",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Test dependencies."
            ),
            ScopeExclude(
                pattern = "jacocoAnt",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Test dependencies."
            ),
            ScopeExclude(
                pattern = "kapt.*",
                reason = ScopeExcludeReason.PROVIDED_DEPENDENCY_OF,
                comment = "Annotation processing dependencies."
            ),
            ScopeExclude(
                pattern = "lintClassPath",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Linter dependencies."
            ),
            ScopeExclude(
                pattern = "test.*",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Test dependencies."
            ),
            ScopeExclude(
                pattern = ".*Test.*",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Test dependencies."
            )
        )
        "Maven" -> listOf(
            ScopeExclude(
                pattern = "provided",
                reason = ScopeExcludeReason.PROVIDED_DEPENDENCY_OF,
                comment = "Dependencies provided by the user."
            ),
            ScopeExclude(
                pattern = "test",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Test dependencies."
            )
        )
        "NPM" -> listOf(
            ScopeExclude(
                pattern = "devDependencies",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Development dependencies."
            )
        )
        "PhpComposer" -> listOf(
            ScopeExclude(
                pattern = "require-dev",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Development dependencies."
            )
        )
        "SBT" -> listOf(
            ScopeExclude(
                pattern = "provided",
                reason = ScopeExcludeReason.PROVIDED_DEPENDENCY_OF,
                comment = "Dependencies provided at runtime."
            ),
            ScopeExclude(
                pattern = "test",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Test dependencies."
            )
        )
        "Stack" -> listOf(
            ScopeExclude(
                pattern = "bench",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Benchmark dependencies."
            ),
            ScopeExclude(
                pattern = "test",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Test dependencies."
            )
        )
        "Yarn" -> listOf(
            ScopeExclude(
                pattern = "devDependencies",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Development dependencies."
            )
        )
        else -> emptyList()
    }
