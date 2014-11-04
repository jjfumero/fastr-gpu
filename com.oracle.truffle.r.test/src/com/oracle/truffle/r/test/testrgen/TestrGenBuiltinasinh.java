/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 * 
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.testrgen;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check
public class TestrGenBuiltinasinh extends TestBase {

    @Test
    @Ignore
    public void testasinh1() {
        assertEval("argv <- list(structure(numeric(0), .Dim = c(0L, 0L)));asinh(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testasinh2() {
        assertEval("argv <- list(FALSE);asinh(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testasinh3() {
        assertEval("argv <- list(c(-10.0178749274099, -9.71883514604503, -9.428631115578, -9.14699900083883, -8.87368275970178, -8.60843391030797, -8.35101130516145, -8.10118091189307, -7.85871560049306, -7.62339493681878, -7.39500498218993, -7.17333809888897, -6.95819276139009, -6.74937337314489, -6.54669008875839, -6.34995864139355, -6.15900017524753, -5.9736410829473, -5.79371284771676, -5.61905189017195, -5.44949941960501, -5.28490128962169, -5.12510785800119, -4.96997385065093, -4.81935822953248, -4.67312406443873, -4.53113840850555, -4.39327217734495, -4.25940003168967, -4.12940026344266, -4.00315468502777, -3.88054852194104, -3.76147030840502, -3.64581178603109, -3.53346780539777, -3.42433623045557, -3.31831784567124, -3.21531626582739, -3.11523784839505, -3.01799160839986, -2.92348913570423, -2.83164451463048, -2.74237424585168, -2.65559717047939, -2.57123439627907, -2.48920922594631, -2.40944708737842, -2.33187546587824, -2.25642383822831, -2.18302360857567, -2.1116080460688, -2.04211222419016, -1.97447296172911, -1.90862876534153, -1.84451977364398, -1.78208770279149, -1.72127579348957, -1.66202875939226, -1.60429273683927, -1.54801523588655, -1.49314509258573, -1.43963242246912, -1.38742857519786, -1.33648609033215, -1.2867586541832, -1.23820105770775, -1.19076915540686, -1.1444198251916, -1.0991109291792, -1.05480127538394, -1.01145058026799, -0.969019432118151, -0.927469255215247, -0.886762274763496, -0.84686148254806, -0.807730603289509, -0.769334061664634, -0.731636949963607, -0.6946049963541, -0.658204533723497, -0.622402469070884, -0.587166253420983, -0.55246385223268, -0.518263716275245, -0.484534752945762, -0.451246298001703, -0.41836808768293, -0.385870231197804, -0.353723183548358, -0.321897718669858, -0.290364902860306, -0.25909606847575, -0.22806278786747, -0.19723684753736, -0.166590222487993, -0.13609505074407, -0.105723608022064, -0.0754482825250618, -0.0452415498398571, -0.0150759479134945, 0.015075947913494, 0.0452415498398571, 0.0754482825250618, 0.105723608022064, 0.136095050744069, 0.166590222487993, 0.19723684753736, 0.22806278786747, 0.259096068475749, 0.290364902860306, 0.321897718669858, 0.353723183548358, 0.385870231197804, 0.41836808768293, 0.451246298001702, 0.484534752945762, 0.518263716275245, 0.55246385223268, 0.587166253420983, 0.622402469070883, 0.658204533723497, 0.6946049963541, 0.731636949963607, 0.769334061664634, 0.807730603289509, 0.84686148254806, 0.886762274763496, 0.927469255215247, 0.96901943211815, 1.01145058026799, 1.05480127538394, 1.0991109291792, 1.1444198251916, 1.19076915540686, 1.23820105770775, 1.2867586541832, 1.33648609033215, 1.38742857519786, 1.43963242246912, 1.49314509258573, 1.54801523588655, 1.60429273683927, 1.66202875939226, 1.72127579348957, 1.78208770279149, 1.84451977364398, 1.90862876534153, 1.97447296172911, 2.04211222419016, 2.1116080460688, 2.18302360857567, 2.25642383822831, 2.33187546587824, 2.40944708737842, 2.48920922594631, 2.57123439627907, 2.65559717047939, 2.74237424585168, 2.83164451463048, 2.92348913570423, 3.01799160839985, 3.11523784839505, 3.21531626582738, 3.31831784567124, 3.42433623045557, 3.53346780539777, 3.64581178603109, 3.76147030840502, 3.88054852194104, 4.00315468502777, 4.12940026344266, 4.25940003168967, 4.39327217734494, 4.53113840850555, 4.67312406443873, 4.81935822953248, 4.96997385065093, 5.12510785800119, 5.28490128962168, 5.44949941960501, 5.61905189017195, 5.79371284771676, 5.9736410829473, 6.15900017524753, 6.34995864139355, 6.54669008875838, 6.74937337314489, 6.95819276139008, 7.17333809888896, 7.39500498218993, 7.62339493681878, 7.85871560049306, 8.10118091189307, 8.35101130516145, 8.60843391030797, 8.87368275970177, 9.14699900083883, 9.428631115578, 9.71883514604503, 10.0178749274099));asinh(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testasinh4() {
        assertEval("argv <- list(c(0+2i, 0.0001+2i, 0-2i, 0-2.0001i));asinh(argv[[1]]);");
    }
}
