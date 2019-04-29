#!/bin/sh
#
# Copyright (C) 2019 Bosch Software Innovations GmbH
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

# Get the absolute directory this script resides in.
SCRIPT_DIR="$(cd "$(dirname $0)" && pwd)"

# If required, first build the image to build ORT.
docker image inspect ort-build > /dev/null 2>&1 || docker/build.sh

(cd $SCRIPT_DIR/.. && \
    . docker/lib && \
    buildWithoutContext docker/test/Dockerfile ort-test:latest && \
    runGradleTask ort-test test
)
