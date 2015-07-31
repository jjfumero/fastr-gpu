/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RContext.Engine.ParseException;
import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.*;

/**
 * Internal component of the {@code parse} base package function.
 *
 * <pre>
 * parse(file, n, text, prompt, srcfile, encoding)
 * </pre>
 *
 * There are two main modalities in the arguments:
 * <ul>
 * <li>Input is taken from "conn" or "text" (in which case conn==stdin(), but ignored).</li>
 * <li>Parse the entire input or just "n" "expressions". The FastR parser cannot handle the latter
 * case properly. It will parse the entire stream whereas GnuR stops after "n" expressions. So,
 * e.g., if there is a syntax error after the "n'th" expression, GnuR does not see it, whereas FastR
 * does and throws an error. However, if there is no error FastR can truncate the expressions vector
 * to length "n"</li>
 * </ul>
 * Despite the modality there is no value in multiple specializations for what is an inherently
 * slow-path builtin.
 * <p>
 * The inputs do not lend themselves to the correct creation of {@link Source} attributes for the
 * FastR AST. In particular the {@code source} builtin reads the input internally and calls us the
 * "text" variant. However useful information regarding the origin of the input can be found either
 * in the connection info or in the "srcfile" argument which, if not {@code RNull#instance} is an
 * {@link REnvironment} with relevant data. So we can fix up the {@link Source} attributes on the
 * AST after the parse. It's relevant to do this for the Truffle instrumentation framework.
 * <p>
 * On the R side, GnuR adds similar R attributes to the result, which is important for R tooling.
 */
@RBuiltin(name = "parse", kind = INTERNAL, parameterNames = {"conn", "n", "text", "prompt", "srcfile", "encoding"})
public abstract class Parse extends RBuiltinNode {
    @Child private CastIntegerNode castIntNode;
    @Child private CastStringNode castStringNode;
    @Child private CastToVectorNode castVectorNode;

    private int castInt(Object n) {
        if (castIntNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castIntNode = insert(CastIntegerNodeGen.create(false, false, false));
        }
        int result = (int) castIntNode.executeInt(n);
        if (RRuntime.isNA(result)) {
            result = -1;
        }
        return result;
    }

    private RStringVector castString(Object s) {
        if (castStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVectorNode = insert(CastToVectorNodeGen.create(false));
            castStringNode = insert(CastStringNodeGen.create(false, false, false, false));
        }
        return (RStringVector) castStringNode.executeString(castVectorNode.execute(s));
    }

    @Specialization
    protected Object parse(RConnection conn, Object n, Object text, RAbstractStringVector prompt, Object srcFile, RAbstractStringVector encoding) {
        controlVisibility();
        int nAsInt;
        if (n != RNull.instance) {
            nAsInt = castInt(n);
        } else {
            nAsInt = -1;
        }
        Object textVec = text;
        if (textVec != RNull.instance) {
            textVec = castString(textVec);
        }
        return doParse(conn, nAsInt, textVec, prompt, srcFile, encoding);
    }

    @TruffleBoundary
    @SuppressWarnings("unused")
    private Object doParse(RConnection conn, int n, Object textVec, RAbstractStringVector prompt, Object srcFile, RAbstractStringVector encoding) {
        String[] lines;
        if (textVec == RNull.instance) {
            if (conn == StdConnections.getStdin()) {
                throw RError.nyi(this, "parse from stdin not implemented");
            }
            try (RConnection openConn = conn.forceOpen("r")) {
                lines = openConn.readLines(0);
            } catch (IOException ex) {
                throw RError.error(this, RError.Message.PARSE_ERROR);
            }
        } else {
            lines = ((RStringVector) textVec).getDataWithoutCopying();
        }
        String coalescedLines = coalesce(lines);
        if (coalescedLines.length() == 0 || n == 0) {
            return RDataFactory.createExpression(RDataFactory.createList());
        }
        try {
            Source source = srcFile != RNull.instance ? createSource(srcFile, coalescedLines) : createSource(conn, coalescedLines);
            RExpression exprs = RContext.getEngine().parse(source);
            if (n > 0 && n > exprs.getLength()) {
                RList list = exprs.getList();
                Object[] listData = list.getDataCopy();
                Object[] subListData = new Object[n];
                System.arraycopy(listData, 0, subListData, 0, n);
                exprs = RDataFactory.createExpression(RDataFactory.createList(subListData));
            }
            // Handle the required R attributes
            if (srcFile instanceof REnvironment) {
                addAttributes(exprs, source, (REnvironment) srcFile);
            }
            return exprs;
        } catch (ParseException ex) {
            throw RError.error(this, RError.Message.PARSE_ERROR);
        }
    }

