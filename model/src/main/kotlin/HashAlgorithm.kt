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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * An enum of supported hash algorithms. Each algorithm has one or more [aliases] associated to it, where the first
 * alias is the definite name.
 */
enum class HashAlgorithm(private vararg val aliases: String, val verifiable: Boolean = true) {
    /**
     * No hash algorithm.
     */
    NONE("", verifiable = false),

    /**
     * An unknown hash algorithm.
     */
    UNKNOWN("UNKNOWN", verifiable = false),

    /**
     * The Message-Digest 5 hash algorithm, see [MD5](http://en.wikipedia.org/wiki/MD5).
     */
    MD5("MD5"),

    /**
     * The Secure Hash Algorithm 1, see [SHA-1](https://en.wikipedia.org/wiki/SHA-1).
     */
    SHA1("SHA-1", "SHA1"),

    /**
     * The Secure Hash Algorithm 2 with 256 bits, see [SHA-256](https://en.wikipedia.org/wiki/SHA-256).
     */
    SHA256("SHA-256", "SHA256"),

    /**
     * The Secure Hash Algorithm 2 with 384 bits, see [SHA-384](https://en.wikipedia.org/wiki/SHA-384).
     */
    SHA384("SHA-384", "SHA384"),

    /**
     * The Secure Hash Algorithm 2 with 512 bits, see [SHA-512](https://en.wikipedia.org/wiki/SHA-512).
     */
    SHA512("SHA-512", "SHA512");

    companion object {
        /**
         * The list of algorithms that can be verified.
         */
        val VERIFIABLE = enumValues<HashAlgorithm>().filter { it.verifiable }

        /**
         * Create a hash algorithm from one of its [alias] names.
         */
        @JsonCreator
        @JvmStatic
        fun fromString(alias: String) =
            enumValues<HashAlgorithm>().find {
                alias.toUpperCase() in it.aliases
            } ?: UNKNOWN

        /**
         * Create a hash algorithm from a hash [value].
         */
        fun create(value: String): HashAlgorithm {
            if (value.isBlank()) return NONE

            return when (value.length) {
                128 -> SHA512
                96 -> SHA384
                64 -> SHA256
                40 -> SHA1
                32 -> MD5
                else -> UNKNOWN
            }
        }
    }

    /**
     * Convert the hash algorithm to a string representation.
     */
    @JsonValue
    override fun toString(): String = aliases.first()
}
