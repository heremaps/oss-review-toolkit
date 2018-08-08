/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.Gradle
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.vcs.Git
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.ExpensiveTag
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.forAll
import io.kotlintest.tables.headers
import io.kotlintest.tables.row
import io.kotlintest.tables.table

import java.io.File

class GradleTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/gradle")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private val isJava9OrAbove = System.getProperty("java.version").split('.').first().toInt() >= 9

    override fun afterSpec(description: Description, spec: Spec) {
        // Reset the Gradle version in the test project to clean up after the tests.
        Git.run(projectDir, "checkout", ".")
    }

    init {
        "Root project dependencies are detected correctly" {
            val packageFile = File(projectDir, "build.gradle")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "gradle-expected-output-root.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val config = AnalyzerConfiguration(false, false, false)
            val result = Gradle.create(config).resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe emptyList()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val packageFile = File(projectDir, "app/build.gradle")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "gradle-expected-output-app.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val config = AnalyzerConfiguration(false, false, false)
            val result = Gradle.create(config).resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe emptyList()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val packageFile = File(projectDir, "lib/build.gradle")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "gradle-expected-output-lib.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val config = AnalyzerConfiguration(false, false, false)
            val result = Gradle.create(config).resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe emptyList()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Unresolved dependencies are detected correctly" {
            val packageFile = File(projectDir, "lib-without-repo/build.gradle")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "gradle-expected-output-lib-without-repo.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val config = AnalyzerConfiguration(false, false, false)
            val result = Gradle.create(config).resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe emptyList()
            patchActualResult(yamlMapper.writeValueAsString(result)) shouldBe expectedResult
        }

        "Fails nicely for Gradle version < 2.14".config(enabled = false) {
            val packageFile = File(projectDir.parentFile, "gradle-unsupported-version/build.gradle")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "gradle-expected-output-unsupported-version.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val config = AnalyzerConfiguration(false, false, false)
            val result = Gradle.create(config).resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Is compatible with Gradle >= 2.14".config(tags = setOf(ExpensiveTag), enabled = false) {
            // See https://blog.gradle.org/java-9-support-update.
            val gradleVersionsThatSupportJava9 = arrayOf(
                    row("4.6", ""),
                    row("4.5.1", "-3.4"),
                    row("4.5", "-3.4"),
                    row("4.4.1", "-3.4"),
                    row("4.4", "-3.4"),
                    row("4.3.1", "-3.4"),
                    row("4.3", "-3.4"),
                    row("4.2.1", "-3.4")
            )

            val gradleVersionsThatDoNotSupportJava9 = arrayOf(
                    row("4.2", "-3.4"),
                    row("4.1", "-3.4"),
                    row("4.0.2", "-3.4"),
                    row("4.0.1", "-3.4"),
                    row("4.0", "-3.4"),
                    row("3.5.1", "-3.4"),
                    row("3.5", "-3.4"),
                    row("3.4.1", "-3.4"),
                    row("3.4", "-3.4"),
                    row("3.3", "-2.14"),
                    row("3.2.1", "-2.14"),
                    row("3.2", "-2.14"),
                    row("3.1", "-2.14"),
                    row("3.0", "-2.14"),
                    row("2.14.1", "-2.14"),
                    row("2.14", "-2.14")
            )

            val gradleVersions = if (isJava9OrAbove) {
                gradleVersionsThatSupportJava9
            } else {
                gradleVersionsThatSupportJava9 + gradleVersionsThatDoNotSupportJava9
            }

            val gradleVersionTable = table(headers("version", "resultsFileSuffix"), *gradleVersions)

            forAll(gradleVersionTable) { version, resultsFileSuffix ->
                gradleWrapper(version)

                val packageFile = File(projectDir, "app/build.gradle")
                val expectedResult = patchExpectedResult(
                        File(projectDir.parentFile, "gradle-expected-output-app$resultsFileSuffix.yml"),
                        url = normalizeVcsUrl(vcsUrl),
                        revision = vcsRevision
                )

                val config = AnalyzerConfiguration(false, false, false)
                val result = Gradle.create(config).resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

                result shouldNotBe null
                result!!.errors shouldBe emptyList()
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }
    }

    private fun gradleWrapper(version: String) {
        println("Installing Gradle wrapper version $version.")

        // When calling Windows batch files directly (without passing them to "cmd" as an argument), Windows requires
        // the absolute path to the batch file to be passed to the underlying ProcessBuilder for some reason.
        val wrapperAbsolutePath = File(projectDir, Gradle.wrapper).absolutePath

        ProcessCapture(projectDir, wrapperAbsolutePath, "wrapper", "--gradle-version", version, "--no-daemon")
                .requireSuccess()
    }
}
