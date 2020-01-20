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

@file:Suppress("MatchingDeclarationName", "TooManyFunctions")

package com.here.ort.helper.common

import com.here.ort.analyzer.Analyzer
import com.here.ort.analyzer.HTTP_CACHE_PATH
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.Downloader
import com.here.ort.model.Identifier
import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.Project
import com.here.ort.model.Provenance
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.RuleViolation
import com.here.ort.model.Severity
import com.here.ort.model.TextLocation
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.ErrorResolution
import com.here.ort.model.config.LicenseFindingCuration
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.RuleViolationResolution
import com.here.ort.model.config.ScopeExclude
import com.here.ort.model.utils.FindingCurationMatcher
import com.here.ort.model.yamlMapper
import com.here.ort.utils.CopyrightStatementsProcessor
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.expandTilde

import java.io.File
import java.io.IOException

import okhttp3.Request

import okio.buffer
import okio.sink

/**
 * Represents a mapping from repository URLs to list of [PathExclude]s for the respective repository.
 */
internal typealias RepositoryPathExcludes = Map<String, List<PathExclude>>

internal typealias RepositoryLicenseFindingCurations = Map<String, List<LicenseFindingCuration>>

/**
 * Try to download the [url] and return the downloaded temporary file. The file is automatically deleted on exit. If the
 * download fails, throw an [IOException].
 */
internal fun download(url: String): File {
    val request = Request.Builder()
        // Disable transparent gzip, otherwise we might end up writing a tar file to disk and expecting to
        // find a tar.gz file, thus failing to unpack the archive.
        // See https://github.com/square/okhttp/blob/parent-3.10.0/okhttp/src/main/java/okhttp3/internal/ \
        // http/BridgeInterceptor.java#L79
        .addHeader("Accept-Encoding", "identity")
        .get()
        .url(url)
        .build()

    OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
        val body = response.body
        if (!response.isSuccessful || body == null) {
            throw IOException(response.message)
        }

        // Use the filename from the request for the last redirect.
        val tempFileName = response.request.url.pathSegments.last()
        return createTempFile("ort", tempFileName).also { tempFile ->
            tempFile.sink().buffer().use { it.writeAll(body.source()) }
            tempFile.deleteOnExit()
        }
    }
}

/**
 * Return all files underneath the given [directory].
 */
internal fun findFilesRecursive(directory: File): List<String> {
    require(directory.isDirectory)

    val result = mutableListOf<String>()

    directory.walk().forEach { file ->
        if (!file.isDirectory) {
            result.add(file.relativeTo(directory).path)
        }
    }

    return result
}

/**
 * Search the given [directory] for repositories and return a mapping from repository URLs to the relative paths where
 * each respective repository was found.
 */
internal fun findRepositoryPaths(directory: File): Map<String, Set<String>> {
    require(directory.isDirectory)

    val analyzer = Analyzer(AnalyzerConfiguration(true, true))
    val ortResult = analyzer.analyze(
        absoluteProjectPath = directory,
        packageManagers = emptyList()
    )

    val result = mutableMapOf<String, MutableSet<String>>()

    ortResult.repository.nestedRepositories.forEach { path, vcs ->
        result
            .getOrPut(vcs.url) { mutableSetOf() }
            .add(path)
    }

    // TODO: strip the user info from the vcs URLs
    return result
}

/**
 * Return an approximation for the Set-Cover Problem, see https://en.wikipedia.org/wiki/Set_cover_problem.
 */
internal fun <K, V> greedySetCover(sets: Map<K, Set<V>>): Set<K> {
    val result = mutableSetOf<K>()

    var uncovered = sets.values.flatMap { it }.toMutableSet()
    var queue = sets.entries.toMutableSet()

    while (queue.isNotEmpty()) {
        val maxCover = queue.maxBy { it.value.intersect(uncovered).size }!!

        if (uncovered.intersect(maxCover.value).isNotEmpty()) {
            uncovered.removeAll(maxCover.value)
            queue.remove(maxCover)
            result.add(maxCover.key)
        } else {
            break
        }
    }

    return result
}

/**
 * Return an approximated minimal sublist of [this] so that the result still matches the exact same entries of the given
 * [projectScopes].
 */
