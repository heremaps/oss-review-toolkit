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

package org.ossreviewtoolkit.spdx

import org.ossreviewtoolkit.spdx.SpdxLicense.*
import org.ossreviewtoolkit.spdx.SpdxLicenseException.*

/**
 * A mapping from license strings collected from the declared licenses of Open Source packages to SPDX expressions. This
 * mapping only contains license strings which can *not* be parsed by [SpdxExpression.parse], for example because the
 * license names contain white spaces. See [SpdxLicenseAliasMapping] for a mapping of varied license names.
 */
object SpdxDeclaredLicenseMapping {
    /**
     * The list of pairs which associates collected license strings with their corresponding SPDX expression.
     */
    internal val mappingList = listOf(
        "(MIT-style) netCDF C library license" to NETCDF.toExpression(),
        "2-clause BSD license" to BSD_2_CLAUSE.toExpression(),
        "2-clause BSDL" to BSD_2_CLAUSE.toExpression(),
        "2-clause bdsl" to BSD_2_CLAUSE.toExpression(),
        "3-Clause BSD" to BSD_3_CLAUSE.toExpression(),
        "3-Clause BSD License" to BSD_3_CLAUSE.toExpression(),
        "3-clause bdsl" to BSD_3_CLAUSE.toExpression(),
        "Affero General Public License (AGPL) v. 3" to AGPL_3_0_ONLY.toExpression(),
        "AL 2.0" to APACHE_2_0.toExpression(),
        "ASF 2.0" to APACHE_2_0.toExpression(),
        "ASL 2" to APACHE_2_0.toExpression(),
        "ASL 2.0" to APACHE_2_0.toExpression(),
        "ASL, version 2" to APACHE_2_0.toExpression(),
        "Academic Free License (AFL)" to AFL_2_1.toExpression(),
        "Academic Free License (AFL-2.1)" to AFL_2_1.toExpression(),
        "AGPL v3+" to AGPL_3_0_OR_LATER.toExpression(),
        "Aladdin Free Public License (AFPL)" to ALADDIN.toExpression(),
        "Amazon Software License" to licenseRef("amazon-sl", "scancode"),
        "Apache  Version 2.0, January 2004" to APACHE_2_0.toExpression(),
        "Apache 2" to APACHE_2_0.toExpression(),
        "Apache 2.0" to APACHE_2_0.toExpression(),
        "Apache 2.0 License" to APACHE_2_0.toExpression(),
        "Apache License" to APACHE_2_0.toExpression(),
        "Apache License (2.0)" to APACHE_2_0.toExpression(),
        "Apache License 2" to APACHE_2_0.toExpression(),
        "Apache License Version 2" to APACHE_2_0.toExpression(),
        "Apache License Version 2.0" to APACHE_2_0.toExpression(),
        "Apache License v2" to APACHE_2_0.toExpression(),
        "Apache License v2.0" to APACHE_2_0.toExpression(),
        "Apache License, 2.0" to APACHE_2_0.toExpression(),
        "Apache License, V2 or later" to APACHE_2_0.toExpression(),
        "Apache License, V2.0 or later" to APACHE_2_0.toExpression(),
        "Apache License, Version 2" to APACHE_2_0.toExpression(),
        "Apache License, Version 2.0" to APACHE_2_0.toExpression(),
        """Apache License, Version 2.0 and
        Common Development And Distribution License (CDDL) Version 1.0 """.trimIndent()
                to (APACHE_2_0 and CDDL_1_0),
        "Apache License,Version 2.0" to APACHE_2_0.toExpression(),
        "Apache Public License 2.0" to APACHE_2_0.toExpression(),
        "Apache Software" to APACHE_2_0.toExpression(),
        "Apache Software License" to APACHE_2_0.toExpression(),
        "Apache Software License - Version 2.0" to APACHE_2_0.toExpression(),
        "Apache Software License (Apache-2.0)" to APACHE_2_0.toExpression(),
        "Apache Software License 2.0" to APACHE_2_0.toExpression(),
        "Apache Software License, version 1.1" to APACHE_1_1.toExpression(),
        "Apache Software License, Version 2" to APACHE_2_0.toExpression(),
        "Apache Software License, version 2.0" to APACHE_2_0.toExpression(),
        "Apache Software Licenses" to APACHE_2_0.toExpression(),
        "Apache v2" to APACHE_2_0.toExpression(),
        "Apache v2.0" to APACHE_2_0.toExpression(),
        "Apache v 2.0" to APACHE_2_0.toExpression(),
        "Apache version 2.0" to APACHE_2_0.toExpression(),
        "Apache, Version 2.0" to APACHE_2_0.toExpression(),
        """Apache-2.0 */ &#39; &quot; &#x3D;end --

 """.trimIndent() to APACHE_2_0.toExpression(),
        "Apple Public Source License" to APSL_1_0.toExpression(),
        "Artistic License" to ARTISTIC_2_0.toExpression(),
        "Artistic 2.0" to ARTISTIC_2_0.toExpression(),
        "artistic license v2.0" to ARTISTIC_2_0.toExpression(),
        "Boost License" to BSL_1_0.toExpression(),
        "Boost License v1.0" to BSL_1_0.toExpression(),
        "Boost Software License" to BSL_1_0.toExpression(),
        "Boost Software License 1.0 (BSL-1.0)" to BSL_1_0.toExpression(),
        "BSD (3-clause)" to BSD_3_CLAUSE.toExpression(),
        "BSD - See ndg/httpsclient/LICENCE file for details" to BSD_3_CLAUSE.toExpression(),
        "BSD 2" to BSD_2_CLAUSE.toExpression(),
        "BSD 2 Clause" to BSD_2_CLAUSE.toExpression(),
        "BSD 2-Clause" to BSD_2_CLAUSE.toExpression(),
        "BSD 2-Clause License" to BSD_2_CLAUSE.toExpression(),
        """BSD 2-clause \"Simplified\" or \"FreeBSD\"
                License
        """.trimIndent() to (BSD_2_CLAUSE or BSD_2_CLAUSE_FREEBSD),
        """BSD 2-clause &quot;Simplified&quot; or &quot;FreeBSD&quot;
                License
        """.trimIndent() to (BSD_2_CLAUSE or BSD_2_CLAUSE_FREEBSD),
        "BSD 3" to BSD_3_CLAUSE.toExpression(),
        "BSD 3 Clause" to BSD_3_CLAUSE.toExpression(),
        "BSD 3-Clause" to BSD_3_CLAUSE.toExpression(),
        "BSD 3-Clause \"New\" or \"Revised\" License (BSD-3-Clause)" to BSD_3_CLAUSE.toExpression(),
        "BSD 3-Clause License" to BSD_3_CLAUSE.toExpression(),
        "BSD 3-clause New License" to BSD_3_CLAUSE.toExpression(),
        "BSD 4 Clause" to BSD_4_CLAUSE.toExpression(),
        "BSD Licence 3" to BSD_3_CLAUSE.toExpression(),
        "BSD License" to BSD_3_CLAUSE.toExpression(),
        "BSD License for HSQL" to BSD_3_CLAUSE.toExpression(),
        "BSD New" to BSD_3_CLAUSE.toExpression(),
        "BSD New license" to BSD_3_CLAUSE.toExpression(),
        "BSD Two Clause License" to BSD_2_CLAUSE.toExpression(),
        "BSD Three Clause License" to BSD_3_CLAUSE.toExpression(),
        "BSD Four Clause License" to BSD_4_CLAUSE.toExpression(),
        "BSD licence" to BSD_3_CLAUSE.toExpression(),
        "BSD or Apache License, Version 2.0" to (BSD_3_CLAUSE or APACHE_2_0),
        "BSD style" to BSD_3_CLAUSE.toExpression(),
        "BSD style license" to BSD_3_CLAUSE.toExpression(),
        "BSD*" to BSD_3_CLAUSE.toExpression(),
        "BSD-like license" to BSD_3_CLAUSE.toExpression(),
        "BSD-Style + Attribution" to BSD_3_CLAUSE_ATTRIBUTION.toExpression(),
        "BSD-style license" to BSD_3_CLAUSE.toExpression(),
        "Berkeley Software Distribution (BSD) License" to BSD_2_CLAUSE.toExpression(),
        "Bouncy Castle Licence" to MIT.toExpression(),
        "Bouncy Castle License" to MIT.toExpression(),
        "bsd 4-clause" to BSD_3_CLAUSE.toExpression(),
        "bzip2 license" to BZIP2_1_0_6.toExpression(),
        "CEA CNRS Inria Logiciel Libre License, version 2.1" to CECILL_2_1.toExpression(),
        "CEA CNRS Inria Logiciel Libre License, version 2.1 (CeCILL-2.1)" to CECILL_2_1.toExpression(),
        "CC0 1.0 Universal" to CC0_1_0.toExpression(),
        "CC0 1.0 Universal License" to CC0_1_0.toExpression(),
        "CC0 1.0 Universal (CC0 1.0) Public Domain Dedication" to CC0_1_0.toExpression(),
        "CDDL + GPLv2 with classpath exception" to (CDDL_1_0 and (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0)),
        "CDDL 1.0" to CDDL_1_0.toExpression(),
        "CDDL 1.1" to CDDL_1_1.toExpression(),
        "CDDL License" to CDDL_1_0.toExpression(),
        "CDDL or GPL 2 with Classpath Exception" to (CDDL_1_0 or (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0)),
        "CDDL or GPLv2 with exceptions" to (CDDL_1_0 or (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0)),
        "CDDL v1.0 / GPL v2 dual license" to (CDDL_1_0 or GPL_2_0_ONLY),
        "CDDL v1.1 / GPL v2 dual license" to (CDDL_1_0 or GPL_2_0_ONLY),
        "CDDL+GPL" to (CDDL_1_0 and GPL_2_0_ONLY),
        "CDDL+GPL License" to (CDDL_1_0 and GPL_2_0_ONLY),
        "CDDL+GPLv2" to (CDDL_1_0 and GPL_2_0_ONLY),
        "CDDL/GPLv2 dual license" to (CDDL_1_0 or GPL_2_0_ONLY),
        "CDDL/GPLv2+CE" to (CDDL_1_0 or (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0)),
        "cddl v1.0" to CDDL_1_0.toExpression(),
        "cddl v1.1" to CDDL_1_1.toExpression(),
        "CECILL v2" to CECILL_2_0.toExpression(),
        "CeCILL 2.0" to CECILL_2_0.toExpression(),
        "CeCILL 2.1" to CECILL_2_1.toExpression(),
        "CeCILL v2.1" to CECILL_2_1.toExpression(),
        "CeCILL-B licence" to CECILL_B.toExpression(),
        "CeCILL-B Free Software License Agreement (CECILL-B)" to CECILL_B.toExpression(),
        "CeCILL-C Free Software License Agreement (CECILL-C)" to CECILL_C.toExpression(),
        "CeCILL Licence française de logiciel libre" to CECILL_2_0.toExpression(),
        "CERN Open Hardware License v1.2" to CERN_OHL_1_2.toExpression(),
        "Commons Clause" to licenseRef("commons-clause", "scancode"),
        "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.0" to CDDL_1_0.toExpression(),
        "Common Development and Distribution License" to CDDL_1_0.toExpression(),
        "Common Development and Distribution License (CDDL) v1.0" to CDDL_1_0.toExpression(),
        "Common Development and Distribution License (CDDL), Version 1.1" to CDDL_1_1.toExpression(),
        "Common Public License" to CPL_1_0.toExpression(),
        "Common Public License - v 1.0" to CPL_1_0.toExpression(),
        "Common Public License Version 1.0" to CPL_1_0.toExpression(),
        "common development and distribution license 1.0 (cddl-1.0)" to CDDL_1_0.toExpression(),
        "common development and distribution license 1.1 (cddl-1.1)" to CDDL_1_1.toExpression(),
        "Creative Commons" to CC_BY_3_0.toExpression(),
        "Creative Commons - Attribution 4.0 International License" to CC_BY_4_0.toExpression(),
        "Creative Commons - BY" to CC_BY_3_0.toExpression(),
        "Creative Commons 3.0" to CC_BY_3_0.toExpression(),
        "Creative Commons 3.0 BY-SA" to CC_BY_SA_3_0.toExpression(),
        "Creative Commons Attribution 1.0" to CC_BY_1_0.toExpression(),
        "Creative Commons Attribution 2.5" to CC_BY_2_5.toExpression(),
        "Creative Commons Attribution 2.5 License" to CC_BY_2_5.toExpression(),
        "Creative Commons Attribution 3.0" to CC_BY_3_0.toExpression(),
        "Creative Commons Attribution 3.0 License" to CC_BY_3_0.toExpression(),
        "Creative Commons Attribution 3.0 Unported (CC BY 3.0)" to CC_BY_3_0.toExpression(),
        "Creative Commons Attribution 4.0" to CC_BY_4_0.toExpression(),
        "Creative Commons Attribution 4.0 International (CC BY 4.0)" to CC_BY_4_0.toExpression(),
        "Creative Commons Attribution 4.0 International Public License" to CC_BY_4_0.toExpression(),
        "Creative Commons Attribution License" to CC_BY_3_0.toExpression(),
        "Creative Commons Attribution-NonCommercial 4.0 International" to CC_BY_NC_4_0.toExpression(),
        "Creative Commons License Attribution-NoDerivs 3.0 Unported" to CC_BY_NC_ND_3_0.toExpression(),
        "Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International" to CC_BY_NC_ND_4_0.toExpression(),
        "Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported (CC BY-NC-SA 3.0)"
                to CC_BY_NC_SA_3_0.toExpression(),
        "Creative Commons CC0" to CC0_1_0.toExpression(),
        "Creative Commons GNU LGPL, Version 2.1" to LGPL_2_1_ONLY.toExpression(),
        "Creative Commons License Attribution-NonCommercial-ShareAlike 3.0 Unported" to CC_BY_NC_SA_3_0.toExpression(),
        "Creative Commons Zero" to CC0_1_0.toExpression(),
        """Creative Commons Attribution-NonCommercial-ShareAlike 4.0
        International
        Public License
        """.trimIndent() to CC_BY_NC_SA_4_0.toExpression(),
        "cc by-nc-sa 2.0" to CC_BY_NC_SA_2_0.toExpression(),
        "cc by-nc-sa 2.5" to CC_BY_NC_SA_2_5.toExpression(),
        "cc by-nc-sa 3.0" to CC_BY_NC_SA_3_0.toExpression(),
        "cc by-nc-sa 4.0" to CC_BY_NC_SA_4_0.toExpression(),
        "cc by-sa 2.0" to CC_BY_SA_2_0.toExpression(),
        "cc by-sa 2.5" to CC_BY_SA_2_5.toExpression(),
        "cc by-sa 3.0" to CC_BY_SA_3_0.toExpression(),
        "cc by-sa 4.0" to CC_BY_SA_4_0.toExpression(),
        "cpal 1.0" to CPAL_1_0.toExpression(),
        "cpal v1.0" to CPAL_1_0.toExpression(),
        "CUP Parser Generator Copyright Notice, License, and Disclaimer" to HPND.toExpression(),
        "DBAD" to licenseRef("dbad", "ort"),
        "Dual License: CDDL 1.0 and GPL V2 with Classpath Exception" to (CDDL_1_0 and GPL_2_0_ONLY),
        "Dual license consisting of the CDDL v1.1 and GPL v2" to (CDDL_1_1 and GPL_2_0_ONLY),
        "EPL (Eclipse Public License), V1.0 or later" to EPL_1_0.toExpression(),
        "Eclipse Distribution License (EDL), Version 1.0" to BSD_3_CLAUSE.toExpression(),
        "Eclipse Distribution License (New BSD License)" to BSD_3_CLAUSE.toExpression(),
        "Eclipse Distribution License - v 1.0" to BSD_3_CLAUSE.toExpression(),
        "Eclipse Distribution License v. 1.0" to BSD_3_CLAUSE.toExpression(),
        "Eclipse Public License" to EPL_1_0.toExpression(),
        "Eclipse Public License (EPL)" to EPL_1_0.toExpression(),
        "Eclipse Public License (EPL) 1.0" to EPL_1_0.toExpression(),
        "Eclipse Public License (EPL) 2.0" to EPL_2_0.toExpression(),
        "Eclipse Public License (EPL), Version 1.0" to EPL_1_0.toExpression(),
        "Eclipse Public License - Version 1.0" to EPL_1_0.toExpression(),
        "Eclipse Public License - v 1.0" to EPL_1_0.toExpression(),
        "Eclipse Public License 1.0 (EPL-1.0)" to EPL_1_0.toExpression(),
        "Eclipse Public License 2.0 (EPL-2.0)" to EPL_2_0.toExpression(),
        "Eclipse Public License v. 2.0" to EPL_2_0.toExpression(),
        "Eclipse Public License v1.0" to EPL_1_0.toExpression(),
        "Eclipse Public License v2.0" to EPL_2_0.toExpression(),
        "Eclipse Public License, Version 1.0" to EPL_1_0.toExpression(),
        "Eclipse Publish License, Version 1.0" to EPL_1_0.toExpression(),
        "eclipse license" to EPL_1_0.toExpression(),
        "eclipse 1.0" to EPL_1_0.toExpression(),
        "eclipse 2.0" to EPL_2_0.toExpression(),
        "EDL 1.0" to licenseRef("edl-1.0", "scancode"),
        "Eiffel Forum License" to EFL_2_0.toExpression(),
        "Eiffel Forum License (EFL)" to EFL_2_0.toExpression(),
        "Eiffel Forum License (EFL-2.0)" to EFL_2_0.toExpression(),
        "eiffel license (EFL)" to EFL_2_0.toExpression(),
        "epl 1.0" to EPL_1_0.toExpression(),
        "epl 2.0" to EPL_2_0.toExpression(),
        "epl v1.0" to EPL_1_0.toExpression(),
        "epl v2.0" to EPL_2_0.toExpression(),
        "European Union Public Licence 1.0" to EUPL_1_0.toExpression(),
        "European Union Public Licence 1.1" to EUPL_1_1.toExpression(),
        "European Union Public Licence 1.2" to EUPL_1_2.toExpression(),
        "European Union Public License v. 1.2" to EUPL_1_2.toExpression(),
        "eupl 1.0" to EUPL_1_0.toExpression(),
        "eupl 1.1" to EUPL_1_1.toExpression(),
        "eupl 1.2" to EUPL_1_2.toExpression(),
        "eupl v1.0" to EUPL_1_0.toExpression(),
        "eupl v1.1" to EUPL_1_1.toExpression(),
        "eupl v1.2" to EUPL_1_2.toExpression(),
        "eu public licence 1.0 (eupl 1.0)" to EUPL_1_0.toExpression(),
        "eu public licence 1.1 (eupl 1.1)" to EUPL_1_1.toExpression(),
        "eu public licence 1.2 (eupl 1.2)" to EUPL_1_2.toExpression(),
        "european union public licence 1.0 (eupl 1.0)" to EUPL_1_0.toExpression(),
        "european union public licence 1.1 (eupl 1.1)" to EUPL_1_1.toExpression(),
        "european union public licence 1.2 (eupl 1.2)" to EUPL_1_2.toExpression(),
        "Expat license" to MIT.toExpression(),
        "GNU Affero General Public License v3" to AGPL_3_0_ONLY.toExpression(),
        "GNU Affero General Public License v3 (AGPL-3.0)" to AGPL_3_0_ONLY.toExpression(),
        "GNU Affero General Public License v3 (AGPLv3)" to AGPL_3_0_ONLY.toExpression(),
        "GNU Affero General Public License v3 or later (AGPL3+)" to AGPL_3_0_OR_LATER.toExpression(),
        "GNU Affero General Public License v3 or later (AGPLv3+)" to AGPL_3_0_OR_LATER.toExpression(),
        "GNU Affero General Public License, Version 3" to AGPL_3_0_ONLY.toExpression(),
        "GNU Affero General Public License, Version 3 with the Commons Clause"
                to (AGPL_3_0_ONLY and licenseRef("public-domain-disclaimer", "scancode")),
        "GNU Free Documentation License (FDL)" to GFDL_1_3_ONLY.toExpression(),
        "GNU Free Documentation License (GFDL-1.3)" to GFDL_1_3_ONLY.toExpression(),
        "GNU GENERAL PUBLIC LICENSE Version 2, June 1991" to GPL_2_0_ONLY.toExpression(),
        "GNU GPL v2" to GPL_2_0_ONLY.toExpression(),
        "GNU General Lesser Public License (LGPL) version 2.1" to LGPL_2_1_ONLY.toExpression(),
        "GNU General Public Library" to GPL_3_0_ONLY.toExpression(),
        "GNU General Public License (GPL)" to GPL_3_0_ONLY.toExpression(),
        "GNU General Public License (GPL), version 2, with the Classpath exception"
                to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GNU General Public License 3" to GPL_3_0_ONLY.toExpression(),
        "GNU General Public License Version 2" to GPL_2_0_ONLY.toExpression(),
        "GNU General Public License v2 (GPLv2)" to GPL_2_0_ONLY.toExpression(),
        "GNU General Public License v2 or later (GPLv2+)" to GPL_2_0_OR_LATER.toExpression(),
        "GNU General Public License v3 (GPLv3)" to GPL_3_0_ONLY.toExpression(),
        "GNU General Public License v3 or later (GPLv3+)" to GPL_3_0_OR_LATER.toExpression(),
        "GNU General Public License (GPL) v. 2" to GPL_2_0_ONLY.toExpression(),
        "GNU General Public License (GPL) v. 3" to GPL_3_0_ONLY.toExpression(),
        "GNU General Public License, Version 2 with the Classpath Exception"
                to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GNU General Public License, Version 3" to GPL_3_0_ONLY.toExpression(),
        "GNU General Public License, version 2" to GPL_2_0_ONLY.toExpression(),
        "GNU General Public License, version 2 (GPL2), with the classpath exception"
                to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GNU General Public License, version 2, with the Classpath Exception"
                to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GNU LESSER GENERAL PUBLIC LICENSE V3.0" to LGPL_3_0_ONLY.toExpression(),
        "GNU LGP (GNU General Public License), V2 or later" to LGPL_2_0_OR_LATER.toExpression(),
        "GNU LGPL" to LGPL_2_1_ONLY.toExpression(),
        "GNU LGPL (GNU Lesser General Public License), V2.1 or later" to LGPL_2_1_OR_LATER.toExpression(),
        "GNU Lesser General Public Licence" to LGPL_2_1_ONLY.toExpression(),
        "GNU LGPL 2.1" to LGPL_2_1_ONLY.toExpression(),
        "GNU LGPL 3.0" to LGPL_3_0_ONLY.toExpression(),
        "GNU LGPL v2" to LGPL_2_1_ONLY.toExpression(),
        "GNU LGPL v2+" to LGPL_2_1_ONLY.toExpression(),
        "GNU LGPL v2.1" to LGPL_2_1_OR_LATER.toExpression(),
        "GNU LGPL v3" to LGPL_3_0_ONLY.toExpression(),
        "GNU LGPL v3+" to LGPL_3_0_OR_LATER.toExpression(),
        "GNU Lesser General Public License" to LGPL_2_1_ONLY.toExpression(),
        "GNU Lesser General Public License (LGPL)" to LGPL_2_1_ONLY.toExpression(),
        "GNU Lesser General Public License (LGPL), Version 2.1" to LGPL_2_1_ONLY.toExpression(),
        "GNU Lesser General Public License (LGPL), Version 3" to LGPL_3_0_ONLY.toExpression(),
        "GNU Lesser General Public License 2.1" to LGPL_2_1_ONLY.toExpression(),
        "GNU Lesser General Public License Version 2.1" to LGPL_2_1_ONLY.toExpression(),
        "GNU Lesser General Public License Version 2.1, February 1999" to LGPL_2_1_ONLY.toExpression(),
        "GNU Lesser General Public License v2 or later (LGPLv2+)" to LGPL_2_0_OR_LATER.toExpression(),
        "GNU Lesser General Public License v3 (LGPLv3)" to LGPL_3_0_ONLY.toExpression(),
        "GNU Lesser General Public License v3 or later (LGPLv3+)" to LGPL_3_0_OR_LATER.toExpression(),
        "GNU Lesser General Public License v3+" to LGPL_3_0_OR_LATER.toExpression(),
        "GNU Lesser General Public License, Version 2.1" to LGPL_2_1_ONLY.toExpression(),
        "GNU Lesser Public License" to LGPL_2_1_ONLY.toExpression(),
        "GNU Library or Lesser General Public License (LGPL)" to LGPL_2_1_ONLY.toExpression(),
        "GNU Library or Lesser General Public License version 2.0 (LGPLv2)" to LGPL_2_0_ONLY.toExpression(),
        "GNU Public" to GPL_2_0_ONLY.toExpression(),
        "General Public License (GPL)" to GPL_2_0_ONLY.toExpression(),
        "General Public License 2.0 (GPL)" to GPL_2_0_ONLY.toExpression(),
        "GPL (with dual licensing option)" to GPL_2_0_ONLY.toExpression(),
        "GPL 2" to GPL_2_0_ONLY.toExpression(),
        "GPL 3" to GPL_3_0_ONLY.toExpression(),
        "GPL v2" to GPL_2_0_ONLY.toExpression(),
        "GPL v2 with ClassPath Exception" to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GPL version 2" to GPL_2_0_ONLY.toExpression(),
        "GPL2 w/ CPE" to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GPLv2+CE" to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GPL v2+" to GPL_2_0_OR_LATER.toExpression(),
        "GPL v3+" to GPL_3_0_OR_LATER.toExpression(),
        "gpl (≥ 3)" to GPL_3_0_OR_LATER.toExpression(),
        "gnu gpl" to GPL_2_0_ONLY.toExpression(),
        "gnu gpl v3" to GPL_3_0_ONLY.toExpression(),
        "HERE Proprietary License" to licenseRef("here-proprietary", "scancode"),
        "HSQLDB License" to BSD_3_CLAUSE.toExpression(),
        "HSQLDB License, a BSD open source license" to BSD_3_CLAUSE.toExpression(),
        "Historical Permission Notice and Disclaimer (HPND)" to HPND.toExpression(),
        "IBM Public License" to IPL_1_0.toExpression(),
        "icu-unicode license" to ICU.toExpression(),
        "Individual BSD License" to BSD_3_CLAUSE.toExpression(),
        "ISC License (ISCL)" to ISC.toExpression(),
        "ISC/BSD License" to (ISC or BSD_2_CLAUSE),
        "Jabber Open Source License" to licenseRef("josl-1.0", "scancode"),
        "Jython Software License" to PYTHON_2_0.toExpression(),
        "jQuery license" to MIT.toExpression(),
        "Kirkk.com BSD License" to BSD_3_CLAUSE.toExpression(),
        "LGPL 2.1" to LGPL_2_1_ONLY.toExpression(),
        "LGPL 3" to LGPL_3_0_ONLY.toExpression(),
        "LGPL 3.0" to LGPL_3_0_ONLY.toExpression(),
        "LGPL 3.0 license" to LGPL_3_0_ONLY.toExpression(),
        "LGPL v3" to LGPL_3_0_ONLY.toExpression(),
        "LGPL v3+" to LGPL_3_0_OR_LATER.toExpression(),
        "LGPL with exceptions or ZPL" to (LGPL_3_0_ONLY or ZPL_2_1),
        "LGPL+BSD" to (LGPL_2_1_ONLY and BSD_2_CLAUSE),
        "LGPL, version 2.1" to LGPL_2_1_ONLY.toExpression(),
        "LGPL, version 3.0" to LGPL_2_1_ONLY.toExpression(),
        "LGPL/MIT" to (LGPL_3_0_ONLY or MIT),
        "LGPLv3 or later" to LGPL_3_0_OR_LATER.toExpression(),
        "Lesser General Public License (LGPL)" to LGPL_2_1_ONLY.toExpression(),
        "Lesser General Public License, version 3 or greater" to LGPL_3_0_OR_LATER.toExpression(),
        "License Agreement For Open Source Computer Vision Library (3-clause BSD License)"
                to BSD_3_CLAUSE.toExpression(),
        "lgplv2 or later" to LGPL_2_1_OR_LATER.toExpression(),
        "JSR-000107 JCACHE 2.9 Public Review - Updated Specification License" to licenseRef(
            "jsr-107-jcache-spec.LICENSE",
            "scancode"
        ),
        "MIT / http://rem.mit-license.org" to MIT.toExpression(),
        "MIT Licence" to MIT.toExpression(),
        "MIT License (http://opensource.org/licenses/MIT)" to MIT.toExpression(),
        "MIT Licensed. http://www.opensource.org/licenses/mit-license.php" to MIT.toExpression(),
        "MIT, 2-clause BSD" to (MIT and BSD_2_CLAUSE),
        "MIT, 3-clause BSD" to (MIT and BSD_3_CLAUSE),
        "MIT/Expat" to MIT.toExpression(),
        "MIT/X11" to (MIT or X11),
        "MirOS License (MirOS)" to MIROS.toExpression(),
        "Modified BSD" to BSD_3_CLAUSE.toExpression(),
        "MPL 1.1" to MPL_1_1.toExpression(),
        "MPL 2.0" to MPL_2_0.toExpression(),
        "MPL 2.0 or EPL 1.0" to (MPL_2_0 or EPL_1_0),
        "MPL 2.0, and EPL 1.0" to (MPL_2_0 and EPL_1_0),
        "MPL v2" to MPL_2_0.toExpression(),
        "Mockrunner License, based on Apache Software License, version 1.1" to APACHE_1_1.toExpression(),
        "Mozilla Public License" to MPL_2_0.toExpression(),
        "Mozilla Public License 1.0 (MPL)" to MPL_1_0.toExpression(),
        "Mozilla Public License 1.1 (MPL 1.1)" to MPL_1_1.toExpression(),
        "Mozilla Public License 2.0 (MPL 2.0)" to MPL_2_0.toExpression(),
        "Mozilla Public License Version 1.0" to MPL_1_0.toExpression(),
        "Mozilla Public License Version 1.1" to MPL_1_1.toExpression(),
        "Mozilla Public License Version 2.0" to MPL_2_0.toExpression(),
        "Mozilla Public License, Version 2.0" to MPL_2_0.toExpression(),
        "Mozilla Public License v 2.0" to MPL_2_0.toExpression(),
        "NCSA License" to NCSA.toExpression(),
        "NCSA Open Source License" to NCSA.toExpression(),
        "NetBeans CDDL/GPL" to (CDDL_1_0 or GPL_2_0_ONLY),
        "Netscape Public License" to NPL_1_0.toExpression(),
        "Netscape Public License (NPL)" to NPL_1_0.toExpression(),
        "New BSD" to BSD_3_CLAUSE.toExpression(),
        "New BSD licence" to BSD_3_CLAUSE.toExpression(),
        "New BSD License" to BSD_3_CLAUSE.toExpression(),
        "netscape License" to NPL_1_1.toExpression(),
        "Nokia Open Source License (NOKOS)" to NOKIA.toExpression(),
        "Open Software License v. 3.0" to OSL_3_0.toExpression(),
        "ODbL 1.0" to ODBL_1_0.toExpression(),
        "ODbL v1.0" to ODBL_1_0.toExpression(),
        "Open Software License 3.0 (OSL-3.0)" to OSL_3_0.toExpression(),
        "Oracle Free Use Terms and Conditions (FUTC)" to licenseRef("oracle-futc", "ort"),
        "Other/Proprietary License" to licenseRef("proprietary-license", "scancode"),
        "Perl Artistic v2" to ARTISTIC_1_0_PERL.toExpression(),
        "Public Domain" to licenseRef("public-domain-disclaimer", "scancode"),
        "Public Domain <http://unlicense.org>" to licenseRef("public-domain-disclaimer", "scancode"),
        "Public Domain, per Creative Commons CC0" to CC0_1_0.toExpression(),
        "Public domain (CC0-1.0)" to CC0_1_0.toExpression(),
        "PublicDomain" to licenseRef("public-domain-disclaimer", "scancode"),
        "Python License (CNRI)" to CNRI_PYTHON.toExpression(),
        "Python License (CNRI Python License)" to CNRI_PYTHON.toExpression(),
        "Python Software Foundation" to PYTHON_2_0.toExpression(),
        "Python Software Foundation License" to PYTHON_2_0.toExpression(),
        "Revised BSD" to BSD_3_CLAUSE.toExpression(),
        "Revised BSD License" to BSD_3_CLAUSE.toExpression(),
        "Ruby's" to RUBY.toExpression(),
        "Qt Public License" to QPL_1_0.toExpression(),
        "Qt Public License (QPL)" to QPL_1_0.toExpression(),
        "SIL OPEN FONT LICENSE Version 1.1" to OFL_1_1.toExpression(),
        "SIL Open Font License 1.1 (OFL-1.1)" to OFL_1_1.toExpression(),
        "Simplified BSD" to BSD_2_CLAUSE.toExpression(),
        "Simplified BSD License" to BSD_2_CLAUSE.toExpression(),
        "Simplified BSD Liscence" to BSD_2_CLAUSE.toExpression(),
        "Sun Industry Standards Source License (SISSL)" to SISSL.toExpression(),
        "Sun Public License" to SPL_1_0.toExpression(),
        "TMate Open Source License (with dual licensing option)" to TMATE.toExpression(),
        "The (New) BSD License" to BSD_3_CLAUSE.toExpression(),
        "The Apache License" to APACHE_2_0.toExpression(),
        "The Apache License, Version 2.0" to APACHE_2_0.toExpression(),
        "The Apache Software Licence, Version 2.0" to APACHE_2_0.toExpression(),
        "The Apache Software License, Version 2.0" to APACHE_2_0.toExpression(),
        "The BSD 2-Clause License" to BSD_2_CLAUSE.toExpression(),
        "The BSD 3-Clause License" to BSD_3_CLAUSE.toExpression(),
        "The BSD License" to BSD_2_CLAUSE.toExpression(),
        "The BSD Software License" to BSD_2_CLAUSE.toExpression(),
        "The Eclipse Public License Version 1.0" to EPL_1_0.toExpression(),
        "The GNU General Public License (GPL), Version 2, With Classpath Exception"
                to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "The GNU General Public License, Version 2" to GPL_2_0_ONLY.toExpression(),
        "The GNU Lesser General Public License, Version 2.1" to LGPL_2_1_ONLY.toExpression(),
        "The GNU Lesser General Public License, Version 3.0" to LGPL_3_0_ONLY.toExpression(),
        "The JSON License" to JSON.toExpression(),
        "The MIT" to MIT.toExpression(),
        "The MIT License" to MIT.toExpression(),
        "The MIT License (MIT)" to MIT.toExpression(),
        "The MIT License(MIT)" to MIT.toExpression(),
        "The New BSD License" to BSD_3_CLAUSE.toExpression(),
        "The PostgreSQL License" to POSTGRESQL.toExpression(),
        "The SAX License" to SAX_PD.toExpression(),
        "The W3C License" to W3C.toExpression(),
        "The W3C Software License" to W3C.toExpression(),
        "Three-clause BSD-style" to BSD_3_CLAUSE.toExpression(),
        "the gpl v3" to GPL_3_0_ONLY.toExpression(),
        "Two-clause BSD-style license" to BSD_2_CLAUSE.toExpression(),
        "Unicode/ICU License" to ICU.toExpression(),
        "Universal Permissive License (UPL)" to UPL_1_0.toExpression(),
        "Vovida License 1.0" to VSL_1_0.toExpression(),
        "Vovida Software License" to VSL_1_0.toExpression(),
        "Vovida Software License 1.0" to VSL_1_0.toExpression(),
        "W3C License" to W3C.toExpression(),
        "ZPL 2.1" to ZPL_2_1.toExpression(),
        "Zope Public" to ZPL_2_1.toExpression(),
        "Zope Public License" to ZPL_2_1.toExpression(),
        "Zlib / Libpng License" to ZLIB_ACKNOWLEDGEMENT.toExpression(),
        "Zlib/Libpng License" to ZLIB_ACKNOWLEDGEMENT.toExpression(),
        "zope license" to ZPL_2_1.toExpression(),
        "zope 1.1" to ZPL_1_1.toExpression(),
        "zope 2.0" to ZPL_2_0.toExpression(),
        "zope 2.1" to ZPL_2_1.toExpression(),
        "zope v2.1" to ZPL_2_1.toExpression(),
        "http://ant-contrib.sourceforge.net/tasks/LICENSE.txt" to APACHE_1_1.toExpression(),
        "https://creativecommons.org/licenses/by/1.0" to CC_BY_1_0.toExpression(),
        "https://creativecommons.org/licenses/by/2.0" to CC_BY_2_0.toExpression(),
        "https://creativecommons.org/licenses/by/2.5" to CC_BY_2_5.toExpression(),
        "https://creativecommons.org/licenses/by/3.0" to CC_BY_3_0.toExpression(),
        "https://creativecommons.org/licenses/by/4.0" to CC_BY_4_0.toExpression(),
        "https://creativecommons.org/licenses/by-nc-nd/1.0" to CC_BY_NC_ND_1_0.toExpression(),
        "https://creativecommons.org/licenses/by-nc-nd/2.0" to CC_BY_NC_ND_2_0.toExpression(),
        "https://creativecommons.org/licenses/by-nc-nd/2.5" to CC_BY_NC_ND_2_5.toExpression(),
        "https://creativecommons.org/licenses/by-nc-nd/3.0" to CC_BY_NC_ND_3_0.toExpression(),
        "https://creativecommons.org/licenses/by-nc-nd/4.0" to CC_BY_NC_ND_4_0.toExpression(),
        "https://creativecommons.org/licenses/by-nc-sa/1.0" to CC_BY_NC_SA_1_0.toExpression(),
        "https://creativecommons.org/licenses/by-nc-sa/2.0" to CC_BY_NC_SA_2_0.toExpression(),
        "https://creativecommons.org/licenses/by-nc-sa/2.5" to CC_BY_NC_SA_2_5.toExpression(),
        "https://creativecommons.org/licenses/by-nc-sa/3.0" to CC_BY_NC_SA_3_0.toExpression(),
        "https://creativecommons.org/licenses/by-nc-sa/4.0" to CC_BY_NC_SA_4_0.toExpression(),
        "https://creativecommons.org/licenses/by-nd/1.0" to CC_BY_ND_1_0.toExpression(),
        "https://creativecommons.org/licenses/by-nd/2.0" to CC_BY_ND_2_0.toExpression(),
        "https://creativecommons.org/licenses/by-nd/2.5" to CC_BY_ND_2_5.toExpression(),
        "https://creativecommons.org/licenses/by-nd/3.0" to CC_BY_ND_3_0.toExpression(),
        "https://creativecommons.org/licenses/by-nd/4.0" to CC_BY_ND_4_0.toExpression(),
        "https://creativecommons.org/licenses/by-sa/1.0" to CC_BY_SA_1_0.toExpression(),
        "https://creativecommons.org/licenses/by-sa/2.0" to CC_BY_SA_2_0.toExpression(),
        "https://creativecommons.org/licenses/by-sa/2.5" to CC_BY_SA_2_5.toExpression(),
        "https://creativecommons.org/licenses/by-sa/3.0" to CC_BY_SA_3_0.toExpression(),
        "https://creativecommons.org/licenses/by-sa/4.0" to CC_BY_SA_4_0.toExpression(),
        "https://creativecommons.org/publicdomain/zero/1.0/" to CC0_1_0.toExpression(),
        "http://creativecommons.org/publicdomain/zero/1.0/legalcode" to CC0_1_0.toExpression(),
        "http://go.microsoft.com/fwlink/?LinkId=329770" to licenseRef("ms-net-library-2018-11", "scancode"),
        "http://polymer.github.io/LICENSE.txt" to BSD_3_CLAUSE.toExpression(),
        "http://svnkit.com/license.html" to TMATE.toExpression(),
        "http://www.apache.org/licenses/LICENSE-2.0" to APACHE_2_0.toExpression(),
        "http://www.apache.org/licenses/LICENSE-2.0.txt" to APACHE_2_0.toExpression(),
        "http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.txt" to CECILL_1_0.toExpression(),
        "http://www.cecill.info/licences/Licence_CeCILL_V2.1-en.txt" to CECILL_2_1.toExpression(),
        "https://www.eclipse.org/legal/epl-v10.html" to EPL_1_0.toExpression(),
        "https://www.eclipse.org/legal/epl-v20.html" to EPL_2_0.toExpression(),
        "http://www.gnu.org/copyleft/lesser.html" to LGPL_3_0_ONLY.toExpression(),
        "https://raw.github.com/RDFLib/rdflib/master/LICENSE" to BSD_3_CLAUSE.toExpression(),
        "public domain, Python, 2-Clause BSD, GPL 3 (see COPYING.txt)"
                to (((licenseRef("public-domain-disclaimer", "scancode") and PYTHON_2_0.toExpression())
                and BSD_2_CLAUSE.toExpression()) and GPL_3_0_ONLY.toExpression()),
        "the Apache License, ASL Version 2.0" to APACHE_2_0.toExpression()
    )

    /**
     * The map of collected license strings associated with their corresponding SPDX expression.
     */
    val mapping = mappingList.toMap().toSortedMap(String.CASE_INSENSITIVE_ORDER)

    /**
     * Return an SPDX LicenseRef string for the given [id] and optional [namespace].
     */
    private fun licenseRef(id: String, namespace: String = "") =
        if (namespace.isEmpty()) {
            SpdxLicenseReferenceExpression("LicenseRef-$id")
        } else {
            SpdxLicenseReferenceExpression("LicenseRef-$namespace-$id")
        }

    /**
     * Return the [SpdxExpression] the [license] string maps to, or null if there is no corresponding expression.
     */
    fun map(license: String) = mapping[license] ?: SpdxLicense.forId(license)?.toExpression()
}
