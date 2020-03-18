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

package com.here.ort.spdx

import com.here.ort.spdx.SpdxLicense.*
import com.here.ort.spdx.SpdxLicenseException.*

/**
 * A mapping from varied SPDX license ids to valid SPDX expressions. When mapping an id without any indication of a
 * version to an SPDX expression with a version, the most commonly used version at the time of writing is used.
 */
object SpdxLicenseAliasMapping {
    /**
     * The map of custom license ids associated with their corresponding SPDX expression.
     */
    internal val customLicenseIds: Map<String, SpdxExpression> = listOf(
        "afl" to AFL_3_0,
        "afl-2" to AFL_2_0,
        "afl2" to AFL_2_0,
        "afl2.0" to AFL_2_0,
        "afl2.1" to AFL_2_1,
        "AFLv2.1" to AFL_2_1,
        "agpl" to AGPL_3_0_ONLY,
        "ALv2" to APACHE_2_0,
        "Apache" to APACHE_2_0,
        "Apache-2" to APACHE_2_0,
        "apache-license" to APACHE_2_0,
        "Apache2" to APACHE_2_0,
        "APL2" to APACHE_2_0,
        "APLv2.0" to APACHE_2_0,
        "ASL" to APACHE_2_0,
        "Boost" to BSL_1_0,
        "Bouncy" to MIT,
        "bouncy-license" to MIT,
        "BSD" to BSD_3_CLAUSE,
        "BSD-3" to BSD_3_CLAUSE,
        "bsd-license" to BSD_3_CLAUSE,
        "bsd-licensed" to BSD_3_CLAUSE,
        "BSD-like" to BSD_3_CLAUSE,
        "BSD-style" to BSD_3_CLAUSE,
        "BSD2" to BSD_2_CLAUSE,
        "BSD3" to BSD_3_CLAUSE,
        "bsl" to BSL_1_0,
        "bsl1.0" to BSL_1_0,
        "CC0" to CC0_1_0,
        "cddl" to CDDL_1_0,
        "cddl1.0" to CDDL_1_0,
        "cddl1.1" to CDDL_1_1,
        "CPL" to CPL_1_0,
        "EDL-1.0" to BSD_3_CLAUSE,
        "efl" to EFL_2_0,
        "epl" to EPL_1_0,
        "epl1.0" to EPL_1_0,
        "epl2.0" to EPL_2_0,
        "eupl" to EUPL_1_0,
        "eupl1.0" to EUPL_1_0,
        "eupl1.1" to EUPL_1_1,
        "eupl1.2" to EUPL_1_2,
        "fdl" to GFDL_1_3_ONLY,
        "FreeBSD" to BSD_2_CLAUSE_FREEBSD,
        "gfdl" to GFDL_1_3_ONLY,
        "GPL" to GPL_2_0_ONLY,
        "GPL-2" to GPL_2_0_ONLY,
        "gpl-license" to GPL_2_0_ONLY,
        "GPL2" to GPL_2_0_ONLY,
        "gpl3" to GPL_3_0_ONLY,
        "GPLv2" to GPL_2_0_ONLY,
        "GPLv2+" to GPL_2_0_OR_LATER,
        "GPLv3" to GPL_3_0_ONLY,
        "GPLv3+" to GPL_3_0_OR_LATER,
        "isc-license" to ISC,
        "ISCL" to ISC,
        "LGPL" to LGPL_2_0_OR_LATER,
        "LGPL-3" to LGPL_3_0_ONLY,
        "LGPL2" to LGPL_2_1_ONLY,
        "LGPL3" to LGPL_3_0_ONLY,
        "LGPLv2" to LGPL_2_1_ONLY,
        "LGPLv3" to LGPL_3_0_ONLY,
        "mit-license" to MIT,
        "mit-licensed" to MIT,
        "MIT-like" to MIT,
        "MIT-style" to MIT,
        "MPL" to MPL_2_0,
        "mpl-2" to MPL_2_0,
        "mpl2" to MPL_2_0,
        "mpl2.0" to MPL_2_0,
        "MPLv2" to MPL_2_0,
        "MPLv2.0" to MPL_2_0,
        "ODBL" to ODBL_1_0,
        "psf" to PYTHON_2_0,
        "psfl" to PYTHON_2_0,
        "python" to PYTHON_2_0,
        "UNLICENSED" to UNLICENSE,
        "w3cl" to W3C,
        "wtf" to WTFPL,
        "zope" to ZPL_2_1
    ).also {
        val keys = it.unzip().first.toMutableList()
        val uniqueKeys = keys.distinct()
        if (keys.size > uniqueKeys.size) {
            uniqueKeys.forEach { uniqueKey -> keys.remove(uniqueKey) }
            require(keys.isEmpty()) {
                val quotedKeys = keys.map { "\"$it\"" }
                "The following ${keys.size} keys are present in the same capitalization: $quotedKeys"
            }
        }
    }.toMap().mapValues { (_, v) -> v.toExpression() }.let { caseSensitiveMap ->
        caseSensitiveMap.toSortedMap(String.CASE_INSENSITIVE_ORDER).also { caseInsensitiveMap ->
            if (caseSensitiveMap.size > caseInsensitiveMap.size) {
                val difference = caseSensitiveMap.keys.subtract(caseInsensitiveMap.keys).map { "\"$it\"" }
                require(difference.isEmpty()) {
                    "The following ${difference.size} keys are present in different capitalizations: $difference"
                }
            }
        }
    }

