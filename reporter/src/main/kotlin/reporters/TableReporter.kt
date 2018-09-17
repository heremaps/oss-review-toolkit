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

package com.here.ort.reporter.reporters

import com.here.ort.model.Error
import com.here.ort.model.Identifier
import com.here.ort.model.OrtResult
import com.here.ort.model.Project
import com.here.ort.model.ScanRecord
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.ProjectExclude
import com.here.ort.model.config.ScopeExclude
import com.here.ort.reporter.Reporter
import com.here.ort.utils.zipWithDefault

import java.io.File
import java.util.SortedMap
import java.util.SortedSet

/**
 * An abstract [Reporter] that converts the [ScanRecord] to a table representation.
 */
abstract class TableReporter : Reporter() {
    data class TabularScanRecord(
            /**
             * The [VcsInfo] for the scanned project.
             */
            val vcsInfo: VcsInfo,

            /**
             * A [ProjectTable] containing all dependencies that caused errors.
             */
            val errorSummary: ErrorTable,

            /**
             * A [ProjectTable] containing the dependencies of all [Project]s.
             */
            val summary: ProjectTable,

            /**
             * The [ProjectTable]s containing the dependencies for each [Project].
             */
            val projectDependencies: SortedMap<Project, ProjectTable>,

            /**
             * Additional metadata read from the "reporter.metadata" field in [OrtResult.data].
             */
            val metadata: Map<String, String>,

            /**
             * Extra columns that shall be added to the results table by the implementing reporter.
             */
            val extraColumns: List<String>
    )

    data class ProjectTable(
            /**
             * The dependencies of this project.
             */
            val rows: List<DependencyRow>,

            /**
             * Information about if and why the project is excluded.
             */
            val exclude: ProjectExclude?
    )

    data class DependencyRow(
            /**
             * The identifier of the package.
             */
            val id: Identifier,

            /**
             * The scopes the package is used in.
             */
            val scopes: SortedMap<String, List<ScopeExclude>>,

            /**
             * The licenses declared by the package.
             */
            val declaredLicenses: SortedSet<String>,

            /**
             * The detected licenses aggregated from all [ScanResult]s for this package.
             */
            val detectedLicenses: SortedSet<String>,

            /**
             * All analyzer errors related to this package.
             */
            val analyzerErrors: List<Error>,

            /**
             * All scan errors related to this package.
             */
            val scanErrors: List<Error>
    ) {
        fun merge(other: DependencyRow) =
                DependencyRow(
                        id = id,
                        scopes = scopes.zipWithDefault(other.scopes, emptyList()) { a, b -> a + b }.toSortedMap(),
                        declaredLicenses = (declaredLicenses + other.declaredLicenses).toSortedSet(),
                        detectedLicenses = (detectedLicenses + other.detectedLicenses).toSortedSet(),
                        analyzerErrors = (analyzerErrors + other.analyzerErrors).distinct(),
                        scanErrors = (scanErrors + other.scanErrors).distinct()
                )
    }

    data class ErrorTable(
            val rows: List<ErrorRow>
    )

    data class ErrorRow(
            /**
             * The identifier of the package.
             */
            val id: Identifier,

            /**
             * All analyzer errors related to this package, grouped by the [Identifier] of the [Project] they appear in.
             */
            val analyzerErrors: SortedMap<Identifier, List<Error>>,

            /**
             * All scan errors related to this package, grouped by the [Identifier] of the [Project] they appear in.
             */
            val scanErrors: SortedMap<Identifier, List<Error>>
    ) {
        fun merge(other: ErrorRow): ErrorRow {
            val plus = { left: List<Error>, right: List<Error> -> left + right }

            return ErrorRow(
                    id = id,
                    analyzerErrors = analyzerErrors.zipWithDefault(other.analyzerErrors, emptyList(), plus)
                            .toSortedMap(),
                    scanErrors = scanErrors.zipWithDefault(other.scanErrors, emptyList(), plus).toSortedMap()
            )
        }
    }

