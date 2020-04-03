/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers.utils

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Scope

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec

import java.time.Instant

class DotNetSupportTest : StringSpec({
    "non-existing project gets registered as error and is not added to scope" {
        val testPackage = Pair("trifj", "2.0.0")
        val testPackage2 = Pair("tffrifj", "2.0.0")
        val dotNetSupport = DotNetSupport(mapOf(testPackage, testPackage2))
        val resultScope = Scope("dependencies", sortedSetOf())
        val resultErrors = listOf(
            OrtIssue(
                timestamp = Instant.EPOCH,
                source = "nuget-API",
                message = "${testPackage.first}:${testPackage.second} can not be found on Nugets RestAPI."
            ),
            OrtIssue(
                timestamp = Instant.EPOCH,
                source = "nuget-API",
                message = "${testPackage2.first}:${testPackage2.second} can not be found on Nugets RestAPI."
            )
        )

        dotNetSupport.scope shouldBe resultScope
        dotNetSupport.issues.map { it.copy(timestamp = Instant.EPOCH) } shouldBe resultErrors
    }

    "dependencies are detected correctly" {
        val testPackage = Pair("WebGrease", "1.5.2")
        val dotNetSupport = DotNetSupport(mapOf(testPackage))
        val resultScope = Scope(
            "dependencies", sortedSetOf(
                PackageReference(
                    Identifier(
                        type = "nuget",
                        namespace = "",
                        name = "WebGrease",
                        version = "1.5.2"
                    ),
                    dependencies = sortedSetOf(
                        PackageReference(
                            Identifier(
                                type = "nuget",
                                namespace = "",
                                name = "Antlr",
                                version = "3.4.1.9004"
                            )
                        ),
                        PackageReference(
                            Identifier(
                                type = "nuget",
                                namespace = "",
                                name = "Newtonsoft.Json",
                                version = "5.0.4"
                            )
                        )
                    )
                )
            )
        )

        dotNetSupport.scope shouldBe resultScope
    }
})
