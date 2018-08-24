/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.reporter

import com.here.ort.model.AnalyzerResult
import com.here.ort.model.OrtResult
import com.here.ort.model.ScanRecord

import java.io.File

/**
 * A reporter that creates a human readable report from the [AnalyzerResult] and [ScanRecord] contained in an
 * [OrtResult]. The signatures of public functions in this class define the library API.
 */
interface Reporter {
    fun generateReport(ortResult: OrtResult, outputDir: File)
}
