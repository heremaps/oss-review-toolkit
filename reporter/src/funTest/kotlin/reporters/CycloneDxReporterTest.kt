/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.cyclonedx.BomParser
import org.cyclonedx.model.ExternalReference

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class CycloneDxReporterTest : WordSpec({
    "Generated BOMs" should {
        "be valid" {
            val outputDir = createTempDir(ORT_NAME, javaClass.simpleName).apply { deleteOnExit() }
            val bomFiles = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir)

            bomFiles.size shouldBe 2
            bomFiles.forAll { bomFile ->
                BomParser().validate(bomFile) should beEmpty()
            }
        }

        "match the result from the official Gradle plugin" {
            val ortResultFile = File("src/funTest/assets/gradle-all-dependencies-result.yml")
            val ortResult = yamlMapper.readValue<OrtResult>(
                patchExpectedResult(
                    ortResultFile,
                    url = "https://github.com/oss-review-toolkit/ort.git",
                    urlProcessed = "https://github.com/oss-review-toolkit/ort.git",
                    revision = "9fded2ad79d07ab5cda44f2549301669ea10442a"
                )
            )

            val outputDir = createTempDir(ORT_NAME, javaClass.simpleName).apply { deleteOnExit() }
            val bomFileFromReporter = CycloneDxReporter().generateReport(ReporterInput(ortResult), outputDir).last()
            val bomFromReporter = BomParser().parse(bomFileFromReporter).apply { components.sortBy { it.name } }

            // The file generated by the official Gradle plugin was modified in the following aspect to be comparable:
            // - all hashes except SHA-1 were removed (because the ORT analyzer does not calculate missing hashes)
            // - all qualifiers from the purl were removed (because the ORT analyzer does not currently handle package
            //   manager specific qualifiers)
            val bomFileFromPlugin = File("src/funTest/assets/gradle-all-dependencies-expected-cyclonedx-bom.xml")
            val bomFromPlugin = BomParser().parse(bomFileFromPlugin).apply { components.sortBy { it.name } }

            // TODO: Remove this once the official Gradle plugin supports getting a component's licenses, see
            //       https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/16.
            bomFromReporter.components.forEach { it.licenseChoice = null }

            // TODO: Remove this once the official Gradle plugin can get all descriptions.
            val componentIteratorForReporter = bomFromReporter.components.iterator()
            val componentIteratorForPlugin = bomFromPlugin.components.iterator()
            while (componentIteratorForReporter.hasNext() && componentIteratorForPlugin.hasNext()) {
                val componentFromReporter = componentIteratorForReporter.next()
                val componentFromPlugin = componentIteratorForPlugin.next()
                if (componentFromPlugin.description == null) {
                    // The official Gradle plugin does not seem to be able to get all descriptions that the ORT analyzer
                    // gets, so clear out ORT's one if the plugin has none, but still compare them if both are present.
                    componentFromReporter.description = null
                } else {
                    componentFromPlugin.description = escapeXml(componentFromPlugin.description)
                }
            }

            // TODO: Remove this once the official Gradle plugin adds project information as external references.
            bomFromPlugin.addExternalReference(ExternalReference().apply {
                type = ExternalReference.Type.VCS
                url = ortResult.repository.vcsProcessed.url
                comment = "URL to the project's Git repository"
            })

            // Clear out the unique serial numbers for comparison.
            bomFromReporter.serialNumber = null
            bomFromPlugin.serialNumber = null

            yamlMapper.writeValueAsString(bomFromReporter) shouldBe yamlMapper.writeValueAsString(bomFromPlugin)
        }
    }
})
