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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException

import org.ossreviewtoolkit.utils.fieldNamesOrEmpty
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.textValueOrEmpty

import kotlin.reflect.full.memberProperties

/**
 * Bundles general Version Control System information.
 */
@JsonDeserialize(using = VcsInfoDeserializer::class)
data class VcsInfo(
    /**
     * The type of the VCS, for example Git, GitRepo, Mercurial, etc.
     */
    val type: VcsType,

    /**
     * The URL to the VCS repository.
     */
    val url: String,

    /**
     * The VCS-specific revision (tag, branch, SHA1) that the version of the package maps to.
     */
    val revision: String,

    /**
     * The VCS-specific revision resolved during downloading from the VCS. In contrast to [revision] this must not
     * contain symbolic names like branches or tags.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val resolvedRevision: String? = null,

    /**
     * The path inside the VCS to take into account, if any. The actual meaning depends on the VCS type. For
     * example, for Git only this subdirectory of the repository should be cloned, or for Git Repo it is
     * interpreted as the path to the manifest file.
     */
    val path: String = ""
) {
    companion object {
        /**
         * A constant for a [VcsInfo] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = VcsInfo(
            type = VcsType.NONE,
            url = "",
            revision = "",
            resolvedRevision = null,
            path = ""
        )
    }

    /**
     * Merge two sources of VCS information by mixing and matching fields to get as complete information as possible.
     * If in question, information in this instance has precedence over information in the other instance.
     */
    fun merge(other: VcsInfo): VcsInfo {
        if (this == EMPTY) {
            return other
        }

        return VcsInfo(
            type.takeUnless { it == EMPTY.type } ?: other.type,
            url.takeUnless { it == EMPTY.url } ?: other.url,
            revision.takeUnless { it == EMPTY.revision } ?: other.revision,
            resolvedRevision.takeUnless { it == EMPTY.resolvedRevision } ?: other.resolvedRevision,
            path.takeUnless { it == EMPTY.path } ?: other.path
        )
    }

    /**
     * Return this [VcsInfo] in normalized form by applying [normalizeVcsUrl] to the [url].
     */
    fun normalize() = copy(url = normalizeVcsUrl(url))

    /**
     * Return a [VcsInfoCurationData] with the properties from this [VcsInfo].
     */
    fun toCuration() = VcsInfoCurationData(type, url, revision, resolvedRevision, path)
}

private class VcsInfoDeserializer : StdDeserializer<VcsInfo>(VcsInfo::class.java) {
    companion object {
        val KNOWN_FIELDS by lazy { VcsInfo::class.memberProperties.map { PROPERTY_NAMING_STRATEGY.translate(it.name) } }
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VcsInfo {
        val node = p.codec.readTree<JsonNode>(p)

        val fields = node.fieldNamesOrEmpty().asSequence().toList()
        (fields - KNOWN_FIELDS).let { unknownFields ->
            if (unknownFields.isNotEmpty()) {
                throw UnrecognizedPropertyException.from(p, VcsInfo::class.java, unknownFields.first(), KNOWN_FIELDS)
            }
        }

        return VcsInfo(
            VcsType(node["type"].textValueOrEmpty()),
            node["url"].textValueOrEmpty(),
            node["revision"].textValueOrEmpty(),
            (node["resolved_revision"] ?: node["resolvedRevision"])?.textValue(),
            node["path"].textValueOrEmpty()
        )
    }
}
