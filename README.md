# OSS Review Toolkit

| Linux (OpenJDK 8)            | Windows (Oracle JDK 9)          |
| :--------------------------- | :------------------------------ |
[ ![Linux build status][1]][2] | [![Windows build status][3]][4] |

[1]: https://travis-ci.org/heremaps/oss-review-toolkit.svg?branch=master
[2]: https://travis-ci.org/heremaps/oss-review-toolkit
[3]: https://ci.appveyor.com/api/projects/status/hbc1mn5hpo9a4hcq/branch/master?svg=true
[4]: https://ci.appveyor.com/project/heremaps/oss-review-toolkit/branch/master

The OSS Review Toolkit (ORT for short) is a suite of tools to assist with reviewing Free and Open Source Software dependencies in your software. At a high level, it works by analyzing your source code for dependencies, downloading the source code of the dependencies, scanning all source code for license information, and summarizing the results. The different tools in the suite are designed as libraries (for programmatic use) with minimal command line interfaces (for scripted use, [doing one thing and doing it well](https://en.wikipedia.org/wiki/Unix_philosophy#Do_One_Thing_and_Do_It_Well)).

The goal of ORT is to give the Open Source community a toolset that can enable reviews during source code creation by providing open-sourced tooling for developers to do basic compliance checks and get insights on their dependencies.

The toolkit is envisioned to consist out of the following libraries:

* *Analyzer* - determines dependencies of software project even when multiple package managers are used. No changes to software project required.
* *Downloader* - fetches the source code based on output generated by the Analyzer.
* *Scanner* - wrapper around existing copyright/license scanners which takes as input the output of the Downloader and produces standardized output such as [SPDX](https://spdx.org/).
* *Evaluator* * - Evaluates the scan results from Scanner as OK or NOT OK based on user specified approval/rejection ruleset.
* *Advisor* * - Retrieves security advisories based on Analyzer results.
* *Reporter* * - Summarizes the output from Analyzer, Scanner and Evaluator in an interactive UI of found copyrights, licenses and NOT OK issues.
* *Documenter* * - Generates the outcome of the review, e.g. Open Source notices and annotated [SPDX](https://spdx.org/) files that can be included with your deliverable.

\* Libraries still to be implemented, plan is to have a complete MVP suite by Q3 2018.

## Installation

To get started with the OSS Review Toolkit, simply:

1. Ensure OpenJDK 8 or Oracle JDK 8u161 or later (not the JRE as you need the `javac` compiler) is installed and the `JAVA_HOME`
environment variable set.
2. Clone this repository.
3. Change into the repo directory on your machine and run `./gradlew installDist` to setup the build environment (e.g.
get Gradle etc.) and build/install the start scripts for ORT. The individual start scripts can then be run directly from
their respective locations as follows:

* `./analyzer/build/install/analyzer/bin/analyzer`
* `./graph/build/install/graph/bin/graph`
* `./downloader/build/install/downloader/bin/downloader`
* `./scanner/build/install/scanner/bin/scanner`

Make sure that the locale of your system is set to `en_US.UTF-8`, using other locales might lead to issues with parsing
the output of external tools.

## Supported package managers

Currently, the following package managers / build systems can be detected and queried for their managed dependencies:

* [Gradle](https://gradle.org/)
* [Maven](http://maven.apache.org/)
* [SBT](http://www.scala-sbt.org/)
* [NPM](https://www.npmjs.com/)
* [PIP](https://pip.pypa.io/)

## Supported license scanners

ORT comes with some example implementations for wrappers around license / copyright scanners:

* [lc](https://github.com/boyter/lc)
* [Licensee](https://github.com/benbalter/licensee)
* [ScanCode](https://github.com/nexB/scancode-toolkit)

## Supported remote caches

For reusing already known scan results, ORT can currently use one of the following backends as a remote cache:

* [Artifactory](https://jfrog.com/artifactory/)

## Usage

### [analyzer](./analyzer/src/main/kotlin)

The Analyzer determines the dependencies of software projects inside the specified input directory (`-i`). It does so by
querying whatever [supported package manager](./analyzer/src/main/kotlin/managers) is found. No modifications to your
existing project source code, or especially to the build system, are necessary for that to work. The tree of transitive
dependencies per project is written out as [ABCD](https://github.com/nexB/aboutcode/tree/master/aboutcode-data)-style
YAML (or JSON, see `-f`) files to the specified output directory (`-o`) whose inner structure mirrors the one from the
input directory. The output files exactly document the status quo of all package-related meta-data. They can and
probably need to be further processed or manually edited before passing them to one of the other tools.

### [graph](./graph/src/main/kotlin)

In order to quickly visualize dependency information from an analysis the Graph tool can be used. Given a dependencies
file (`-d`) it diplays a simple representation of the dependency graph in a GUI. The graph is interactive in the sense
that nodes can be dragged & dropped with the mouse to rearrange them for a better overview.

### [downloader](./downloader/src/main/kotlin)

Taking a single ABCD-syle dependencies file as the input (`-d`), the Downloader retrieves the source code of all
contained packages to the specified output directory (`-o`). The Downloader takes care of things like normalizing URLs
and using the [appropriate VCS tool](./downloader/src/main/kotlin/vcs) to checkout source code from version control.

### [scanner](./scanner/src/main/kotlin)

This tool wraps underlying license / copyright scanners with a common API. This way all supported scanners can be used
in the same way to easily run them and compare their results. If passed a dependencies analysis file (`-d`), the Scanner
will automatically download the sources of the dependencies via the Downloader and scan them afterwards. In order to not
download or scan any previously scanned sources, the Scanner can be configured (`-c`) to use a remote cache, hosted
e.g. on [Artifactory](./scanner/src/main/kotlin/ArtifactoryCache.kt) or S3 (not yet implemented). Using the example of
configuring an Artifactory cache, the YAML-based configuration file would look like:

```
scanner:
  cache:
    type: Artifactory
    url: "https://artifactory.domain.com/artifactory/generic-repository-name"
    apiToken: $ARTIFACTORY_API_KEY
```

## Development

The toolkit is written in [Kotlin](https://kotlinlang.org/) and uses [Gradle](https://gradle.org/) as the build system.
We recommend the [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) as the IDE which can
directly import the Gradle build files.

The most important root project Gradle tasks are listed in the table below.

| Task        | Purpose                                                           |
| ----------- | ----------------------------------------------------------------- |
| assemble    | Build the JAR artifacts for all projects                          |
| detektCheck | Run static code analysis on all projects                          |
| test        | Run unit tests for all projects                                   |
| funTest     | Run functional tests for all projects                             |
| installDist | Build all projects and install the start scripts for distribution |

## License

Copyright (c) 2017-2018 HERE Europe B.V.

See the [LICENSE](./LICENSE) file in the root of this project for license details.
