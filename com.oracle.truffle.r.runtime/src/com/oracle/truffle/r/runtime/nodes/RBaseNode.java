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

import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.RDeparse.State;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RSerialize;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.env.REnvironment;

/**
 * This class should be used as the superclass for all instances of nodes in the FastR AST in
 * preference to {@link Node}. Its basic function is to provide access to the {@link RSyntaxNode}
 * after replace transformations have taken place that replace the {@link RSyntaxNode} with a
 * subclass of this class. The mechanism for locating the original {@link RSyntaxNode} is
 * subclass-specific, specified by the {@link #getRSyntaxNode()} method, the implementation of which
 * defaults to a hierarchical search, since many replacement strategies fit that structure.
 *
 * It also overrides the implementations of {@link #getSourceSection()},
 * {@link #assignSourceSection}, {@link #clearSourceSection()} and
 * {@link #getEncapsulatingSourceSection()} to enforce the FastR invariant that <b>only</b>nodes
 * that implement {@link #getRSyntaxNode()} should have a {@link SourceSection} attribute.
 *
 * The {@code ReplacementNode} class is give special handling because its child nodes are
 * necessarily syntax nodes but we never want to return them as results. TODO find a low-cost,
 * minimally invasive way, of finessing this, as it also applies to any AST transformation that
 * rewrites user code.
 *
 * Is it ever acceptable to subclass {@link Node} directly? The answer is yes, with the following
 * caveats:
 * <ul>
 * <li>The code in the subclass does not invoke methods in the {@link RError} class <b>or</b>takes
 * the responsibility to locate the appropriate {@link RBaseNode} to pass</li>
 * <li>An instance of the subclass is never used to {@link #replace} an instance of
 * {@link RBaseNode}.</li>
 * </ul>
 *
 * N.B. When a {@link WrapperNode} replaces a node for instrumentation, the {@link SourceSection} is
 * propagated to the wrapper node by code in {@link Node#replace}. We can't do anything about this
 * other than special case it, even though FastR does not treat a {@link WrapperNode} as a
 * {@link RSyntaxNode}.
 */
public abstract class RBaseNode extends Node {

    /**
     * Since {@link RSyntaxNode}s are sometimes used (for convenience) in non-syntax contexts, this
     * function also checks the {@link RSyntaxNode#isSyntax()} method. This method should always be
     * used in preference to {@code instanceof RSyntaxNode}.
     */
    public boolean isRSyntaxNode() {
        return this instanceof RSyntaxNode && ((RSyntaxNode) this).isSyntax();
    }

    /**
     * Convenience method for working with {@link Node}, e.g. in {@link NodeVisitor}.
     */
    public static boolean isRSyntaxNode(Node node) {
        return node instanceof RSyntaxNode && ((RSyntaxNode) node).isSyntax();
    }

    /**
     * Handles the discovery of the {@link RSyntaxNode} that this node is derived from.
     */
    public RSyntaxNode asRSyntaxNode() {
        if (isRSyntaxNode()) {
            return (RSyntaxNode) this;
        } else {
            return getRSyntaxNode();
        }
    }

    /**
     * Many nodes organize themselves in such a way that the relevant {@link RSyntaxNode} can be
     * found by following the parent chain, which is therefore the default implementation.
     */
    protected RSyntaxNode getRSyntaxNode() {
        RSyntaxNode node = checkReplacementChild();
        if (node != null) {
            return node;
        }
        Node current = this;
        while (current != null) {
            if (current instanceof RSyntaxNode && ((RSyntaxNode) current).isSyntax()) {
                return (RSyntaxNode) current;
            }
            current = current.getParent();
        }
        throw RInternalError.shouldNotReachHere("getRSyntaxNode");
    }

    public void deparse(State state) {
        RSyntaxNode syntaxNode = getRSyntaxNode();
        syntaxNode.deparseImpl(state);

    }

    public RSyntaxNode substitute(REnvironment env) {
        RSyntaxNode syntaxNode = getRSyntaxNode();
        return syntaxNode.substituteImpl(env);
    }

    public void serialize(RSerialize.State state) {
        RSyntaxNode syntaxNode = getRSyntaxNode();
        syntaxNode.serializeImpl(state);
    }

    public int getRLength() {
        RSyntaxNode syntaxNode = getRSyntaxNode();
        return syntaxNode.getRlengthImpl();
    }

    public Object getRelement(int index) {
        RSyntaxNode syntaxNode = getRSyntaxNode();
        return syntaxNode.getRelementImpl(index);
    }

    public boolean getRequals(RSyntaxNode other) {
        RSyntaxNode syntaxNode = getRSyntaxNode();
        return syntaxNode.getRequalsImpl(other);
    }

    @Override
    public SourceSection getSourceSection() {
        if (this instanceof WrapperNode) {
            return super.getSourceSection();
        }
        /* Explicitly allow on a node for which isSyntax() == false */
        if (this instanceof RSyntaxNode) {
            if (RContext.getRRuntimeASTAccess().isReplacementNode(this)) {
                return super.getSourceSection();
            }
            RSyntaxNode node = checkReplacementChild();
            if (node != null) {
                return node.getSourceSection();
            }
            return super.getSourceSection();
        } else {
            throw RInternalError.shouldNotReachHere("getSourceSection on non-syntax node");
        }
    }

    @Override
    public void assignSourceSection(SourceSection section) {
        if (this instanceof WrapperNode) {
            super.assignSourceSection(section);
            return;
        }
        /* Explicitly allow on a node for which isSyntax() == false */
        if (this instanceof RSyntaxNode) {
            super.assignSourceSection(section);
        } else {
            throw RInternalError.shouldNotReachHere("assignSourceSection on non-syntax node");
        }
    }

    @Override
    public void clearSourceSection() {
        /* Explicitly allow on a node for which isSyntax() == false */
        if (this instanceof RSyntaxNode) {
            super.clearSourceSection();
        } else {
            /*
             * Eventually this should be an error but currently "substitute" walks the entire tree
             * calling this method.
             */
            super.clearSourceSection();
        }
    }

    @Override
    /**
     * Returns the {@link SourceSection} for this node, by locating the associated {@link RSyntaxNode}.
     * We do not want any code in FastR calling this method as it is subsumed by {@link #getRSyntaxNode}.
     * However, tools code may call it, so we simply delegate the call.
     */
    public SourceSection getEncapsulatingSourceSection() {
        return getRSyntaxNode().getSourceSection();
    }

    /**
     * This is rather nasty, but then that applies to {@code ReplacementNode} in general. Since a
     * auto-generated child of a {@code ReplacementNode} may have a {@link SourceSection}, we might
     * return it using the normal logic, but that would be wrong, we really need to return the the
     * {@link SourceSection} of the {@code ReplacementNode} itself. This is a case where we can't
     * use {@link #getRSyntaxNode} as a workaround because the {@code ReplacementNode} child nodes
     * are just standard {@link RSyntaxNode}s.
     *
     * @return {@code null} if not a child of a {@code ReplacementNode}, otherwise the
     *         {@code ReplacementNode}.
     */
    private RSyntaxNode checkReplacementChild() {
        Node node = this;
        while (node != null) {
            if (RContext.getRRuntimeASTAccess().isReplacementNode(node)) {
                return (RSyntaxNode) node;
            }
            node = node.getParent();
        }
        return null;
    }

}
