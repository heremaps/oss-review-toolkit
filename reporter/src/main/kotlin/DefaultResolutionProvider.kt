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

package com.here.ort.reporter

import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.RuleViolation
import com.here.ort.model.config.Resolutions

class DefaultResolutionProvider : ResolutionProvider {
    private var resolutions = Resolutions()

    fun add(resolutions: Resolutions) {
        this.resolutions = this.resolutions.merge(resolutions)
    }

    override fun getErrorResolutionsFor(issue: OrtIssue) = resolutions.errors.filter { it.matches(issue) }

    override fun getRuleViolationResolutionsFor(violation: RuleViolation) =
        resolutions.ruleViolations.filter { it.matches(violation) }

    override fun getResolutionsFor(ortResult: OrtResult): Resolutions {
        val errorResolutions = ortResult.collectIssues().values.flatten().let { errors ->
            resolutions.errors.filter { resolution -> errors.any { resolution.matches(it) } }
        }

        val ruleViolationResolutions = ortResult.evaluator?.violations?.let { violations ->
            resolutions.ruleViolations.filter { resolution -> violations.any { resolution.matches(it) } }
        }.orEmpty()

        return Resolutions(errorResolutions, ruleViolationResolutions)
    }
}
