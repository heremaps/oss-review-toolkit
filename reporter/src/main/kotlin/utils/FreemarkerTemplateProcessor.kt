/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.utils

import freemarker.cache.ClassTemplateLoader
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler

import java.io.File

import kotlin.reflect.full.memberProperties

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseFileInfo
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.licenses.filterExcluded
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.log

/**
 * A class to process [Apache Freemarker][1] templates, intended to be called by a [Reporter] that uses the generated
 * files in a postprocessing step, e.g., generating a PDF.
 *
 * [1]: https://freemarker.apache.org
 */
class FreemarkerTemplateProcessor(
    private val filePrefix: String,
    private val fileExtension: String,
    private val templatesResourceDirectory: String
) {
    companion object {
        const val OPTION_TEMPLATE_ID = "template.id"
        const val OPTION_TEMPLATE_PATH = "template.path"
    }

    /**
     * Process all Freemarker templates referenced in "template.id" and "template.path" options and returns the
     * generated files.
     */
    fun processTemplates(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val projects = input.ortResult.getProjects().map { project ->
            PackageModel(project.id, input)
        }

        val packages = input.ortResult.getPackages().map { pkg ->
            PackageModel(pkg.pkg.id, input)
        }

        val dataModel = mapOf(
            "projects" to projects,
            "packages" to packages,
            "ortResult" to input.ortResult,
            "licenseTextProvider" to input.licenseTextProvider,
            "helper" to TemplateHelper(input.ortResult, input.licenseClassifications)
        )

        val freemarkerConfig = Configuration(Configuration.VERSION_2_3_30).apply {
            defaultEncoding = "UTF-8"
            fallbackOnNullLoopVariable = false
            logTemplateExceptions = true
            tagSyntax = Configuration.SQUARE_BRACKET_TAG_SYNTAX
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            templateLoader = ClassTemplateLoader(
                this@FreemarkerTemplateProcessor.javaClass.classLoader,
                "templates/$templatesResourceDirectory"
            )
            wrapUncheckedExceptions = true
        }

        val templatePaths = options[OPTION_TEMPLATE_PATH]?.split(",").orEmpty()
        val templateIds = options[OPTION_TEMPLATE_ID]?.split(",")
            ?: if (templatePaths.isEmpty()) listOf("default") else emptyList()

        val templateFiles = templatePaths.map { path ->
            File(path).expandTilde().also {
                require(it.isFile) { "Could not find template file at ${it.absolutePath}." }
            }
        }

        val fileExtensionWithDot = fileExtension.takeIf { it.isEmpty() } ?: ".$fileExtension"
        val outputFiles = mutableListOf<File>()

        templateIds.forEach { id ->
            val outputFile = outputDir.resolve("$filePrefix$id$fileExtensionWithDot")

            log.info { "Generating output file '$outputFile' using template id '$id'." }

            val template = freemarkerConfig.getTemplate("$id.ftl")
            outputFile.writer().use { template.process(dataModel, it) }

            outputFiles += outputFile
        }

        templateFiles.forEach { file ->
            val outputFile = outputDir.resolve("$filePrefix${file.nameWithoutExtension}$fileExtensionWithDot")

            log.info { "Generating output file '$outputFile' using template file '${file.absolutePath}'." }

            val template = freemarkerConfig.run {
                setDirectoryForTemplateLoading(file.parentFile)
                getTemplate(file.name)
            }
            outputFile.writer().use { template.process(dataModel, it) }

            outputFiles += outputFile
        }

        return outputFiles
    }

    /**
     * License information for a single package or project.
     */
    class PackageModel(
        val id: Identifier,
        private val input: ReporterInput
    ) {
        /**
         * True if the package is excluded.
         */
        val excluded: Boolean by lazy { input.ortResult.isExcluded(id) }

        /**
         * The resolved license information for the package.
         */
        val license: ResolvedLicenseInfo by lazy {
            val resolved = input.licenseInfoResolver.resolveLicenseInfo(id).filterExcluded()
            resolved.copy(licenses = resolved.licenses.sortedBy { it.license.toString() })
        }

        /**
         * The resolved license file information for the package.
         */
        val licenseFiles: ResolvedLicenseFileInfo by lazy { input.licenseInfoResolver.resolveLicenseFiles(id) }

        /**
         * Return all [ResolvedLicense]s for this package excluding those licenses which are contained in any of the
         * license files. This is useful when the raw texts of the license files are included in the generated output
         * file and all licenses not contained in those files shall be listed separately.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun licensesNotInLicenseFiles(): List<ResolvedLicense> {
            val outputFileLicenses = licenseFiles.files.flatMap { it.licenses }
            return license.filter { it !in outputFileLicenses }
        }
    }

    /**
     * A collection of helper functions for the Freemarker templates.
     */
    class TemplateHelper(private val ortResult: OrtResult, private val licenseClassifications: LicenseClassifications) {
        /**
         * Return [packages] that are a dependency of at least one of the provided [projects][projectIds].
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun filterByProjects(
            packages: Collection<PackageModel>,
            projectIds: Collection<Identifier>
        ): List<PackageModel> {
            val dependencies = projectIds.mapNotNull { ortResult.getProject(it) }
                .flatMapTo(mutableSetOf()) { it.collectDependencies() }

            return packages.filter { pkg -> pkg.id in dependencies }
        }

        @Suppress("UNUSED") // This function is used in the templates.
        fun filterForCategory(licenses: Collection<ResolvedLicense>, category: String): List<ResolvedLicense> =
            licenses.filter { resolvedLicense ->
                licenseClassifications[resolvedLicense.license]?.categories?.contains(category) ?: true
            }

        /**
         * Return a [LicenseView] constant by name to make them easily available to the Freemarker templates.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun licenseView(name: String): LicenseView =
            LicenseView.Companion::class.memberProperties
                .first { it.name == name }
                .get(LicenseView.Companion) as LicenseView

        /**
         * Merge the [ResolvedLicense]s of multiple [models] and filter them using [licenseView]. [Omits][omitExcluded]
         * excluded packages, licenses, and copyrights by default. The returned list is sorted by license identifier.
         */
        @JvmOverloads
        @Suppress("UNUSED") // This function is used in the templates.
        fun mergeLicenses(
            models: Collection<PackageModel>,
            licenseView: LicenseView = LicenseView.ALL,
            omitExcluded: Boolean = true
        ): List<ResolvedLicense> =
            mergeResolvedLicenses(
                models.filter { !omitExcluded || !it.excluded }.flatMap {
                    val licenses = it.license.filter(licenseView).licenses
                    if (omitExcluded) licenses.filterExcluded() else licenses
                }
            )

        /**
         * Return a list of [ResolvedLicense]s where all duplicate entries for a single license in [licenses] are
         * merged. The returned list is sorted by license identifier.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun mergeResolvedLicenses(licenses: List<ResolvedLicense>): List<ResolvedLicense> =
            licenses.groupBy { it.license }
                .map { (_, licenses) -> licenses.merge() }
                .sortedBy { it.license.toString() }
    }
}

private fun List<ResolvedLicense>.merge(): ResolvedLicense {
    require(isNotEmpty()) { "Cannot not merge an empty list." }
    return ResolvedLicense(
        license = first().license,
        sources = flatMapTo(mutableSetOf()) { it.sources },
        originalDeclaredLicenses = flatMapTo(mutableSetOf()) { it.originalDeclaredLicenses },
        locations = flatMapTo(mutableSetOf()) { it.locations }
    )
}
