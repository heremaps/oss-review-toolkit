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

package com.here.ort.scanner

import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.Provenance
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.VcsInfo
import com.here.ort.model.jsonMapper

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import io.kotlintest.specs.StringSpec
import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.contain
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant

class HttpCacheTest : StringSpec() {
    private val loopback = InetAddress.getLoopbackAddress()
    private val port = 8888

    private class MyHttpHandler : HttpHandler {
        val requests = mutableMapOf<String, String>()

        override fun handle(exchange: HttpExchange) {
            when (exchange.requestMethod) {
                "PUT" -> {
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_CREATED, 0)
                    requests[exchange.requestURI.toString()] = exchange.requestBody.reader().use { it.readText() }
                }
                "GET" -> {
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
                    exchange.responseBody.writer().use { it.write(requests[exchange.requestURI.toString()]) }
                }
            }
        }
    }

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        val server = HttpServer.create(InetSocketAddress(loopback, port), 0)

        try {
            // Start the local HTTP server.
            server.createContext("/", MyHttpHandler())
            server.start()

            super.interceptTestCase(context, test)
        } finally {
            // Ensure the server is properly stopped even in case of exceptions.
            server.stop(0)
        }
    }

    private val id = Identifier("provider", "namespace", "name", "version")

    private val sourceArtifact = RemoteArtifact(
            "http://example.com/sources.jar",
            "0123456789abcdef0123456789abcdef01234567",
            HashAlgorithm.SHA1)

    private val vcs = VcsInfo("Git", "https://example.com/repo.git", "0123456789abcdef0123456789abcdef01234567", "")

    private val pkg = Package.EMPTY.copy(
            id = id,
            sourceArtifact = sourceArtifact,
            vcs = vcs,
            vcsProcessed = vcs.normalize()
    )

    private val downloadTime1 = Instant.EPOCH + Duration.ofDays(1)
    private val downloadTime2 = Instant.EPOCH + Duration.ofDays(2)
    private val downloadTime3 = Instant.EPOCH + Duration.ofDays(3)

    private val provenanceWithSourceArtifact = Provenance(
            downloadTime = downloadTime1,
            sourceArtifact = sourceArtifact
    )
    private val provenanceWithVcsInfo = Provenance(
            downloadTime = downloadTime2,
            vcsInfo = vcs
    )
    private val provenanceEmpty = Provenance(downloadTime3)

    private val scannerDetails1 = ScannerDetails("name 1", "1.0.0", "config 1")
    private val scannerDetails2 = ScannerDetails("name 2", "2.0.0", "config 2")
    private val scannerDetailsCompatibleVersion1 = ScannerDetails("name 1", "1.0.1", "config 1")
    private val scannerDetailsCompatibleVersion2 = ScannerDetails("name 1", "1.0.0-alpha.1", "config 1")
    private val scannerDetailsIncompatibleVersion = ScannerDetails("name 1", "1.1.0", "config 1")

    private val scannerStartTime1 = downloadTime1 + Duration.ofMinutes(1)
    private val scannerEndTime1 = scannerStartTime1 + Duration.ofMinutes(1)
    private val scannerStartTime2 = downloadTime2 + Duration.ofMinutes(1)
    private val scannerEndTime2 = scannerStartTime2 + Duration.ofMinutes(1)

    private val scanSummaryWithFiles = ScanSummary(
            scannerStartTime1,
            scannerEndTime1,
            1,
            sortedSetOf("license 1.1", "license 1.2"),
            sortedSetOf("error 1.1", "error 1.2")
    )
    private val scanSummaryWithoutFiles = ScanSummary(
            scannerStartTime2,
            scannerEndTime2,
            0,
            sortedSetOf(),
            sortedSetOf()
    )

    private val rawResultWithContent = jsonMapper.readTree("\"key 1\": \"value 1\"")
    private val rawResultEmpty = EMPTY_JSON_NODE

    init {
        "Scan result can be added to the cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles,
                    rawResultWithContent)

            val result = cache.add(id, scanResult)
            val cachedResults = cache.read(id)

            result shouldBe true
            cachedResults.id shouldBe id
            cachedResults.results.size shouldBe 1
            cachedResults.results[0] shouldBe scanResult
        }

        "Does not add scan result with fileCount 0 to cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithoutFiles,
                    rawResultWithContent)

            val result = cache.add(id, scanResult)
            val cachedResults = cache.read(id)

            result shouldBe false
            cachedResults.id shouldBe id
            cachedResults.results.size shouldBe 0
        }

        "Does not add scan result without provenance information to cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult = ScanResult(provenanceEmpty, scannerDetails1, scanSummaryWithFiles,
                    rawResultEmpty)

            val result = cache.add(id, scanResult)
            val cachedResults = cache.read(id)

            result shouldBe false
            cachedResults.id shouldBe id
            cachedResults.results.size shouldBe 0
        }

        "Can retrieve all scan results from cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles,
                    rawResultWithContent)
            val scanResult2 = ScanResult(provenanceWithSourceArtifact, scannerDetails2, scanSummaryWithFiles,
                    rawResultWithContent)

            val result1 = cache.add(id, scanResult1)
            val result2 = cache.add(id, scanResult2)
            val cachedResults = cache.read(id)

            result1 shouldBe true
            result2 shouldBe true
            cachedResults.results.size shouldBe 2
            cachedResults.results should contain(scanResult1)
            cachedResults.results should contain(scanResult2)
        }

        "Can retrieve all scan results for specific scanner from cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles,
                    rawResultWithContent)
            val scanResult2 = ScanResult(provenanceWithVcsInfo, scannerDetails1, scanSummaryWithFiles,
                    rawResultWithContent)
            val scanResult3 = ScanResult(provenanceWithSourceArtifact, scannerDetails2, scanSummaryWithFiles,
                    rawResultWithContent)

            val result1 = cache.add(id, scanResult1)
            val result2 = cache.add(id, scanResult2)
            val result3 = cache.add(id, scanResult3)
            val cachedResults = cache.read(pkg, scannerDetails1)

            result1 shouldBe true
            result2 shouldBe true
            result3 shouldBe true
            cachedResults.results.size shouldBe 2
            cachedResults.results should contain(scanResult1)
            cachedResults.results should contain(scanResult2)
        }

        "Can retrieve all scan results for compatible scanners from cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles,
                    rawResultWithContent)
            val scanResultCompatible1 = ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion1,
                    scanSummaryWithFiles, rawResultWithContent)
            val scanResultCompatible2 = ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion2,
                    scanSummaryWithFiles, rawResultWithContent)
            val scanResultIncompatible = ScanResult(provenanceWithSourceArtifact, scannerDetailsIncompatibleVersion,
                    scanSummaryWithFiles, rawResultWithContent)

            val result = cache.add(id, scanResult)
            val resultCompatible1 = cache.add(id, scanResultCompatible1)
            val resultCompatible2 = cache.add(id, scanResultCompatible2)
            val resultIncompatible = cache.add(id, scanResultIncompatible)
            val cachedResults = cache.read(pkg, scannerDetails1)

            result shouldBe true
            resultCompatible1 shouldBe true
            resultCompatible2 shouldBe true
            resultIncompatible shouldBe true
            cachedResults.results.size shouldBe 3
            cachedResults.results should contain(scanResult)
            cachedResults.results should contain(scanResultCompatible1)
            cachedResults.results should contain(scanResultCompatible2)
        }

        "Returns only packages with matching provenance" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResultSourceArtifactMatching = ScanResult(provenanceWithSourceArtifact, scannerDetails1,
                    scanSummaryWithFiles, rawResultWithContent)
            val scanResultVcsMatching = ScanResult(provenanceWithVcsInfo, scannerDetails1, scanSummaryWithFiles,
                    rawResultWithContent)
            val provenanceNonMatching = provenanceWithSourceArtifact.copy(
                    sourceArtifact = sourceArtifact.copy(url = "http://example.com/source.2.jar"))
            val scanResultNonMatching = ScanResult(provenanceNonMatching, scannerDetails1, scanSummaryWithFiles,
                    rawResultWithContent)

            val result1 = cache.add(id, scanResultSourceArtifactMatching)
            val result2 = cache.add(id, scanResultVcsMatching)
            val result3 = cache.add(id, scanResultNonMatching)
            val cachedResults = cache.read(pkg, scannerDetails1)

            result1 shouldBe true
            result2 shouldBe true
            result3 shouldBe true
            cachedResults.results.size shouldBe 2
            cachedResults.results should contain(scanResultSourceArtifactMatching)
            cachedResults.results should contain(scanResultVcsMatching)
        }
    }
}
