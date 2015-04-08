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
public class TestrGenBuiltinregexpr extends TestBase {

    @Test
    public void testregexpr1() {
        assertEval(Ignored.Unknown, "argv <- list(\'package:\', \'exNSS4\', FALSE, FALSE, TRUE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr2() {
        assertEval(Ignored.Unknown, "argv <- list(\'éè\', \'«Latin-1 accented chars»: éè øØ å<Å æ<Æ é éè\', FALSE, FALSE, TRUE, TRUE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr3() {
        assertEval(Ignored.Unknown, "argv <- list(\'package:\', \'graphics\', FALSE, FALSE, TRUE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr4() {
        assertEval(Ignored.Unknown, "argv <- list(\'^.*\\\\{n\', \'my(ugly[file{name\', FALSE, FALSE, FALSE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr5() {
        assertEval(Ignored.Unknown, "argv <- list(\'(\\\\\\\\S4method\\\\{([._[:alnum:]]*|\\\\$|\\\\[\\\\[?|\\\\+|\\\\-|\\\\*|\\\\/|\\\\^|<=?|>=?|!=?|==|\\\\&|\\\\||\\\\%[[:alnum:][:punct:]]*\\\\%)\\\\}\\\\{((([._[:alnum:]]+|`[^`]+`),)*([._[:alnum:]]+|`[^`]+`))\\\\})\', \'\\nread.00Index(file)\\n\', FALSE, FALSE, FALSE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr6() {
        assertEval(Ignored.Unknown, "argv <- list(\'\\\\.([[:alnum:]]+)$\', character(0), FALSE, FALSE, FALSE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr7() {
        assertEval(Ignored.Unknown, "argv <- list(\'(?<first>[[:upper:]][[:lower:]]+) (?<last>[[:upper:]][[:lower:]]+)\', c(\'  Ben Franklin and Jefferson Davis\', \'\\tMillard Fillmore\'), FALSE, TRUE, FALSE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr8() {
        assertEval(Ignored.Unknown, "argv <- list(\'^[[:space:]]*@(?i)attribute\', \'% 4. Relevant Information Paragraph:\', FALSE, TRUE, FALSE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr9() {
        assertEval(Ignored.Unknown, "argv <- list(\'package:\', \'dummy\', FALSE, FALSE, TRUE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testregexpr10() {
        assertEval(Ignored.Unknown, "argv <- list(\'package:\', \'environmental\', FALSE, FALSE, TRUE, FALSE); .Internal(regexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

	@Test
	public void testregexpr12() {
		assertEval(Ignored.Unknown, "argv <- structure(list(pattern = \'\\d\', text = c(\'1\', \'B\', \'3\')),     .Names = c(\'pattern\', \'text\'));"+
			"do.call(\'regexpr\', argv)");
	}


	@Test
	public void testregexpr13() {
		assertEval(Ignored.Unknown, "argv <- structure(list(pattern = \'[a-z]\', text = NA), .Names = c(\'pattern\',     \'text\'));"+
			"do.call(\'regexpr\', argv)");
	}

}

