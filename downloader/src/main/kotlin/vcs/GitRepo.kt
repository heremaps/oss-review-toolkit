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

package org.ossreviewtoolkit.downloader.vcs

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

import java.io.File
import java.io.IOException

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.xmlMapper
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.ProcessCapture
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.getPathFromEnvironment
import org.ossreviewtoolkit.utils.isSymbolicLink
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.realFile
import org.ossreviewtoolkit.utils.searchUpwardsForSubdirectory
import org.ossreviewtoolkit.utils.showStackTrace

/**
 * The branch of git-repo to use. This allows to override git-repo's default of using the "stable" branch.
 */
private const val GIT_REPO_BRANCH = "stable"

/**
 * The minimal manifest structure as used by the wrapping "manifest.xml" file as of repo version 2.4. For the full
 * structure see https://gerrit.googlesource.com/git-repo/+/refs/heads/master/docs/manifest-format.md.
 */
private data class Manifest(
    val include: Include
)

/**
 * The include tag of a wrapping "manifest.xml" file, see
 * https://gerrit.googlesource.com/git-repo/+/refs/heads/master/docs/manifest-format.md#Element-include.
 */
private data class Include(
    @JacksonXmlProperty(isAttribute = true)
    val name: String
)

class GitRepo : VersionControlSystem(), CommandLineTool {
    override val type = VcsType.GIT_REPO
    override val priority = 50
    override val defaultBranchName = "master"
    override val latestRevisionNames = listOf("HEAD", "@")

    override fun command(workingDir: File?) = "repo"

    override fun getVersion() = getVersion(null)

    override fun transformVersion(output: String): String {
        val launcherVersion = output.lineSequence().mapNotNull { line ->
            line.removePrefix("repo launcher version ").takeIf { it != line }
        }.singleOrNull()
            ?: throw IOException("The 'repo' version can only be determined from an initialized working tree.")

        return "$launcherVersion (launcher)"
    }

    override fun getWorkingTree(vcsDirectory: File): WorkingTree {
        val repoRoot = vcsDirectory.searchUpwardsForSubdirectory(".repo")

        return if (repoRoot == null) {
            object : GitWorkingTree(vcsDirectory, type) {
                override fun isValid() = false
            }
        } else {
            // GitRepo is special in that the workingDir points to the Git working tree of the manifest files, yet
            // the root path is the directory containing the ".repo" directory. This way Git operations work on a valid
            // Git repository, but path operations work relative to the path GitRepo was initialized in.
            object : GitWorkingTree(repoRoot.resolve(".repo/manifests"), type) {
                // Return the path to the manifest as part of the VCS information, as that is required to recreate the
                // working tree.
                override fun getInfo(): VcsInfo {
                    val manifestWrapper = getRootPath().resolve(".repo/manifest.xml")

                    val manifestFile = if (manifestWrapper.isSymbolicLink()) {
                        manifestWrapper.realFile()
                    } else {
                        // As of repo 2.4, the active manifest is a real file with an include directive instead of a
                        // symbolic link, see https://gerrit-review.googlesource.com/c/git-repo/+/256313.
                        val manifest = xmlMapper.readValue(manifestWrapper, Manifest::class.java)
                        workingDir.resolve(manifest.include.name)
                    }

                    return super.getInfo().copy(path = manifestFile.relativeTo(workingDir).invariantSeparatorsPath)
                }

                override fun getNested(): Map<String, VcsInfo> {
                    val paths = runRepoCommand(workingDir, "list", "-p").stdout.lines().filter { it.isNotBlank() }
                    val nested = mutableMapOf<String, VcsInfo>()

                    paths.forEach { path ->
                        // Add the nested Repo project.
                        val workingTree = Git().getWorkingTree(getRootPath().resolve(path))
                        nested[path] = workingTree.getInfo()

                        // Add the Git submodules of the nested Repo project.
                        workingTree.getNested().forEach { (submodulePath, vcsInfo) ->
                            nested["$path/$submodulePath"] = vcsInfo
                        }
                    }

                    return nested
                }

                // Return the directory in which "repo init" was run (that directory in not managed with Git).
                override fun getRootPath() = workingDir.parentFile.parentFile
            }
        }
    }

    override fun isApplicableUrlInternal(vcsUrl: String) = false

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        val manifestRevision = vcs.revision.takeUnless { it.isBlank() } ?: "master"
        val manifestPath = vcs.path.takeUnless { it.isBlank() } ?: "default.xml"

        log.info {
            "Initializing git-repo from ${vcs.url} with revision '$manifestRevision' and manifest '$manifestPath'."
        }

        // Clone all projects instead of only those in the "default" group until we support specifying groups.
        runRepoCommand(
            targetDir,
            "init", "--groups=all", "--no-repo-verify",
            "--no-clone-bundle", "--repo-branch=$GIT_REPO_BRANCH",
            "-b", manifestRevision,
            "-u", vcs.url,
            "-m", manifestPath
        )

        return getWorkingTree(targetDir)
    }

    override fun updateWorkingTree(
        workingTree: WorkingTree,
        revision: String,
        path: String,
        recursive: Boolean
    ): Boolean {
        val manifestRevision = revision.takeUnless { it.isBlank() } ?: "master"
        val manifestPath = path.takeUnless { it.isBlank() } ?: "default.xml"

        return try {
            // Switching manifest branches / revisions requires running "init" again.
            runRepoCommand(workingTree.workingDir, "init", "-b", manifestRevision, "-m", manifestPath)

            // Repo allows to checkout Git repositories to nested directories. If a manifest is badly configured, a
            // nested Git checkout overwrites files in a directory of the upper-level Git repository. However, we still
            // want to be able to download such projects, so specify "--force-sync" to work around that issue.
            val syncArgs = mutableListOf("sync", "-c", "--force-sync")

            if (recursive) {
                syncArgs += "--fetch-submodules"
            }

            runRepoCommand(workingTree.workingDir, *syncArgs.toTypedArray())

            log.debug { runRepoCommand(workingTree.workingDir, "info").stdout }

            true
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn {
                "Failed to sync the working tree to revision '$manifestRevision' using manifest '$manifestPath': " +
                        e.collectMessagesAsString()
            }

            false
        }
    }

    private fun runRepoCommand(targetDir: File, vararg args: String) =
        if (Os.isWindows) {
            val repo = getPathFromEnvironment("repo") ?: throw IOException("'repo' not found in PATH.")

            // On Windows, the script itself is not executable, so we need to explicitly specify Python as the
            // interpreter. As of repo version 2.4, Python 3.6 is required also on Windows.
            ProcessCapture(targetDir, "py", "-3", repo.absolutePath, *args).requireSuccess()
        } else {
            ProcessCapture(targetDir, "repo", *args).requireSuccess()
        }
}
