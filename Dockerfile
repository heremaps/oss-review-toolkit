# syntax=docker/dockerfile:1.2

# Copyright (C) 2020 Bosch Software Innovations GmbH
# Copyright (C) 2021 Helio Chissini de Castro <helio@kde.org>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE


#------------------------------------------------------------------------
# Build components
FROM ubuntu:focal AS build

# http://bugs.python.org/issue19846
ENV LANG C.UTF-8

USER root

#------------------------------------------------------------------------
# Basic intro
RUN --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt \
    apt update \
    && DEBIAN_FRONTEND=noninteractive apt upgrade -y --no-install-recommends \
    && apt install -y --no-install-recommends bash ca-certificates curl gnupg wget

#------------------------------------------------------------------------
# Ubuntun build toolchain
RUN --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt \
    apt update \
    && DEBIAN_FRONTEND=noninteractive apt install -y --no-install-recommends \
        build-essential \
        dirmngr \
		dpkg-dev \
        git \
		libbluetooth-dev \
		libbz2-dev \
		libc6-dev \
		libexpat1-dev \
		libffi-dev \
        libgmp-dev \
		libgdbm-dev \
		liblzma-dev \
        libmpdec-dev \
		libncursesw5-dev \
		libreadline-dev \
		libsqlite3-dev \
		libssl-dev \
		make \
        netbase \
        openjdk-11-jdk \
		tk-dev \
        tzdata \
        unzip \
		uuid-dev \
		xz-utils \
		zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /work

# Copy the necessary bash resource to switch pythons
# Same mechanism can be used for other languages as long
# We treat in a non distribution native
COPY docker/root-bashrc /root/.bashrc
RUN mkdir -p /root/.bash_includes

#------------------------------------------------------------------------
# Multiple Python versions
ENV CONAN_VERSION=1.18.0 
ENV SCANCODE_VERSION=21.3.31
COPY docker/multi_python_build.sh /work       
RUN chmod +x multi_python_build.sh

# Python versions
# To call specificx python version, add -e PYTHON_VERSION=3.x ( no patch level )
# i.e docker run -e PYTHON_VERSION=3.9
# If no version is declared, 3.6 will be the default
# The script will install all python Ort dependencies required: 
# scancode-toolkit, mercurial, conan, pipenv and virtualenv
RUN ./multi_python_build.sh 3.6.13
RUN ./multi_python_build.sh 3.7.10
RUN ./multi_python_build.sh 3.8.9
RUN ./multi_python_build.sh 3.9.4
COPY docker/python.sh /root/.bash_includes

#------------------------------------------------------------------------
# Golang
# Golang dep depends on some development packages to be installed, so need build
# in the build stage
ENV GO_DEP_VERSION=0.5.4
ENV GO_VERSION=1.16.3
ENV GOPATH=/opt/go
RUN wget -qO- https://golang.org/dl/go${GO_VERSION}.linux-amd64.tar.gz | tar -C /opt -xz \
    && export PATH=/opt/go/bin:$PATH \
    && curl -ksS https://raw.githubusercontent.com/golang/dep/v$GO_DEP_VERSION/install.sh | bash
RUN echo "add_local_path /opt/go/bin:$PATH" > /root/.bash_includes/go.sh

#------------------------------------------------------------------------
# Haskell
ENV HASKELL_STACK_VERSION=2.1.3
ENV DEST=/opt/haskell/bin/stack
RUN curl -ksS https://raw.githubusercontent.com/commercialhaskell/stack/v$HASKELL_STACK_VERSION/etc/scripts/get-stack.sh | bash -s -- -d /usr/bin

# ORT
COPY . /usr/local/src/ort

WORKDIR /usr/local/src/ort


# This can be set to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES=""
COPY "$CRT_FILES" /tmp/certificates/
ARG ORT_VERSION="DOCKER-SNAPSHOT"

# Gradle ORT build.
RUN --mount=type=cache,target=/root/.gradle/ \
    scripts/import_proxy_certs.sh \
    && if [ -n "$CRT_FILES" ]; then \
        /opt/ort/bin/import_certificates.sh /tmp/certificates/; \
       fi \
    && scripts/set_gradle_proxy.sh \
    && sed -i -r 's,(^distributionUrl=)(.+)-all\.zip$,\1\2-bin.zip,' gradle/wrapper/gradle-wrapper.properties \
    && ./gradlew --no-daemon --stacktrace -Pversion=$ORT_VERSION :cli:distTar :helper-cli:startScripts
