/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import com.here.ort.analyzer.MavenLogger
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Scope
import com.here.ort.util.log

import java.io.File

import org.apache.maven.artifact.repository.LegacyLocalRepositoryManager
import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.project.DefaultProjectBuildingRequest
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.project.ProjectBuildingResult
import org.apache.maven.repository.internal.MavenRepositorySystemUtils

import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.logging.BaseLoggerManager

import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.metadata.Metadata
import org.eclipse.aether.repository.LocalArtifactRegistration
import org.eclipse.aether.repository.LocalArtifactRequest
import org.eclipse.aether.repository.LocalArtifactResult
import org.eclipse.aether.repository.LocalMetadataRegistration
import org.eclipse.aether.repository.LocalMetadataRequest
import org.eclipse.aether.repository.LocalMetadataResult
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.LocalRepositoryManager
import org.eclipse.aether.repository.RemoteRepository

class Maven : PackageManager() {
    companion object : PackageManagerFactory<Maven>(
            "https://maven.apache.org/",
            "Java",
            listOf("pom.xml")
    ) {
        val SCM_REGEX = Regex("scm:[^:]+:(.+)")

        override fun create() = Maven()
    }

    /**
     * Set of scope names for which [Scope.delivered] will be set to false by default. This can be changed later by
     * manually editing the output file.
     */
    private val assumedNonDeliveredScopes = setOf("test")

    private val container = createContainer()
    private val repositorySystemSession = createRepositorySystemSession()
    private val projectsByIdentifier = mutableMapOf<String, ProjectBuildingResult>()

    override fun command(workingDir: File) = "mvn"

    override fun prepareResolution(definitionFiles: List<File>) {
        val projectBuilder = container.lookup(ProjectBuilder::class.java, "default")
        val projectBuildingRequest = createProjectBuildingRequest(false)
        val projectBuildingResults = projectBuilder.build(definitionFiles, false, projectBuildingRequest)

        projectBuildingResults.forEach { projectBuildingResult ->
            val project = projectBuildingResult.project
            val identifier = "${project.groupId}:${project.artifactId}:${project.version}"

            projectsByIdentifier[identifier] = projectBuildingResult
        }
    }

    override fun resolveDependencies(projectDir: File, workingDir: File, definitionFile: File): AnalyzerResult? {
        val projectBuildingResult = buildMavenProject(definitionFile)
        val mavenProject = projectBuildingResult.project
        val packages = mutableMapOf<String, Package>()
        val scopes = mutableMapOf<String, Scope>()

        projectBuildingResult.dependencyResolutionResult.dependencyGraph.children.forEach { node ->
            val scopeName = node.dependency.scope
            val scope = scopes.getOrPut(scopeName) {
                Scope(scopeName, scopeName !in assumedNonDeliveredScopes, sortedSetOf())
            }

            scope.dependencies.add(parseDependency(node, packages))
        }

        val vcsInfo = parseVcsInfo(mavenProject).let {
            if (it.isEmpty()) {
                val vcs = VersionControlSystem.fromDirectory(projectDir)
                VcsInfo(vcs?.javaClass?.simpleName ?: "", vcs?.getRemoteUrl(projectDir) ?: "",
                        vcs?.getWorkingRevision(projectDir) ?: "")
            } else it
        }
        val project = Project(
                packageManager = javaClass.simpleName,
                namespace = mavenProject.groupId,
                name = mavenProject.artifactId,
                version = mavenProject.version,
                declaredLicenses = mavenProject.licenses.map { it.name }.toSortedSet(),
                aliases = emptyList(),
                vcsProvider = vcsInfo.provider,
                vcsUrl = vcsInfo.url,
                vcsRevision = vcsInfo.revision,
                vcsPath = "",
                homepageUrl = mavenProject.url,
                scopes = scopes.values.toSortedSet()
        )

        return AnalyzerResult(true, project, packages.values.toSortedSet())
    }

    private fun buildMavenProject(pomFile: File): ProjectBuildingResult {
        val projectBuilder = container.lookup(ProjectBuilder::class.java, "default")
        val projectBuildingRequest = createProjectBuildingRequest(true)

        return projectBuilder.build(pomFile, projectBuildingRequest)
    }

    private fun createContainer(): PlexusContainer {
        val configuration = DefaultContainerConfiguration().apply {
            autoWiring = true
            classPathScanning = PlexusConstants.SCANNING_INDEX
            classWorld = ClassWorld("plexus.core", javaClass.classLoader)
        }

        return DefaultPlexusContainer(configuration).apply {
            loggerManager = object : BaseLoggerManager() {
                override fun createLogger(name: String) = MavenLogger(log.effectiveLevel)
            }
        }
    }

    private fun createProjectBuildingRequest(resolveDependencies: Boolean) =
            DefaultProjectBuildingRequest().apply {
                isResolveDependencies = resolveDependencies
                repositorySession = repositorySystemSession
                systemProperties["java.home"] = System.getProperty("java.home")
                systemProperties["java.version"] = System.getProperty("java.version")
            }


    private fun createRepositorySystemSession(): RepositorySystemSession {
        val mavenRepositorySystem = container.lookup(MavenRepositorySystem::class.java, "default")
        val aetherRepositorySystem = container.lookup(RepositorySystem::class.java, "default")
        val repositorySystemSession = MavenRepositorySystemUtils.newSession()
        val mavenExecutionRequest = DefaultMavenExecutionRequest()
        val localRepository = mavenRepositorySystem.createLocalRepository(mavenExecutionRequest,
                org.apache.maven.repository.RepositorySystem.defaultUserLocalRepository)

        val session = LegacyLocalRepositoryManager.overlay(localRepository, repositorySystemSession,
                aetherRepositorySystem)
        val wrapper = LocalRepositoryManagerWrapper(session.localRepositoryManager)

        return DefaultRepositorySystemSession(session).setLocalRepositoryManager(wrapper)
    }

