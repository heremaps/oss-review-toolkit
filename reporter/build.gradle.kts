/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

val antennaVersion: String by project
val apachePoiVersion: String by project
val apachePoiSchemasVersion: String by project
val cyclonedxCoreJavaVersion: String by project
val flexmarkVersion: String by project
val hamcrestCoreVersion: String by project
val jacksonVersion: String by project
val kotlinxHtmlVersion: String by project
val retrofitVersion: String by project
val simpleExcelVersion: String by project
val xalanVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

val generatedResourcesDir = file("$buildDir/generated-resources/main")
val copyWebAppTemplate by tasks.registering(Copy::class) {
    dependsOn(":reporter-web-app:yarnBuild")

    from(project(":reporter-web-app").file("build")) {
        include("scan-report-template.html")
    }

    into(generatedResourcesDir)
    outputs.cacheIf { true }
}

sourceSets.named("main") {
    output.dir(mapOf("builtBy" to copyWebAppTemplate), generatedResourcesDir)
}

repositories {
    exclusiveContent {
        forRepository {
            maven("http://www.robotooling.com/maven/")
        }

        filter {
            includeGroup("bad.robot")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://download.eclipse.org/antenna/releases/")
        }

        filter {
            includeGroup("org.eclipse.sw360.antenna")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://jitpack.io")
        }

        filter {
            includeGroup("com.github.ralfstuckert.pdfbox-layout")
        }
    }
}

dependencies {
    api(project(":model"))

    implementation(project(":downloader"))
    implementation(project(":spdx-utils"))
    implementation(project(":utils"))

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.squareup.retrofit2:converter-jackson:$retrofitVersion")
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")
    implementation("org.apache.poi:ooxml-schemas:$apachePoiSchemasVersion")
    implementation("org.apache.poi:poi-ooxml:$apachePoiVersion")
    implementation("org.cyclonedx:cyclonedx-core-java:$cyclonedxCoreJavaVersion")
    implementation("org.eclipse.sw360.antenna:attribution-document-core:$antennaVersion")
    implementation("org.eclipse.sw360.antenna:attribution-document-basic-bundle:$antennaVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinxHtmlVersion")

    // This is required to not depend on the version of Apache Xalan bundled with the JDK. Otherwise the formatting of
    // the HTML generated in StaticHtmlReporter is slightly different with different Java versions.
    implementation("xalan:xalan:$xalanVersion")

    funTestImplementation("bad.robot:simple-excel:$simpleExcelVersion")
    funTestImplementation("org.hamcrest:hamcrest-core:$hamcrestCoreVersion")
}
