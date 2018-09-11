/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer.integration

import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.analyzer.managers.Bower
import com.here.ort.analyzer.managers.NPM
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo

import java.io.File

class PolymerIntegrationTest : AbstractIntegrationSpec() {
    override val pkg: Package = Package(
            id = Identifier(
                    provider = "Bower",
                    namespace = "",
                    name = "polymer",
                    version = "2.4.0"
            ),
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(
                    type = "Git",
                    url = "https://github.com/Polymer/polymer.git",
                    revision = "v2.4.0"
            )
    )

    fun findDownloadedFiles( vararg filenames: String ): MutableList<File>
    {
        return downloadResult.downloadDirectory.walkTopDown()
                .filter { it.name in filenames }.toMutableList()
    }

    override val expectedManagedFiles by lazy {
        val bowerFiles = findDownloadedFiles("bower.json")
        val npmFiles = findDownloadedFiles( "package.json")

        mapOf(Bower.Factory() as PackageManagerFactory to bowerFiles,
                NPM.Factory() as PackageManagerFactory to npmFiles)
    }
}
