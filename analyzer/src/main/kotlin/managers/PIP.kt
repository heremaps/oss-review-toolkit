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

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Scope
import com.here.ort.util.OkHttpClientHelper
import com.here.ort.util.OS
import com.here.ort.util.ProcessCapture
import com.here.ort.util.checkCommandVersion
import com.here.ort.util.jsonMapper
import com.here.ort.util.log

import com.vdurmont.semver4j.Semver

import java.io.File
import java.util.SortedSet

import okhttp3.Request

object PIP : PackageManager(
        "https://pip.pypa.io/",
        "Python",
        // See https://packaging.python.org/discussions/install-requires-vs-requirements/ and
        // https://caremad.io/posts/2013/07/setup-vs-requirement/.
        listOf("requirements*.txt", "setup.py")
) {
    private const val PIPDEPTREE_VERSION = "0.10.1"
    private const val PYDEP_REVISION = "ea18b40fca03438a0fb362e552c26df2d29fc19f"

    // TODO: Need to replace this hard-coded list of domains with e.g. a command line option.
    private val TRUSTED_HOSTS = listOf(
        "pypi.python.org"
    ).flatMap { listOf("--trusted-host", it) }.toTypedArray()

    override fun command(workingDir: File): String {
        return "pip"
    }

    private fun runPipInVirtualEnv(virtualEnvDir: File, workingDir: File, vararg commandArgs: String): ProcessCapture {
        return runInVirtualEnv(virtualEnvDir, workingDir, command(workingDir), *TRUSTED_HOSTS, *commandArgs)
    }

    private fun runInVirtualEnv(virtualEnvDir: File, workingDir: File, commandName: String, vararg commandArgs: String)
            : ProcessCapture {
        val binDir = if (OS.isWindows) "Scripts" else "bin"
        var command = File(virtualEnvDir, binDir + File.separator + commandName)

        if (OS.isWindows && command.extension.isEmpty()) {
            // On Windows specifying the extension is optional, so try them in order.
            val extensions = System.getenv("PATHEXT").split(File.pathSeparator)
            val commandWin = extensions.map { File(command.path + it.toLowerCase()) }.find { it.isFile }
            if (commandWin != null) {
                command = commandWin
            }
        }

        // TODO: Maybe work around long shebang paths in generated scripts within a virtualenv by calling the Python
        // executable in the virtualenv directly, see https://github.com/pypa/virtualenv/issues/997.
        val process = ProcessCapture(workingDir, command.path, *commandArgs)
        log.debug { process.stdout() }
        return process
    }

    override fun prepareResolution() {
        // virtualenv bundles pip. In order to get pip 9.0.1 inside a virtualenv, which is a version that supports
        // installing packages from a Git URL that include a commit SHA1, we need at least virtualenv 15.1.0.
        checkCommandVersion("virtualenv", Semver("15.1.0"), ignoreActualVersion = Main.ignoreVersions)
    }

    override fun resolveDependencies(projectDir: File, workingDir: File, definitionFile: File): AnalyzerResult? {
        val virtualEnvDir = setupVirtualEnv(workingDir, definitionFile)

        // List all packages installed locally in the virtualenv in JSON format.
        val pipdeptree = runInVirtualEnv(virtualEnvDir, workingDir, "pipdeptree", "-l", "--json")

        // Install pydep after running any other command but before looking at the dependencies because it
        // downgrades pip to version 7.1.2. Use it to get meta-information from about the project from setup.py. As
        // pydep is not on PyPI, install it from Git instead.
        val pydepUrl = "git+https://github.com/sourcegraph/pydep@$PYDEP_REVISION"
        val pip = if (OS.isWindows) {
            // On Windows, in-place pip up- / downgrades require pip to be wrapped by "python -m", see
            // https://github.com/pypa/pip/issues/1299.
            runInVirtualEnv(virtualEnvDir, workingDir, "python", "-m", command(workingDir),
                    *TRUSTED_HOSTS, "install", pydepUrl)
        } else {
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", pydepUrl)
        }
        pip.requireSuccess()

        val pydep = if (OS.isWindows) {
            // On Windows, the script itself is not executable, so we need to wrap the call by "python".
            runInVirtualEnv(virtualEnvDir, workingDir, "python",
                    virtualEnvDir.path + "\\Scripts\\pydep-run.py", "info", ".")
        } else {
            runInVirtualEnv(virtualEnvDir, workingDir, "pydep-run.py", "info", ".")
        }
        pydep.requireSuccess()

        // What pydep actually returns as "repo_url" is either setup.py's
        // - "url", denoting the "home page for the package", or
        // - "download_url", denoting the "location where the package may be downloaded".
        // So the best we can do it so map this the project's homepage URL.
        val (projectName, projectVersion, projectHomepage) = jsonMapper.readTree(pydep.stdout()).let {
            listOf(it["project_name"].asText(), it["version"].asText(), it["repo_url"].asText())
        }

        val packages = sortedSetOf<Package>()
        val installDependencies = sortedSetOf<PackageReference>()

        if (pipdeptree.exitValue() == 0) {
            val allDependencies = jsonMapper.readTree(pipdeptree.stdout())
            val packageTemplates = sortedSetOf<Package>()
            parseDependencies(projectName, allDependencies, packageTemplates, installDependencies)

            packageTemplates.mapTo(packages) { pkg ->
                // See https://wiki.python.org/moin/PyPIJSON.
                val pkgRequest = Request.Builder()
                        .get()
                        .url("https://pypi.python.org/pypi/${pkg.name}/${pkg.version}/json")
                        .build()

                val pkgJson = OkHttpClientHelper.execute("analyzer", pkgRequest).use { response ->
                    response.body()?.string()
                }

                @Suppress("CatchException")
                try {
                    val pkgData = jsonMapper.readTree(pkgJson)
                    val pkgInfo = pkgData["info"]

                    // TODO: Support multiple package types of the same package version. Arbitrarily choose the
                    // first for now.
                    val pkgRelease = pkgData["releases"][pkg.version][0]

                    val declaredLicenses = sortedSetOf<String>()

                    // Use the top-level license field as well as the license classifiers as the declared licenses.
                    setOf(pkgInfo["license"]).mapNotNullTo(declaredLicenses) {
                        it?.asText()
                    }

                    // Example license classifier:
                    // "License :: OSI Approved :: GNU Library or Lesser General Public License (LGPL)"
                    pkgInfo["classifiers"]?.mapNotNullTo(declaredLicenses) {
                        val classifier = it.asText().split(" :: ")
                        if (classifier.first() == "License") {
                            classifier.last()
                        } else {
                            null
                        }
                    }

                    // Amend package information with more details.
                    Package(
                            packageManager = pkg.packageManager,
                            namespace = pkg.namespace,
                            name = pkg.name,
                            version = pkg.version,
                            declaredLicenses = declaredLicenses,
                            description = pkgInfo["summary"]?.asText() ?: pkg.description,
                            homepageUrl = pkgInfo["home_page"]?.asText() ?: pkg.homepageUrl,
                            downloadUrl = pkgRelease["url"]?.asText() ?: pkg.downloadUrl,
                            hash = pkgRelease["md5_digest"]?.asText() ?: pkg.hash,
                            hashAlgorithm = "MD5",
                            vcsProvider = pkg.vcsProvider,
                            vcsUrl = pkg.vcsUrl,
                            vcsRevision = pkg.vcsRevision,
                            vcsPath = pkg.vcsPath
                    )
                } catch (e: Exception) {
                    if (Main.stacktrace) {
                        e.printStackTrace()
                    }

                    log.warn { "Unable to retrieve PyPI meta-data for package '${pkg.identifier}': ${e.message}" }

                    // Fall back to returning the original package data.
                    pkg
                }
            }
        } else {
            log.error { "Unable to determine dependencies for project in directory '$workingDir'." }
        }

        // TODO: Handle "extras" and "tests" dependencies.
        val scopes = sortedSetOf(
                Scope("install", true, installDependencies)
        )

        val vcs = VersionControlSystem.fromDirectory(projectDir).firstOrNull()
        val project = Project(
                packageManager = javaClass.simpleName,
                namespace = "",
                name = projectName,
                version = projectVersion,
                declaredLicenses = sortedSetOf(),  // TODO: Get the licenses for local projects.
                aliases = emptyList(),
                vcsProvider = vcs?.javaClass?.simpleName ?: "",
                vcsUrl = vcs?.getRemoteUrl(projectDir) ?: "",
                vcsRevision = vcs?.getWorkingRevision(projectDir) ?: "",
                vcsPath = vcs?.getPathToRoot(projectDir),
                homepageUrl = projectHomepage,
                scopes = scopes
        )

        // Remove the virtualenv by simply deleting the directory.
        virtualEnvDir.deleteRecursively()

        return AnalyzerResult(true, project, packages)
    }

    private fun setupVirtualEnv(workingDir: File, definitionFile: File): File {
        // Create an out-of-tree virtualenv.
        println("Creating a virtualenv for the '${workingDir.name}' project directory...")
        val virtualEnvDir = createTempDir(workingDir.name, "virtualenv")
        ProcessCapture(workingDir, "virtualenv", virtualEnvDir.path).requireSuccess()

        var pip: ProcessCapture

        // Install pipdeptree inside the virtualenv as that's the only way to make it report only the project's
        // dependencies instead of those of all (globally) installed packages, see
        // https://github.com/naiquevin/pipdeptree#known-issues.
        // We only depend on pipdeptree to be at least version 0.5.0 for JSON output, but we stick to a fixed
        // version to be sure to get consistent results.
        pip = runPipInVirtualEnv(virtualEnvDir, workingDir, "install", "pipdeptree==$PIPDEPTREE_VERSION")
        pip.requireSuccess()

        // TODO: Find a way to make installation of packages with native extensions work on Windows where often
        // the appropriate compiler is missing / not set up, e.g. by using pre-built packages from
        // http://www.lfd.uci.edu/~gohlke/pythonlibs/
        println("Installing dependencies for the '${workingDir.name}' project directory...")
        pip = if (definitionFile.name == "setup.py") {
            // Note that this only installs required "install" dependencies, not "extras" or "tests" dependencies.
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", ".")
        } else {
            // In "setup.py"-speak, "requirements.txt" just contains required "install" dependencies.
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", "-r", definitionFile.name)
        }

        // TODO: Consider logging a warning instead of an error if the command is run on a file that likely belongs
        // to a test.
        with(pip) {
            if (exitValue() != 0) {
                log.error { failMessage }
            }
        }

        return virtualEnvDir
    }

    private fun parseDependencies(rootPackageName: String, allDependencies: Iterable<JsonNode>,
                                  packages: SortedSet<Package>, installDependencies: SortedSet<PackageReference>) {
        // pipdeptree returns JSON like:
        // [
        //     {
        //         "dependencies": [],
        //         "package": {
        //             "installed_version": "1.16",
        //             "package_name": "patch",
        //             "key": "patch"
        //         }
        //     },
        //     {
        //         "dependencies": [
        //             {
        //                 "required_version": null,
        //                 "installed_version": "36.5.0",
        //                 "package_name": "setuptools",
        //                 "key": "setuptools"
        //             }
        //         ],
        //         "package": {
        //             "installed_version": "1.2.1",
        //             "package_name": "zc.lockfile",
        //             "key": "zc.lockfile"
        //         }
        //     }
        // ]

        val packageData = allDependencies.find { it["package"]["package_name"].asText() == rootPackageName }
        if (packageData == null) {
            log.error { "No package data found for '$rootPackageName'." }
            return
        }

        val packageDependencies = packageData["dependencies"]
        packageDependencies.forEach {
            val packageName = it["package_name"].asText()
            val packageVersion = it["installed_version"].asText()

            val dependencyPackage = Package(
                    packageManager = javaClass.simpleName,
                    namespace = "",
                    name = packageName,
                    version = packageVersion,
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = null,
                    downloadUrl = null,
                    hash = "",
                    hashAlgorithm = null,
                    vcsProvider = null,
                    vcsUrl = null,
                    vcsRevision = null,
                    vcsPath = null
            )
            packages.add(dependencyPackage)

            val packageRef = dependencyPackage.toReference()
            installDependencies.add(packageRef)

            parseDependencies(packageName, allDependencies, packages, packageRef.dependencies)
        }
    }
}
