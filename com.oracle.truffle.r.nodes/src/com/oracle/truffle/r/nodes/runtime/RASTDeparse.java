/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.runtime;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RDeparse.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.nodes.*;

/**
 * Deparse support for AST instances.
 *
 * N.B. We ignore the {@link SourceSection} for GnuR compatibility. E.g. in <a
 * href="https://stat.ethz.ch/R-manual/R-devel/library/base/html/deparse.html">deparse
 * specification</a>: "To avoid the risk of a source attribute out of sync with the actual function
 * definition, the source attribute of a function will never be deparsed as an attribute."
 *
 * Parts transcribed from GnuR deparse.c
 */
public class RASTDeparse {

    private static final String SQUARE = "[";

    /**
     * Ensure that {@code node} has a {@link SourceSection} by deparsing if necessary.
     */
    public static void ensureSourceSection(RSyntaxNode nodeIn) {
        SourceSection ss = nodeIn.getSourceSection();
        if (ss == null) {
            RBaseNode node = RASTUtils.unwrap(nodeIn);
            RDeparse.State state = RDeparse.State.createPrintableState();
            node.deparse(state);
            String sourceString = state.toString();
            Source source = Source.fromText(sourceString, "ensureSource");
            nodeIn.asRNode().assignSourceSection(source.createSection("", 0, sourceString.length()));
        }
    }

    public static void deparse(State state, RLanguage rl) {
        RBaseNode node = rl.getRep();
        node.deparse(state);
    }

    public static void deparse(State state, RFunction f) {
        ((RSyntaxNode) f.getRootNode()).deparseImpl(state);
    }

    public static Func isInfixOperator(Object fname) {
        if (fname instanceof RSymbol) {
            Func func = RDeparse.getFunc(((RSymbol) fname).getName());
            if (func == null) {
                return null;
            } else {
                return func.info.kind == PP.RETURN ? null : func;
            }
        }
        return null;
    }

    private static Func isInfixOperatorNode(Node node) {
        if (node instanceof RCallNode || node instanceof GroupDispatchNode) {
            Object fname = RASTUtils.findFunctionName(node);
            return isInfixOperator(fname);
        } else {
            return null;
        }
    }

    public static void deparseInfixOperator(RDeparse.State state, Node node, RDeparse.Func func) {
        Arguments<RSyntaxNode> args = RASTUtils.findCallArguments(node);
        RSyntaxNode[] argValues = args.getArguments();
        PP kind = func.info.kind;
        if (kind == PP.BINARY && argValues.length == 1) {
            kind = PP.UNARY;
        }
        switch (kind) {
            case UNARY:
                state.append(func.op);
                argValues[0].deparseImpl(state);
                break;

            case BINARY:
            case BINARY2:
                // TODO lbreak
                boolean parens = needsParens(func.info, argValues[0], true);
                if (parens) {
                    state.append('(');
                }
                argValues[0].deparseImpl(state);
                if (parens) {
                    state.append(')');
                }
                if (kind == PP.BINARY) {
                    state.append(' ');
                }
                state.append(func.op);
                if (kind == PP.BINARY) {
                    state.append(' ');
                }
                parens = needsParens(func.info, argValues[1], false);
                if (parens) {
                    state.append('(');
                }
                argValues[1].deparseImpl(state);
                if (parens) {
                    state.append(')');
                }
                break;

            case SUBSET:
                argValues[0].deparseImpl(state);
                state.append(func.op == SQUARE ? "[" : "[[");
                ArgumentsSignature signature = args.getSignature();
                // similar to ArgumentsNode.deparse()
                for (int i = 1; i < argValues.length; i++) {
                    RSyntaxNode argument = argValues[i];
                    String name = signature.getName(i);
                    if (name != null) {
                        state.append(name);
                        state.append(" = ");
                    }
                    if (argument != null) {
                        // e.g. not f(, foo)
                        argument.deparseImpl(state);
                    }
                    if (i != argValues.length - 1) {
                        state.append(", ");
                    }
                }
                state.append(func.op == SQUARE ? "]" : "]]");

                break;
            case DOLLAR:
                argValues[0].deparseImpl(state);
                state.append(func.op);
                String fieldName = ConstantNode.getString(argValues[1]);
                if (fieldName != null) {
                    state.append(fieldName);
                } else {
                    // FIXME: this needs to be handled in RCallNode, not here
                    argValues[1].deparseImpl(state);
                }
                break;
            default:
                assert false;
        }
    }

    private static boolean needsParens(PPInfo mainop, RSyntaxNode arg, boolean isLeft) {
        Node node = RASTUtils.unwrap(arg);
        Func func = isInfixOperatorNode(node);
        if (func != null) {
            Arguments<RSyntaxNode> args = RASTUtils.findCallArguments(node);
            PPInfo arginfo = func.info;
            switch (arginfo.kind) {
                case BINARY:
                case BINARY2:
                    switch (args.getArguments().length) {
                        case 1:
                            if (!isLeft) {
                                return false;
                            }
                            if (arginfo.prec == RDeparse.PREC_SUM) {
                                arginfo = arginfo.changePrec(RDeparse.PREC_SIGN);
                            }
                            break;
                        case 2:
                            break;
                        default:
                            return false;
                    }
                    return RDeparse.checkPrec(mainop, arginfo, isLeft);

                    // CheckStyle: stop case fall through check
                case UNARY:
                    if (mainop.prec > arginfo.prec || (mainop.prec == arginfo.prec && isLeft == mainop.rightassoc)) {
                        return true;
                    }
                    // CheckStyle: resume case fall through check
            }
        } else {
            // TODO complex
        }
        return false;
    }

}
