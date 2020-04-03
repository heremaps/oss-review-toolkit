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

package org.ossreviewtoolkit.reporter.reporters

import bad.robot.excel.matchers.WorkbookMatcher.sameWorkbook

import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.readOrtResult

import io.kotest.core.spec.style.WordSpec

import java.io.ByteArrayOutputStream
import java.io.File

import org.apache.poi.ss.usermodel.WorkbookFactory

import org.hamcrest.MatcherAssert.assertThat

class ExcelReporterTest : WordSpec({
    "ExcelReporter" should {
        "successfully export to an Excel sheet".config(enabled = false) {
            val outputStream = ByteArrayOutputStream()
            val ortResult =
                readOrtResult("../scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml")
            ExcelReporter().generateReport(outputStream, ReporterInput(ortResult))
            val actualWorkbook = WorkbookFactory.create(outputStream.toByteArray().inputStream())

            val expectedFile = File("src/funTest/assets/file-counter-expected-scan-report.xlsx")
            val expectedWorkbook = WorkbookFactory.create(expectedFile)

            assertThat(actualWorkbook, sameWorkbook(expectedWorkbook))
        }
    }
})
