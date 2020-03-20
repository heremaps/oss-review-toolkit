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

package org.ossreviewtoolkit.spdx

import io.kotlintest.matchers.endWith
import io.kotlintest.matchers.startWith
import io.kotlintest.matchers.string.include
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.WordSpec

class SpdxLicenseTest : WordSpec({
    "The license lookup" should {
        "work by SPDX id" {
            SpdxLicense.forId("Apache-2.0") shouldBe SpdxLicense.APACHE_2_0
        }

        "work by human-readable name" {
            SpdxLicense.forId("Apache License 2.0") shouldBe SpdxLicense.APACHE_2_0
        }
    }

    "The license text" should {
        "be correct for 'or later' GPL ids" {
            val gpl10OrLater = SpdxLicense.forId("GPL-1.0+")
            gpl10OrLater shouldNotBe null
            val gpl10OrLaterText = gpl10OrLater!!.text.replace("\n", " ").trimEnd()
            gpl10OrLaterText should startWith(
                "This program is free software; you can redistribute it and/or modify it " +
                        "under the terms of the GNU General Public License"
            )
            gpl10OrLaterText should include("; either version 1, or (at your option) any later version.")
            gpl10OrLaterText should endWith("That's all there is to it!")

            val gpl20OrLater = SpdxLicense.forId("GPL-2.0-or-later")
            gpl20OrLater shouldNotBe null
            val gpl20OrLaterText = gpl20OrLater!!.text.replace("\n", " ").trimEnd()
            gpl20OrLaterText should startWith(
                "This program is free software; you can redistribute it and/or modify it " +
                        "under the terms of the GNU General Public License"
            )
            gpl20OrLaterText should include("; either version 2 of the License, or (at your option) any later version.")
            gpl20OrLaterText should endWith(
                "If this is what you want to do, use the GNU Lesser General Public License " +
                        "instead of this License."
            )
        }

        "be correct for 'or later' non-GPL ids" {
            val gfdl11OrLater = SpdxLicense.forId("GFDL-1.1-or-later")
            gfdl11OrLater shouldNotBe null
            gfdl11OrLater!!.text shouldBe javaClass.getResource("/licenses/GFDL-1.1-or-later").readText()
        }
    }
})
