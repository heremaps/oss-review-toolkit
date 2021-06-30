/*
 * Copyright (C) 2021 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

import io.kotest.assertions.throwables.shouldThrow

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.Environment

class DependencyGraphNavigatorTest : AbstractDependencyNavigatorTest() {
    override val resultFileName: String = RESULT_FILE

    override val resultWithIssuesFileName: String = RESULT_WITH_ISSUES_FILE

    init {
        "init" should {
            "fail if there is no analyzer result" {
                shouldThrow<IllegalArgumentException> {
                    DependencyGraphNavigator(OrtResult.EMPTY)
                }
            }

            "fail if the map with dependency graphs is empty" {
                val result = OrtResult.EMPTY.copy(
                    analyzer = AnalyzerRun(
                        result = AnalyzerResult.EMPTY,
                        environment = Environment(),
                        config = AnalyzerConfiguration()
                    )
                )

                shouldThrow<IllegalArgumentException> {
                    DependencyGraphNavigator(result)
                }
            }
        }
    }
}

/** Name of a file with a more complex ORT result that is used by multiple test cases. */
private const val RESULT_FILE = "src/test/assets/sbt-multi-project-example-graph.yml"

/** Name of the file with a result that contains some issues. */
private const val RESULT_WITH_ISSUES_FILE = "src/test/assets/result-with-issues-graph.yml"
