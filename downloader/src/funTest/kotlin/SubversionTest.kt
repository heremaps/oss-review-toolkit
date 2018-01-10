/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.model.VcsInfo
import com.here.ort.utils.Expensive

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://svn.code.sf.net/p/sendmessage/code"
private const val REPO_REV = "115"
private const val REPO_PATH = "trunk"
private const val REPO_VERSION = "1.0.1"
private const val REPO_REV_FOR_VERSION = "30"
private const val REPO_PATH_FOR_VERSION = "src/resources"

class SubversionTest : StringSpec() {
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        try {
            super.interceptTestCase(context, test)
        } finally {
            outputDir.deleteRecursively()
        }
    }

    init {
        "Subversion can download a given revision" {
            val vcs = VcsInfo("Subversion", REPO_URL, REPO_REV, "")

            val workingTree = Subversion.download(vcs, "", outputDir)
            workingTree.getRevision() shouldBe REPO_REV

            val expectedFiles = listOf(
                    ".svn",
                    "branches",
                    "tags",
                    "trunk",
                    "wiki"
            )
            val actualFiles = workingTree.workingDir.list()
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(Expensive))

        "Subversion can download only a single path" {
            val vcs = VcsInfo("Subversion", REPO_URL, REPO_REV, REPO_PATH)

            val workingTree = Subversion.download(vcs, "", outputDir)
            workingTree.getRevision() shouldBe REPO_REV

            val expectedFiles = listOf(
                    "default.build",
                    "default.build.user.tmpl",
                    "SendMessage.sln",
                    "sktoolslib", // This is an external.
                    "src",
                    "tools",
                    "version.build.in",
                    "versioninfo.build"
            )
            val actualFiles = workingTree.workingDir.list()
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(Expensive))

        "Subversion can download based on a version" {
            val vcs = VcsInfo("Subversion", REPO_URL, "", "")

            val workingTree = Subversion.download(vcs, REPO_VERSION, outputDir)
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }.config(tags = setOf(Expensive))

        "Subversion can download only a single path based on a version" {
            val vcs = VcsInfo("Subversion", REPO_URL, "", REPO_PATH_FOR_VERSION)

            val workingTree = Subversion.download(vcs, REPO_VERSION, outputDir)
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION

            val expectedFiles = listOf(
                    "searchw.cur",
                    "searchw.ico",
                    "SendMessage.ico",
                    "windowmessages.xml"
            )
            val actualFiles = File(workingTree.workingDir, REPO_PATH_FOR_VERSION).list()
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(Expensive))
    }
}
