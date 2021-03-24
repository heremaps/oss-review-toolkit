/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner

import com.vdurmont.semver4j.Semver
import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.scanner.storages.PostgresStorage
import org.ossreviewtoolkit.utils.*
import java.io.File
import java.time.Instant
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Abstraction for a [Scanner] that runs on a remote host.
 */
abstract class RemoteScanner(name: String, config: ScannerConfiguration) : Scanner(name, config) {
    /**
     * The version of the scanner, or an empty string if not applicable.
     */
    abstract val version: String

    /**
     * The configuration used by the scanner (this could also be the URL of a specially configured instance), or an
     * empty string if not applicable.
     */
    abstract val configuration: String

    /**
     * Return the [ScannerDetails] of this [RemoteScanner].
     */
    val details by lazy { ScannerDetails(scannerName, version, configuration) }

    /**
     * A property containing the file name extension of the scanner's native output format, without the dot.
     */
    abstract val resultFileExt: String

    private val archiver by lazy {
        config.archive.createFileArchiver()
    }

    /**
     * Return a [ScannerCriteria] object to be used when looking up existing scan results from a [ScanResultsStorage].
     * Per default, the properties of this object are initialized to match this scanner implementation. It is,
     * however, possible to override these defaults from the configuration, in the [ScannerConfiguration.options]
     * property: Use properties of the form _scannerName.criteria.property_, where _scannerName_ is the name of
     * the scanner the configuration applies to, and _property_ is the name of a property of the [ScannerCriteria]
     * class. For instance, to specify that a specific minimum version of ScanCode is allowed, set this property:
     * `options.ScanCode.criteria.minScannerVersion=3.0.2`.
     */
    open fun getScannerCriteria(): ScannerCriteria {
        val options = config.options?.get(scannerName).orEmpty()
        val minVersion =
            parseVersion(options[LocalScanner.PROP_CRITERIA_MIN_VERSION]) ?: Semver(normalizeVersion(version))
        val maxVersion = parseVersion(options[LocalScanner.PROP_CRITERIA_MAX_VERSION]) ?: minVersion.nextMinor()
        val name = options[LocalScanner.PROP_CRITERIA_NAME] ?: scannerName
        return ScannerCriteria(name, minVersion, maxVersion, ScannerCriteria.exactConfigMatcher(configuration))
    }

    override suspend fun scanPackages(
        packages: List<Package>,
        outputDirectory: File,
        downloadDirectory: File
    ): Map<Package, List<ScanResult>> {
        val scannerCriteria = getScannerCriteria()

        log.info { "Searching scan results for ${packages.size} packages." }

        val remainingPackages = packages.filterTo(mutableListOf()) { pkg ->
            !pkg.isMetaDataOnly.also {
                if (it) RemoteScanner.log.info { "Skipping '${pkg.id.toCoordinates()}' as it is meta data only." }
            }
        }

        val resultsFromStorage = readResultsFromStorage(packages, scannerCriteria)

        log.info { "Found stored scan results for ${resultsFromStorage.size} packages and $scannerCriteria." }

        if (config.createMissingArchives) {
            createMissingArchives(resultsFromStorage, downloadDirectory)
        }

        remainingPackages.removeAll { it in resultsFromStorage.keys }

        log.info { "Scanning ${remainingPackages.size} packages for which no stored scan results were found." }

        val resultsFromScanner = remainingPackages.scan(outputDirectory, downloadDirectory)

        return resultsFromStorage + resultsFromScanner
    }

    private fun List<Package>.scan(outputDirectory: File, downloadDirectory: File): Map<Package, List<ScanResult>> {
        var index = 0

        return associateWith { pkg ->
            index++

            val packageIndex = "($index of $size)"

            RemoteScanner.log.info {
                "Scanning ${pkg.id.toCoordinates()}' in thread '${Thread.currentThread().name}' $packageIndex"
            }

            val scanResult = try {
                scanPackage(details, pkg, outputDirectory, downloadDirectory).also {
                    RemoteScanner.log.info {
                        "Finished scanning ${pkg.id.toCoordinates()} in thread '${Thread.currentThread().name}' " +
                                "$packageIndex."
                    }
                }
            } catch (e: ScanException) {
                e.showStackTrace()
                e.createFailedScanResult(pkg, packageIndex)
            }

            listOf(scanResult)
        }
    }

