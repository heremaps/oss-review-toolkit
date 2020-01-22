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

package com.here.ort.analyzer.managers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.HTTP_CACHE_PATH
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.managers.utils.expandNpmShortcutURL
import com.here.ort.analyzer.managers.utils.hasNpmLockFile
import com.here.ort.analyzer.managers.utils.mapDefinitionFilesForNpm
import com.here.ort.analyzer.managers.utils.readProxySettingFromNpmRc
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Hash
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
import com.here.ort.model.createAndLogIssue
import com.here.ort.model.jsonMapper
import com.here.ort.model.readValue
import com.here.ort.spdx.SpdxLicense
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.Os
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.OkHttpClientHelper.applyProxySettingsFromUrl
import com.here.ort.utils.getUserHomeDirectory
import com.here.ort.utils.log
import com.here.ort.utils.realFile
import com.here.ort.utils.stashDirectories
import com.here.ort.utils.textValueOrEmpty

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.SortedSet

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The [Node package manager](https://www.npmjs.com/) for JavaScript.
 */
open class Npm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Npm>("NPM") {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Npm(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    /**
     * Array of parameters passed to the install command when installing dependencies.
     */
    protected open val installParameters = arrayOf("--ignore-scripts")

    protected open fun hasLockFile(projectDir: File) = hasNpmLockFile(projectDir)

    override fun command(workingDir: File?) = if (Os.isWindows) "npm.cmd" else "npm"

    override fun getVersionRequirement(): Requirement = Requirement.buildNPM("5.7.* - 6.13.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) = mapDefinitionFilesForNpm(definitionFiles).toList()

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to an NPM version, but we still want to stick to a
        // fixed minor version to be sure to get consistent results.
        checkVersion(ignoreActualVersion = analyzerConfig.ignoreToolVersions)

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile

        stashDirectories(File(workingDir, "node_modules")).use {
            // Actually installing the dependencies is the easiest way to get the meta-data of all transitive
            // dependencies (i.e. their respective "package.json" files). As NPM uses a global cache, the same
            // dependency is only ever downloaded once.
            installDependencies(workingDir)

            val packages = parseInstalledModules(workingDir)

            // Optional dependencies are just like regular dependencies except that NPM ignores failures when installing
            // them (see https://docs.npmjs.com/files/package.json#optionaldependencies), i.e. they are not a separate
            // scope in our semantics.
            val dependencies = parseDependencies(definitionFile, "dependencies", packages) +
                    parseDependencies(definitionFile, "optionalDependencies", packages)
            val dependenciesScope = Scope("dependencies", dependencies.toSortedSet())

            val devDependencies = parseDependencies(definitionFile, "devDependencies", packages)
            val devDependenciesScope = Scope("devDependencies", devDependencies)

            // TODO: add support for peerDependencies and bundledDependencies.

            return parseProject(
                definitionFile, sortedSetOf(dependenciesScope, devDependenciesScope),
                packages.values.toSortedSet()
            )
        }
    }

    private val applyProxySettingsFromNpmRc: OkHttpClient.Builder.() -> Unit = {
        val npmRcFile = getUserHomeDirectory().resolve(".npmrc")
        if (npmRcFile.isFile) {
            readProxySettingFromNpmRc(npmRcFile.readText())?.let { proxyUrl ->
                applyProxySettingsFromUrl(URL(proxyUrl))
            }
        }
    }

    private fun parseLicenses(json: JsonNode): SortedSet<String> {
        val declaredLicenses = sortedSetOf<String>()

        // See https://docs.npmjs.com/files/package.json#license. Some old packages use a "license" (singular) node
        // which ...
        json["license"]?.let { licenseNode ->
            // ... can either be a direct text value, an array of text values (which is not officially supported), or
            // an object containing nested "type" (and "url") text nodes.
            when {
                licenseNode.isTextual -> declaredLicenses += licenseNode.textValue()
                licenseNode.isArray -> licenseNode.mapNotNullTo(declaredLicenses) { it.textValue() }
                licenseNode.isObject -> declaredLicenses += licenseNode["type"].textValue()
                else -> throw IllegalArgumentException("Unsupported node type in '$licenseNode'.")
            }
        }

        // New packages use a "licenses" (plural) node containing an array of objects with nested "type" (and "url")
        // text nodes.
        json["licenses"]?.mapNotNullTo(declaredLicenses) { licenseNode ->
            licenseNode["type"]?.textValue()
        }

        return declaredLicenses.mapTo(sortedSetOf()) {
            // NPM does not mean https://unlicense.org/ here, but the wish to not "grant others the right to use a
            // private or unpublished package under any terms", which corresponds to SPDX's "NONE".
            if (it == "UNLICENSED") SpdxLicense.NONE else it
        }
    }

    private fun parseInstalledModules(rootDirectory: File): Map<String, Package> {
        val packages = mutableMapOf<String, Package>()
        val nodeModulesDir = File(rootDirectory, "node_modules")

        log.info { "Searching for 'package.json' files in '$nodeModulesDir'..." }

        nodeModulesDir.walkTopDown().filter {
            it.name == "package.json" && isValidNodeModulesDirectory(nodeModulesDir, nodeModulesDirForPackageJson(it))
        }.forEach {
            val packageDir = it.parentFile
            val realPackageDir = packageDir.realFile()
            val isSymbolicPackageDir = packageDir != realPackageDir

            log.debug {
                val prefix = "Found a 'package.json' file in '$packageDir'"
                if (isSymbolicPackageDir) {
                    "$prefix which links to '$realPackageDir'."
                } else {
                    "$prefix."
                }
            }

            val json = it.readValue<ObjectNode>()
            val rawName = json["name"].textValue()
            val (namespace, name) = splitNamespaceAndName(rawName)
            val version = json["version"].textValue()

            val declaredLicenses = parseLicenses(json)

            var description = json["description"].textValueOrEmpty()
            var homepageUrl = json["homepage"].textValueOrEmpty()
            var downloadUrl = json["_resolved"].textValueOrEmpty()

            var vcsFromPackage = parseVcsInfo(json)

            val identifier = "$rawName@$version"

            var hash = Hash.create(json["_integrity"].textValueOrEmpty())

            // Download package info from registry.npmjs.org.
            // TODO: check if unpkg.com can be used as a fallback in case npmjs.org is down.
            val encodedName = if (rawName.startsWith("@")) {
                "@${URLEncoder.encode(rawName.substringAfter("@"), "UTF-8")}"
            } else {
                rawName
            }

            if (isSymbolicPackageDir) {
                // Yarn workspaces refer to project dependencies from the same workspace via symbolic links. Use that
                // as the trigger to get VcsInfo locally instead of querying the NPM registry.
                log.debug { "Resolving the package info for '$identifier' locally." }

                val vcsFromDirectory = VersionControlSystem.forDirectory(realPackageDir)?.getInfo() ?: VcsInfo.EMPTY
                vcsFromPackage = vcsFromPackage.merge(vcsFromDirectory)
            } else {
                log.debug { "Resolving the package info for '$identifier' via NPM registry." }

                val pkgRequest = Request.Builder()
                    .get()
                    .url("https://registry.npmjs.org/$encodedName")
                    .build()

                OkHttpClientHelper.execute(HTTP_CACHE_PATH, pkgRequest, applyProxySettingsFromNpmRc).use { response ->
                    if (response.code == HttpURLConnection.HTTP_OK) {
                        log.debug {
                            if (response.cacheResponse != null) {
                                "Retrieved info about '$encodedName' from local cache."
                            } else {
                                "Downloaded info about '$encodedName' from NPM registry."
                            }
                        }

                        response.body?.let { body ->
                            val packageInfo = jsonMapper.readTree(body.string())

                            packageInfo["versions"][version]?.let { versionInfo ->
                                description = versionInfo["description"].textValueOrEmpty()
                                homepageUrl = versionInfo["homepage"].textValueOrEmpty()

                                versionInfo["dist"]?.let { dist ->
                                    downloadUrl = dist["tarball"].textValueOrEmpty().let { tarballUrl ->
                                        if (tarballUrl.startsWith("http://registry.npmjs.org/")) {
                                            // Work around the issue described at
                                            // https://npm.community/t/some-packages-have-dist-tarball-as-http-and-not-https/285/19.
                                            "https://" + tarballUrl.removePrefix("http://")
                                        } else {
                                            tarballUrl
                                        }
                                    }

                                    hash = Hash.create(dist["shasum"].textValueOrEmpty())
                                }

                                vcsFromPackage = parseVcsInfo(versionInfo)
                            }
                        }
                    } else {
                        log.info {
                            "Could not retrieve package information for '$encodedName' " +
                                    "from public NPM registry: ${response.message} (code ${response.code})."
                        }
                    }
                }
            }

            val vcsFromDownloadUrl = VersionControlSystem.splitUrl(expandNpmShortcutURL(downloadUrl))
            if (vcsFromDownloadUrl.url != downloadUrl) {
                vcsFromPackage = vcsFromPackage.merge(vcsFromDownloadUrl)
            }

            val module = Package(
                id = Identifier(
                    type = "NPM",
                    namespace = namespace,
                    name = name,
                    version = version
                ),
                declaredLicenses = declaredLicenses,
                description = description,
                homepageUrl = homepageUrl,
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact(
                    url = downloadUrl,
                    hash = hash
                ),
                vcs = vcsFromPackage,
                vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
            )

            require(module.id.name.isNotEmpty()) {
                "Generated package info for $identifier has no name."
            }

            require(module.id.version.isNotEmpty()) {
                "Generated package info for $identifier has no version."
            }

            packages[identifier] = module
        }

        return packages
    }

    private fun isValidNodeModulesDirectory(rootModulesDir: File, modulesDir: File?): Boolean {
        if (modulesDir == null) {
            return false
        }

        var currentDir: File = modulesDir
        while (currentDir != rootModulesDir) {
            if (currentDir.name != "node_modules") {
                return false
            }

            currentDir = currentDir.parentFile.parentFile
            if (currentDir.name.startsWith("@")) {
                currentDir = currentDir.parentFile
            }
        }

        return true
    }

    private fun nodeModulesDirForPackageJson(packageJson: File): File? {
        var modulesDir = packageJson.parentFile.parentFile
        if (modulesDir.name.startsWith("@")) {
            modulesDir = modulesDir.parentFile
        }

        return modulesDir.takeIf { it.name == "node_modules" }
    }

    private fun parseDependencies(packageJson: File, scope: String, packages: Map<String, Package>):
            SortedSet<PackageReference> {
        // Read package.json
        val json = jsonMapper.readTree(packageJson)
        val dependencies = sortedSetOf<PackageReference>()
        json[scope]?.let { dependencyMap ->
            log.debug { "Looking for dependencies in scope '$scope'." }
            dependencyMap.fields().forEach { (name, _) ->
                val modulesDir = packageJson.resolveSibling("node_modules")
                buildTree(modulesDir, modulesDir, name, packages)?.let { dependency ->
                    dependencies += dependency
                }
            }
        } ?: log.debug { "Could not find scope '$scope' in '$packageJson'." }

        return dependencies
    }

    private fun parseVcsInfo(node: JsonNode): VcsInfo {
        // See https://github.com/npm/read-package-json/issues/7 for some background info.
        val head = node["gitHead"].textValueOrEmpty()

        return node["repository"]?.let { repo ->
            val type = repo["type"].textValueOrEmpty()
            val url = repo.textValue() ?: repo["url"].textValueOrEmpty()
            val path = repo["directory"].textValueOrEmpty()
            VcsInfo(VcsType(type), expandNpmShortcutURL(url), head, path = path)
        } ?: VcsInfo(VcsType.NONE, "", head)
    }

    private fun buildTree(
        rootModulesDir: File, startModulesDir: File, name: String, packages: Map<String, Package>,
        dependencyBranch: List<String> = emptyList()
    ): PackageReference? {
        log.debug { "Building dependency tree for '$name' from directory '$startModulesDir'." }

        val packageFile = startModulesDir.resolve(name).resolve("package.json")

        if (packageFile.isFile) {
            log.debug { "Found package file for module '$name' at '$packageFile'." }

            val packageJson = jsonMapper.readTree(packageFile)
            val rawName = packageJson["name"].textValue()
            val version = packageJson["version"].textValue()
            val identifier = "$rawName@$version"

            if (identifier in dependencyBranch) {
                log.debug {
                    "Not adding circular dependency '$identifier' to the tree, it is already on this branch of the " +
                            "dependency tree: ${dependencyBranch.joinToString(" -> ")}."
                }
                return null
            }

            val dependencies = sortedSetOf<PackageReference>()

            val dependencyNames = mutableSetOf<String>()
            packageJson["dependencies"]?.fieldNames()?.forEach { dependencyNames += it }
            packageJson["optionalDependencies"]?.fieldNames()?.forEach { dependencyNames += it }

            val newDependencyBranch = dependencyBranch + identifier
            dependencyNames.forEach { dependencyName ->
                buildTree(
                    rootModulesDir, packageFile.resolveSibling("node_modules"),
                    dependencyName, packages, newDependencyBranch
                )?.let { dependencies += it }
            }

            val packageInfo = packages[identifier]
                ?: throw IOException("Could not find package info for $identifier")
            return packageInfo.toReference(dependencies = dependencies)
        } else if (rootModulesDir == startModulesDir) {
            val id = Identifier(managerName, "", name, "")

            val issue = createAndLogIssue(
                source = managerName,
                message = "Package '$name' was not installed, because the package file could not be found anywhere " +
                        "in '$rootModulesDir'. This might be fine if the module was not installed because it is " +
                        "specific to a different platform."
            )

            return PackageReference(id, issues = listOf(issue))
        } else {
            // Skip the package name directory when going up.
            var parentModulesDir = startModulesDir.parentFile.parentFile

            // For scoped packages we need to go one more directory up.
            if (parentModulesDir.name.startsWith("@")) {
                parentModulesDir = parentModulesDir.parentFile
            }

            // E.g. when using Yarn workspaces, the dependencies of the projects are consolidated in a single top-level
            // "node_modules" directory for de-duplication, so go up.
            log.debug {
                "Could not find package file for '$name' in '$startModulesDir', looking in '$parentModulesDir' instead."
            }

            return buildTree(rootModulesDir, parentModulesDir, name, packages, dependencyBranch)
        }
    }

    private fun parseProject(packageJson: File, scopes: SortedSet<Scope>, packages: SortedSet<Package>):
            ProjectAnalyzerResult {
        log.debug { "Parsing project info from '$packageJson'." }

        val json = jsonMapper.readTree(packageJson)

        val rawName = json["name"].textValueOrEmpty()
        val (namespace, name) = splitNamespaceAndName(rawName)
        if (name.isBlank()) {
            log.warn { "'$packageJson' does not define a name." }
        }

        val version = json["version"].textValueOrEmpty()
        if (version.isBlank()) {
            log.warn { "'$packageJson' does not define a version." }
        }

        val declaredLicenses = parseLicenses(json)
        val homepageUrl = json["homepage"].textValueOrEmpty()
        val projectDir = packageJson.parentFile
        val vcsFromPackage = parseVcsInfo(json)

        val project = Project(
            id = Identifier(
                type = managerName,
                namespace = namespace,
                name = name,
                version = version
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(packageJson).path,
            declaredLicenses = declaredLicenses,
            vcs = vcsFromPackage,
            vcsProcessed = processProjectVcs(projectDir, vcsFromPackage, homepageUrl),
            homepageUrl = homepageUrl,
            scopes = scopes
        )

        return ProjectAnalyzerResult(project, packages.mapTo(sortedSetOf()) { it.toCuratedPackage() })
    }

    /**
     * Install dependencies using the given package manager command.
     */
    private fun installDependencies(workingDir: File) {
        requireLockfile(workingDir) { hasLockFile(workingDir) }

        // Install all NPM dependencies to enable NPM to list dependencies.
        if (hasLockFile(workingDir) && this::class.java == Npm::class.java) {
            run(workingDir, "ci")
        } else {
            run(workingDir, "install", *installParameters)
        }

        // TODO: Capture warnings from npm output, e.g. "Unsupported platform" which happens for fsevents on all
        //       platforms except for Mac.
    }

    private fun splitNamespaceAndName(rawName: String): Pair<String, String> {
        val name = rawName.substringAfterLast("/")
        val namespace = rawName.removeSuffix(name).removeSuffix("/")
        return Pair(namespace, name)
    }
}
