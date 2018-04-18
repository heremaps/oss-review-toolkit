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

package com.here.ort.model

import com.vdurmont.semver4j.Semver

/**
 * Details about the used source code scanner.
 */
data class ScannerDetails(
        /**
         * The name of the scanner.
         */
        val name: String,

        /**
         * The version of the scanner.
         */
        val version: String,

        /**
         * The configuration of the scanner, could be command line arguments for example.
         */
        val configuration: String
) {
    private val compatibleVersionDiffs = listOf(
            Semver.VersionDiff.NONE,
            Semver.VersionDiff.PATCH,
            Semver.VersionDiff.SUFFIX,
            Semver.VersionDiff.BUILD
    )

    /**
     * True if the [other] scanner has the same name and configuration, and the [Semver] version differs only in the
     * [patch][Semver.VersionDiff.PATCH], [suffix][Semver.VersionDiff.SUFFIX], or [build][Semver.VersionDiff.BUILD]
     * part. For the comparison the [loose][Semver.SemverType.LOOSE] Semver type is used for maximum compatibility with
     * the versions returned from the scanners.
     */
    fun isCompatible(other: ScannerDetails) =
            Semver(version, Semver.SemverType.LOOSE).diff(Semver(other.version, Semver.SemverType.LOOSE)) in
                    compatibleVersionDiffs
}
