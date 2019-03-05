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

package com.here.ort.model.config

import java.nio.file.FileSystems
import java.nio.file.Paths

/**
 * Defines paths which should be excluded. Each file that is matched by the [glob][pattern] is marked as excluded. If a
 * project definition file is matched by the [pattern] the whole project is excluded. For details about the glob syntax
 * see the [official documentation][https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob].
 */
data class PathExclude(
        /**
         * A glob to match the path of the project definition file, relative to the root of the repository.
         */
        val pattern: String,

        /**
         * The reason why the project is excluded, out of a predefined choice.
         */
        val reason: PathExcludeReason,

        /**
         * A comment to further explain why the [reason] is applicable here.
         */
        val comment: String
) {
    private val pathMatcher by lazy { FileSystems.getDefault().getPathMatcher("glob:$pattern") }

    fun matches(path: String) = pathMatcher.matches(Paths.get(path))
}
