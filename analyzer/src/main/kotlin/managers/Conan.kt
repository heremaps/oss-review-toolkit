/*
 * Copyright (C) 2019 HERE Europe B.V.
 * Copyright (C) 2019 Verifa Oy.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.getUserHomeDirectory
import com.here.ort.utils.log
import com.here.ort.utils.stashDirectories
import com.here.ort.utils.textValueOrEmpty
import com.here.ort.utils.ProcessCapture

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.util.SortedSet
import java.util.Stack

/**
 * The [Conan](https://conan.io/) package manager for C / C++.
 *
 * TODO: Add support for `python_requires`.
 */
class Conan(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        private const val REQUIRED_CONAN_VERSION = "1.18.0"

        private const val SCOPE_NAME_DEPENDENCIES = "requires"
        private const val SCOPE_NAME_DEV_DEPENDENCIES = "build_requires"
    }

    class Factory : AbstractPackageManagerFactory<Conan>("Conan") {
        override val globsForDefinitionFiles = listOf("conanfile.txt", "conanfile.py")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Conan(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "conan"

    // TODO: Add support for Conan lock files.
    // protected open fun hasLockFile(projectDir: File) = null

    override fun getVersionRequirement(): Requirement = Requirement.buildStrict(REQUIRED_CONAN_VERSION)

    override fun beforeResolution(definitionFiles: List<File>) =
        // Conan could report version strings like:
        // Conan version 1.18.0
        checkVersion(
            ignoreActualVersion = analyzerConfig.ignoreToolVersions,
            transform = { it.removePrefix("Conan version ") }
        )

    /**
     * Primary method for resolving dependencies from [definitionFile].
     */
    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        log.info { "Resolving dependencies for: '$definitionFile'" }

        val conanHome = getUserHomeDirectory().resolve(".conan")

        stashDirectories(File(conanHome.resolve("data").path)).use {
            val workingDir = definitionFile.parentFile

            installDependencies(workingDir)

            val dependenciesJson = run(workingDir, "info", ".", "-j").stdout
            val rootNode = jsonMapper.readTree(dependenciesJson)
            val packageList = removeProjectPackage(rootNode, definitionFile)
            val packages = extractPackages(packageList, workingDir)

            val dependenciesScope = Scope(
                name = SCOPE_NAME_DEPENDENCIES,
                dependencies = extractDependencies(rootNode, SCOPE_NAME_DEPENDENCIES, workingDir)
            )
            val devDependenciesScope = Scope(
                name = SCOPE_NAME_DEV_DEPENDENCIES,
                dependencies = extractDependencies(rootNode, SCOPE_NAME_DEV_DEPENDENCIES, workingDir)
            )

            val projectPackage = extractProjectPackage(rootNode, definitionFile, workingDir)

            return ProjectAnalyzerResult(
                project = Project(
                    id = projectPackage.id,
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    declaredLicenses = projectPackage.declaredLicenses,
                    vcs = projectPackage.vcs,
                    vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
                    homepageUrl = projectPackage.homepageUrl,
                    scopes = sortedSetOf(dependenciesScope, devDependenciesScope)
                ),
                packages = packages.map { it.value.toCuratedPackage() }.toSortedSet()
            )
        }
    }

    /**
     * Return the dependency tree starting from a [rootNode] for given [scopeName].
     */
    private fun extractDependencyTree(
        rootNode: JsonNode,
        workingDir: File,
        pkg: JsonNode,
        scopeName: String
    ): SortedSet<PackageReference> {
        val result = mutableSetOf<PackageReference>()

        pkg[scopeName]?.forEach {
            val childRef = it.textValueOrEmpty()
            rootNode.iterator().forEach { child ->
                if (child["reference"].textValueOrEmpty() == childRef) {
                    log.debug { "Found child '$childRef'." }

                    val packageReference = PackageReference(
                        id = extractPackageId(child, workingDir),
                        dependencies = extractDependencyTree(rootNode, workingDir, child, SCOPE_NAME_DEPENDENCIES)
                    )
                    result.add(packageReference)

                    val packageDevReference = PackageReference(
                        id = extractPackageId(child, workingDir),
                        dependencies = extractDependencyTree(rootNode, workingDir, child, SCOPE_NAME_DEV_DEPENDENCIES)
                    )

                    result.add(packageDevReference)
                }
            }
        }
        return result.toSortedSet()
    }

    /**
     * Run through each package and extract list of its dependencies (also transitive ones).
     */
    private fun extractDependencies(
        rootNode: JsonNode,
        scopeName: String,
        workingDir: File
    ): SortedSet<PackageReference> {
        val stack = Stack<JsonNode>().apply { addAll(rootNode) }
        val dependencies = mutableSetOf<PackageReference>()

        while (!stack.empty()) {
            val pkg = stack.pop()
            extractDependencyTree(rootNode, workingDir, pkg, scopeName).forEach {
                dependencies.add(it)
            }
        }
        return dependencies.toSortedSet()
    }

    /**
     * Return the map of packages and their identifiers which are contained in [node].
     */
    private fun extractPackages(node: List<JsonNode>, workingDir: File): Map<String, Package> {
        val result = mutableMapOf<String, Package>()
        val stack = Stack<JsonNode>().apply { addAll(node) }

        while (!stack.empty()) {
            val currentNode = stack.pop()
            val pkg = extractPackage(currentNode, workingDir)
            result["${pkg.id.name}:${pkg.id.version}"] = pkg
        }

        return result
    }

    /**
     * Return the [Package] extracted from given [node].
     */
    private fun extractPackage(node: JsonNode, workingDir: File) =
        Package(
            id = extractPackageId(node, workingDir),
            declaredLicenses = extractDeclaredLicenses(node),
            description = extractPackageField(node, workingDir, "description"),
            homepageUrl = node["homepage"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = extractVcsInfo(node)
        )

    /**
     * Return the value `conan inspect` reports for the given [field].
     */
    private fun runInspectRawField(pkgName: String, workingDir: File, field: String): String =
        run(workingDir, "inspect", pkgName, "--raw", field).stdout

    /**
     * Return the full list of packages, excluding the project level information.
     */
    private fun removeProjectPackage(rootNode: JsonNode, definitionFile: File): List<JsonNode> =
        rootNode.find {
            // Contains because conanfile.py's reference string often includes other data.
            it["reference"].textValueOrEmpty().contains(definitionFile.name)
        }?.let { projectPackage ->
            rootNode.minusElement(projectPackage)
        } ?: rootNode.toList<JsonNode>()

    /**
     * Return the set of declared licenses contained in [node].
     */
    private fun extractDeclaredLicenses(node: JsonNode): SortedSet<String> =
        sortedSetOf<String>().also { licenses ->
            node["license"]?.mapNotNullTo(licenses) { it.textValue() }
        }

    /**
     * Return the [Identifier] for the package contained in [node].
     */
    private fun extractPackageId(node: JsonNode, workingDir: File) =
        Identifier(
            type = "Conan",
            namespace = "",
            name = extractPackageField(node, workingDir, "name"),
            version = extractPackageField(node, workingDir, "version")
        )

    /**
     * Return the [VcsInfo] contained in [node].
     */
    private fun extractVcsInfo(node: JsonNode) =
        VcsInfo(
            type = VcsType.GIT,
            url = node["url"].textValueOrEmpty(),
            revision = if (node["revision"].textValueOrEmpty() == "0") "" else node["revision"].textValueOrEmpty()
        )

    /**
     * Return the value of [field] from the output of `conan inspect --raw` for the package in [node].
     */
    private fun extractPackageField(node: JsonNode, workingDir: File, field: String): String =
        runInspectRawField(node["display_name"].textValue(), workingDir, field)

    /**
     * Return a [Package] containing project-level information depending on which [definitionFile] was found:
     * - conanfile.txt: `conan inspect conanfile.txt` is not supported.
     * - conanfile.py: `conan inspect conanfile.py` is supported and more useful project metadata is extracted.
     *
     * TODO: The format of `conan info` output for a conanfile.txt file may be such that we can get project metadata
     *       from the `requires` field. Need to investigate whether this is a sure thing before implementing.
     */
    private fun extractProjectPackage(rootNode: JsonNode, definitionFile: File, workingDir: File): Package {
        val projectPackageJson = requireNotNull(rootNode.find {
            it["reference"].textValue().contains(definitionFile.name)
        })

        return if (definitionFile.name == "conanfile.py") {
            generateProjectPackageFromConanfilePy(projectPackageJson, definitionFile, workingDir)
        } else {
            generateProjectPackageFromConanfileTxt(projectPackageJson)
        }
    }

    /**
     * Return a [Package] containing project-level information extracted from [node] and [definitionFile] using the
     * `conan inspect` command.
     */
    private fun generateProjectPackageFromConanfilePy(node: JsonNode, definitionFile: File, workingDir: File): Package =
        Package(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = runInspectRawField(definitionFile.name, workingDir, "name"),
                version = runInspectRawField(definitionFile.name, workingDir, "version")
            ),
            declaredLicenses = extractDeclaredLicenses(node),
            description = runInspectRawField(definitionFile.name, workingDir, "description"),
            homepageUrl = node["homepage"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = extractVcsInfo(node)
        )

    /**
     * Return a [Package] containing project-level information extracted from [node].
     */
    private fun generateProjectPackageFromConanfileTxt(node: JsonNode): Package =
        Package(
            id = Identifier(
                    type = managerName,
                namespace = "",
                name = node["reference"].textValueOrEmpty(),
                version = ""
            ),
            declaredLicenses = extractDeclaredLicenses(node),
            description = "",
            homepageUrl = node["homepage"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = extractVcsInfo(node)
        )

    /**
     * Run `conan install .` to install packages in [workingDir]. The `conan install .` command looks for the package
     * in the remote repository that is built for the same architecture as the host that runs this command. That package
     * may not exist in the remote and in that case the command will fail. As this is acceptable since package
     * metadata is fetched anyway, ignore the exit code by not using [run] but [ProcessCapture] directly.
     */
    private fun installDependencies(workingDir: File) {
        ProcessCapture(workingDir, "conan", "install", ".")
    }
}
