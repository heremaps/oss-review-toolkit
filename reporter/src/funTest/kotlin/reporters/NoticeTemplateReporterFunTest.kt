/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.FileStorageConfiguration
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseCategory
import org.ossreviewtoolkit.model.licenses.LicenseConfiguration
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.LicenseFilenamePatterns.LICENSE_FILENAMES
import org.ossreviewtoolkit.utils.ORT_NAME

class NoticeTemplateReporterFunTest : WordSpec({
    "The default template" should {
        "generate the correct license notes" {
            val expectedText = File("src/funTest/assets/notice-template-reporter-expected-results").readText()

            val report = generateReport(ORT_RESULT)

            report shouldBe expectedText
        }

        "generate the correct license notes with archived license files" {
            val expectedText =
                File("src/funTest/assets/notice-template-reporter-expected-results-with-license-files").readText()

            val archiveDir = File("src/funTest/assets/archive")
            val config = OrtConfiguration(
                ScannerConfiguration(
                    archive = FileArchiverConfiguration(
                        patterns = LICENSE_FILENAMES,
                        storage = FileStorageConfiguration(
                            localFileStorage = LocalFileStorageConfiguration(
                                directory = archiveDir,
                                compression = false
                            )
                        )
                    )
                )
            )

            val report = generateReport(ORT_RESULT, config)

            report shouldBe expectedText
        }
    }

    "The summary template" should {
        "generate the correct license notes" {
            val expectedText = File("src/funTest/assets/notice-template-reporter-expected-results-summary").readText()

            val report = generateReport(ORT_RESULT, options = mapOf("template.id" to "summary"))

            report shouldBe expectedText
        }
    }
})

private fun generateReport(
    ortResult: OrtResult,
    config: OrtConfiguration = OrtConfiguration(),
    copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),
    options: Map<String, String> = emptyMap()
): String {
    val input = ReporterInput(
        ortResult,
        config,
        copyrightGarbage = copyrightGarbage,
        licenseConfiguration = createLicenseConfiguration()
    )

    val outputDir = createTempDirectory("$ORT_NAME-${NoticeTemplateReporterFunTest::class.simpleName}").toFile().apply {
        deleteOnExit()
    }

    return NoticeTemplateReporter().generateReport(input, outputDir, options).single().readText()
}

private fun createLicenseConfiguration(): LicenseConfiguration {
    val includeNoticeCategory = LicenseCategory("include-in-notice-file")
    val includeSourceCategory = LicenseCategory("include-source-code-offer-in-notice-file")
    val mitLicense = LicenseCategorization(
        SpdxSingleLicenseExpression.parse("MIT"), sortedSetOf(includeNoticeCategory.name)
    )
    val bsdLicense = LicenseCategorization(
        SpdxSingleLicenseExpression.parse("BSD-3-Clause"), sortedSetOf(includeSourceCategory.name)
    )
    return LicenseConfiguration(
        categories = listOf(includeNoticeCategory, includeSourceCategory),
        categorizations = listOf(mitLicense, bsdLicense)
    )
}
