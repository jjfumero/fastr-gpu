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
package com.oracle.truffle.r.runtime;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.vm.*;
import com.oracle.truffle.r.runtime.context.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

/**
 * A collection of methods that need access to the AST types, needed by code that resides in the
 * runtime project, which does not have direct access, as it would introduce project circularities.
 *
 */
public interface RRuntimeASTAccess {
    /**
     * Computes the "length" of the language element as per the R specification.
     */
    int getLength(RLanguage rl);

    /**
     * Returns the object ({@link RSymbol}, {@link RLanguage} or scalar value (e.g. {@link Double})
     * at index {@code index}.
     */
    Object getDataAtAsObject(RLanguage rl, int index);

    /**
     * Converts {@code rl} to a {@link RList}.
     */
    RList asList(RLanguage rl);

    Object fromList(RAbstractVector list);

    /**
     * Get the "names" attribute for an {@link RLanguage} object, or {@code null} if none.
     */
    RStringVector getNames(RLanguage rl);

    /**
     * Set the "names" attribute for an {@link RLanguage} object.
     */
    void setNames(RLanguage rl, RStringVector names);

    /**
     * Field update for a language (call) object.
     */
    RLanguage updateField(RLanguage rl, String field, Object value);

    /**
     * Deparse {@code rl}.
     */
    void deparse(RDeparse.State state, RLanguage rl);

    /**
     * Deparse non-builtin function.
     */
    void deparse(RDeparse.State state, RFunction f);

    /**
     * Serialize a runtime value that requires non-standard treatment.
     */
    Object serialize(RSerialize.State state, Object f);

    /**
     * Helper function for {@code serialize} working around cyclic dependency. {@code node} is an
     * {@RNode}.
     */
    void serializeNode(RSerialize.State state, Object node);

    /**
     * Returns the real caller associated with {@code rl}, by locating the {@code RSyntaxNode}
     * associated with the node stored with {@code rl}.
     */
    RLanguage getSyntaxCaller(RCaller rl);

    /**
     * Returns a string for a call as represented by {@code rl}, returned originally by
     * {@link #getSyntaxCaller}.
     */
    String getCallerSource(RLanguage rl);

    /**
     * Used by error/warning handling to try to find the call that provoked the error/warning.
     *
     * If there is no caller, return {@link RNull#instance}, e.g. "call" of a builtin from the
     * global env, otherwise return an {@code RLanguage} instance that represents the call.
     *
     * @param call may be {@code null} or it may be the {@link Node} that was executing when the
     *            error.warning was generated (builtin or associated node).
     */
    Object findCaller(Node call);

    /**
     * Convenience method for {@code getCallerSource(getSyntaxCaller(caller))}.
     */
    default String getCallerSource(RCaller caller) {
        return getCallerSource(getSyntaxCaller(caller));
    }

    /**
     * Callback to an R function from the internal implementation. Since this is part of the
     * implementation and not part of the user-visible execution debug handling is disabled across
     * the call.
     */
    Object callback(RFunction f, Object[] args);

    /**
     * Force a promise by slow-path evaluation.
     */
    Object forcePromise(Object val);

    /**
     * {@code RASTUtils.createNodeForValue}.
     *
     * @return and {@code RNode} for {@code value}.
     */
    Object createNodeForValue(Object value);

    /**
     * Returns the {@link ArgumentsSignature} for {@code f}.
     */
    ArgumentsSignature getArgumentsSignature(RFunction f);

    /**
     * Returns the default parameters for a builtin.
     */
    Object[] getBuiltinDefaultParameterValues(RFunction f);

    /**
     * Update the {@code name} in a {@code FunctionDefinitionNode}.
     */
    void setFunctionName(RootNode node, String name);

    TruffleVM create(ContextInfo info);

    Engine createEngine(RContext context);

    /**
     * Returns {@code true} iff {@code node} is an instance of {@code ReplacementNode}, which is not
     * visible from {@code runtime}, or {@code false} otherwise.
     */
    boolean isReplacementNode(Node node);

    /**
     * Returns {@code true} iff {@code node} is an instance of {@code FunctionDefinitionNode}, which
     * is not visible from {@code runtime}, or {@code false} otherwise.
     */
    boolean isFunctionDefinitionNode(Node node);

}
