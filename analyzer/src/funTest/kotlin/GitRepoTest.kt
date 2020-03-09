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

package com.here.ort.analyzer

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.vcs.GitRepo
import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType
import com.here.ort.model.yamlMapper
import com.here.ort.utils.Ci
import com.here.ort.utils.ORT_NAME
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://github.com/heremaps/oss-review-toolkit-test-data-git-repo"
private const val REPO_REV = "f00ec4cbb670b49a156fd95d29e8fd148d931ba9"
private const val REPO_MANIFEST = "manifest.xml"

class GitRepoTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeSpec(spec: Spec) {
        // Do not use the class name as a suffix here to shorten the path. Otherwise the path will get too long for
        // Windows to handle.
        outputDir = createTempDir(ORT_NAME)

        val vcs = VcsInfo(VcsType.GIT_REPO, REPO_URL, REPO_REV, path = REPO_MANIFEST)
        val pkg = Package.EMPTY.copy(vcsProcessed = vcs)

        GitRepo().download(pkg, outputDir)
    }

    override fun afterSpec(spec: Spec) {
        outputDir.safeDeleteRecursively()
    }

    init {
        "Analyzer correctly reports VcsInfo for git-repo projects".config(enabled = !Ci.isTravis) {
            val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).analyze(outputDir)
            val actualResult = yamlMapper.writeValueAsString(ortResult)
            val expectedResult = patchExpectedResult(
                File("src/funTest/assets/projects/external/git-repo-expected-output.yml"),
                revision = REPO_REV,
                path = outputDir.invariantSeparatorsPath
            )

            patchActualResult(actualResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "GitRepo correctly lists submodules".config(enabled = !Ci.isTravis) {
            val expectedSubmodules = listOf(
                "spdx-tools",
                "submodules",
                "submodules/commons-text",
                "submodules/test-data-npm",
                "submodules/test-data-npm/entities",
                "submodules/test-data-npm/long.js"
            ).associateWith { VersionControlSystem.getPathInfo(File(outputDir, it)) }

            val workingTree = GitRepo().getWorkingTree(outputDir)
            workingTree.getNested() shouldBe expectedSubmodules
        }
    }
}