internal fun List<ScopeExclude>.minimize(projectScopes: List<String>): List<ScopeExclude> {
    val scopeExcludes = associateWith { scopeExclude ->
        projectScopes.filter { scopeExclude.matches(it) }.toSet()
    }

    return greedySetCover(scopeExcludes).toList()
}

/**
 * Fetches the sources from either the VCS or source artifact for the package denoted by
 * the given [id] depending on whether a scan result is present with matching [Provenance].
 */
internal fun OrtResult.fetchScannedSources(id: Identifier): File {
    val tempDir = createTempDir("helper-cli", ".temp", File("."))

    val pkg = getPackageOrProject(id)!!.let {
        if (getProvenance(id)!!.sourceArtifact != null) {
            it.copy(vcs = VcsInfo.EMPTY, vcsProcessed = VcsInfo.EMPTY)
        } else {
            it.copy(sourceArtifact = RemoteArtifact.EMPTY)
        }
    }

    return Downloader().download(pkg, tempDir).downloadDirectory
}

/**
 * A processed copyright statement.
 */
internal data class ProcessedCopyrightStatement(
    /**
     * The package containing the copyright statement.
     */
    val packageId: Identifier,

    /**
     * The license associated with the copyright statement.
     */
    val licenseId: String,

    /**
     * The processed copyright statement.
     */
    val statement: String,

    /**
     * The original statement(s) which yield this processed [statement].
     */
    val rawStatements: Set<String>
) {
    init {
        require(rawStatements.isNotEmpty()) { "The set of raw statements must not be empty." }
    }
}

/**
 * Return the processed copyright statements of all packages and projects contained in this [OrtResult]. The statements
 * are processed for each package and license separately for consistency with the [NoticeByPackageReporter].
 * Statements contained in the given [copyrightGarbage] are omitted.
 */
internal fun OrtResult.processAllCopyrightStatements(
    omitExcluded: Boolean = true,
    copyrightGarbage: Set<String> = emptySet()
): List<ProcessedCopyrightStatement> {
    val result = mutableListOf<ProcessedCopyrightStatement>()
    val processor = CopyrightStatementsProcessor()

    collectLicenseFindings(omitExcluded).forEach { (id, findings) ->
        findings.forEach innerForEach@{ (licenseFindings, pathExcludes) ->
            if (omitExcluded && pathExcludes.isNotEmpty()) return@innerForEach

            val processResult = processor.process(
                licenseFindings.copyrights.map { it.statement }.filterNot { it in copyrightGarbage }
            )

            processResult.processedStatements.filterNot { it.key in copyrightGarbage }.forEach {
                result.add(
                    ProcessedCopyrightStatement(
                        packageId = id,
                        licenseId = licenseFindings.license,
                        statement = it.key,
                        rawStatements = it.value.toSet()
                    )
                )
            }

            processResult.unprocessedStatements.filterNot { it in copyrightGarbage }.forEach {
                result.add(
                        ProcessedCopyrightStatement(
                        packageId = id,
                        licenseId = licenseFindings.license,
                        statement = it,
                        rawStatements = setOf(it)
                    )
                )
            }
        }
    }

    return result
}

/**
 * Return all license findings for the project or package associated with the given [id]. The license
 * [LicenseFindingCuration]s contained in this [OrtResult] are applied if and only if [applyCurations] is true.
 */
internal fun OrtResult.getLicenseFindingsById(
    id: Identifier,
    applyCurations: Boolean = true
): Map<String, Set<TextLocation>> =
    scanner?.results?.scanResults
        ?.filter { it.id == id }
        .orEmpty()
        .flatMapTo(mutableSetOf()) { container ->
            container.results.flatMap { it.summary.licenseFindings }
        }
        .let { licenseFindings ->
            if (applyCurations) {
                FindingCurationMatcher().applyAll(licenseFindings, getLicenseFindingsCurations(id))
            } else {
                licenseFindings
            }
        }
        .groupBy({ it.license }, { it.location })
        .mapValues { (_, locations) -> locations.toSet() }

/**
 * Return all license finding curations from this [OrtResult] represented as [RepositoryPathExcludes].
 */
