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
import com.here.ort.reporter.Reporter

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
            val errorSummary: ProjectTable,

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
            val rows: List<DependencyRow>
    )

    data class DependencyRow(
            /**
             * The identifier of the package.
             */
            val id: Identifier,

            /**
             * The scopes the package is used in.
             */
            val scopes: SortedSet<String>,

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
                        scopes = (scopes + other.scopes).toSortedSet(),
                        declaredLicenses = (declaredLicenses + other.declaredLicenses).toSortedSet(),
                        detectedLicenses = (detectedLicenses + other.detectedLicenses).toSortedSet(),
                        analyzerErrors = (analyzerErrors + other.analyzerErrors).distinct(),
                        scanErrors = (scanErrors + other.scanErrors).distinct()
                )
    }

    override fun generateReport(ortResult: OrtResult, outputDir: File) {
        val errorSummaryRows = mutableMapOf<Identifier, DependencyRow>()
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
            val tableRows = (listOf(project.id) + project.collectDependencyIds()).map { id ->
                val scanResult = scanRecord.scanResults.find { it.id == id }

                val scopes = project.scopes.filter { id in it }.map { it.name }.toSortedSet()

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
                    if (row.analyzerErrors.isNotEmpty() || row.scanErrors.isNotEmpty()) {
                        errorSummaryRows[row.id] = errorSummaryRows[row.id]?.merge(row) ?: row
                    }
                }
            }

            Pair(project, ProjectTable(tableRows))
        }.toSortedMap()

        val errorSummaryTable = ProjectTable(errorSummaryRows.values.toList().sortedBy { it.id })
        val summaryTable = ProjectTable(summaryRows.values.toList().sortedBy { it.id })

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
