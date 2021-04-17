#!/bin/bash 

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

# Define the pythonversion
PYTHON_VERSION=${PYTHON_VERSION:-3.6}

# Set alternatives for the correct version
# slave chain add both python, python3, pip and pip3 on main /usr/bin
update-alternatives --set python "/opt/python/${PYTHON_VERSION}/bin/python${PYTHON_VERSION}"

# We use python shared, so need to set LD_LIBRTARY_PATH
LD_LIBRARY_PATH="/opt/python/${PYTHON_VERSION}/lib"
export LD_LIBRARY_PATH

add_local_path "/opt/python/${PYTHON_VERSION}/bin"
