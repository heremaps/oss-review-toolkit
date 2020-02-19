/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package com.here.ort.utils.storage

import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.BufferedReader
import java.io.File

class XZCompressedLocalFileStorageTest : StringSpec() {
    private fun storage(block: (XZCompressedLocalFileStorage, File) -> Unit) {
        val directory = createTempDir(javaClass.simpleName)
        val storage = XZCompressedLocalFileStorage(directory)
        block(storage, directory)
        directory.safeDeleteRecursively()
    }

    init {
        "Can read written compressed data" {
            storage { storage, _ ->
                storage.write("new-file", "content".byteInputStream())

                val content = storage.read("new-file").bufferedReader().use(BufferedReader::readText)

                content shouldBe "content"
            }
        }

        "Can read existing uncompressed data" {
            storage { storage, directory ->
                val file = directory.resolve("existing-file")
                file.writeText("content")

                val content = storage.read("existing-file").bufferedReader().use(BufferedReader::readText)

                content shouldBe "content"
            }
        }
    }
}
