/*
 * Copyright (C) 2020 HERE Europe B.V.
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

import java.io.File

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.utils.FreemarkerTemplateProcessor

/**
 * A [Reporter] that creates notice files using [Apache Freemarker][1] templates. For each template provided using the
 * options described below a separate output file is created. If no options are provided the "default" template
 * is used. The name of the template id or template path (without extension) is used for the generated file, so be
 * careful to not use two different templates with the same name.
 *
 * This reporter supports the following options:
 * - *template.id*: A comma-separated list of IDs of templates provided by ORT. Currently only the "default"
 *                  and "summary" templates are available.
 * - *template.path*: A comma-separated list of paths to template files provided by the user.
 *
 * [1]: https://freemarker.apache.org
 */
class NoticeTemplateReporter : Reporter {
    companion object {
        private const val NOTICE_FILE_PREFIX = "NOTICE_"
        private const val NOTICE_TEMPLATE_DIRECTORY = "notice"
    }

    override val reporterName = "NoticeTemplate"

    private val templateProcessor = FreemarkerTemplateProcessor(
        NOTICE_FILE_PREFIX,
        NOTICE_TEMPLATE_DIRECTORY
    )

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>) =
        templateProcessor.processTemplates(input, outputDir, options)
}
