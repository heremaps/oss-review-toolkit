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

package com.here.ort.model.utils

import com.here.ort.model.CopyrightFinding
import com.here.ort.model.CopyrightFindings
import com.here.ort.model.LicenseFinding
import com.here.ort.model.LicenseFindings
import com.here.ort.model.TextLocation
import com.here.ort.utils.FileMatcher

import kotlin.math.absoluteValue

private fun Collection<CopyrightFinding>.toCopyrightFindings(): List<CopyrightFindings> {
    val locationsByStatement = mutableMapOf<String, MutableSet<TextLocation>>()

    forEach { copyrightFinding ->
        locationsByStatement.getOrPut(copyrightFinding.statement) { mutableSetOf() } += copyrightFinding.location
    }

    return locationsByStatement.map { (statement, locations) ->
        CopyrightFindings(statement, locations.toSortedSet())
    }
}

/**
 * A class for matching copyright findings to license findings. Copyright statements may be matched either to license
 * findings located nearby in the same file or to a license found in a license file whereas the given
 * [licenseFileMatcher] determines whether a file is a license file.
 */
class FindingsMatcher(
    private val licenseFileMatcher: FileMatcher = FileMatcher.LICENSE_FILE_MATCHER,
    private val toleranceLines: Int = DEFAULT_TOLERANCE_LINES
) {
    companion object {
        /**
         * The default value of 5 seems to be a good balance between associating findings separated by blank lines but
         * not skipping complete license statements.
         */
        const val DEFAULT_TOLERANCE_LINES = 5
    }

    /**
     * Get the licenses found in all commonly named license files, if any, or an empty list otherwise.
     */
    private fun getRootLicenses(licenseFindings: Collection<LicenseFinding>): List<String> =
        licenseFindings
            .filter { licenseFileMatcher.matches(it.location.path) }
            .map { it.license }
            .distinct()

    /**
     * Return those statements in [copyrights] which are in the vicinity of [licenseStartLine] as defined by
     * [toleranceLines].
     */
    private fun getClosestCopyrightStatements(
        copyrights: List<CopyrightFinding>,
        licenseStartLine: Int
    ): Set<CopyrightFinding> {
        require(copyrights.map { it.location.path }.distinct().size <= 1) {
            "Given copyright statements must all point to the same file."
        }

        val closestCopyrights = copyrights.filter {
            (it.location.startLine - licenseStartLine).absoluteValue <= toleranceLines
        }

        return closestCopyrights.toSet()
    }

    /**
     * Associate copyright findings to license findings within a single file.
     */
    private fun matchFileFindings(
        licenses: List<LicenseFinding>,
        copyrights: List<CopyrightFinding>
    ): Map<String, Set<CopyrightFinding>> {
        require((licenses.map { it.location.path } + copyrights.map { it.location.path }).distinct().size <= 1) {
            "The given license and copyright findings must all point to the same file."
        }

        // If there is only a single license finding, associate all copyright findings with that license. If there is
        // no license return no matches.
        if (licenses.size <= 1) return licenses.associateBy({ it.license }, { copyrights.toSet() })

        // If there are multiple license findings in a single file, search for the closest copyright statements
        // for each of these, if any.
        val result = mutableMapOf<String, MutableSet<CopyrightFinding>>()
        licenses.forEach { (license, location) ->
            val closestCopyrights = getClosestCopyrightStatements(copyrights, location.startLine)
            result.getOrPut(license) { mutableSetOf() } += closestCopyrights
        }

        return result
    }

    /**
     * Return an association of the given [copyrightFindings] to [licenseFindings].
     * Copyright findings are either matched to a license finding located nearby in the same file or to a license
     * finding pointing to a license file. Whether a file is a license file is determined by the
     * [FileMatcher] passed to the constructor. All [CopyrightFindings]s which cannot be matched are not present
     * in the result while all given [licenseFindings] are contained in the result exactly once.
     */
    fun match(licenseFindings: Collection<LicenseFinding>, copyrightFindings: Collection<CopyrightFinding>):
            Set<LicenseFindings> {
        val licenseFindingsByPath = licenseFindings.groupBy { it.location.path }
        val copyrightFindingsByPath = copyrightFindings.groupBy { it.location.path }
        val paths = (licenseFindingsByPath.keys + copyrightFindingsByPath.keys).toSet()
        val rootLicenses = getRootLicenses(licenseFindings)

        val locationsByLicense = licenseFindings
            .groupBy({ it.license }, { it.location })
            .mapValuesTo(mutableMapOf()) { it.value.toSortedSet() }

        val copyrightsByLicense = mutableMapOf<String, MutableSet<CopyrightFinding>>()

        paths.forEach { path ->
            val licenses = licenseFindingsByPath[path].orEmpty()
            val copyrights = copyrightFindingsByPath[path].orEmpty()
            val findings = matchFileFindings(licenses, copyrights)

            findings.forEach { (license, copyrightFindings) ->
                copyrightsByLicense.getOrPut(license) { mutableSetOf() } += copyrightFindings
            }

            // Associate all unmatched copyright findings with all root licenses.
            val unmatchedCopyrights = copyrights.toSet() - findings.values.flatten()
            rootLicenses.forEach { license ->
                copyrightsByLicense.getOrPut(license) { mutableSetOf() } += unmatchedCopyrights
            }
        }

        return (copyrightsByLicense.keys + locationsByLicense.keys).mapTo(mutableSetOf()) { license ->
            LicenseFindings(
                license,
                locationsByLicense[license] ?: sortedSetOf(),
                copyrightsByLicense[license].orEmpty().toCopyrightFindings().toSortedSet()
            )
        }
    }
}
