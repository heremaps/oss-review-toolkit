/*
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

package org.ossreviewtoolkit.commands

import com.fasterxml.jackson.module.kotlin.readValue

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.net.HttpURLConnection
import java.net.URL

import okhttp3.ResponseBody

import org.ossreviewtoolkit.analyzer.curation.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.analyzer.curation.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.ContributionInfo
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.ContributionPatch
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Curation
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Described
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Licensed
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Patch
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.clearlydefined.ContributionType
import org.ossreviewtoolkit.clearlydefined.ErrorResponse
import org.ossreviewtoolkit.clearlydefined.HarvestStatus
import org.ossreviewtoolkit.clearlydefined.string
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.hasNonNullProperty
import org.ossreviewtoolkit.utils.log

class ClearlyDefinedUploadCommand : CliktCommand(
    name = "cd-upload",
    help = "Upload ORT package curations to ClearlyDefined."
) {
    private val inputFile by option(
        "--input-file", "-i",
        help = "The file with package curations to upload."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .required()

    private val server by option(
        "--server", "-s",
        help = "The ClearlyDefined server to upload to."
    ).enum<Server>().default(Server.DEVELOPMENT)

    private val service by lazy { ClearlyDefinedService.create(server, OkHttpClientHelper.buildClient()) }

    private fun getDefinitions(coordinates: Collection<String>): Map<String, ClearlyDefinedService.Defined> {
        val call = service.getDefinitions(coordinates)

        log.debug {
            val request = call.request()
            "Going to execute API call at ${request.url} with body:\n${request.body?.string()}"
        }

        val response = call.execute()
        val responseCode = response.code()

        if (responseCode == HttpURLConnection.HTTP_OK) {
            response.body()?.let { definitions ->
                return definitions
            } ?: log.warn { "The REST API call succeeded but no response body was returned." }
        } else {
            response.errorBody()?.let { logInnerError(it) }
        }

        return emptyMap()
    }

    private fun logInnerError(errorBody: ResponseBody) {
        val errorResponse = jsonMapper.readValue<ErrorResponse>(errorBody.string())
        log.error { "The REST API call failed with: ${errorResponse.error.innererror.message}" }
        log.debug { errorResponse.error.innererror.stack }
    }

    override fun run() {
        val absoluteInputFile = inputFile.normalize()
        val curations = absoluteInputFile.readValue<List<PackageCuration>>()

        var error = false

        val curationsToCoordinates = curations.associateWith { it.id.toClearlyDefinedCoordinates().toString() }
        val definitions = getDefinitions(curationsToCoordinates.values)

        val uploadableCurations = curations.filter { curation ->
            val harvestStatus = definitions[curationsToCoordinates[curation]]?.getHarvestStatus()

            print("Package '${curation.id.toCoordinates()}' ")
            when (harvestStatus) {
                HarvestStatus.HARVESTED -> {
                    println("has been harvested, will try to upload curations.")
                    true
                }

                HarvestStatus.NOT_HARVESTED, HarvestStatus.PARTIALLY_HARVESTED -> {
                    println("has not been harvested yet, cannot upload curations.")
                    false
                }

                null -> {
                    println("has an unknown harvest status, cannot upload curations.")
                    false
                }
            }
        }

        uploadableCurations.forEachIndexed { index, curation ->
            val call = service.putCuration(curation.toContributionPatch())

            log.debug {
                val request = call.request()
                "Going to execute API call at ${request.url} with body:\n${request.body?.string()}"
            }

            val response = call.execute()
            val responseCode = response.code()

            print("Curation ${index + 1} of ${uploadableCurations.size} for package '${curation.id.toCoordinates()}' ")
            if (responseCode == HttpURLConnection.HTTP_OK) {
                response.body()?.let { summary ->
                    println("was successfully uploaded:\n${summary.url}")
                } ?: log.warn { "The REST API call succeeded but no response body was returned." }
            } else {
                println("failed to be uploaded (response code $responseCode).")

                response.errorBody()?.let { logInnerError(it) }

                error = true
            }
        }

        if (error) throw UsageError("An error occurred.", statusCode = 2)
    }
}

private fun PackageCuration.toContributionPatch(): ContributionPatch {
    val info = ContributionInfo(
        // The exact values to use here are unclear; use what is mostly used at
        // https://github.com/clearlydefined/curated-data/pulls.
        type = ContributionType.OTHER,
        summary = "Curation for component ${id.toClearlyDefinedCoordinates()}.",
        details = "Imported from curation data of the " +
                "[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) via the " +
                "[clearly-defined](https://github.com/oss-review-toolkit/ort/tree/master/clearly-defined) " +
                "module.",
        resolution = data.comment ?: "Unknown, original data contains no comment.",
        removedDefinitions = false
    )

    val licenseExpression = data.concludedLicense?.toString() ?: data.declaredLicenses?.joinToString(" AND ")

    val described = Described(
        projectWebsite = data.homepageUrl?.let { URL(it) },
        sourceLocation = toClearlyDefinedSourceLocation(id, data.vcs, data.sourceArtifact)
    )

    val curation = Curation(
        described = described.takeIf { it.hasNonNullProperty() },
        licensed = licenseExpression?.let { Licensed(declared = it) }
    )

    val patch = Patch(
        coordinates = id.toClearlyDefinedCoordinates(),
        revisions = mapOf(id.version to curation)
    )

    return ContributionPatch(info, listOf(patch))
}
