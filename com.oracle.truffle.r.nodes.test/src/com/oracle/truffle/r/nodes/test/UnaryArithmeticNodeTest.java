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
package com.oracle.truffle.r.nodes.test;

import static com.oracle.truffle.r.nodes.test.TestUtilities.*;
import static com.oracle.truffle.r.runtime.data.RDataFactory.*;
import static com.oracle.truffle.r.runtime.ops.UnaryArithmetic.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.Assume.*;

import java.util.*;

import org.junit.*;
import org.junit.experimental.theories.*;
import org.junit.runner.*;

import com.oracle.truffle.r.nodes.test.TestUtilities.NodeHandle;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.RAttributes.RAttribute;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.*;

/**
 * This test verifies white box assumptions for the arithmetic node. Please note that this node
 * should NOT verify correctness. This is done by the integration test suite.
 */
@RunWith(Theories.class)
public class UnaryArithmeticNodeTest extends BinaryVectorTest {

    @DataPoints public static final UnaryArithmeticFactory[] UNARY = ALL;

    @Theory
    public void testVectorResult(UnaryArithmeticFactory factory, RAbstractVector originalOperand) {
        RAbstractVector operand = originalOperand.copy();
        assumeThat(operand, is(not(instanceOf(RScalarVector.class))));

        Object result = executeArithmetic(factory, operand);
        Assert.assertFalse(isPrimitive(result));
        assumeThat(result, is(instanceOf(RAbstractVector.class)));
        RAbstractVector resultCast = (RAbstractVector) result;

        assertThat(resultCast.getLength(), is(equalTo(operand.getLength())));
    }

    @Theory
    public void testSharing(UnaryArithmeticFactory factory, RAbstractVector originalOperand) {
        RAbstractVector operand = originalOperand.copy();
        // sharing does not work if a is a scalar vector
        assumeThat(true, is(isShareable(operand, operand.getRType())));

        RType resultType = getArgumentType(operand);
        Object sharedResult = null;
        if (isShareable(operand, resultType)) {
            sharedResult = operand;
        }

        Object result = executeArithmetic(factory, operand);
        if (sharedResult == null) {
            Assert.assertNotSame(operand, result);
        } else {
            Assert.assertSame(sharedResult, result);
        }
    }

    private static boolean isShareable(RAbstractVector a, RType resultType) {
        if (a.getRType() != resultType) {
            // needs cast -> not shareable
            return false;
        }

        if (a instanceof RShareable) {
            if (((RShareable) a).isTemporary()) {
                return true;
            }
        }
        return false;
    }

    @Theory
    public void testCompleteness(UnaryArithmeticFactory factory, RAbstractVector originalOperand) {
        RAbstractVector operand = originalOperand.copy();
        Object result = executeArithmetic(factory, operand);

        boolean resultComplete = isPrimitive(result) ? true : ((RAbstractVector) result).isComplete();

        if (operand.getLength() == 0) {
            Assert.assertTrue(resultComplete);
        } else {
            boolean expectedComplete = operand.isComplete();
            Assert.assertEquals(expectedComplete, resultComplete);
        }
    }

    @Theory
    public void testCopyAttributes(UnaryArithmeticFactory factory, RAbstractVector originalOperand) {
        RAbstractVector operand = originalOperand.copy();
        // we have to e careful not to change mutable vectors
        RAbstractVector a = operand.copy();
        if (a instanceof RShareable) {
            if (FastROptions.NewStateTransition) {
                ((RShareable) a).incRefCount();
            } else {
                ((RShareable) a).markNonTemporary();
            }
        }

        RVector aMaterialized = a.copy().materialize();
        aMaterialized.setAttr("a", "a");
        assertAttributes(executeArithmetic(factory, aMaterialized.copy()), "a");
    }

    @Theory
    public void testPlusFolding(RAbstractVector originalOperand) {
        RAbstractVector operand = originalOperand.copy();
        assumeThat(operand, is(not(instanceOf(RScalarVector.class))));
        if (operand.getRType() == getArgumentType(operand)) {
            assertFold(true, operand, PLUS);
        } else {
            assertFold(false, operand, PLUS);
        }
    }

    @Test
    public void testSequenceFolding() {
        assertFold(true, createIntSequence(1, 3, 10), NEGATE);
        assertFold(true, createDoubleSequence(1, 3, 10), NEGATE);
        assertFold(false, createIntSequence(1, 3, 10), ROUND, FLOOR, CEILING);
        assertFold(false, createDoubleSequence(1, 3, 10), ROUND, FLOOR, CEILING);
    }

    @Theory
    public void testGeneric(UnaryArithmeticFactory factory) {
        // this should trigger the generic case
        for (RAbstractVector vector : ALL_VECTORS) {
            executeArithmetic(factory, vector.copy());
        }
    }

    private static void assertAttributes(Object value, String... keys) {
        if (!(value instanceof RAbstractVector)) {
            Assert.assertEquals(0, keys.length);
            return;
        }

        RAbstractVector vector = (RAbstractVector) value;
        Set<String> expectedAttributes = new HashSet<>(Arrays.asList(keys));

        RAttributes attributes = vector.getAttributes();
        if (attributes == null) {
            Assert.assertEquals(0, keys.length);
            return;
        }
        Set<Object> foundAttributes = new HashSet<>();
        for (RAttribute attribute : attributes) {
            foundAttributes.add(attribute.getName());
            foundAttributes.add(attribute.getValue());
        }
        Assert.assertEquals(expectedAttributes, foundAttributes);
    }

    private static RType getArgumentType(RAbstractVector operand) {
        return RType.maxPrecedence(RType.Integer, operand.getRType());
    }

    private static boolean isPrimitive(Object result) {
        return result instanceof Integer || result instanceof Double || result instanceof Byte || result instanceof RComplex;
    }

    private void assertFold(boolean expectedFold, RAbstractVector operand, UnaryArithmeticFactory... arithmetics) {
        for (int i = 0; i < arithmetics.length; i++) {
            UnaryArithmeticFactory factory = arithmetics[i];
            Object result = executeArithmetic(factory, operand);
            if (expectedFold) {
                assertThat(String.format("expected fold %s <op> ", operand), result instanceof RSequence || result == operand);
            } else {
                assertThat(String.format("expected not fold %s <op> ", operand), !(result instanceof RSequence));
            }
        }
    }

    private NodeHandle<UnaryArithmeticNode> handle;
    private UnaryArithmeticFactory currentFactory;

    @Before
    public void setUp() {
        handle = null;
    }

    @After
    public void tearDown() {
        handle = null;
    }

    private Object executeArithmetic(UnaryArithmeticFactory factory, Object operand) {
        if (handle == null || this.currentFactory != factory) {
            handle = create(factory);
            this.currentFactory = factory;
        }
        return handle.call(operand);
    }

    private static NodeHandle<UnaryArithmeticNode> create(UnaryArithmeticFactory factory) {
        return createHandle(UnaryArithmeticNodeGen.create(factory, null), //
                        (node, args) -> node.execute(args[0]));
    }

}
