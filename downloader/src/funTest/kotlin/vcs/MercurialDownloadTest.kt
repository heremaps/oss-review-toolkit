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

package com.here.ort.downloader.vcs

import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType
import com.here.ort.utils.ORT_NAME
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.ExpensiveTag

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val PKG_VERSION = "1.1"

private const val REPO_URL = "https://bitbucket.org/creaceed/mercurial-xcode-plugin"
private const val REPO_REV = "02098fc8bdaca4739ec52cbcb8ed51654eacee25"
private const val REPO_PATH = "Classes"

private const val REPO_REV_FOR_VERSION = "562fed42b4f3dceaacf6f1051963c865c0241e28"
private const val REPO_PATH_FOR_VERSION = "Resources"

class MercurialDownloadTest : StringSpec() {
    private val hg = Mercurial()
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDir(ORT_NAME, javaClass.simpleName)
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "Mercurial can download a given revision".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.MERCURIAL, REPO_URL, REPO_REV))
            val expectedFiles = listOf(
                ".hg",
                ".hgsub",
                ".hgsubstate",
                "Classes",
                "LICENCE.md",
                "MercurialPlugin.xcodeproj",
                "README.md",
                "Resources",
                "Script"
            )

            val workingTree = hg.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Mercurial can download only a single path"
            .config(enabled = hg.isAtLeastVersion("4.3"), tags = setOf(ExpensiveTag)) {
                val pkg = Package.EMPTY.copy(
                    vcsProcessed = VcsInfo(VcsType.MERCURIAL, REPO_URL, REPO_REV, path = REPO_PATH)
                )
                val expectedFiles = listOf(
                    File(".hgsub"), // We always get these configuration files, if present.
                    File(".hgsubstate"),
                    File(REPO_PATH, "MercurialPlugin.h"),
                    File(REPO_PATH, "MercurialPlugin.m"),
                    File("LICENCE.md"),
                    File("README.md"),
                    File("Script", "README"), // As a submodule, "Script" is always included.
                    File("Script", "git.py"),
                    File("Script", "gpl-2.0.txt"),
                    File("Script", "install_bridge.sh"),
                    File("Script", "sniff.py"),
                    File("Script", "uninstall_bridge.sh")
                )

                val workingTree = hg.download(pkg, outputDir)
                val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".hg" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }
                    .toList()

                workingTree.isValid() shouldBe true
                workingTree.getRevision() shouldBe REPO_REV
                actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
            }

        "Mercurial can download based on a version".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$PKG_VERSION"),

                // Use a non-blank dummy revision to enforce multiple revision candidates being tried.
                vcsProcessed = VcsInfo(VcsType.MERCURIAL, REPO_URL, "dummy")
            )

            val workingTree = hg.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }

        "Mercurial can download only a single path based on a version"
            .config(enabled = hg.isAtLeastVersion("4.3"), tags = setOf(ExpensiveTag)) {
                val pkg = Package.EMPTY.copy(
                    id = Identifier("Test:::$PKG_VERSION"),

                    // Use a non-blank dummy revision to enforce multiple revision candidates being tried.
                    vcsProcessed = VcsInfo(VcsType.MERCURIAL, REPO_URL, "dummy", path = REPO_PATH_FOR_VERSION)
                )
                val expectedFiles = listOf(
                    File(".hgsub"), // We always get these configuration files, if present.
                    File(".hgsubstate"),
                    File("LICENCE.md"),
                    File("README.md"),
                    File(REPO_PATH_FOR_VERSION, "Info.plist"),
                    File(REPO_PATH_FOR_VERSION, "icon.icns"),
                    File(REPO_PATH_FOR_VERSION, "icon_blank.icns"),
                    File("Script", "README"), // As a submodule, "Script" is always included.
                    File("Script", "git.py"),
                    File("Script", "gpl-2.0.txt"),
                    File("Script", "install_bridge.sh"),
                    File("Script", "sniff.py"),
                    File("Script", "uninstall_bridge.sh")
                )

                val workingTree = hg.download(pkg, outputDir)
                val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".hg" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }
                    .toList()

                workingTree.isValid() shouldBe true
                workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
                actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
            }
    }
}
