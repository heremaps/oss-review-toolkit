/*
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

package org.ossreviewtoolkit.advisor

import java.util.ServiceLoader

import org.ossreviewtoolkit.model.config.AdvisorConfiguration

/**
 * A common interface for use with [ServiceLoader] that all [AbstractAdvisorFactory] classes need to implement.
 */
interface AdvisorFactory {
    /**
     * The name to use to refer to the advisor.
     */
    val advisorName: String

    /**
     * Create an [Advisor] using the specified [config].
     */
    fun create(config: AdvisorConfiguration): Advisor
}

/**
 * A generic factory class for an [Advisor].
 */
abstract class AbstractAdvisorFactory<out T : Advisor>(
    override val advisorName: String
) : AdvisorFactory {
    abstract override fun create(config: AdvisorConfiguration): T

    /**
     * Return the advisor's name here to allow Clikt to display something meaningful when listing the scanners
     * which are enabled by default via their factories.
     */
    override fun toString() = advisorName
}
