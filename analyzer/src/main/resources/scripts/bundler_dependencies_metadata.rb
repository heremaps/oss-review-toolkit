#!/usr/bin/env ruby

# Copyright (C) 2021 Bosch.IO GmbH
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

# This script mimics the behavior of calling the CLI command `bundle lock`, which
# resolves the dependencies inside the Gemfile in the current working directory
# and writes them together with the respective version to a lock file.
# Internally, Bundler tries to find the gemspec files of the dependencies,
# both locally and remotely, and retrieves the respective metadata.
# However, except for the name, version, and transitive dependencies, Bundler
# discards the rest of the metadata. Thus, this script follows the steps but prints
# the metadata to be further processed by other tools.
# See https://github.com/rubygems/bundler/blob/35be6d9a603084f719fec4f4028c18860def07f6/lib/bundler/cli/lock.rb#L49-L58

require 'bundler'

definition = Bundler.definition()

# This command tries to resolve dependencies that are specified in the Gemfile of the current working directory.
# Although it says `remotely`, this only holds for dependencies specified as `gem` or `git` dependency.
# `path` dependencies are still resolved locally.
definition.resolve_remotely!

definition.specs.each do |spec|
  puts "\\0"
  puts spec.to_yaml
end