    private fun ScanException.createFailedScanResult(pkg: Package, packageIndex: String): ScanResult {
        val issue = createAndLogIssue(
            source = scannerName,
            message = "Could not scan '${pkg.id.toCoordinates()}' $packageIndex: ${collectMessagesAsString()}"
        )

        val now = Instant.now()
        return ScanResult(
            provenance = Provenance(),
            scanner = details,
            summary = ScanSummary(
                startTime = now,
                endTime = now,
                fileCount = 0,
                packageVerificationCode = "",
                licenseFindings = sortedSetOf(),
                copyrightFindings = sortedSetOf(),
                issues = listOf(issue)
            )
        )
    }


    private fun readResultsFromStorage(packages: List<Package>, scannerCriteria: ScannerCriteria) =
        when (val results = ScanResultsStorage.storage.read(packages, scannerCriteria)) {
            is Success -> results.result
            is Failure -> emptyMap()
        }.filter { it.value.isNotEmpty() }
            .mapKeys { (id, _) -> packages.single { it.id == id } }
            .mapValues { it.value.deduplicateScanResults() }
            .mapValues { (_, scanResults) ->
                // Due to a bug that has been fixed in d839f6e the scan results for packages were not properly filtered
                // by VCS path. Filter them again to fix the problem.
                // TODO: Remove this workaround together with the next change that requires recreating the scan storage.
                scanResults.map { it.filterByVcsPath().filterByIgnorePatterns(config.ignorePatterns) }
            }

    /**
     * Parse the given [versionStr] to a [Semver] object, trying to be failure tolerant.
     */
    private fun parseVersion(versionStr: String?): Semver? =
        versionStr?.let { Semver(normalizeVersion(it)) }

    /**
     * Normalize the given [versionStr] to make sure that it can be parsed to a [Semver]. The [Semver] class
     * requires that all components of a semantic version number are present. This function enables a more lenient
     * style when declaring a version. So for instance, the user can just write "2", and this gets expanded to
     * "2.0.0".
     */
    private fun normalizeVersion(versionStr: String): String =
        versionStr.takeIf { v -> v.count { it == '.' } >= 2 } ?: normalizeVersion("$versionStr.0")

    private fun archiveFiles(directory: File, id: Identifier, provenance: Provenance) {
        log.info { "Archiving files for ${id.toCoordinates()}." }

        val duration = measureTime { archiver.archive(directory, provenance) }

        log.perf { "Archived files for '${id.toCoordinates()}' in ${duration.inMilliseconds}ms." }
    }

    /**
     * Scan the provided [path] for license information and write the results to [resultsFile] using the scanner's
     * native file format.
     *
     * No scan results storage is used by this function.
     *
     * The return value is a [ScanSummary]. If the path could not be scanned, a [ScanException] is thrown.
     */
    protected abstract fun scanPathInternal(path: File, resultsFile: File): ScanSummary

    /**
     * Return the result file inside [outputDirectory]. The name of the file is derived from [pkg] and
     * [scannerDetails].
     */
    private fun getResultsFile(scannerDetails: ScannerDetails, pkg: Package, outputDirectory: File): File {
        val scanResultsForPackageDirectory = outputDirectory.resolve(pkg.id.toPath()).apply { safeMkdirs() }
        return scanResultsForPackageDirectory.resolve("scan-results_${scannerDetails.name}.$resultFileExt")
    }

    private fun createMissingArchives(scanResults: Map<Package, List<ScanResult>>, downloadDirectory: File) {
        scanResults.forEach { (pkg, results) ->
            val missingArchives = results.mapNotNullTo(mutableSetOf()) { result ->
                result.provenance.takeUnless { archiver.hasArchive(result.provenance) }
            }

            if (missingArchives.isNotEmpty()) {
                val pkgDownloadDirectory = downloadDirectory.resolve(pkg.id.toPath())
                Downloader.download(pkg, pkgDownloadDirectory)

                missingArchives.forEach { provenance ->
                    archiveFiles(pkgDownloadDirectory, pkg.id, provenance)
                }
            }
        }
    }

