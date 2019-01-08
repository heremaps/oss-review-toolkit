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

package com.here.ort.reporter.reporters

import ch.frankel.slf4k.*

import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.jsonMapper
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.utils.log

import java.io.File

class WebAppReporter : Reporter() {
    override fun generateReport(
            ortResult: OrtResult,
            resolutionProvider: ResolutionProvider,
            copyrightGarbage: CopyrightGarbage,
            outputDir: File,
            postProcessingScript: String?
    ): File {
        val template = javaClass.classLoader.getResource("scan-report-template.html").readText()
        val resultJson = jsonMapper.writeValueAsString(ortResult)

        val relevantResolutions = resolutionProvider.getResolutionsFor(ortResult)
        val resolutionsJson = jsonMapper.writeValueAsString(relevantResolutions)

        val result = template
                .replace("id=\"ort-report-data\"><", "id=\"ort-report-data\">$resultJson<")
                .replace("id=\"ort-report-resolution-data\"><", "id=\"ort-report-resolution-data\">$resolutionsJson<")

        val outputFile = File(outputDir, "scan-report-web-app.html")

        log.info { "Writing web app report to '${outputFile.absolutePath}'." }

        outputFile.writeText(result)

        return outputFile
    }
}
