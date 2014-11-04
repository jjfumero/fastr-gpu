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
public class TestrGenBuiltinexp extends TestBase {

    @Test
    public void testexp1() {
        assertEval("argv <- list(-3.99290891786396);exp(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testexp2() {
        assertEval("argv <- list(structure(3.3059560902335, .Names = \'lymax\'));exp(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testexp3() {
        assertEval("argv <- list(structure(c(-0.00324566582797463, -0.0174366299092001, -0.0697465196367962, -0.00678668749718479, -0.0364599944879883, -0.145839977951944, -0.014190964081224, -0.0762378512927396, -0.304951405170939, -0.0296733069908004, -0.159413352946301, -0.637653411785165, -0.0620468872115091, -0.333333333333361, -1.33333333333336, -0.129740045955487, -0.697000025766712, -2.78800010306667, -0.271286446121824, -1.45742710775627, -5.8297084310247, -0.567259979811165, -3.04748019497741, -12.1899207799089, -1.18614066163432, -6.37228132326786, -25.4891252930698), .Dim = c(3L, 9L), .Dimnames = list(c(\'x\', \'x\', \'\'), NULL)));exp(argv[[1]]);");
    }

    @Test
    public void testexp4() {
        assertEval("argv <- list(c(0, 0, 0, 0, 0, 0.312525079410893, 0.312525079410893, 0.519628781161469, 0.519628781161469, 0.519628781161469, 0.519628781161469, 0.519628781161469, 0.519628781161469, 0.656871985321733, 0.656871985321733, 0.656871985321733, 0.747820128348458, 0.747820128348458, 0.747820128348458, 0.747820128348458, 0.808089522163767, 0.808089522163767, 0.808089522163767, 0.808089522163767, 0.808089522163767, 0.808089522163767, 0.848028763471832, 0.848028763471832, 0.848028763471832, 0.848028763471832, 0.848028763471832, 0.848028763471832, 0.848028763471832, 0.848028763471832, 0.848028763471832, 0.874495646499593, 0.874495646499593, 0.874495646499593, 0.874495646499593, 0.874495646499593, 0.874495646499593, 0.874495646499593, 0.874495646499593, 0.874495646499593, 0.874495646499593, 0.892034685155886, 0.892034685155886, 0.892034685155886, 0.892034685155886, 0.892034685155886, 0.911359578074335, 0.916463626041527));exp(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testexp5() {
        assertEval("argv <- list(c(0+0.392699081698724i, 0+0.785398163397448i, 0+1.17809724509617i, 0+1.5707963267949i, 0+1.96349540849362i, 0+2.35619449019234i, 0+2.74889357189107i, 0+3.14159265358979i, 0+3.53429173528852i, 0+3.92699081698724i, 0+4.31968989868597i, 0+4.71238898038469i, 0+5.10508806208341i, 0+5.49778714378214i, 0+5.89048622548086i, 0+6.28318530717959i));exp(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testexp6() {
        assertEval("argv <- list(c(-0.1, -3.16227766016838, -100));exp(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testexp8() {
        assertEval("argv <- list(logical(0));exp(argv[[1]]);");
    }

    @Test
    public void testexp9() {
        assertEval("argv <- list(c(4.92585186838819, 4.80249477012754, 4.18570927882429, 4.06235218056364, 3.81563798404234, 3.32220959099974, 3.19885249273909, 2.95213829621779, 2.45870990317518, 2.33535280491453, 2.08863860839323, 1.59521021535063, 1.47185311708998, 1.34849601882933, 0.608353429265429, 0.361639232744128, -0.131789160298473, -0.255146258559123, -1.11864594638368, -1.24200304464433, -1.85878853594758, -1.98214563420823, -2.84564532203278, -3.09235951855408, -3.70914500985733));exp(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testexp10() {
        assertEval("argv <- list(c(47.5645940356179, -6.38155741912049, -4.72835558025993, -9.12276173822938, 16.7269898773273, 1.72738845624351, 2.57214256243276, -6.38155741912049, 54.3235157345705, -1.01577550708815, 1.03229146110395, -7.85550713095368, -0.907238963715769, -0.92327375484205, -4.72835558025993, -1.01577550708815, 46.5450612116912, 4.59443066488959, -2.69397762349353, -13.3238428844397, -4.89920529326131, -9.12276173822938, 1.03229146110395, 4.59443066488959, 47.7416929123262, -3.84567249122941, -9.99434616922533, -0.0518296900644576, 16.7269898773273, -7.85550713095368, -2.69397762349353, -3.84567249122941, 49.4381847193856, 8.76151535039852, 0.371991514317358, 1.72738845624351, -0.907238963715769, -13.3238428844397, -9.99434616922533, 8.76151535039852, 50.1823716395239, -1.41801229530673, 2.57214256243276, -0.92327375484205, -4.89920529326131, -0.0518296900644576, 0.371991514317358, -1.41801229530673, 44.6019728197531));exp(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testexp11() {
        assertEval("argv <- list(c(-745, -744, -743, -742, -741, -740, -730, -720, -710, -709, -708, -707, -706, -705));exp(argv[[1]]);");
    }
}