internal fun OrtResult.getRepositoryLicenseFindingCurations(): RepositoryLicenseFindingCurations {
    val result = mutableMapOf<String, MutableList<LicenseFindingCuration>>()
    val curations = repository.config.curations.licenseFindings

    repository.nestedRepositories.forEach { (path, vcs) ->
        val pathExcludesForRepository = result.getOrPut(vcs.url) { mutableListOf() }
        curations.forEach { curation ->
            if (curation.path.startsWith(path)) {
                pathExcludesForRepository.add(
                    curation.copy(
                        path = curation.path.substring(path.length).removePrefix("/")
                    )
                )
            }
        }
    }

    return result.mapValues { excludes -> excludes.value.sortedBy { it.path } }.toSortedMap()
}

/**
 * Return all license [Identifiers]s which triggered at least one [RuleViolation] with a [severity]
 * greater or equal to the given [minSeverity] associated with the rule names of all corresponding violated rules.
 */
internal fun OrtResult.getViolatedRulesByLicense(
    id: Identifier,
    minSeverity: Severity
): Map<String, List<String>> =
    getRuleViolations()
        .filter { it.pkg == id && it.severity.ordinal <= minSeverity.ordinal && it.license != null }
        .groupBy { it.license!! }
        .mapValues { (_, ruleViolations) -> ruleViolations.map { it.rule } }

/**
 * Return the Package with the given [id] denoting either a [Project] or a [Package].
 */
internal fun OrtResult.getPackageOrProject(id: Identifier): Package? =
    getProject(id)?.let { it.toPackage() } ?: getPackage(id)?.pkg

/**
 * Return the first [Provenance] matching the given [id] or null if there is no match.
 */
internal fun OrtResult.getProvenance(id: Identifier): Provenance? {
    val pkg = getPackageOrProject(id)!!

    scanner?.results?.scanResults?.forEach { container ->
        container.results.forEach { scanResult ->
            if (scanResult.provenance.matches(pkg)) {
                return scanResult.provenance
            }
        }
    }

    return null
}

/**
 * Return all issues from scan results. Issues for excludes [Project]s or [Package]s are not returned if and only if
 * the given [omitExcluded] is true.
 */
fun OrtResult.getScanIssues(omitExcluded: Boolean = false): List<OrtIssue> {
    val result = mutableListOf<OrtIssue>()

    scanner?.results?.scanResults?.forEach { container ->
        if (!omitExcluded || !isExcluded(container.id)) {
            container.results.forEach { scanResult ->
                result.addAll(scanResult.summary.issues)
            }
        }
    }

    return result
}

/**
 * Return all path excludes from this [OrtResult] represented as [RepositoryPathExcludes].
 */
internal fun OrtResult.getRepositoryPathExcludes(): RepositoryPathExcludes {
    fun isDefinitionsFile(pathExclude: PathExclude) = PackageManager.ALL.any {
        it.matchersForDefinitionFiles.any {
            pathExclude.pattern.endsWith(it.toString())
        }
    }

    val result = mutableMapOf<String, MutableList<PathExclude>>()
    val pathExcludes = repository.config.excludes.paths

    repository.nestedRepositories.forEach { (path, vcs) ->
        val pathExcludesForRepository = result.getOrPut(vcs.url) { mutableListOf() }
        pathExcludes.forEach { pathExclude ->
            if (pathExclude.pattern.startsWith(path) && !isDefinitionsFile(pathExclude)) {
                pathExcludesForRepository.add(
                    pathExclude.copy(
                        pattern = pathExclude.pattern.substring(path.length).removePrefix("/")
                    )
                )
            }
        }
    }

    return result.mapValues { excludes -> excludes.value.sortedBy { it.pattern } }.toSortedMap()
}

/**
 * Return all unresolved rule violations.
 */
internal fun OrtResult.getUnresolvedRuleViolations(): List<RuleViolation> {
    val resolutions = getResolutions().ruleViolations
    val violations = evaluator?.violations.orEmpty()

    return violations.filter { violation ->
        !resolutions.any { it.matches(violation) }
    }
}

/**
 * Return a copy with the [ErrorResolution]s replaced by the given [errorResolutions].
 */