    private fun parseDependency(node: DependencyNode, packages: MutableMap<String, Package>): PackageReference {
        val pkg = packages.getOrPut(node.artifact.identifier()) {
            parsePackage(node)
        }

        val dependencies = node.children.map { parseDependency(it, packages) }.toSortedSet()

        return pkg.toReference(dependencies)
    }

    private fun parsePackage(node: DependencyNode): Package {
        val mavenRepositorySystem = container.lookup(MavenRepositorySystem::class.java, "default")
        val projectBuilder = container.lookup(ProjectBuilder::class.java, "default")
        val projectBuildingRequest = createProjectBuildingRequest(true).apply {
            validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
        }

        val cachedProject = projectsByIdentifier[node.artifact.identifier()]

        val mavenProject = cachedProject?.project ?:
                node.artifact.let {
                    val artifact = mavenRepositorySystem
                            .createArtifact(it.groupId, it.artifactId, it.version, "", "pom")

                    projectBuilder.build(artifact, projectBuildingRequest).project
                }

        return Package(
                packageManager = javaClass.simpleName,
                namespace = mavenProject.groupId,
                name = mavenProject.artifactId,
                version = mavenProject.version,
                declaredLicenses = mavenProject.licenses.map { it.name }.toSortedSet(),
                description = mavenProject.description ?: "",
                homepageUrl = mavenProject.url,
                downloadUrl = "", // TODO: Try to get URL for downloaded dependencies.
                hash = "", // TODO: Get hash from local metadata?
                hashAlgorithm = "",
                vcsProvider = parseVcsProvider(mavenProject),
                vcsUrl = parseVcsUrl(mavenProject),
                vcsRevision = parseVcsRevision(mavenProject),
                vcsPath = ""
        )
    }

    private fun parseVcsInfo(mavenProject: MavenProject) =
            VcsInfo(parseVcsProvider(mavenProject), parseVcsUrl(mavenProject), parseVcsRevision(mavenProject))

    private fun parseVcsProvider(mavenProject: MavenProject): String {
        mavenProject.scm?.connection?.split(":")?.let {
            if (it.size > 1 && it[0] == "scm") {
                return it[1]
            }
        }
        return ""
    }

    private fun parseVcsUrl(mavenProject: MavenProject) =
            mavenProject.scm?.connection?.let {
                SCM_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)
            } ?: ""

    private fun parseVcsRevision(mavenProject: MavenProject) = mavenProject.scm?.tag ?: ""

    /**
     * A wrapper for the [LocalRepositoryManager] used in [repositorySystemSession] that pretends that the POM files of
     * the currently analyzed project are available in the local repository. Without it the resolution of transitive
     * dependencies of project dependencies would not work without installing the project in the local repository first.
     */
    private inner class LocalRepositoryManagerWrapper(private val localRepositoryManager: LocalRepositoryManager)
        : LocalRepositoryManager {

        override fun add(session: RepositorySystemSession, request: LocalArtifactRegistration) =
                localRepositoryManager.add(session, request)

        override fun add(session: RepositorySystemSession, request: LocalMetadataRegistration) =
                localRepositoryManager.add(session, request)

        override fun find(session: RepositorySystemSession, request: LocalArtifactRequest): LocalArtifactResult {
            log.debug { "Request to find local artifact: $request" }

            projectsByIdentifier[request.artifact.identifier()]?.let {
                log.debug { "Found cached artifact: ${it.pomFile}" }
                val pomFile = File(it.pomFile.absolutePath)

                return LocalArtifactResult(request).apply {
                    file = pomFile
                    isAvailable = true
                }
            }

            return localRepositoryManager.find(session, request)
        }

        override fun find(session: RepositorySystemSession, request: LocalMetadataRequest): LocalMetadataResult =
                localRepositoryManager.find(session, request)

        override fun getPathForLocalArtifact(artifact: Artifact): String {
            log.debug { "Request path for local artifact: $artifact" }

            projectsByIdentifier[artifact.identifier()]?.let {
                log.debug { "Found cached artifact: ${it.pomFile}" }
                return it.pomFile.absolutePath
            }

            return localRepositoryManager.getPathForLocalArtifact(artifact)
        }

        override fun getPathForLocalMetadata(metadata: Metadata)
                : String = localRepositoryManager.getPathForLocalMetadata(metadata)

        override fun getPathForRemoteArtifact(artifact: Artifact, repository: RemoteRepository, context: String)
                : String = localRepositoryManager.getPathForRemoteArtifact(artifact, repository, context)

        override fun getPathForRemoteMetadata(metadata: Metadata, repository: RemoteRepository, context: String)
                : String = localRepositoryManager.getPathForRemoteMetadata(metadata, repository, context)

        override fun getRepository(): LocalRepository = localRepositoryManager.repository
    }

    private data class VcsInfo(val provider: String, val url: String, val revision: String) {
        fun isEmpty() = provider.isEmpty() && url.isEmpty() && revision.isEmpty()
    }

    fun Artifact.identifier() = "$groupId:$artifactId:$version"
}
