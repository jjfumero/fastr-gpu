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
package com.oracle.truffle.r.runtime.nodes;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.runtime.context.*;

/**
 * An interface that identifies an AST node as being part of the syntactic structure of the
 * (original) AST. Essentially a syntax node is defined as one produced by the parser and before any
 * evaluation takes place. I.e. a visit to every node in such a tree should have
 * {@code node instanceof RSyntaxNode == true}. A syntax node (necessarily) includes <i>backbone</i>
 * nodes that, while carrying no actual syntactic information, are used to connect one syntax node
 * to another. It is possible to detect backbone nodes using the {@link #isBackbone} method.
 *
 * Currently FastR deviates from this ideal in that the parsing process or, more accurately, the
 * conversion of the AST produced by the parser into a Truffle AST effects some transformations that
 * introduce nodes that do not correspond to the original syntax. The most notable such
 * transformation is seen in the {@code ReplacementNode} that implements a lowering of the so-called
 * <i>replacement</i> logic into a sequence of lower-level nodes that nonetheless implement this
 * interface.
 *
 * A more subtle issue is the general use of syntax nodes in rewrites of the AST where the nodes do
 * not correspond to nodes in the original source. Examples are anonymous variables created as a
 * {code ReadVariableNode}. Evidently such nodes, and the larger structures that contain them,
 * cannot be given meaningful {@link SourceSection} information. Ideally such nodes would be
 * refactored in such a way that such nodes did not implement this interface. However, as a
 * workaround, the {@link #isSyntax} method can be overridden in such nodes, using some contextual
 * information, to return {@code false}.
 *
 * Every implementor of this interface must provide an implementation of the {@link #deparseImpl},
 * {@link #serializeImpl} and {@link #substituteImpl} methods. These are invoked by the
 * corresponding methods on {@link RBaseNode} after the correct {@link RSyntaxNode} is located.
 */
public interface RSyntaxNode extends RSyntaxNodeSPI {
    /**
     * A convenience method that captures the fact that, while the notion of a syntax node is
     * described in this interface, in practice all {@link RSyntaxNode} instances are also
     * {@link RNode}s. At runtime this method should be inlined away.
     */
    default RNode asRNode() {
        return (RNode) this;
    }

    /**
     * Similar but handles {@code FunctionDefinitionNode}.
     */
    default Node asNode() {
        return (Node) this;
    }

    /**
     * Denotes that this node is part of the "backbone" of the AST, but carries no useful syntactic
     * information.
     */
    default boolean isBackbone() {
        return false;
    }

    /**
     * If overridden to return {@code false}, denotes a node that is being used in a non-syntactic
     * situation.
     */
    default boolean isSyntax() {
        return true;
    }

    /*
     * Convenience methods that also handle the fact that an {@code FunctionDefinitionNode} is not
     * an {@code RNode}.
     */

    default SourceSection getSourceSection() {
        return (asNode().getSourceSection());
    }

    default void clearSourceSection() {
        asNode().clearSourceSection();
    }

    default void assignSourceSection(SourceSection section) {
        asNode().assignSourceSection(section);
    }

    /**
     * Traverses the entire tree but only invokes the {@code visit} method for nodes that return
     * {@code true} to {@code instanceof RSyntaxNode} and {@link #isSyntax()}. Similar therefore to
     * {@code Node#accept}. Note that AST transformations can change the shape of the tree in
     * drastic ways; in particular one cannot truncate the walk on encountering a non-syntax node,
     * as the related {@link RSyntaxNode} may be have been transformed into a child of a non-syntax
     * node. We visit but do not call the {@code nodeVisitor} on {@link RSyntaxNode}s that return
     * {@code true} to {@link #isBackbone()}.
     *
     * N.B. A {@code ReplacementNode} is a very special case. Its children are {@link RSyntaxNode}s,
     * but we do not want to visit them at all. TODO perhaps we should visit the associated
     * {@code syntaxAST}.
     */
    static void accept(Node node, int depth, RSyntaxNodeVisitor nodeVisitor) {
        boolean visitChildren = true;
        int incDepth = 0;
        if (RBaseNode.isRSyntaxNode(node)) {
            RSyntaxNode syntaxNode = (RSyntaxNode) node;
            if (!syntaxNode.isBackbone()) {
                visitChildren = nodeVisitor.visit(syntaxNode, depth);
            }
            incDepth = 1;
        }
        if (!RContext.getRRuntimeASTAccess().isReplacementNode(node)) {
            if (visitChildren) {
                for (Node child : node.getChildren()) {
                    if (child != null) {
                        accept(child, depth + incDepth, nodeVisitor);
                    }
                }
            }
        }
    }

}
