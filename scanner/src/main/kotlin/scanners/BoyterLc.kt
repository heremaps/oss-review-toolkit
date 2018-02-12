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

package com.here.ort.scanner.scanners

import ch.frankel.slf4k.*

import com.here.ort.downloader.unpack
import com.here.ort.scanner.Main
import com.here.ort.scanner.ScanException
import com.here.ort.scanner.Scanner
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log

import okhttp3.Request
import okio.Okio

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection

object BoyterLc : Scanner() {
    const val VERSION = "1.1.1"

    override val scannerExe = if (OS.isWindows) "lc.exe" else "lc"
    override val resultFileExt = "json"

    override fun bootstrap(): File? {
        val url = if (OS.isWindows) {
            "https://github.com/boyter/lc/releases/download/v$VERSION/lc-$VERSION-x86_64-pc-windows.zip"
        } else {
            "https://github.com/boyter/lc/releases/download/v$VERSION/lc-$VERSION-x86_64-unknown-linux.zip"
        }

        log.info { "Downloading $this from '$url'... " }

        val request = Request.Builder().get().url(url).build()

        return OkHttpClientHelper.execute(Main.HTTP_CACHE_PATH, request).use { response ->
            val body = response.body()

            if (response.code() != HttpURLConnection.HTTP_OK || body == null) {
                throw IOException("Failed to download $this from $url.")
            }

            if (response.cacheResponse() != null) {
                log.info { "Retrieved $this from local cache." }
            }

            val scannerArchive = createTempFile(suffix = url.substringAfterLast("/"))
            Okio.buffer(Okio.sink(scannerArchive)).use { it.writeAll(body.source()) }

            val scannerDir = createTempDir()

            log.info { "Unpacking '$scannerArchive' to '$scannerDir'... " }
            scannerArchive.unpack(scannerDir)

            if (!OS.isWindows) {
                // The Linux version is distributed as a ZIP, but our ZIP unpacker seems to be unable to properly handle
                // Unix mode bits.
                File(scannerDir, scannerExe).setExecutable(true)
            }

            scannerDir
        }
    }

    override fun scanPath(path: File, resultsFile: File): Result {
        val process = ProcessCapture(
                File(scannerDir, scannerExe).absolutePath,
                "--confidence", "0.982", // Cut-off value to only get "Apache-2.0" (and not also "ECL-2.0") returned.
                "--format", "json",
                "--output", resultsFile.absolutePath,
                path.absolutePath
        )

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        with(process) {
            if (exitValue() == 0) {
                println("Stored $this results in '${resultsFile.absolutePath}'.")
                return getResult(resultsFile)
            } else {
                throw ScanException(failMessage)
            }
        }
    }

    override fun getResult(resultsFile: File): Result {
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        if (resultsFile.isFile && resultsFile.length() > 0) {
            val json = jsonMapper.readTree(resultsFile)
            json.forEach { file ->
                licenses.addAll(file["LicenseGuesses"].map { license ->
                    license["LicenseId"].asText()
                })
            }
        }

        return Result(licenses, errors)
    }
}