    override fun generateReport(ortResult: OrtResult, outputDir: File) {
        val errorSummaryRows = mutableMapOf<Identifier, ErrorRow>()
        val summaryRows = mutableMapOf<Identifier, DependencyRow>()

        require(ortResult.analyzer?.result != null) {
            "The provided ORT result does not contain an analyzer result."
        }

        val analyzerResult = ortResult.analyzer!!.result

        require(ortResult.scanner?.results != null) {
            "The provided ORT result does not contain any scan results."
        }

        val scanRecord = ortResult.scanner!!.results

        val projectTables = analyzerResult.projects.associate { project ->
            val projectExclude = ortResult.repository.config.excludes?.findProjectExclude(project)?.let {
                // Only add the project exclude to the model if the whole project is excluded. If only parts of the
                // project are excluded this information will be stored in the rows of the affected dependencies.
                if (it.exclude) it else null
            }

            val tableRows = (listOf(project.id) + project.collectDependencyIds()).map { id ->
                val scanResult = scanRecord.scanResults.find { it.id == id }

                val scopes = project.scopes.filter { id in it }.associate {
                    val scopeExcludes = ortResult.repository.config.excludes?.findScopeExcludes(it, project)
                            ?: emptyList()
                    Pair(it.name, scopeExcludes)
                }.toSortedMap()

                val declaredLicenses = analyzerResult.projects.find { it.id == id }?.declaredLicenses
                        ?: analyzerResult.packages.find { it.pkg.id == id }?.pkg?.declaredLicenses
                        ?: sortedSetOf()

                val detectedLicenses = scanResult?.results?.flatMap {
                    it.summary.licenses
                }?.toSortedSet() ?: sortedSetOf()

                val analyzerErrors = project.collectErrors(id).toMutableList()
                analyzerResult.errors[id]?.let {
                    analyzerErrors += it
                }

                val scanErrors = scanResult?.results?.flatMap {
                    it.summary.errors
                }?.distinct() ?: emptyList()

                DependencyRow(
                        id = id,
                        scopes = scopes,
                        declaredLicenses = declaredLicenses,
                        detectedLicenses = detectedLicenses,
                        analyzerErrors = analyzerErrors,
                        scanErrors = scanErrors
                ).also { row ->
                    summaryRows[row.id] = summaryRows[row.id]?.merge(row) ?: row
                    if ((row.analyzerErrors.isNotEmpty() || row.scanErrors.isNotEmpty())
                            && (scopes.isEmpty() || scopes.any { it.value.isEmpty() }) && projectExclude == null) {
                        val errorRow = ErrorRow(
                                id = row.id,
                                analyzerErrors = if (row.analyzerErrors.isNotEmpty())
                                    sortedMapOf(project.id to row.analyzerErrors) else sortedMapOf(),
                                scanErrors = if (row.scanErrors.isNotEmpty())
                                    sortedMapOf(project.id to row.scanErrors) else sortedMapOf()
                        )

                        errorSummaryRows[row.id] = errorSummaryRows[errorRow.id]?.merge(errorRow) ?: errorRow
                    }
                }
            }

            Pair(project, ProjectTable(tableRows, projectExclude))
        }.toSortedMap()

        val errorSummaryTable = ErrorTable(errorSummaryRows.values.toList().sortedBy { it.id })
        val summaryTable = ProjectTable(summaryRows.values.toList().sortedBy { it.id }, null)

        val metadata = ortResult.data["reporter.metadata"]?.let {
            if (it is Map<*, *>) {
                it.entries.associate { (key, value) -> key.toString() to value.toString() }
            } else {
                null
            }
        } ?: emptyMap()

        val extraColumns = scanRecord.data["reporter.extraColumns"]?.let { extraColumns ->
            if (extraColumns is List<*>) {
                extraColumns.map { it.toString() }
            } else {
                null
            }
        } ?: emptyList()

        generateReport(TabularScanRecord(ortResult.repository.vcsProcessed, errorSummaryTable, summaryTable,
                projectTables, metadata, extraColumns), outputDir)
    }

    abstract fun generateReport(tabularScanRecord: TabularScanRecord, outputDir: File)
}
