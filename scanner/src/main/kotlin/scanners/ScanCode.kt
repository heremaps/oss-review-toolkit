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
import ch.qos.logback.classic.Level

import com.here.ort.model.EMPTY_NODE
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerSpecification
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanException
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.log
import com.here.ort.utils.searchUpwardsForSubdirectory

import java.io.File
import java.time.Instant
import java.util.regex.Pattern

object ScanCode : LocalScanner() {
    private const val OUTPUT_FORMAT = "json-pp"
    private const val TIMEOUT = 300

    private val DEFAULT_OPTIONS = listOf(
            "--copyright",
            "--license",
            "--license-text",
            "--info",
            "--strip-root",
            "--timeout", TIMEOUT.toString(),
            "--processes", Math.max(1, Runtime.getRuntime().availableProcessors() - 1).toString()
    )

    private val OUTPUT_FORMAT_OPTION = if (OUTPUT_FORMAT.startsWith("json")) {
        "--$OUTPUT_FORMAT"
    } else {
        "--output-$OUTPUT_FORMAT"
    }

    // Note: The "(File: ...)" part in the patterns below is actually added by our own getResult() function.
    private val UNKNOWN_ERROR_REGEX = Pattern.compile(
            "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
                    "ERROR: Unknown error:\n.+\n(?<error>\\w+Error)(:|\n)(?<message>.*) \\(File: (?<file>.+)\\)",
            Pattern.DOTALL)

    private val TIMEOUT_ERROR_REGEX = Pattern.compile(
            "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
                    "ERROR: Processing interrupted: timeout after (?<timeout>\\d+) seconds. \\(File: (?<file>.+)\\)")

    override val scannerExe = if (OS.isWindows) "scancode.bat" else "scancode"
    override val resultFileExt = "json"

    override fun bootstrap(): File? {
        val gitRoot = File(".").searchUpwardsForSubdirectory(".git")
        val scancodeDir = File(gitRoot, "scanner/src/funTest/assets/scanners/scancode-toolkit")
        if (!scancodeDir.isDirectory) return null

        val configureExe = if (OS.isWindows) "configure.bat" else "configure"
        val configurePath = File(scancodeDir, configureExe)
        ProcessCapture(configurePath.absolutePath, "--clean").requireSuccess()
        ProcessCapture(configurePath.absolutePath).requireSuccess()

        return scancodeDir
    }

    override fun getConfiguration() = DEFAULT_OPTIONS.toMutableList().run {
        add(OUTPUT_FORMAT_OPTION)
        joinToString(" ")
    }

    override fun getVersion(executable: String) =
            getCommandVersion(scannerPath.absolutePath, transform = {
                // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181", so simply remove
                // the prefix.
                it.substringAfter("ScanCode version ")
            })

    override fun scanPath(path: File, resultsFile: File, provenance: Provenance, specification: ScannerSpecification)
            : ScanResult {
        val options = DEFAULT_OPTIONS.toMutableList()

        if (log.isEnabledFor(Level.DEBUG)) {
            options.add("--license-diag")
            options.add("--verbose")
        }

        val startTime = Instant.now()

        val process = ProcessCapture(
                scannerPath.absolutePath,
                *options.toTypedArray(),
                path.absolutePath,
                OUTPUT_FORMAT_OPTION,
                resultsFile.absolutePath
        )

        val endTime = Instant.now()

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        val result = getPlainResult(resultsFile)

        val hasOnlyMemoryErrors = mapUnknownErrors(result)
        val hasOnlyTimeoutErrors = mapTimeoutErrors(result)

        with(process) {
            if (isSuccess() || hasOnlyMemoryErrors || hasOnlyTimeoutErrors) {
                val summary = ScanSummary(startTime, endTime, result.fileCount, result.licenses, result.errors)
                return ScanResult(provenance, specification, summary, result.rawResult)
            } else {
                throw ScanException(failMessage)
            }
        }

        // TODO: convert json output to spdx
        // TODO: convert json output to html
        // TODO: Add results of license scan to YAML model
    }

    internal fun getPlainResult(resultsFile: File): Result {
        var fileCount = 0
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        val json = if (resultsFile.isFile && resultsFile.length() > 0) {
            jsonMapper.readTree(resultsFile).also {
                fileCount = it["files_count"].intValue()

                it["files"]?.forEach { file ->
                    file["licenses"]?.forEach { license ->
                        var name = license["spdx_license_key"].asText()
                        if (name.isNullOrBlank()) {
                            val key = license["key"].asText()
                            name = if (key == "unknown") "NOASSERTION" else "LicenseRef-$key"
                        }
                        licenses.add(name)
                    }

                    val path = file["path"].asText()
                    errors.addAll(file["scan_errors"].map { "${it.asText()} (File: $path)" })
                }
            }
        } else {
            EMPTY_NODE
        }

        return Result(fileCount, licenses, errors, json)
    }

    override fun getResult(resultsFile: File) =
            getPlainResult(resultsFile).also {
                mapUnknownErrors(it)
                mapTimeoutErrors(it)
            }

    /**
     * Map messages about unknown errors to a more compact form. Return true if solely memory errors occurred, return
     * false otherwise.
     */
    internal fun mapUnknownErrors(result: Result): Boolean {
        if (result.errors.isEmpty()) {
            return false
        }

        var onlyMemoryErrors = true

        val errors = result.errors.toSortedSet()
        result.errors.clear()

        errors.mapTo(result.errors) { fullError ->
            UNKNOWN_ERROR_REGEX.matcher(fullError).let { matcher ->
                if (matcher.matches()) {
                    val file = matcher.group("file")
                    val error = matcher.group("error")
                    if (error == "MemoryError") {
                        "ERROR: MemoryError while scanning file '$file'."
                    } else {
                        onlyMemoryErrors = false
                        val message = matcher.group("message").trim()
                        "ERROR: $error while scanning file '$file' ($message)."
                    }
                } else {
                    onlyMemoryErrors = false
                    fullError
                }
            }
        }

        return onlyMemoryErrors
    }

    /**
     * Map messages about timeout errors to a more compact form. Return true if solely timeout errors occurred, return
     * false otherwise.
     */
    internal fun mapTimeoutErrors(result: Result): Boolean {
        if (result.errors.isEmpty()) {
            return false
        }

        var onlyTimeoutErrors = true

        val errors = result.errors.toSortedSet()
        result.errors.clear()

        errors.mapTo(result.errors) { fullError ->
            TIMEOUT_ERROR_REGEX.matcher(fullError).let { matcher ->
                if (matcher.matches() && matcher.group("timeout") == TIMEOUT.toString()) {
                    val file = matcher.group("file")
                    "ERROR: Timeout after $TIMEOUT seconds while scanning file '$file'."
                } else {
                    onlyTimeoutErrors = false
                    fullError
                }
            }
        }

        return onlyTimeoutErrors
    }
}
