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

package com.here.ort.downloader

import com.here.ort.downloader.vcs.Mercurial

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class MercurialTest : StringSpec() {
    private lateinit var zipContentDir: File

    override val oneInstancePerTest = false

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        val zipFile = Paths.get("src/test/assets/lz4revlog-2018-01-03-hg.zip")

        zipContentDir = createTempDir()

        println("Extracting '$zipFile' to '$zipContentDir'...")

        FileSystems.newFileSystem(zipFile, null).use { zip ->
            zip.rootDirectories.forEach { root ->
                Files.walk(root).forEach { file ->
                    Files.copy(file, Paths.get(zipContentDir.toString(), file.toString()),
                            StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        try {
            spec()
        } finally {
            zipContentDir.deleteRecursively()
        }
    }

    init {
        "Mercurial correctly lists remote tags" {
            val expectedTags = listOf("1.0.2", "1.0.1", "1.0")

            val workingTree = Mercurial.getWorkingTree(zipContentDir)
            workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
        }
    }
}
