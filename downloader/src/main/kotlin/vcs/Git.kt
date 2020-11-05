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

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.agentproxy.AgentProxyException
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.util.regex.Pattern

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.LsRemoteCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.URIish

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.LicenseFilenamePatterns
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.installAuthenticatorAndProxySelector
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.showStackTrace

// TODO: Make this configurable.
const val GIT_HISTORY_DEPTH = 50

class Git : VersionControlSystem(), CommandLineTool {
    companion object {
        init {
            installAuthenticatorAndProxySelector()

            val sessionFactory = object : JschConfigSessionFactory() {
                @Suppress("EmptyFunctionBlock")
                override fun configure(hc: OpenSshConfig.Host, session: Session) {}

                override fun configureJSch(jsch: JSch) {
                    // Accept unknown hosts.
                    JSch.setConfig("StrictHostKeyChecking", "no")

                    // Limit to "publickey" to avoid "keyboard-interactive" prompts.
                    JSch.setConfig("PreferredAuthentications", "publickey")

                    try {
                        // By default, JGit configures JSch to use identity files (named "identity", "id_rsa" or
                        // "id_dsa") from the current user's ".ssh" directory only, also see
                        // https://www.codeaffine.com/2014/12/09/jgit-authentication/. Additionally configure JSch to
                        // connect to an SSH-Agent if available.
                        if (SSHAgentConnector.isConnectorAvailable()) {
                            val socketFactory = JNAUSocketFactory()
                            val connector = SSHAgentConnector(socketFactory)
                            jsch.identityRepository = RemoteIdentityRepository(connector)
                        }
                    } catch (e: AgentProxyException) {
                        e.showStackTrace()

                        log.error { "Could not create SSH Agent connector: ${e.collectMessagesAsString()}" }
                    }
                }
            }

            SshSessionFactory.setInstance(sessionFactory)
        }
    }

    private val versionRegex = Pattern.compile("[Gg]it [Vv]ersion (?<version>[\\d.a-z-]+)(\\s.+)?")

    override val type = VcsType.GIT
    override val priority = 100
    override val defaultBranchName = "master"
    override val latestRevisionNames = listOf("HEAD", "@")

    override fun command(workingDir: File?) = "git"

    override fun getVersion() = getVersion(null)

    override fun transformVersion(output: String) =
        versionRegex.matcher(output.lineSequence().first()).let {
            if (it.matches()) {
                it.group("version")
            } else {
                ""
            }
        }

    override fun getWorkingTree(vcsDirectory: File): WorkingTree = GitWorkingTree(vcsDirectory, type)

    override fun isApplicableUrlInternal(vcsUrl: String): Boolean =
        runCatching {
            LsRemoteCommand(null).setRemote(vcsUrl).call().isNotEmpty()
        }.onFailure {
            log.debug { "Failed to check whether $type is applicable for $vcsUrl: ${it.collectMessagesAsString()}" }
        }.isSuccess

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        try {
            Git.init().setDirectory(targetDir).call().use { git ->
                git.remoteAdd().setName("origin").setUri(URIish(vcs.url)).call()

                // Enable the more efficient Git wire protocol version 2, if possible. While JGit supports it since its
                // version 5.1, we still use the Git CLI to update the working tree (as JGit does not support sparse
                // checkouts yet), so we need to still also check the Git CLI's version.
                if (Semver(getVersion()).isGreaterThanOrEqualTo("2.18.0")) {
                    git.repository.config.setInt("protocol", null, "version", 2)
                }

                if (Os.isWindows) {
                    git.repository.config.setBoolean("core", null, "longpaths", true)
                }

                if (vcs.path.isNotBlank()) {
                    log.info { "Configuring Git to do sparse checkout of path '${vcs.path}'." }

                    git.repository.config.setBoolean("core", null, "sparseCheckout", true)

                    val gitInfoDir = targetDir.resolve(".git/info").apply { safeMkdirs() }
                    val path = vcs.path.let { if (it.startsWith("/")) it else "/$it" }
                    val sparseCheckoutPatterns = "$path\n" +
                            LicenseFilenamePatterns.getLicenseFileGlobsForDirectory(path).joinToString("\n")

                    gitInfoDir.resolve("sparse-checkout").writeText(sparseCheckoutPatterns)
                }

                git.repository.config.save()
            }
        } catch (e: GitAPIException) {
            throw IOException("Unable to initialize $type working tree at directory '$targetDir'.", e)
        }

