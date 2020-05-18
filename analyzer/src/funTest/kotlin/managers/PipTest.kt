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

package org.ossreviewtoolkit.analyzer.managers

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.WordSpec

import java.io.File

class PipTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Python 2" should {
            "resolve setup.py dependencies correctly for spdx-tools-python" {
                val definitionFile = File(projectsDir, "external/spdx-tools-python/setup.py")

                val result = createPIP().resolveDependencies(listOf(definitionFile))[definitionFile]
                val expectedResult = File(projectsDir, "external/spdx-tools-python-expected-output.yml").readText()

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "resolve requirements.txt dependencies correctly for example-python-flask" {
                val definitionFile = File(projectsDir, "external/example-python-flask/requirements.txt")

                val result = createPIP().resolveDependencies(listOf(definitionFile))[definitionFile]
                val expectedResult = File(projectsDir, "external/example-python-flask-expected-output.yml").readText()

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "capture metadata from setup.py even if requirements.txt is present" {
                val definitionFile = File(projectsDir, "synthetic/pip/requirements.txt")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val expectedResult = patchExpectedResult(
                    File(projectsDir, "synthetic/pip-expected-output.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                val result = createPIP().resolveDependencies(listOf(definitionFile))[definitionFile]

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }

        "Python 3" should {
            "resolve dependencies correctly for a Django project" {
                val definitionFile = File(projectsDir, "synthetic/pip-python3/requirements.txt")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val result = createPIP().resolveDependencies(listOf(definitionFile))[definitionFile]
                val expectedResultFile = File(projectsDir, "synthetic/pip-python3-expected-output.yml")
                val expectedResult = patchExpectedResult(
                    expectedResultFile,
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }
    }

    private fun createPIP() =
        Pip("PIP", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
