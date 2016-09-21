/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_ngettext extends TestBase {

    @Test
    public void testngettext1() {
        assertEval("argv <- list(1L, '%s is not TRUE', '%s are not all TRUE', NULL); .Internal(ngettext(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testngettext2() {
        assertEval("argv <- list(2L, '%s is not TRUE', '%s are not all TRUE', NULL); .Internal(ngettext(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testNgettext() {
        assertEval("{ ngettext(1, \"a\", \"b\") }");
        assertEval("{ ngettext(0, \"a\", \"b\") }");
        assertEval("{ ngettext(42, \"a\", \"b\") }");
        assertEval("{ ngettext(1, c(\"a\"), \"b\") }");
        assertEval("{ ngettext(1, \"a\", c(\"b\")) }");
        assertEval("{ ngettext(c(1), \"a\", \"b\") }");
        assertEval("{ ngettext(c(1,2), \"a\", \"b\") }");
        assertEval(Output.ContainsWarning, "{ ngettext(1+1i, \"a\", \"b\") }");
        assertEval(Output.ContainsError, "{ ngettext(1, NULL, \"b\") }");
        assertEval(Output.ContainsError, "{ ngettext(1, \"a\", NULL) }");
        assertEval(Output.ContainsError, "{ ngettext(1, NULL, NULL) }");
        assertEval(Output.ContainsError, "{ ngettext(1, c(\"a\", \"c\"), \"b\") }");
        assertEval(Output.ContainsError, "{ ngettext(1, \"a\", c(\"b\", \"c\")) }");
        assertEval(Output.ContainsError, "{ ngettext(1, c(1), \"b\") }");
        assertEval(Output.ContainsError, "{ ngettext(1, \"a\", c(1)) }");
        assertEval(Output.ContainsError, "{ ngettext(-1, \"a\", \"b\") }");
    }
}