    /**
     * The map of deprecated SPDX license ids associated with their current SPDX expression.
     */
    private val deprecatedLicenseIds = mapOf(
        "AGPL-1.0" to AGPL_1_0_ONLY,
        "AGPL-1.0+" to AGPL_1_0_OR_LATER,
        "AGPL-3.0" to AGPL_3_0_ONLY,
        "AGPL-3.0+" to AGPL_3_0_OR_LATER,
        "GFDL-1.1" to GFDL_1_1_ONLY,
        "GFDL-1.1+" to GFDL_1_1_OR_LATER,
        "GFDL-1.2" to GFDL_1_2_ONLY,
        "GFDL-1.2+" to GFDL_1_2_OR_LATER,
        "GFDL-1.3" to GFDL_1_3_ONLY,
        "GFDL-1.3+" to GFDL_1_3_OR_LATER,
        "GPL-1.0" to GPL_1_0_ONLY,
        "GPL-1.0+" to GPL_1_0_OR_LATER,
        "GPL-2.0" to GPL_2_0_ONLY,
        "GPL-2.0+" to GPL_2_0_OR_LATER,
        "GPL-3.0" to GPL_3_0_ONLY,
        "GPL-3.0+" to GPL_3_0_OR_LATER,
        "LGPL-2.0" to LGPL_2_0_ONLY,
        "LGPL-2.0+" to LGPL_2_0_OR_LATER,
        "LGPL-2.1" to LGPL_2_1_ONLY,
        "LGPL-2.1+" to LGPL_2_1_OR_LATER,
        "LGPL-3.0" to LGPL_3_0_ONLY,
        "LGPL-3.0+" to LGPL_3_0_OR_LATER
    ).mapValues { (_, v) -> v.toExpression() }

    /**
     * The map of deprecated SPDX license exception ids associated with their current compound SPDX expression.
     */
    private val deprecatedExceptionIds = mapOf(
        "GPL-2.0-with-autoconf-exception" to (GPL_2_0_ONLY with AUTOCONF_EXCEPTION_2_0),
        "GPL-2.0-with-bison-exception" to (GPL_2_0_ONLY with BISON_EXCEPTION_2_2),
        "GPL-2.0-with-classpath-exception" to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GPL-2.0-with-font-exception" to (GPL_2_0_ONLY with FONT_EXCEPTION_2_0),
        "GPL-2.0-with-GCC-exception" to (GPL_2_0_ONLY with GCC_EXCEPTION_2_0),
        "GPL-3.0-with-autoconf-exception" to (GPL_3_0_ONLY with AUTOCONF_EXCEPTION_3_0),
        "GPL-3.0-with-GCC-exception" to (GPL_3_0_ONLY with GCC_EXCEPTION_3_1)
    )

    /**
     * The map of varied SPDX license ids associated with their corresponding SPDX expression.
     */
    val mapping =
        (customLicenseIds + deprecatedLicenseIds + deprecatedExceptionIds).toSortedMap(String.CASE_INSENSITIVE_ORDER)

    /**
     * Return the [SpdxExpression] the [license] id maps to, or null if there is no corresponding expression. If
     * [mapDeprecated] is true, license ids marked as deprecated in the SPDX standard are mapped to their
     * corresponding current expression, otherwise they are mapped to their corresponding deprecated expression.
     */
    fun map(license: String, mapDeprecated: Boolean = true) =
        (if (mapDeprecated) mapping else customLicenseIds)[license] ?: SpdxLicense.forId(license)?.toExpression()
}
