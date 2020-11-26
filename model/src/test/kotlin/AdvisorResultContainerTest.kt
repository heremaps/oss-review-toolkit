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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.net.URL
import java.time.Duration
import java.time.Instant

class AdvisorResultContainerTest : WordSpec() {
    private val id = Identifier("type", "namespace", "name", "version")

    private val vulnerability11 = Vulnerability("CVE-11", 1.1F, URL("https://1.1.com"))
    private val vulnerability12 = Vulnerability("CVE-12", 1.2F, URL("https://1.2.com"))
    private val vulnerability21 = Vulnerability("CVE-21", 2.1F)
    private val vulnerability22 = Vulnerability("CVE-22", 2.2F)

    private val vulnerabilities1 = listOf(vulnerability11, vulnerability12)
    private val vulnerabilities2 = listOf(vulnerability21, vulnerability22)

    private val advisorDetails1 = AdvisorDetails("name 1")
    private val advisorDetails2 = AdvisorDetails("name 2")

    private val advisorStartTime1 = Instant.EPOCH + Duration.ofDays(1) + Duration.ofMinutes(1)
    private val advisorEndTime1 = advisorStartTime1 + Duration.ofMinutes(1)
    private val advisorStartTime2 = Instant.EPOCH + Duration.ofDays(2) + Duration.ofMinutes(1)
    private val advisorEndTime2 = advisorStartTime2 + Duration.ofMinutes(1)

    private val issue11 = OrtIssue(source = "source-11", message = "issue-11")
    private val issue12 = OrtIssue(source = "source-12", message = "issue-12")
    private val issue21 = OrtIssue(source = "source-21", message = "issue-21")
    private val issue22 = OrtIssue(source = "source-22", message = "issue-22")

    private val advisorSummary1 = AdvisorSummary(
            advisorStartTime1,
            advisorEndTime1,
            mutableListOf(issue11, issue12)
    )

    private val advisorSummary2 = AdvisorSummary(
            advisorStartTime2,
            advisorEndTime2,
            mutableListOf(issue21, issue22)
    )

    private val advisorResult1 = AdvisorResult(vulnerabilities1, advisorDetails1, advisorSummary1)
    private val advisorResult2 = AdvisorResult(vulnerabilities2, advisorDetails2, advisorSummary2)

    private val advisorResults = AdvisorResultContainer(id, listOf(advisorResult1, advisorResult2))

    init {
        "AdvisorResults" should {
            "be serialized and deserialized correctly" {
                val serializedAdvisorResults = jsonMapper.writeValueAsString(advisorResults)
                val deserializedAdvisorResults = jsonMapper.readValue<AdvisorResultContainer>(serializedAdvisorResults)

                deserializedAdvisorResults shouldBe advisorResults
            }
        }
    }
}
