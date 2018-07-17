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

package com.here.ort.analyzer.managers

import com.here.ort.analyzer.AnalyzerConfiguration
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory

import java.io.File

class Bower(config: AnalyzerConfiguration) : PackageManager(config) {
    companion object : PackageManagerFactory<Bower>(
            "https://bower.io/",
            "JavaScript",
            listOf("bower.json")
    ) {
        override fun create(config: AnalyzerConfiguration) = Bower(config)
    }

    override fun command(workingDir: File) = "bower"

    override fun toString() = Bower.toString()
}
