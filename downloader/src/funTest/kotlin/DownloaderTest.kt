/*
 * Copyright (c) 2017 HERE Europe B.V.
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

package com.here.ort.downloader

import com.here.ort.model.Package

import io.kotlintest.Spec
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.contain
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class DownloaderTest : StringSpec() {

    private val outputDir = createTempDir()

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        spec()
        outputDir.deleteRecursively()
    }

    init {
        "Downloads gradle module by vcs path" {
            val submoduleName = "model"
            val pkg = Package(
                    packageManager = "Gradle",
                    namespace = "",
                    name = "project :model",
                    version = "",
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    downloadUrl = "",
                    hash = "",
                    hashAlgorithm = "",
                    vcsProvider = "Git",
                    vcsUrl = "git@github.com:heremaps/oss-review-toolkit.git",
                    vcsRevision = "",
                    vcsPath = submoduleName
                    )
            Main.download(pkg, outputDir)
            val downloadedProjectDir = File(outputDir, pkg.normalizedName)
            val downloadedModuleDir = File(downloadedProjectDir, submoduleName)

            outputDir.list().size shouldBe 1
            outputDir.list().first() shouldBe pkg.normalizedName

            downloadedProjectDir.isDirectory shouldBe true
            downloadedModuleDir.isDirectory shouldBe true

            downloadedModuleDir.list().size shouldBe beGreaterThan(1)

            val moduleDirList = downloadedModuleDir.list().asList()
            moduleDirList should contain("build.gradle")
            moduleDirList should contain("src")
        }.config(tags = setOf(Expensive))

        "Downloads github oss-review-toolkit repo " {
            val pkg = Package(
                    packageManager = "Gradle",
                    namespace = "",
                    name = "oss-review-toolkit",
                    version = "",
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    downloadUrl = "",
                    hash = "",
                    hashAlgorithm = "",
                    vcsProvider = "Git",
                    vcsUrl = "git@github.com:heremaps/oss-review-toolkit.git",
                    vcsRevision = "",
                    vcsPath = ""
                    )
            try {
                Main.download(pkg, outputDir)
            } catch (exception: DownloadException) {

            }

            val downloadDir = File(outputDir, pkg.normalizedName)
            val downloadedPkgDirList = downloadDir.list().asList()
            print("dir listing: $downloadedPkgDirList")
            outputDir.list().size shouldBe 1
            outputDir.list().first() shouldBe pkg.normalizedName
            downloadedPkgDirList should contain("build.gradle")
            downloadedPkgDirList should contain("downloader")
            downloadedPkgDirList should contain("scanner")
            downloadedPkgDirList should contain("model")
            downloadedPkgDirList should contain("analyzer")
            downloadedPkgDirList should contain("util")
            downloadedPkgDirList should contain("graph")
            downloadedPkgDirList should contain("docs")
        }.config(tags = setOf(Expensive))
    }
}
