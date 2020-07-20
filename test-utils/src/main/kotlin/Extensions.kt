/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.test

import io.kotest.matchers.Matcher
import io.kotest.matchers.maps.MapContainsMatcher
import io.kotest.matchers.nulls.shouldNotBeNull

fun <K, V> containExactly(vararg expected: Pair<K, V>): Matcher<Map<K, V>> = MapContainsMatcher(expected.toMap())

infix fun <T> T?.shouldNotBeNull(block: T.() -> Unit) {
    this.shouldNotBeNull()
    this.block()
}
