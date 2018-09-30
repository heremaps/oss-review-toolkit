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

package com.here.ort.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import config.CacheConfiguration

data class ArtifactoryCacheConfiguration(
        val url: String,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        val apiToken: String = ""

) : CacheConfiguration {
    override fun validate() {
        require(url.isNotBlank()) {
            "URL for Artifactory cache is missing."
        }

        require(apiToken.isNotBlank()) {
            "API token for Artifactory cache is missing."
        }
    }
}
