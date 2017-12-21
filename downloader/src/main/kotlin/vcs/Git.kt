/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.downloader.vcs

import ch.frankel.slf4k.*

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.WorkingTree
import com.here.ort.downloader.WorkingTreeWithRevision
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OS
import com.here.ort.utils.log
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.safeMkdirs

import java.io.File
import java.io.IOException

abstract class GitBase : VersionControlSystem<WorkingTreeWithRevision>() {
    override fun getVersion(): String {
        val gitVersionRegex = Regex("[Gg]it [Vv]ersion (?<version>[\\d.a-z-]+)(\\s.+)?")

        return getCommandVersion("git") {
            gitVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingTree(vcsDirectory: File) =
            object : WorkingTreeWithRevision(vcsDirectory, this@GitBase.toString()) {
                override fun isValid(): Boolean {
                    if (!workingDir.isDirectory) {
                        return false
                    }

                    val isInsideWorkTree = ProcessCapture(workingDir, "git", "rev-parse", "--is-inside-work-tree")
                    return isInsideWorkTree.exitValue() == 0 && isInsideWorkTree.stdout().trimEnd().toBoolean()
                }

                override fun getRemoteUrl() =
                        runGitCommand(workingDir, "remote", "get-url", "origin").stdout().trimEnd()

                override fun getRevision() =
                        runGitCommand(workingDir, "rev-parse", "HEAD").stdout().trimEnd()

                override fun getRootPath() =
                        runGitCommand(workingDir, "rev-parse", "--show-toplevel").stdout().trimEnd('\n', '/')

                override fun listRemoteTags(): List<String> {
                    val tags = runGitCommand(workingDir, "ls-remote", "--refs", "origin", "refs/tags/*")
                            .stdout().trimEnd()
                    return tags.lines().map {
                        it.split('\t').last().removePrefix("refs/tags/")
                    }
                }
            }

    protected fun runGitCommand(workingDir: File, vararg args: String) =
            ProcessCapture(workingDir, "git", *args).requireSuccess()
}

object Git : GitBase() {
    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("git", true)

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("git", "ls-remote", vcsUrl).exitValue() == 0

    /**
     * Clones the Git repository using the native Git command.
     *
     * @param vcsPath If this parameter is not null or empty, the working tree is deleted and the path is selectively
     *                checked out using 'git checkout HEAD -- vcsPath'.
     *
     * @throws DownloadException In case the download failed.
     */
    @Suppress("ComplexMethod")
    override fun download(vcs: VcsInfo, version: String, targetDir: File): WorkingTree {
        log.info { "Using $this version ${getVersion()}." }

        try {
            // Do not use "git clone" to have more control over what is being fetched.
            runGitCommand(targetDir, "init")
            runGitCommand(targetDir, "remote", "add", "origin", vcs.url)

            if (OS.isWindows) {
                runGitCommand(targetDir, "config", "core.longpaths", "true")
            }

            val workingTree = getWorkingTree(targetDir)

            if (vcs.path.isNotBlank()) {
                log.info { "Configuring Git to do sparse checkout of path '${vcs.path}'." }
                runGitCommand(targetDir, "config", "core.sparseCheckout", "true")
                val gitInfoDir = File(targetDir, ".git/info").apply { safeMkdirs() }
                File(gitInfoDir, "sparse-checkout").writeText(vcs.path)
            }

            val committish = if (vcs.revision.isNotBlank()) vcs.revision else "HEAD"

            // Do safe network bandwidth, first try to only fetch exactly the committish we want.
            try {
                runGitCommand(targetDir, "fetch", "origin", committish)
                runGitCommand(targetDir, "checkout", "FETCH_HEAD")
                return workingTree
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn {
                    "Could not fetch only '$committish': ${e.message}\n" +
                            "Falling back to fetching everything."
                }
            }

            // Fall back to fetching everything.
            log.info { "Fetching origin and trying to checkout '$committish'." }
            runGitCommand(targetDir, "fetch", "origin")

            try {
                runGitCommand(targetDir, "checkout", committish)
                return workingTree
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn { "Could not checkout '$committish': ${e.message}" }
            }

            // If checking out the provided committish did not work and we have a version, try finding a tag
            // belonging to the version to checkout.
            if (version.isNotBlank()) {
                log.info { "Trying to guess tag for version '$version'." }

                val tag = runGitCommand(targetDir, "ls-remote", "--tags", "origin")
                        .stdout()
                        .lineSequence()
                        .map { it.split('\t').last() }
                        .find { it.endsWith(version) || it.endsWith(version.replace('.', '_')) }

                if (tag != null) {
                    log.info { "Using '$tag'." }
                    runGitCommand(targetDir, "fetch", "origin", tag)
                    runGitCommand(targetDir, "checkout", "FETCH_HEAD")
                    return workingTree
                }

                log.warn { "No matching tag found for version '$version'." }
            }

            throw IOException("Unable to determine a committish to checkout.")
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not clone ${vcs.url}: ${e.message}" }
            throw DownloadException("Could not clone ${vcs.url}.", e)
        }
    }
}
