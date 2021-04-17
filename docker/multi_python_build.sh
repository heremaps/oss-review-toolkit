#! /bin/bash

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

WORKDIR=${WORKDIR:-/work}
export WORKDIR

# Main GPG key from python.org
GPG_KEY=E3FF2839C048B25C084DEBE9B26995E310250568
export GPG_KEY

# Set canonical version and major version
PYTHON_VERSION="$1"
PYTHON_MAJOR_VERSION="${PYTHON_VERSION%.*}"
export PYTHON_VERSION PYTHON_MAJOR_VERSION

check_stage () {
    # Check is already downloaded
    [ -f "${WORKDIR}/out/$1" ] && return 0
    mkdir -p "${WORKDIR}/out" || exit 1
    return 1
}

download_python() {  
    check_stage download && return 0

    cd "${WORKDIR}" || { echo "No base workdir ${WORKDIR}. Bail out !" && exit 1; }    

    wget -O python.tar.xz "https://www.python.org/ftp/python/${PYTHON_VERSION%%[a-z]*}/Python-$PYTHON_VERSION.tar.xz" || return 1
    wget -O python.tar.xz.asc "https://www.python.org/ftp/python/${PYTHON_VERSION%%[a-z]*}/Python-$PYTHON_VERSION.tar.xz.asc" || return 1

    gpg --batch --keyserver hkps://keys.openpgp.org --recv-keys "$GPG_KEY"
    gpg --batch --verify python.tar.xz.asc python.tar.xz
    { command -v gpgconf > /dev/null && gpgconf --kill all || :; }
    mkdir "${WORKDIR}/build-$PYTHON_VERSION"
    tar -xJC "${WORKDIR}/build-$PYTHON_VERSION" --strip-components=1 -f python.tar.xz
    
    touch "${WORKDIR}/out/download"
}

install_upstream_pip() {
    local pip_version
    pip_version=21.0.1

    local pip_url
    pip_url="https://bootstrap.pypa.io/get-pip.py" 

    check_stage pip && return 0

    wget -O get-pip.py "$pip_url"
    LD_LIBRARY_PATH="/opt/python/$PYTHON_VERSION/lib" \
        "/opt/python/$PYTHON_VERSION/"/bin/python3 get-pip.py \
		--disable-pip-version-check \
		--no-cache-dir \
		"pip==$pip_version" 
    
    touch "${WORKDIR}/out/built"
}

build_python () {
    check_stage built && return 0

    local gnuArch
    gnuArch="$(dpkg-architecture --query DEB_BUILD_GNU_TYPE)"

    cd "${WORKDIR}/build-$PYTHON_VERSION" || { echo "No python build source dir. Bail out !" && exit 1; }

	./configure \
        --prefix="/opt/python/$PYTHON_VERSION" \
        --build="$gnuArch" \
		--enable-loadable-sqlite-extensions \
		--enable-optimizations \
		--enable-option-checking=fatal \
        --enable-shared \
        --enable-ipv6 \
        --with-dbmliborder=bdb:gdbm \
        --with-computed-gotos \
		--with-system-expat \
		--with-system-ffi \
		--without-ensurepip
	
    make -j "$(nproc)" LDFLAGS="-Wl,--strip-all" 
    
    make install

    touch "${WORKDIR}/out/built"
}

install_alternatives () {
    check_stage alternatives && return 0

    # Remove link if exists and add for current version
    rm -f "/opt/python/$PYTHON_MAJOR_VERSION"
    ln -sf "/opt/python/$PYTHON_VERSION" "/opt/python/$PYTHON_MAJOR_VERSION"

    local alternatives_prio
    alternatives_prio="${PYTHON_MAJOR_VERSION/\./}"

    update-alternatives --install \
        /usr/bin/python python "/opt/python/$PYTHON_MAJOR_VERSION/bin/python$PYTHON_MAJOR_VERSION" $alternatives_prio \
        --slave /usr/bin/python3 python3 "/opt/python/$PYTHON_MAJOR_VERSION/bin/python$PYTHON_MAJOR_VERSION" \
        --slave /usr/bin/pip pip "/opt/python/$PYTHON_MAJOR_VERSION/bin/pip3" \
        --slave /usr/bin/pip3 pip3 "/opt/python/$PYTHON_MAJOR_VERSION/bin/pip3" \

    touch "${WORKDIR}/out/alternatives"
}

install_ort_python_dependencies () {
    check_stage deps && return 0

    SCANCODE_VERSION=${SCANCODE_VERSION:-21.3.31}
    CONAN_VERSION=${CONAN_VERSION:-1.18.0}
    
    LD_LIBRARY_PATH="/opt/python/$PYTHON_VERSION/lib"
    export LD_LIBRARY_PATH

    PATH="/opt/python/$PYTHON_VERSION/bin":$PATH
    export PATH

    pip3 install wheel

    #shellcheck disable=SC2102
    pip3 install \
        conan=="${CONAN_VERSION}" \
        Mercurial \
        pipenv \
        scancode-toolkit[full]=="${SCANCODE_VERSION}" \
        virtualenv

    touch "${WORKDIR}/out/deps"
}

cleanup_install () {
    find "/opt/python/$PYTHON_MAJOR_VERSION/" -depth \
        \( \
			\( -type d -a \( -name test -o -name tests -o -name idle_test \) \) \
			-o \( -type f -a \( -name '*.pyc' -o -name '*.pyo' -o -name '*.a' \) \) \
			-o \( -type f -a -name 'wininst-*.exe' \) \
		\) -exec rm -rf '{}' \; 

    rm -rf "${WORKDIR}/out"
}

main_process() {
    # Add a local test check out dir
    mkdir -p "${WORKDIR}/out"
    download_python || { echo "Download python version $PYTHON_VERSION failed !"; exit 1; }
    build_python || { echo "Build python version $PYTHON_VERSION failed !"; exit 1; }
    install_upstream_pip || { echo "Install latest pip failed !"; exit 1; }
    install_alternatives
    # Ort python dependencies
    install_ort_python_dependencies
    # Cleanup install unused files and source builds
    cleanup_install

    echo "Built Python  ${PYTHON_VERSION}"
}

main_process
