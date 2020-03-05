/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort

import com.github.ajalt.clikt.core.MutuallyExclusiveGroupException

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.utils.ORT_NAME
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.redirectStdout
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec

import java.io.File

/**
 * A test for the main entry point of the application.
 */
class OrtMainTest : StringSpec() {
    private val projectDir = File("../analyzer/src/funTest/assets/projects/synthetic")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDir(ORT_NAME, javaClass.simpleName)
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "Activating only Gradle works" {
            val inputDir = File(projectDir, "gradle")

            val stdout = runMain(
                "analyze",
                "-m", "Gradle",
                "-i", inputDir.path,
                "-o", File(outputDir, "gradle").path
            )
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following package managers are activated:") break
            }

            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\tGradle"
        }

        "Activating only NPM works" {
            val inputDir = File(projectDir, "npm/package-lock")

            val stdout = runMain(
                "analyze",
                "-m", "NPM",
                "-i", inputDir.path,
                "-o", File(outputDir, "package-lock").path
            )
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following package managers are activated:") break
            }

            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\tNPM"
        }

        "Output formats are deduplicated" {
            val inputDir = File(projectDir, "gradle")

            val stdout = runMain(
                "analyze",
                "-m", "Gradle",
                "-i", inputDir.path,
                "-o", File(outputDir, "gradle").path,
                "-f", "json,yaml,json"
            )
            val lines = stdout.filter { it.startsWith("Writing analyzer result to ") }

            lines.count() shouldBe 2
        }

        "Analyzer creates correct output" {
            val analyzerOutputDir = File(outputDir, "merged-results")
            val expectedResult = patchExpectedResult(
                File(projectDir, "gradle-all-dependencies-expected-result.yml"),
                url = vcsUrl,
                revision = vcsRevision,
                urlProcessed = normalizeVcsUrl(vcsUrl)
            )

            runMain(
                "analyze",
                "-m", "Gradle",
                "-i", File(projectDir, "gradle").absolutePath,
                "-o", analyzerOutputDir.path
            )
            val analyzerResult = File(analyzerOutputDir, "analyzer-result.yml").readText()

            patchActualResult(analyzerResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "Package curation data file is applied correctly" {
            val analyzerOutputDir = File(outputDir, "curations")
            val expectedResult = patchExpectedResult(
                File(projectDir, "gradle-all-dependencies-expected-result-with-curations.yml"),
                url = vcsUrl,
                revision = vcsRevision,
                urlProcessed = normalizeVcsUrl(vcsUrl)
            )

            runMain(
                "analyze",
                "-m", "Gradle",
                "-i", File(projectDir, "gradle").absolutePath,
                "-o", analyzerOutputDir.path,
                "--package-curations-file", File(projectDir, "gradle/curations.yml").toString()
            )
            val analyzerResult = File(analyzerOutputDir, "analyzer-result.yml").readText()

            patchActualResult(analyzerResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "Passing mutually exclusive evaluator options fails" {
            shouldThrow<MutuallyExclusiveGroupException> {
                runMain(
                    "evaluate",
                    "-i", "build.gradle.kts",
                    "--rules-file", "build.gradle.kts",
                    "--rules-resource", "DUMMY"
                )
            }
        }

        "Requirements are listed correctly" {
            val stdout = runMain("requirements")
            val errorLogs = stdout.find { it.contains(" ERROR - ") }

            errorLogs shouldBe null
        }
    }

    private fun runMain(vararg args: String) = redirectStdout { OrtMain().parse(args.asList()) }.lineSequence()
}
