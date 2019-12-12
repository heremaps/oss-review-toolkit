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

package com.here.ort.reporter.reporters

import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.utils.test.readOrtResult

import io.kotlintest.matchers.string.contain
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNot
import io.kotlintest.specs.WordSpec

import java.io.ByteArrayOutputStream
import java.io.File

private fun generateReport(
    ortResult: OrtResult,
    copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),
    preProcessingScript: String? = null
) =
    ByteArrayOutputStream().also { outputStream ->
        NoticeSummaryReporter().generateReport(
            outputStream,
            ortResult,
            copyrightGarbage = copyrightGarbage,
            preProcessingScript = preProcessingScript
        )
    }.toString("UTF-8")

class NoticeSummaryReporterTest : WordSpec({
    "NoticeReporter" should {
        "generate the correct license notes" {
            val expectedText = File("src/funTest/assets/NPM-is-windows-1.0.2-expected-NOTICE").readText()
            val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

            val report = generateReport(ortResult)

            report shouldBe expectedText
        }

        "contain all licenses without excludes" {
            val expectedText = File("src/funTest/assets/npm-test-without-exclude-expected-NOTICE").readText()
            val ortResult = readOrtResult("src/funTest/assets/npm-test-without-exclude-scan-results.yml")

            val report = generateReport(ortResult)

            report shouldBe expectedText
        }

        "not contain licenses of excluded packages" {
            val expectedText = File("src/funTest/assets/npm-test-with-exclude-expected-NOTICE").readText()
            val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

            val report = generateReport(ortResult)

            report shouldBe expectedText
        }

        "evaluate the provided pre-processing script" {
            val expectedText = File("src/funTest/assets/pre-processed-expected-NOTICE").readText()
            val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

            val preProcessingScript = """
                headers = listOf("Header 1\n", "Header 2\n")
                findings = model.findings.filter { (_, findings) ->
                    findings.all { it.value.isEmpty() }
                }.toSortedMap()
                footers = listOf("Footer 1\n", "Footer 2\n")
            """.trimIndent()

            val report = generateReport(ortResult, preProcessingScript = preProcessingScript)

            report shouldBe expectedText
        }

        "return the input as-is for an empty pre-processing script" {
            val expectedText =
                File("src/funTest/assets/NPM-is-windows-1.0.2-expected-NOTICE").readText()
            val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

            val report = generateReport(ortResult, preProcessingScript = "")

            report shouldBe expectedText
        }

        "contain a copyright statement if not contained in copyright garbage" {
            val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

            val report = generateReport(ortResult, CopyrightGarbage())

            report should contain("\nCopyright (c) Felix Bohm")
        }

        "contain a copyright statement if only its prefix is contained in copyright garbage" {
            val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

            val report = generateReport(ortResult, CopyrightGarbage("Copyright (c) Fel"))

            report should contain("\nCopyright (c) Felix Bohm")
        }

        "contain a copyright statement if only its super string contained in copyright garbage" {
            val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

            val report = generateReport(ortResult, CopyrightGarbage("Copyright (c) Felix BohmX"))

            report should contain("\nCopyright (c) Felix Bohm")
        }

        "not contain a copyright statement if it is contained in garbage" {
            val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

            val report = generateReport(ortResult, CopyrightGarbage("Copyright (c) Felix Bohm"))

            report shouldNot contain("\nCopyright (c) Felix Bohm")
        }
    }
})