internal fun RepositoryConfiguration.replaceErrorResolutions(
    errorResolutions: List<ErrorResolution>
): RepositoryConfiguration = copy(resolutions = resolutions.copy(errors = errorResolutions))

/**
 * Return a copy with the [LicenseFindingCuration]s replaced by the given scope excludes.
 */
internal fun RepositoryConfiguration.replaceLicenseFindingCurations(
    curations: List<LicenseFindingCuration>
): RepositoryConfiguration = copy(curations = this.curations.copy(licenseFindings = curations))

/**
 * Return a copy with the [PathExclude]s replaced by the given scope excludes.
 */
internal fun RepositoryConfiguration.replacePathExcludes(pathExcludes: List<PathExclude>): RepositoryConfiguration =
    copy(excludes = excludes.copy(paths = pathExcludes))

/**
 * Return a copy with the [ScopeExclude]s replaced by the given [scopeExcludes].
 */
internal fun RepositoryConfiguration.replaceScopeExcludes(scopeExcludes: List<ScopeExclude>): RepositoryConfiguration =
    copy(excludes = excludes.copy(scopes = scopeExcludes))

/**
 * Return a copy with the [RuleViolationResolution]s replaced by the given [ruleViolations].
 */
internal fun RepositoryConfiguration.replaceRuleViolationResolutions(ruleViolations: List<RuleViolationResolution>):
    RepositoryConfiguration = copy(resolutions = resolutions.copy(ruleViolations = ruleViolations))

/**
 * Return a copy with sorting applied to all entry types which are to be sorted.
 */
internal fun RepositoryConfiguration.sortEntries(): RepositoryConfiguration =
    sortLicenseFindingCurations().sortPathExcludes().sortScopeExcludes()

/**
 * Return a copy with the [LicenseFindingCuration]s sorted.
 */
internal fun RepositoryConfiguration.sortLicenseFindingCurations(): RepositoryConfiguration =
    copy(
        curations = curations.copy(
            licenseFindings = curations.licenseFindings.sortedBy { curation ->
                curation.path.removePrefix("*").removePrefix("*")
            }
        )
    )

/**
 * Return a copy with the [PathExclude]s sorted.
 */
internal fun RepositoryConfiguration.sortPathExcludes(): RepositoryConfiguration =
    copy(
        excludes = excludes.copy(
            paths = excludes.paths.sortedBy { pathExclude ->
                pathExclude.pattern.removePrefix("*").removePrefix("*")
            }
        )
    )

/**
 * Return a copy with the [ScopeExclude]s sorted.
 */
internal fun RepositoryConfiguration.sortScopeExcludes(): RepositoryConfiguration =
    copy(
        excludes = excludes.copy(
            scopes = excludes.scopes.sortedBy { (pattern, _, _) ->
                pattern.removePrefix(".*")
            }
        )
    )

/**
 * Serialize a [RepositoryConfiguration] as YAML to the given target [File].
 */
internal fun RepositoryConfiguration.writeAsYaml(targetFile: File) {
    targetFile.expandTilde().parentFile.safeMkdirs()

    yamlMapper.writeValue(targetFile, this)
}

/**
 * Merge the given [RepositoryLicenseFindingCurations] replacing entries with equal [LicenseFindingCuration.path],
 * [LicenseFindingCuration.startLines], [LicenseFindingCuration.lineCount], [LicenseFindingCuration.detectedLicense]
 * and [LicenseFindingCuration.concludedLicense]. If the given [updateOnlyExisting] is true then only entries with
 * matching [LicenseFindingCuration.path] are merged.
 */