RUN echo "add_local_path /opt/ort/bin" > /root/.bash_includes/ort.sh 

#------------------------------------------------------------------------
# Remove the work directory
RUN rm -rf /work

#------------------------------------------------------------------------
# Main ORT docker
FROM ubuntu:focal

# Basic intro
RUN --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt \
    apt update \
    && DEBIAN_FRONTEND=noninteractive apt upgrade -y --no-install-recommends \
    && apt install -y --no-install-recommends ca-certificates curl gnupg wget

#------------------------------------------------------------------------
# External repositories for SBT
ENV SBT_VERSION=1.3.8
RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list \
    && curl -ksS "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key adv --import -

# Exgternal repository for NodeJS
RUN curl -ksS "https://deb.nodesource.com/gpgkey/nodesource.gpg.key" | apt-key adv --import - \
    && echo 'deb https://deb.nodesource.com/node_15.x focal main' | tee -a /etc/apt/sources.list.d/nodesource.list \
    && echo 'deb-src https://deb.nodesource.com/node_15.x focal main' >> /etc/apt/sources.list.d/nodesource.list

#------------------------------------------------------------------------
# Minimal set of packages for main docker
RUN --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt \
    apt update \
    && DEBIAN_FRONTEND=noninteractive apt upgrade -y --no-install-recommends \
    && DEBIAN_FRONTEND=noninteractive apt install -y --no-install-recommends \
        # Install general tools required by this Dockerfile.
        bash \
        bundler \
        cargo \
        composer \
        cvs \
        git \
        lib32stdc++6 \
        libffi7 \
        libgmp10 \
        libgomp1 \
        libxext6 \
        libxi6 \
        libxrender1 \
        libxtst6 \
        netbase \
        nodejs \
        openjdk-11-jre \
        openssh-client \
        sbt=$SBT_VERSION \
        subversion \
        unzip \
        xz-utils \
        zlib1g \
    && rm -rf /var/lib/apt/lists/*

#------------------------------------------------------------------------
# Base switch scripts
COPY --from=build /root/.bashrc /root/
COPY --from=build /root/.bash_includes/* /root/.bash_includes/

#------------------------------------------------------------------------
# Python from build
COPY --from=build /opt/python /opt/python/
COPY --from=build /var/lib/dpkg/alternatives/python /var/lib/dpkg/alternatives/

#------------------------------------------------------------------------
# Golang from build
COPY --from=build /opt/go /opt/go/

#------------------------------------------------------------------------
# Stack from build
COPY --from=build /usr/bin/stack /usr/bin/

#------------------------------------------------------------------------
# Google Repo tool
RUN curl -ksS https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo \
    && chmod a+x /usr/local/bin/repo

# Android SDK
ENV ANDROID_SDK_VERSION=6858069 
ENV ANDROID_HOME=/opt/android-sdk
RUN curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip \
    && unzip -q commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip -d $ANDROID_HOME \
    && rm commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip \
    && yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS --sdk_root=$ANDROID_HOME "platform-tools"

#------------------------------------------------------------------------
# NPM based package managers  
ENV BOWER_VERSION=1.8.8
ENV NPM_VERSION=6.14.2 
ENV YARN_VERSION=1.22.4
RUN npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION yarn@$YARN_VERSION

#------------------------------------------------------------------------
# ORT
COPY --from=build /usr/local/src/ort/scripts/*.sh /opt/ort/bin/
COPY --from=build /usr/local/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/
COPY --from=build /usr/local/src/ort/helper-cli/build/libs/helper-cli-*.jar /opt/ort/lib/
COPY --from=build /usr/local/src/ort/cli/build/distributions/ort-*.tar /opt/ort.tar
RUN tar xf /opt/ort.tar -C /opt/ort --strip-components 1 \
    && rm /opt/ort.tar

ENTRYPOINT ["/opt/ort/bin/ort"]
