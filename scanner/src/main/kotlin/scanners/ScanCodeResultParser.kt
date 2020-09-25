/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.scanner.scanners

import com.fasterxml.jackson.databind.JsonNode

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.calculatePackageVerificationCode
import org.ossreviewtoolkit.utils.textValueOrEmpty

private val UNKNOWN_LICENSE_KEYS = listOf(
    "free-unknown",
    "unknown",
    "unknown-license-reference"
)

/**
 * Parse a [resultsFile] from ScanCode to a JSON node, which can then be further processed.
 */
internal fun parseScanCodeResult(resultsFile: File): JsonNode =
    if (resultsFile.isFile && resultsFile.length() > 0L) {
        jsonMapper.readTree(resultsFile)
    } else {
        EMPTY_JSON_NODE
    }

/**
 * Generate a summary from the given raw ScanCode [result], using [startTime] and [endTime] metadata.
 * From the [scanPath] the package verification code is generated.
 */
internal fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: JsonNode) =
    generateSummary(
        startTime,
        endTime,
        calculatePackageVerificationCode(scanPath),
        result
    )

/**
 * Generate a summary from the given raw ScanCode [result], using [startTime], [endTime], and [verificationCode]
 * metadata. This variant can be used if the result is not read from a local file.
 */
internal fun generateSummary(startTime: Instant, endTime: Instant, verificationCode: String, result: JsonNode) =
    ScanSummary(
        startTime = startTime,
        endTime = endTime,
        fileCount = getFileCount(result),
        packageVerificationCode = verificationCode,
        licenseFindings = getLicenseFindings(result).toSortedSet(),
        copyrightFindings = getCopyrightFindings(result).toSortedSet(),
        issues = getIssues(result)
    )

/**
 * Generates an object with details about the ScanCode scanner that produced the given [result]. The
 * corresponding metadata from the result is evaluated.
 */
internal fun generateScannerDetails(result: JsonNode): ScannerDetails {
    result["headers"]?.let {
        return generateScannerDetailsFromNode(it[0], "options", "tool_version")
    }
    return generateScannerDetailsFromNode(result, "scancode_options", "scancode_version")
}

private fun getFileCount(result: JsonNode): Int {
    // ScanCode 2.9.8 and above nest the files count in an extra header.
    result["headers"]?.forEach { header ->
        header["extra_data"]?.get("files_count")?.let {
            return it.intValue()
        }
    }

    // ScanCode 2.9.7 and below contain the files count at the top level.
    return result["files_count"]?.intValue() ?: 0
}

/**
 * Get the license findings from the given [result].
 */
private fun getLicenseFindings(result: JsonNode): List<LicenseFinding> {
    val licenseFindings = mutableListOf<LicenseFinding>()

    val files = result["files"]?.asSequence().orEmpty()
    files.flatMapTo(licenseFindings) { file ->
        val licenses = file["licenses"]?.asSequence().orEmpty()
        licenses.map {
            LicenseFinding(
                license = getLicenseId(it),
                location = TextLocation(
                    // The path is already relative as we run ScanCode with "--strip-root".
                    path = file["path"].textValue(),
                    startLine = it["start_line"].intValue(),
                    endLine = it["end_line"].intValue()
                )
            )
        }
    }

    return licenseFindings
}

/**
 * Get the SPDX license id (or a fallback) for a license finding.
 */
private fun getLicenseId(license: JsonNode): String {
    // The fact that ScanCode 3.0.2 uses an empty string here for licenses unknown to SPDX seems to have been a bug
    // in ScanCode, and it should have always been using null instead.
    var name = license["spdx_license_key"].textValueOrEmpty()

    if (name.isEmpty()) {
        val key = license["key"].textValue()
        name = if (key in UNKNOWN_LICENSE_KEYS) {
            SpdxConstants.NOASSERTION
        } else {
            // Starting with version 2.9.8, ScanCode uses "scancode" as a LicenseRef namespace, but only for SPDX
            // output formats, see https://github.com/nexB/scancode-toolkit/pull/1307.
            "LicenseRef-${ScanCode.SCANNER_NAME.toLowerCase()}-$key"
        }
    }

    return name
}

private fun getIssues(result: JsonNode): List<OrtIssue> =
    result["files"]?.flatMap { file ->
        val path = file["path"].textValue()
        file["scan_errors"].map {
            OrtIssue(
                source = ScanCode.SCANNER_NAME,
                message = "${it.textValue()} (File: $path)"
            )
        }
    }.orEmpty()

/**
 * Get the copyright findings from the given [result].
 */
private fun getCopyrightFindings(result: JsonNode): List<CopyrightFinding> {
    val copyrightFindings = mutableListOf<CopyrightFinding>()

    val files = result["files"]?.asSequence().orEmpty()
    files.flatMapTo(copyrightFindings) { file ->
        val path = file["path"].textValue()

        val copyrights = file["copyrights"]?.asSequence().orEmpty()
        copyrights.flatMap { copyright ->
            val startLine = copyright["start_line"].intValue()
            val endLine = copyright["end_line"].intValue()

            // While ScanCode 2.9.2 was still using "statements", version 2.9.7 is using "value".
            val statements = (copyright["statements"]?.asSequence() ?: sequenceOf(copyright["value"]))

            statements.map { statement ->
                CopyrightFinding(
                    statement = statement.textValue(),
                    location = TextLocation(
                        // The path is already relative as we run ScanCode with "--strip-root".
                        path = path,
                        startLine = startLine,
                        endLine = endLine
                    )
                )
            }
        }
    }

    return copyrightFindings
}

/**
 * Generate a ScannerDetails object from the given [result] node, which structure depends on the current ScanCode
 * version. The node names to check are specified via [optionsNode], and [versionNode].
 */
private fun generateScannerDetailsFromNode(result: JsonNode, optionsNode: String, versionNode: String):
        ScannerDetails {
    val config = generateScannerOptions(result[optionsNode])
    val version = result[versionNode].textValueOrEmpty()
    return ScannerDetails(ScanCode.SCANNER_NAME, version, config)
}

/**
 * Convert the JSON node with ScanCode [options] to a string that corresponds to the options as they have been
 * passed on the command line.
 */
private fun generateScannerOptions(options: JsonNode?): String {
    fun addValues(list: MutableList<String>, node: JsonNode, key: String) {
        if (node.isEmpty) {
            list.add(key)
            list.add(node.asText())
        } else {
            node.forEach {
                list.add(key)
                list.add(it.asText())
            }
        }
    }

    return options?.let {
        val optionList = it.fieldNames().asSequence().fold(mutableListOf<String>()) { list, opt ->
            addValues(list, it[opt], opt)
            list
        }
        optionList.joinToString(separator = " ")
    }.orEmpty()
}