        return getWorkingTree(targetDir)
    }

    override fun updateWorkingTree(workingTree: WorkingTree, revision: String, path: String, recursive: Boolean) =
        updateWorkingTreeWithoutSubmodules(workingTree, revision) && (!recursive || updateSubmodules(workingTree))

    private fun updateWorkingTreeWithoutSubmodules(workingTree: WorkingTree, revision: String): Boolean {
        // To safe network bandwidth, first try to only fetch exactly the revision we want. Skip this optimization for
        // SSH URLs to GitHub as GitHub does not have "allowReachableSHA1InWant" (nor "allowAnySHA1InWant") enabled and
        // the SSH transport invokes "git-upload-pack" without the "--stateless-rpc" option, causing different
        // reachability rules to kick in. Over HTTPS, the ref advertisement and the want/have negotiation happen over
        // two separate connections so the later actually does a reachability check instead of relying on the advertised
        // refs.
        if (!workingTree.getRemoteUrl().startsWith("ssh://git@github.com/")) {
            try {
                log.info { "Trying to fetch only revision '$revision' with depth limited to $GIT_HISTORY_DEPTH." }
                run(workingTree.workingDir, "fetch", "--depth", GIT_HISTORY_DEPTH.toString(), "origin", revision)

                // The documentation for git-fetch states that "By default, any tag that points into the histories being
                // fetched is also fetched", but that is not true for shallow fetches of a tag; then the tag itself is
                // not fetched. So create it manually afterwards.
                if (revision in workingTree.listRemoteTags()) {
                    run(workingTree.workingDir, "tag", revision, "FETCH_HEAD")
                }

                return run(workingTree.workingDir, "checkout", revision).isSuccess
            } catch (e: IOException) {
                e.showStackTrace()

                log.warn {
                    "Could not fetch only revision '$revision': ${e.collectMessagesAsString()}\n" +
                            "Falling back to fetching all refs."
                }
            }
        }

        // Fall back to fetching all refs with limited depth of history.
        try {
            log.info { "Trying to fetch all refs with depth limited to $GIT_HISTORY_DEPTH." }
            run(workingTree.workingDir, "fetch", "--depth", GIT_HISTORY_DEPTH.toString(), "--tags", "origin")
            return run(workingTree.workingDir, "checkout", revision).isSuccess
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn {
                "Could not fetch with only a depth of $GIT_HISTORY_DEPTH: ${e.collectMessagesAsString()}\n" +
                        "Falling back to fetching everything."
            }
        }

        // Fall back to fetching everything.
        return try {
            log.info { "Trying to fetch everything including tags." }

            if (workingTree.isShallow()) {
                run(workingTree.workingDir, "fetch", "--unshallow", "--tags", "origin")
            } else {
                run(workingTree.workingDir, "fetch", "--tags", "origin")
            }

            run(workingTree.workingDir, "checkout", revision).isSuccess
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Failed to fetch everything: ${e.collectMessagesAsString()}" }

            false
        }
    }

    private fun updateSubmodules(workingTree: WorkingTree) =
        try {
            !workingTree.workingDir.resolve(".gitmodules").isFile
                    || run(workingTree.workingDir, "submodule", "update", "--init", "--recursive").isSuccess
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Failed to update submodules: ${e.collectMessagesAsString()}" }

            false
        }
}
