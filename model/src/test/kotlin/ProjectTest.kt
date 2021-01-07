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

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File
import java.time.Instant
import java.util.*

import kotlin.io.path.createTempFile

import org.ossreviewtoolkit.utils.test.containExactly

private fun readAnalyzerResult(analyzerResultFilename: String): Project =
    File("../analyzer/src/funTest/assets/projects/synthetic")
        .resolve(analyzerResultFilename)
        .readValue<ProjectAnalyzerResult>().project

private val exampleId = Identifier("Gradle:org.ossreviewtoolkit.gradle.example:lib:1.0.0")
private val textId = Identifier("Maven:org.apache.commons:commons-text:1.1")
private val langId = Identifier("Maven:org.apache.commons:commons-lang3:3.5")
private val strutsId = Identifier("Maven:org.apache.struts:struts2-assembly:2.5.14.1")
private val csvId = Identifier("Maven:org.apache.commons:commons-csv:1.4")

/**
 * Create a [Project] whose dependencies are represented as a [DependencyGraph].
 */
private fun projectWithDependencyGraph(): Project =
    Project(
        id = Identifier.EMPTY,
        definitionFilePath = "/some/path",
        declaredLicenses = sortedSetOf(),
        vcs = VcsInfo.EMPTY,
        homepageUrl = "https//www.test-project.org",
        scopeDependencies = sortedSetOf(),
        dependencyGraph = createDependencyGraph()
    )

/**
 * Create a [DependencyGraph] containing some test dependencies.
 */
private fun createDependencyGraph(): DependencyGraph {
    val langRef = PackageReference(langId)
    val textRef = PackageReference(textId, dependencies = sortedSetOf(langRef))
    val strutsRef = PackageReference(strutsId)
    val csvRef = PackageReference(csvId, dependencies = sortedSetOf(langRef))
    val exampleRef = PackageReference(exampleId, dependencies = sortedSetOf(textRef, strutsRef))

    val scopeMapping = mapOf(
        "default" to setOf(exampleId),
        "compile" to setOf(exampleId),
        "test" to setOf(exampleId, csvId),
        "partial" to setOf(textId)
    )

    return DependencyGraph(setOf(exampleRef, csvRef), scopeMapping)
}

/**
 * Lookup the scope with the given [name] in the set of [scopes].
 */
private fun findScope(scopes: SortedSet<Scope>, name: String): Scope =
    scopes.find { it.name == name } ?: fail("Could not resolve scope $name.")

/**
 * Return a set with the identifiers of the (direct) dependencies of the given [scope].
 */
private fun scopeDependencies(scope: Scope): Set<Identifier> =
    scope.dependencies.map { it.id }.toSet()

class ProjectTest : WordSpec({
    "collectDependencies" should {
        "get all dependencies by default" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies().map { it.toCoordinates() }

            dependencies should containExactlyInAnyOrder(
                "Maven:junit:junit:4.12",
                "Maven:org.apache.commons:commons-lang3:3.5",
                "Maven:org.apache.commons:commons-text:1.1",
                "Maven:org.apache.struts:struts2-assembly:2.5.14.1",
                "Maven:org.hamcrest:hamcrest-core:1.3"
            )
        }

        "get no dependencies for a depth of 0" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies(maxDepth = 0)

            dependencies should beEmpty()
        }

        "get only direct dependencies for a depth of 1" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies(maxDepth = 1).map { it.toCoordinates() }

            dependencies should containExactlyInAnyOrder(
                "Maven:junit:junit:4.12",
                "Maven:org.apache.commons:commons-text:1.1",
                "Maven:org.apache.struts:struts2-assembly:2.5.14.1"
            )
        }
    }

    "collectIssues" should {
        "find all issues" {
            val project = readAnalyzerResult("gradle-expected-output-lib-without-repo.yml")

            val issues = project.collectIssues()

            issues should containExactly(
                Identifier("Unknown:org.apache.commons:commons-text:1.1") to setOf(
                    OrtIssue(
                        Instant.EPOCH,
                        "Gradle",
                        "Unresolved: ModuleVersionNotFoundException: Cannot resolve external dependency " +
                                "org.apache.commons:commons-text:1.1 because no repositories are defined."
                    )
                ),
                Identifier("Unknown:junit:junit:4.12") to setOf(
                    OrtIssue(
                        Instant.EPOCH,
                        "Gradle",
                        "Unresolved: ModuleVersionNotFoundException: Cannot resolve external dependency " +
                                "junit:junit:4.12 because no repositories are defined."
                    )
                )
            )
        }
    }

    "scopes" should {
        "be initialized from scope dependencies" {
            val project = readAnalyzerResult("gradle-expected-output-lib-without-repo.yml")

            project.scopes shouldBe project.scopeDependencies
        }

        "be initialized from a dependency graph" {
            val project = projectWithDependencyGraph()
            val scopes = project.scopes
            scopes.map { it.name } shouldContainExactly listOf("compile", "default", "partial", "test")

            val defaultScope = findScope(scopes, "default")
            scopeDependencies(defaultScope) should containExactly(exampleId)
            val testScope = findScope(scopes, "test")
            scopeDependencies(testScope) should containExactlyInAnyOrder(exampleId, csvId)
            val partialScope = findScope(scopes, "partial")
            scopeDependencies(partialScope) should containExactly(textId)
        }
    }

    "Project" should {
        "be serializable with a dependency graph" {
            val outputFile = createTempFile(prefix = "project", suffix = ".yml").toFile().apply {
                deleteOnExit()
            }

            val project = projectWithDependencyGraph()
            jsonMapper.writeValue(outputFile, project)

            val projectCopy = jsonMapper.readValue(outputFile, Project::class.java)
            projectCopy.scopes shouldBe project.scopes
        }
    }
})