    /**
     * Scan the provided [pkg] for license information and write the results to [outputDirectory] using the scanner's
     * native file format.
     *
     * The package's source code is downloaded to [downloadDirectory] and scanned afterwards.
     *
     * Return the [ScanResult], if the package could not be scanned a [ScanException] is thrown.
     */
    fun scanPackage(
        scannerDetails: ScannerDetails,
        pkg: Package,
        outputDirectory: File,
        downloadDirectory: File
    ): ScanResult {
        val resultsFile = getResultsFile(scannerDetails, pkg, outputDirectory)
        val pkgDownloadDirectory = downloadDirectory.resolve(pkg.id.toPath())

        val provenance = try {
            Downloader.download(pkg, pkgDownloadDirectory)
        } catch (e: DownloadException) {
            e.showStackTrace()

            val now = Instant.now()
            return ScanResult(
                Provenance(),
                scannerDetails,
                ScanSummary(
                    startTime = now,
                    endTime = now,
                    fileCount = 0,
                    packageVerificationCode = "",
                    licenseFindings = sortedSetOf(),
                    copyrightFindings = sortedSetOf(),
                    issues = listOf(
                        createAndLogIssue(
                            source = scannerName,
                            message = "Could not download '${pkg.id.toCoordinates()}': ${e.collectMessagesAsString()}"
                        )
                    )
                )
            )
        }

        log.info {
            "Running $scannerDetails on directory '${pkgDownloadDirectory.absolutePath}'."
        }

        archiveFiles(pkgDownloadDirectory, pkg.id, provenance)

        val (scanSummary, scanDuration) = measureTimedValue {
            val vcsPath = provenance.vcsInfo?.takeUnless { it.type == VcsType.GIT_REPO }?.path.orEmpty()
            scanPathInternal(pkgDownloadDirectory, resultsFile).filterByPath(vcsPath)
        }

        log.perf {
            "Scanned source code of '${pkg.id.toCoordinates()}' with ${javaClass.simpleName} in " +
                    "${scanDuration.inMilliseconds}ms."
        }

        val scanResult = ScanResult(provenance, scannerDetails, scanSummary)
        val storageResult = ScanResultsStorage.storage.add(pkg.id, scanResult)
        val filteredResult = scanResult.filterByIgnorePatterns(config.ignorePatterns)

        return when (storageResult) {
            is Success -> filteredResult
            is Failure -> {
                val issue = OrtIssue(
                    source = ScanResultsStorage.storage.name,
                    message = storageResult.error,
                    severity = Severity.WARNING
                )
                val issues = scanSummary.issues + issue
                val summary = scanSummary.copy(issues = issues)
                filteredResult.copy(summary = summary)
            }
        }
    }

    /**
     * Workaround to prevent that duplicate [ScanResult]s from the [ScanResultsStorage] do get duplicated in the
     * [OrtResult] produced by this scanner.
     *
     * The time interval between a failing read from storage and the resulting scan with the following store operation
     * can be relatively large. Thus this [LocalScanner] is prone to adding duplicate scan results if multiple instances
     * of the scanner run in parallel. In particular the [PostgresStorage] allows adding duplicate tuples
     * (identifier, provenance, scanner details) which should be made unique.
     *
     * TODO: Implement a solution that prevents duplicate scan results in the storages.
     */
    private fun List<ScanResult>.deduplicateScanResults(): List<ScanResult> {
        // Use vcsInfo and sourceArtifact instead of provenance in order to ignore the download time and original VCS
        // info.
        data class Key(
            val vcsInfo: VcsInfo?,
            val sourceArtifact: RemoteArtifact?,
            val scannerDetails: ScannerDetails
        )

        fun ScanResult.key() = Key(provenance.vcsInfo, provenance.sourceArtifact, scanner)

        val deduplicatedResults = distinctBy { it.key() }

        val duplicatesCount = size - deduplicatedResults.size
        if (duplicatesCount > 0) {
            LocalScanner.log.info { "Removed $duplicatesCount duplicates out of $size scan results." }
        }

        return deduplicatedResults
    }
}