    private static String coalesce(String[] lines) {
        StringBuffer sb = new StringBuffer();
        for (String line : lines) {
            sb.append(line);
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Creates a {@link Source} object by gleaning information from {@code srcFile}.
     */
    private static Source createSource(Object srcFile, String coalescedLines) {
        if (srcFile instanceof REnvironment) {
            REnvironment srcFileEnv = (REnvironment) srcFile;
            boolean isFile = RRuntime.fromLogical((byte) srcFileEnv.get("isFile"));
            if (isFile) {
                // Might be a URL
                String urlFileName = RRuntime.asString(srcFileEnv.get("filename"));
                assert urlFileName != null;
                String fileName = ConnectionSupport.removeFileURLPrefix(urlFileName);
                File fnf = new File(fileName);
                String path = null;
                if (!fnf.isAbsolute()) {
                    String wd = RRuntime.asString(srcFileEnv.get("wd"));
                    path = String.join(File.separator, wd, fileName);
                } else {
                    path = fileName;
                }
                return createFileSource(path, coalescedLines);
            } else {
                return Source.fromText(coalescedLines, "<parse>");
            }
        } else {
            String srcFileText = RRuntime.asString(srcFile);
            if (srcFileText.equals("<text>")) {
                return Source.fromText(coalescedLines, "<parse>");
            } else {
                return createFileSource(ConnectionSupport.removeFileURLPrefix(srcFileText), coalescedLines);
            }
        }

    }

    private static Source createSource(RConnection conn, String coalescedLines) {
        // TODO check if file
        String path = ConnectionSupport.getBaseConnection(conn).getSummaryDescription();
        return createFileSource(path, coalescedLines);
    }

    private static Source createFileSource(String path, CharSequence chars) {
        try {
            return Source.fromFileName(chars, path);
        } catch (IOException ex) {
            throw RInternalError.shouldNotReachHere();
        }
    }

    private static final RStringVector SRCREF_CLASS = RDataFactory.createStringVectorFromScalar("srcref");

    private static void addAttributes(RExpression exprs, Source source, REnvironment srcFile) {
        Object[] srcrefData = new Object[exprs.getLength()];
        for (int i = 0; i < srcrefData.length; i++) {
            Node node = (Node) ((RLanguage) exprs.getDataAt(i)).getRep();
            SourceSection ss = node.getSourceSection();
            int[] llocData = new int[8];
            int startLine = ss.getStartLine();
            int startColumn = ss.getStartColumn();
            int lastLine = ss.getEndLine();
            int lastColumn = ss.getEndColumn();
            // no multi-byte support, so byte==line
            llocData[0] = startLine;
            llocData[1] = startColumn;
            llocData[2] = lastLine;
            llocData[3] = lastColumn;
            llocData[4] = startColumn;
            llocData[5] = lastColumn;
            llocData[6] = startLine;
            llocData[7] = lastLine;
            RIntVector lloc = RDataFactory.createIntVector(llocData, RDataFactory.COMPLETE_VECTOR);
            lloc.setClassAttr(SRCREF_CLASS, false);
            lloc.setAttr("srcfile", srcFile);
            srcrefData[i] = lloc;
        }
        exprs.setAttr("srcref", RDataFactory.createList(srcrefData));
        int[] wholeSrcrefData = new int[8];
        int endOffset = source.getCode().length() - 1;
        wholeSrcrefData[0] = source.getLineNumber(0);
        wholeSrcrefData[3] = source.getLineNumber(endOffset);
        source.getColumnNumber(0);
        wholeSrcrefData[6] = wholeSrcrefData[0];
        wholeSrcrefData[6] = wholeSrcrefData[3];

        exprs.setAttr("wholeSrcref", RDataFactory.createIntVector(wholeSrcrefData, RDataFactory.COMPLETE_VECTOR));
        exprs.setAttr("srcfile", srcFile);
    }

}
