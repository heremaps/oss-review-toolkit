/*
 * Copyright (C) 2019 HERE Europe B.V.
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

/* eslint constructor-super: 0 */

import Package from './Package';
import WebAppScanFindingCopyright from './WebAppScanFindingCopyright';
import WebAppScanFindingLicense from './WebAppScanFindingLicense';

class WebAppPackage extends Package {
    #children = [];

    #curations = [];

    #delivered = false;

    #detectedLicenses = new Set();

    #issues;

    #key;

    #levels = new Set();

    #paths = [];

    #projectIndexes = new Set();

    #scanFindings;

    #scopes = new Set();

    #violations;

    constructor(obj) {
        if (obj) {
            super(obj);

            if (obj.children) {
                this.children = obj.children;
            }

            if (obj.curations) {
                this.curations = obj.curations;
            }

            if (obj.delivered) {
                this.delivered = obj.delivered;
            }

            if (obj.detectedLicenses) {
                this.detectedLicenses = obj.detectedLicenses;
            }

            if (obj.key) {
                this.key = obj.key;
            }

            if (obj.levels) {
                this.levels = obj.levels;
            }

            if (obj.paths) {
                this.paths = obj.paths;
            }

            if (obj.projectIndexes) {
                this.projectIndexes = obj.projectIndexes;
            }

            if (obj.scopes) {
                this.scopes = obj.scopes;
            }

            if (obj.violations) {
                this.violations = obj.violations;
            }
        }
    }

    get children() {
        return this.#children;
    }

    set children(children) {
        this.#children = children;
    }

    get curations() {
        return this.#curations;
    }

    set curations(curations) {
        this.#curations = curations;
    }

    get delivered() {
        return this.#delivered;
    }

    set delivered(delivered) {
        this.#delivered = delivered;
    }

    get detectedLicenses() {
        return Array.from(this.#detectedLicenses);
    }

    set detectedLicenses(set) {
        this.#detectedLicenses = new Set(set);
    }

    get key() {
        return this.#key;
    }

    set key(key) {
        this.#key = key;
    }

    get levels() {
        return Array.from(this.#levels);
    }

    set levels(levels) {
        this.#levels = levels;
    }

    get paths() {
        return this.#paths;
    }

    set paths(paths) {
        this.#paths = paths;
    }

    get projectIndexes() {
        return Array.from(this.#projectIndexes).sort();
    }

    set projectIndexes(projectIndexes) {
        this.#projectIndexes = projectIndexes;
    }

    get scopes() {
        return Array.from(this.#scopes);
    }

    set scopes(scopes) {
        this.#scopes = scopes;
    }

    addLevel(level) {
        if (Number.isInteger(level)) {
            return this.#levels.add(level);
        }

        return null;
    }

    addPath(path) {
        if (path) {
            return this.#paths.push(path);
        }

        return null;
    }

    addProjectIndex(num) {
        if (Number.isInteger(num)) {
            return this.#projectIndexes.add(num);
        }

        return null;
    }

    addScope(scope) {
        if (scope) {
            return this.#scopes.add(scope);
        }

        return null;
    }

    getIssues(webAppOrtResult) {
        if (!this.#issues) {
            if (webAppOrtResult) {
                this.#issues = webAppOrtResult.issues.filter(
                    issue => issue.pkg === this.id
                );
            }
        }

        return this.#issues;
    }

    getScanSummaryForScannerId(webAppOrtResult, scannerId) {
        if (webAppOrtResult && scannerId) {
            return webAppOrtResult.getScanSummaryByPackageIdAndByScannerId(
                this.id,
                scannerId
            );
        }

        return null;
    }

    getScanFindings(webAppOrtResult) {
        if (this.hasScanFindings(webAppOrtResult)) {
            if (!this.#scanFindings) {
                const scanResultContainer = webAppOrtResult.getScanResultContainerForPackageId(this.id);
                const { results } = scanResultContainer;
                this.#scanFindings = [];

                for (let x = 0, lenResults = results.length; x < lenResults; x++) {
                    const { provenance, scanner, summary: { copyrightFindings, licenseFindings } } = results[x];

                    for (let y = 0, lenCopyrightFindings = copyrightFindings.length; y < lenCopyrightFindings; y++) {
                        const copyrightFinding = copyrightFindings[y];
                        const { statement: value, location: { path, startLine, endLine } } = copyrightFinding;

                        this.#scanFindings.push(new WebAppScanFindingCopyright({
                            path,
                            startLine,
                            endLine,
                            value,
                            provenance,
                            scanner
                        }));
                    }

                    for (let z = 0, lenLicenseFindings = licenseFindings.length; z < lenLicenseFindings; z++) {
                        const licenseFinding = licenseFindings[z];
                        const { license: value, location: { path, startLine, endLine } } = licenseFinding;

                        this.#scanFindings.push(new WebAppScanFindingLicense({
                            path,
                            startLine,
                            endLine,
                            value,
                            provenance,
                            scanner
                        }));
                    }
                }
            }

            return this.#scanFindings;
        }

        return null;
    }

    getViolations(webAppOrtResult) {
        if (!this.#violations) {
            if (webAppOrtResult && webAppOrtResult.violations) {
                this.#violations = webAppOrtResult.violations.filter(
                    violation => violation.pkg === this.id
                );
            }
        }

        return this.#violations;
    }

    hasIssues(webAppOrtResult) {
        if (!this.#issues) {
            this.getIssues(webAppOrtResult);
        }

        return this.#issues.length;
    }

    hasCurations() {
        return this.#curations.length;
    }

    hasDetectedLicense(license) {
        return this.#detectedLicenses.has(license);
    }

    hasDetectedLicenses() {
        return this.#detectedLicenses.size > 0;
    }

    hasLicenses() {
        if (this.declaredLicenses.length !== 0
            || this.detectedLicenses.length !== 0) {
            return true;
        }

        return false;
    }

    hasPaths() {
        if (this.paths.length === 1 && this.paths[0].length === 0) {
            return false;
        }

        return true;
    }

    hasScanFindings(webAppOrtResult) {
        if (webAppOrtResult && webAppOrtResult.hasScanResultContainerForPackageId(this.id)) {
            return true;
        }

        return false;
    }

    hasViolations(webAppOrtResult) {
        if (!this.#violations) {
            this.getViolations(webAppOrtResult);
        }

        return this.#violations.length;
    }
}

export default WebAppPackage;
