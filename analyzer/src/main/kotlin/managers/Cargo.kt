/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageLinkage
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Hash
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.log
import com.here.ort.utils.textValueOrEmpty

import com.vdurmont.semver4j.Requirement
import com.moandjiezana.toml.Toml

import java.io.File
import java.util.SortedSet

class Cargo(
    name: String,
    analyzerRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analyzerRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Cargo>("Cargo") {
        override val globsForDefinitionFiles = listOf("Cargo.toml")

        override fun create(
            analyzerRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) =
            Cargo(managerName, analyzerRoot, analyzerConfig, repoConfig)
    }

    companion object {
        private const val REQUIRED_CARGO_VERSION = "1.0.0"
        private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
        private const val SCOPE_NAME_DEV_DEPENDENCIES = "devDependencies"
    }

    override fun command(workingDir: File?) = "cargo"

    override fun getVersionRequirement(): Requirement = Requirement.buildStrict(REQUIRED_CARGO_VERSION)

    private fun runMetadata(workingDir: File): String {
        return run(workingDir, "metadata", "--format-version=1").stdout
    }

    private fun extractCargoId(node: JsonNode): String {
        return node["id"].textValue()!!
    }

    private fun extractPackageId(node: JsonNode) = Identifier(
        type = "Cargo",
        namespace = "",
        name = node["name"].textValueOrEmpty(),
        version = node["version"].textValueOrEmpty()
    )

    private fun extractRepositoryUrl(node: JsonNode) =
        node["repository"]?.textValue()

    private fun extractVcsInfo(node: JsonNode): VcsInfo {
        val url = extractRepositoryUrl(node)
        val type = if (url != null) "git" else "" // for now cargo supports only git
        return VcsInfo(type, url.orEmpty(), revision = "")
    }

    private fun extractDeclaredLicenses(node: JsonNode): SortedSet<String> {
        return node["license"].textValueOrEmpty().split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSortedSet()
    }

    private fun extractSourceArtifact(
        node: JsonNode,
        hashes: Map<String, String>
    ): RemoteArtifact? {
        if (node["source"].textValueOrEmpty()
            == "registry+https://github.com/rust-lang/crates.io-index"
        ) {
            val name = node["name"]?.asText() ?: return null
            val version = node["version"]?.asText() ?: return null
            val url = "https://crates.io/api/v1/crates/$name/$version/download"
            val checksum = checksumKeyOf(node)
            val hash = Hash.create(hashes[checksum] ?: "")
            return RemoteArtifact(url, hash)
        } else {
            return null
        }
    }

    private fun extractPackage(node: JsonNode, hashes: Map<String, String>): Package {
        val vcsInfo = extractVcsInfo(node)
        return Package(
            id = extractPackageId(node),
            declaredLicenses = extractDeclaredLicenses(node),
            description = node["description"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = extractSourceArtifact(node, hashes) ?: RemoteArtifact.EMPTY,
            homepageUrl = "",
            vcs = vcsInfo,
            vcsProcessed = vcsInfo.normalize()
        )
    }

    private fun checksumKeyOf(metadata: JsonNode): String {
        val id = metadata["id"]!!.textValue()
        return "\"checksum $id\""
    }

    // Cargo.lock is located next to Cargo.toml or in one of the parent directories. The latter
    // is case when the project is part of a workspace. Cargo.lock is then located next to the
    // Cargo.toml file defining the workspace.
    private fun findCargoLock(definitionFile: File): File {
        var workingDir = definitionFile.parentFile
        while (workingDir != analyzerRoot.parentFile) {
            val lockfile = File(workingDir, "Cargo.lock")
            if (lockfile.exists()) {
                return lockfile
            }
            workingDir = workingDir.parentFile
        }
        // we reached the root directory we are analyzing
        throw IllegalArgumentException("missing Cargo.lock file")
    }

    private fun readHashes(lockfile: File): Map<String, String> {
        val contents = Toml().read(lockfile)
        val metadata = contents.getTable("metadata") ?: return emptyMap()
        val metadataMap = metadata.toMap()
        return metadataMap.asSequence().mapNotNull {
            val key = it.key
            val value = it.value as? String
            if (key != null && value != null) {
                Pair(key, value)
            } else {
                null
            }
        }.toMap()
    }

    private fun resolveDependenciesTree(
        metadata: JsonNode,
        packages: Map<String, Package>,
        filter: (pkgId: String, depId: String) -> Boolean = { _, _ -> true }
    ): SortedSet<PackageReference> {
        val resolve = metadata["resolve"]
        val nodes = resolve["nodes"]
        val root = resolve["root"].textValueOrEmpty()

        return resolveDependenciesOf(
            root, nodes, packages, filter
        ).dependencies
    }

    private fun resolveDependenciesOf(
        id: String,
        nodes: JsonNode,
        packages: Map<String, Package>,
        filter: (pkgId: String, depId: String) -> Boolean
    ): PackageReference {
        val node = nodes.find { it["id"].textValueOrEmpty() == id }!!
        val depsReferences = node["dependencies"].asSequence()
            .map { it.textValue()!! }
            .filter { filter(id, it) }
            .map { resolveDependenciesOf(it, nodes, packages, filter) }
        val pkg = packages[id]!!
        val linkage = if (isProjectDependency(id)) PackageLinkage.PROJECT_STATIC else PackageLinkage.STATIC
        return pkg.toReference(linkage, dependencies = depsReferences.toSortedSet())
    }

    private fun isDevDependencyOf(id: String, depId: String, metadata: JsonNode): Boolean {
        val packages = metadata["packages"]
        val pkg = packages.asSequence().find { it["id"].textValueOrEmpty() == id }!!
        val depPkg = packages.asSequence().find { it["id"].textValueOrEmpty() == depId }!!
        val dep = pkg["dependencies"].find {
            val name = it["name"].textValueOrEmpty()
            name == depPkg["name"].textValueOrEmpty()
        }!!
        return dep["kind"].textValueOrEmpty() == "dev"
    }

    // Check if a package is a project
    //
    // We treat all path dependencies inside of the analyzer root as project dependencies
    private fun isProjectDependency(id: String): Boolean {
        val pathRegex = Regex("""^.*\(path\+file://(.*)\)$""")
        val match = pathRegex.matchEntire(id)?.groups?.get(1)
        if (match != null) {
            val packageDir = File(match.value)
            return packageDir.startsWith(analyzerRoot)
        }
        return false
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        log.info { "Resolving dependencies for: '$definitionFile'" }

        // get project name; if none => we have a workspace definition => return null
        val pkgDefinition = Toml().read(definitionFile)
        val projectName = pkgDefinition.getString("package.name") ?: return null
        val projectVersion = pkgDefinition.getString("package.version") ?: return null

        val workingDir = definitionFile.parentFile
        val metadataJson = runMetadata(workingDir)
        val metadata = jsonMapper.readTree(metadataJson)
        val hashes = readHashes(findCargoLock(definitionFile))

        // collect all packages
        val packages: Map<String, Package> = metadata["packages"].asSequence().associateBy(
            { extractCargoId(it) },
            { extractPackage(it, hashes) })

        // resolve dependencies tree
        val dependencies = resolveDependenciesTree(metadata, packages,
            { id, devId -> !isDevDependencyOf(id, devId, metadata) })

        val devDependencies = resolveDependenciesTree(metadata, packages,
            { id, devId -> isDevDependencyOf(id, devId, metadata) })

        val dependenciesScope = Scope(
            name = SCOPE_NAME_DEPENDENCIES,
            dependencies = dependencies
        )
        val devDependenciesScope = Scope(
            name = SCOPE_NAME_DEV_DEPENDENCIES,
            dependencies = devDependencies
        )

        // resolve project
        val projectPkg = packages.values.asSequence().find {
            val pkgId = it.id
            pkgId.name == projectName && pkgId.version == projectVersion
        }!!

        val homepageUrl = pkgDefinition.getString("package.homepage") ?: ""
        val project = Project(
            id = projectPkg.id,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            declaredLicenses = projectPkg.declaredLicenses,
            vcs = projectPkg.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPkg.vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopes = sortedSetOf(dependenciesScope, devDependenciesScope)
        )

        val nonProjectPackages = packages
            .filter { !isProjectDependency(it.key) }
            .map { it.value.toCuratedPackage() }
            .toSortedSet()

        return ProjectAnalyzerResult(project, nonProjectPackages)
    }
}