internal fun RepositoryLicenseFindingCurations.mergeLicenseFindingCurations(
    other: RepositoryLicenseFindingCurations,
    updateOnlyExisting: Boolean = false
): RepositoryLicenseFindingCurations {
    val result: MutableMap<String, MutableMap<LicenseFindingCurationHashKey, LicenseFindingCuration>> = mutableMapOf()

    fun merge(repositoryUrl: String, curation: LicenseFindingCuration, updateOnlyUpdateExisting: Boolean = false) {
        if (updateOnlyUpdateExisting && !result.containsKey(repositoryUrl)) {
            return
        }

        val curations = result.getOrPut(repositoryUrl, { mutableMapOf() })

        val key = curation.hashKey()
        if (updateOnlyUpdateExisting && !curations.containsKey(key)) {
            return
        }

        curations[key] = curation
    }

    forEach { (repositoryUrl, curations) ->
        curations.forEach { curation ->
            merge(repositoryUrl, curation, false)
        }
    }

    other.forEach { (repositoryUrl, pathExcludes) ->
        pathExcludes.forEach { pathExclude ->
            merge(repositoryUrl, pathExclude, updateOnlyExisting)
        }
    }

    return result.mapValues { (_, pathExcludes) ->
        pathExcludes.values.toList()
    }
}

/**
 * Merge the given [RepositoryPathExcludes] replacing entries with equal [PathExclude.pattern].
 * If the given [updateOnlyExisting] is true then only entries with matching [PathExclude.pattern] are merged.
 */
internal fun RepositoryPathExcludes.mergePathExcludes(
    other: RepositoryPathExcludes,
    updateOnlyExisting: Boolean = false
): RepositoryPathExcludes {
    val result: MutableMap<String, MutableMap<String, PathExclude>> = mutableMapOf()

    fun merge(repositoryUrl: String, pathExclude: PathExclude, updateOnlyUpdateExisting: Boolean = false) {
        if (updateOnlyUpdateExisting && !result.containsKey(repositoryUrl)) {
            return
        }

        val pathExcludes = result.getOrPut(repositoryUrl, { mutableMapOf() })
        if (updateOnlyUpdateExisting && !result.containsKey(pathExclude.pattern)) {
            return
        }

        pathExcludes[pathExclude.pattern] = pathExclude
    }

    forEach { (repositoryUrl, pathExcludes) ->
        pathExcludes.forEach { pathExclude ->
            merge(repositoryUrl, pathExclude, false)
        }
    }

    other.forEach { (repositoryUrl, pathExcludes) ->
        pathExcludes.forEach { pathExclude ->
            merge(repositoryUrl, pathExclude, updateOnlyExisting)
        }
    }

    return result.mapValues { (_, pathExcludes) ->
        pathExcludes.values.toList()
    }
}

/**
 * Merge the given [PathExclude]s replacing entries with equal [PathExclude.pattern].
 * If the given [updateOnlyExisting] is true then only entries with matching [PathExclude.pattern] are merged.
 */
internal fun Collection<PathExclude>.mergePathExcludes(
    other: Collection<PathExclude>,
    updateOnlyExisting: Boolean = false
): List<PathExclude> {
    val result = mutableMapOf<String, PathExclude>()

    associateByTo(result) { it.pattern }

    other.forEach {
        if (!updateOnlyExisting || result.containsKey(it.pattern)) {
            result[it.pattern] = it
        }
    }

    return result.values.toList()
}

/**
 * Merge the given [LicenseFindingCuration]s replacing entries with equal [LicenseFindingCuration.path],
 * [LicenseFindingCuration.startLines], [LicenseFindingCuration.lineCount], [LicenseFindingCuration.detectedLicense]
 * and [LicenseFindingCuration.concludedLicense].
 * If the given [updateOnlyExisting] is true then only entries replacing existing ones are merged.
 */
internal fun Collection<LicenseFindingCuration>.mergeLicenseFindingCurations(
    other: Collection<LicenseFindingCuration>,
    updateOnlyExisting: Boolean = false
): List<LicenseFindingCuration> {
    val result = mutableMapOf<LicenseFindingCurationHashKey, LicenseFindingCuration>()

    associateByTo(result) { it.hashKey() }

    other.forEach {
        if (!updateOnlyExisting || result.containsKey(it.hashKey())) {
            result[it.hashKey()] = it
        }
    }

    return result.values.toList()
}

private data class LicenseFindingCurationHashKey(
    val path: String,
    val startLines: List<Int> = emptyList(),
    val lineCount: Int? = null,
    val detectedLicense: String?,
    val concludedLicense: String
)

private fun LicenseFindingCuration.hashKey() =
    LicenseFindingCurationHashKey(path, startLines, lineCount, detectedLicense, concludedLicense)
