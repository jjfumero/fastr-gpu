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
public class TestrGenBuiltinencodeString extends TestBase {

    @Test
    @Ignore
    public void testencodeString1() {
        assertEval("argv <- list(c(\'1\', \'2\', NA), 0L, \'\\\'\', 0L, FALSE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString3() {
        assertEval("argv <- list(c(\'a\', \'ab\', \'abcde\'), NA, \'\', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString4() {
        assertEval("argv <- list(c(\'a\', \'ab\', \'abcde\'), NA, \'\', 1L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString6() {
        assertEval("argv <- list(c(\'NA\', \'a\', \'b\', \'c\', \'d\', NA), 0L, \'\', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString7() {
        assertEval("argv <- list(\'ab\\bc\\ndef\', 0L, \'\', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString8() {
        assertEval("argv <- list(c(\'FALSE\', NA), 0L, \'\', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString9() {
        assertEval("argv <- list(structure(\'integer(0)\', .Names = \'c0\', row.names = character(0)), 0L, \'\', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString10() {
        assertEval("argv <- list(\'\\\'class\\\' is a reserved slot name and cannot be redefined\', 0L, \'\\\'\', 0L, FALSE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString11() {
        assertEval("argv <- list(structure(character(0), .Dim = c(0L, 0L)), 0L, \'\', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString12() {
        assertEval("argv <- list(character(0), logical(0), \'\', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    @Ignore
    public void testencodeString13() {
        assertEval("argv <- list(structure(\'integer(0)\', .Names = \'c0\', row.names = character(0)), structure(list(c0 = structure(integer(0), .Label = character(0), class = \'factor\')), .Names = \'c0\', row.names = character(0), class = structure(\'integer(0)\', .Names = \'c0\')), \'\', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }
}

