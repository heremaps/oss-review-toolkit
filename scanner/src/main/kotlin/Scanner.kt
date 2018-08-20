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

package com.here.ort.scanner

import com.here.ort.model.Package
import com.here.ort.model.ScanResult

import java.io.File
import java.util.ServiceLoader

abstract class Scanner {
    companion object {
        private val LOADER by lazy { ServiceLoader.load(Scanner::class.java)!! }

        /**
         * The sequence of all available scanners in the classpath.
         */
        val ALL = LOADER.iterator().asSequence()
    }

    /**
     * Scan the [packages] using this [Scanner].
     *
     * @param packages The packages to scan.
     * @param outputDirectory Where to store the scan results.
     * @param downloadDirectory Where to store the downloaded source code. If null the source code is downloaded to the
     *                          outputDirectory.
     *
     * @return The scan results by identifier. It can contain multiple results for one identifier if the
     *         cache contains more than one result for the specification of this scanner.
     */
    abstract fun scan(packages: List<Package>, outputDirectory: File, downloadDirectory: File? = null)
            : Map<Package, List<ScanResult>>

    /**
     * Return the Java class name as a simple way to refer to the scanner.
     */
    override fun toString(): String = javaClass.simpleName
}
