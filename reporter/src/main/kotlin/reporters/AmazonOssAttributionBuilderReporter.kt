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

package org.ossreviewtoolkit.reporter.reporters

import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.time.Instant

import okhttp3.Credentials

import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.utils.AmazonOssAttributionBuilderService
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace

import retrofit2.Response

/**
 * A [Reporter] that creates an attribution document in .txt format using a running instance of the Amazon Attribution
 * Builder (https://github.com/amzn/oss-attribution-builder).
 */
class AmazonOssAttributionBuilderReporter : Reporter {
    override val reporterName = "AmazonOssAttributionBuilder"

    private val reportFilename = "OssAttribution.txt"

    private val service = AmazonOssAttributionBuilderService.create(
        AmazonOssAttributionBuilderService.Server.DEFAULT,
        OkHttpClientHelper.buildClient()
    )

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        // TODO: Allow to configure username and password via the config file; use the defaults for now.
        val credentials = Credentials.basic("admin", "admin")

        val rootProject = input.ortResult.getProjects().singleOrNull()

        // TODO: Add support for multiple projects.
        requireNotNull(rootProject) {
            "The $reporterName currently only supports ORT results with a single project."
        }

        try {
            // Create a new project which gives all permissions to everyone for simplicity.
            val acl = mapOf("everyone" to AmazonOssAttributionBuilderService.Role.OWNER)

            // Projects have a list of contacts, but at the moment the UI only supports one contact, the legal contact.
            val contacts = AmazonOssAttributionBuilderService.ProjectContacts(listOf("Insert legal contact here."))

            val metadata = jsonMapper.createObjectNode().put("open_sourcing", true)
            val newProject = AmazonOssAttributionBuilderService.NewProject(
                rootProject.id.name,
                rootProject.id.version,
                "Imported from results of the OSS Review Toolkit.",
                Instant.now().toString(), // Use a fake planned release date.
                contacts,
                acl,
                metadata
            )

            val newProjectResponse = service.createNewProject(newProject, credentials).execute()
            val newProjectBody = getBodyOrHandleError(newProjectResponse, "Unable to create a new project")

            log.info { "Successfully created project with id '${newProjectBody.projectId}'." }

            // Attach packages to the newly created project.
            val dependencies = input.ortResult.collectDependencies(rootProject.id)
            dependencies.forEach { id ->
                // We know that a package exists for the reference.
                val pkg = input.ortResult.getPackage(id)!!.pkg

                // TODO: Theoretically, a project could use the same package multiple times in different linkages in the
                //       ORT model. Think about how to map that to the Attributon Builder.
                //       If the package was modified is something that is not currently captured by ORT. Think about
                //       whether this is something we should do.
                val usage = AmazonOssAttributionBuilderService.Usage("No notes.", link = "dynamic", modified = false)

                // URL passed into the Amazon Oss Attribution Builder needs to be reachable!
                val pkgUrl = pkg.homepageUrl.takeUnless { it.isEmpty() } ?: "https://github.com/404"

                val licenseInfo = input.licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded()

                val allCopyrights = licenseInfo.filter(LicenseView.ONLY_DETECTED).flatMapTo(mutableSetOf()) {
                    it.getCopyrights()
                }.ifEmpty {
                    // The copyright set must not be empty as otherwise the Attribution builder rejects the request.
                    setOf("No Copyright detected.")
                }

                val license = licenseInfo.filter(LicenseView.ONLY_DECLARED).firstOrNull()?.license?.simpleLicense()

                val licenseText = license?.let { input.licenseTextProvider.getLicenseText(it) }

                val attachPackage = AmazonOssAttributionBuilderService.AttachPackage(
                    pkg.id.name,
                    pkg.id.version,
                    pkgUrl,
                    allCopyrights.joinToString("\n"),
                    license,
                    licenseText,
                    usage
                )

                val attachPackageResponse = service.attachPackage(
                    newProjectBody.projectId,
                    attachPackage,
                    credentials
                ).execute()

                val attachPackageBody = getBodyOrHandleError(
                    attachPackageResponse,
                    "Unable to attach package '${pkg.id.toCoordinates()}' to project with id " +
                            "'${newProjectBody.projectId}'"
                )

                log.info { "Successfully created package with id '${attachPackageBody.packageId}'." }
            }

            // Generate an attribution document for this project.
            val generateAttributionDocResponse = service.generateAttributionDoc(
                newProjectBody.projectId,
                credentials
            ).execute()

            val generateAttributionDocBody = getBodyOrHandleError(
                generateAttributionDocResponse,
                "Unable to generate an attribution document for project with id '${newProjectBody.projectId}'"
            )

            log.info {
                "Successfully generated an attribution document with id '${generateAttributionDocBody.documentId}'."
            }

            // Fetch the newly generated attribution document.
            val fetchAttributionDocResponse = service.fetchAttributionDoc(
                newProjectBody.projectId,
                generateAttributionDocBody.documentId.toString(),
                credentials
            ).execute()

            val fetchAttributionDocBody = getBodyOrHandleError(
                fetchAttributionDocResponse,
                "Unable to fetch the attribution document with id '${generateAttributionDocBody.documentId}'"
            )

            log.info {
                "Successfully fetched the attribution document with id '${generateAttributionDocBody.documentId}'."
            }

            val outputFile = outputDir.resolve(reportFilename)

            outputFile.bufferedWriter().use {
                it.write(fetchAttributionDocBody.content)
            }

            return listOf(outputFile)
        } catch (e: ConnectException) {
            e.showStackTrace()

            throw IOException(
                "Please make sure that you have an instance of the Amazon OSS Attribution Builder " +
                        "running as described at https://github.com/amzn/oss-attribution-builder#quickstart.", e
            )
        }
    }
}

private fun <T> getBodyOrHandleError(response: Response<T>, errorTitle: String): T {
    val body = response.body()
    if (response.isSuccessful && body != null) return body

    val errorMessage = response.errorBody()?.let { errorBody ->
        val errorResponse = jsonMapper.readValue(
            errorBody.string(),
            AmazonOssAttributionBuilderService.ErrorResponse::class.java
        )

        errorResponse.error
    } ?: "Error code ${response.code()}"

    throw IOException("$errorTitle: $errorMessage")
}
