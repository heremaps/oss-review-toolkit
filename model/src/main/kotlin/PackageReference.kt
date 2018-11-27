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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedSet

// A custom value filter for [PackageLinkage] to work around
// https://github.com/FasterXML/jackson-module-kotlin/issues/193.
class PackageLinkageValueFilter {
    override fun equals(other: Any?) = other == PackageLinkage.DYNAMIC
}

/**
 * A human-readable reference to a software [Package]. Each package reference itself refers to other package
 * references that are dependencies of the package.
 */
// Do not serialize default values to reduce the size of the result file.
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class PackageReference(
        /**
         * The identifier of the package.
         */
        val id: Identifier,

        /**
         * The type of linkage used for the referred package from its dependent package. As most of our supported
         * [PackageManager]s / languages only support dynamic linking or at least default to it, also use that as the
         * default value here to not blow up our result files.
         */
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = PackageLinkageValueFilter::class)
        val linkage: PackageLinkage = PackageLinkage.DYNAMIC,

        /**
         * The list of references to packages this package depends on. Note that this list depends on the scope in
         * which this package reference is used.
         */
        val dependencies: SortedSet<PackageReference> = sortedSetOf(),

        /**
         * A list of errors that occurred handling this [PackageReference].
         */
        val errors: List<OrtIssue> = emptyList(),

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        val data: CustomData = emptyMap()
) : Comparable<PackageReference> {
    /**
     * Return the set of [PackageReference]s this [PackageReference] transitively depends on, up to and including a
     * depth of [maxDepth] where counting starts at 0 (for the [PackageReference] itself) and 1 are direct dependencies
     * etc. A value below 0 means to not limit the depth. If [includeErroneous] is true, [PackageReference]s with errors
     * (but not their dependencies without errors) are excluded, otherwise they are included.
     */
    fun collectDependencies(maxDepth: Int = -1, includeErroneous: Boolean = true): SortedSet<PackageReference> =
            dependencies.fold(sortedSetOf<PackageReference>()) { refs, ref ->
                refs.also {
                    if (maxDepth != 0) {
                        if (ref.errors.isEmpty() || includeErroneous) it += ref
                        it += ref.collectDependencies(maxDepth - 1, includeErroneous)
                    }
                }
            }

    /**
     * A comparison function to sort package references by their identifier. This function ignores all other properties
     * except for [id].
     */
    override fun compareTo(other: PackageReference) = id.compareTo(other.id)

    /**
     * Return whether the package identified by [id] is a (transitive) dependency of this reference.
     */
    fun dependsOn(id: Identifier): Boolean = dependencies.any { it.id == id || it.dependsOn(id) }

    /**
     * Return whether this package reference or any of its dependencies has errors.
     */
    fun hasErrors(): Boolean = errors.isNotEmpty() || dependencies.any { it.hasErrors() }

    /**
     * Apply the provided [transform] to each node in the dependency tree represented by this [PackageReference] and
     * return the modified [PackageReference]. The tree is traversed depth-first (post-order).
     */
    fun traverse(transform: (PackageReference) -> PackageReference): PackageReference {
        val transformedDependencies = dependencies.map {
            it.traverse(transform)
        }
        return transform(copy(dependencies = transformedDependencies.toSortedSet()))
    }
}
