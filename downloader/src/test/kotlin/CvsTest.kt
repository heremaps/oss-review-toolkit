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

import com.here.ort.utils.unpack
import com.here.ort.utils.getUserConfigDirectory
import com.here.ort.utils.normalizePath
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class CvsTest : StringSpec() {
    private lateinit var zipContentDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        val zipFile = File("src/test/assets/tyrex-2018-01-29-cvs.zip")

        zipContentDir = createTempDir()

        println("Extracting '$zipFile' to '$zipContentDir'...")
        zipFile.unpack(zipContentDir)

        try {
            super.interceptSpec(context, spec)
        } finally {
            zipContentDir.safeDeleteRecursively()
        }
    }

    init {
        "Detected CVS version is not empty" {
            val version = Cvs.getVersion()
            println("CVS version $version detected.")
            version shouldNotBe ""
        }.config(enabled = Cvs.isInPath())

        "CVS detects non-working-trees" {
            Cvs.getWorkingTree(getUserConfigDirectory()).isValid() shouldBe false
        }.config(enabled = Cvs.isInPath())

        "CVS correctly detects URLs to remote repositories" {
            Cvs.isApplicableUrl(":pserver:anonymous@tyrex.cvs.sourceforge.net:/cvsroot/tyrex") shouldBe true
            Cvs.isApplicableUrl(":ext:jrandom@cvs.foobar.com:/usr/local/cvs") shouldBe true
            Cvs.isApplicableUrl("http://svn.code.sf.net/p/grepwin/code/") shouldBe false
        }.config(enabled = Cvs.isInPath())

        "Detected CVS working tree information is correct" {
            val workingTree = Cvs.getWorkingTree(zipContentDir)

            workingTree.getType() shouldBe "Cvs"
            workingTree.isValid() shouldBe true
            workingTree.getRemoteUrl() shouldBe ":pserver:anonymous@tyrex.cvs.sourceforge.net:/cvsroot/tyrex"
            workingTree.getRevision() shouldBe "8707a14c78c6e77ffc59e685360fa20071c1afb6"
            workingTree.getRootPath() shouldBe zipContentDir.path.normalizePath()
            workingTree.getPathToRoot(File(zipContentDir, "tomcat")) shouldBe "tomcat"
        }.config(enabled = false)

        "CVS correctly lists remote tags" {
            val expectedTags = listOf(
                    "A02",
                    "A03",
                    "A04",
                    "DEV0_9_3",
                    "DEV0_9_4",
                    "DEV_0_9_7",
                    "Exoffice",
                    "PROD_1_0_1",
                    "PROD_1_0_2",
                    "PROD_1_0_3",
                    "before_SourceForge",
                    "before_debug_changes"
            )

            val workingTree = Cvs.getWorkingTree(zipContentDir)
            workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
        }.config(enabled = false)
    }
}
