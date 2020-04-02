/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.spdx

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.WordSpec

import org.ossreviewtoolkit.spdx.SpdxExpression.Strictness

class SpdxDeclaredLicenseMappingTest : WordSpec({
    "The list" should {
        "not contain any duplicate keys with respect to capitalization" {
            val keys = SpdxDeclaredLicenseMapping.mappingList.unzip().first.toMutableList()
            val uniqueKeys = SpdxDeclaredLicenseMapping.mapping.keys

            // Remove keys one by one as calling "-" would remove all occurrences of a key.
            uniqueKeys.forEach { uniqueKey -> keys.remove(uniqueKey) }

            keys should beEmpty()
        }
    }

    "The mapping" should {
        "contain only unparsable keys" {
            val parsableLicenses = SpdxDeclaredLicenseMapping.mapping.filter { (declaredLicense, _) ->
                try {
                    // Restrict parsing to SPDX license identifier strings as otherwise almost anything could be parsed,
                    // but we do want to have mappings e.g. for something like "CDDL or GPLv2 with exceptions".
                    SpdxExpression.parse(declaredLicense, Strictness.ALLOW_DEPRECATED)
                    true
                } catch (e: SpdxException) {
                    false
                }
            }

            parsableLicenses shouldBe emptyMap()
        }

        "not contain plain SPDX license ids" {
            assertSoftly {
                SpdxDeclaredLicenseMapping.mapping.forEach { (declaredLicense, _) ->
                    "\"$declaredLicense\" maps to ${SpdxLicense.forId(declaredLicense)}" shouldBe
                            "\"$declaredLicense\" maps to null"
                }
            }
        }

        "be case-insensitve" {
            val map = SpdxDeclaredLicenseMapping.mapping
            map.forEach { (key, license) ->
                map[key.toLowerCase()] shouldBe license
                map[key.toUpperCase()] shouldBe license
                map[key.toLowerCase().capitalize()] shouldBe license
            }
        }
    }
})
