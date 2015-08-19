/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.access.vector;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.profile.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

/**
 * This node encapsulates a speculative search of the first elements in an character vector and
 * returns an integer vector with their indices.
 */
final class SearchFirstStringNode extends Node {

    private static final int[] UNINTIALIZED_CACHED_INDICES = new int[0];

    private final VectorLengthProfile targetLengthProfile = VectorLengthProfile.create();
    private final VectorLengthProfile elementsLengthProfile = VectorLengthProfile.create();
    private final ValueProfile targetClassProfile = ValueProfile.createClassProfile();
    private final ValueProfile elementsClassProfile = ValueProfile.createClassProfile();

    @Child private CompareStringNode stringEquals = CompareStringNode.createEquals();
    @Child private CompareStringNode stringStartsWith;
    @Child private CompareStringNode equalsDuplicate;

    private final NACheck elementsNACheck = NACheck.create();
    private final NACheck targetNACheck = NACheck.create();
    private final BranchProfile everFoundDuplicate = BranchProfile.create();
    private final BranchProfile seenInvalid = BranchProfile.create();

    /** Instead of using the notFoundStartIndex we use NA. */
    private final boolean useNAForNotFound;
    protected final boolean exactMatch;

    @CompilationFinal private int[] cachedIndices;

    public SearchFirstStringNode(boolean exactMatch, boolean useNAForNotFound) {
        this.exactMatch = exactMatch;
        this.useNAForNotFound = useNAForNotFound;
        if (!exactMatch) {
            stringStartsWith = CompareStringNode.createStartsWith();
        }
    }

    public RAbstractIntVector apply(RAbstractStringVector target, RAbstractStringVector elements, int notFoundStartIndex) {
        RAbstractStringVector targetProfiled = targetClassProfile.profile(target);
        RAbstractStringVector elementsProfiled = elementsClassProfile.profile(elements);

        int targetLength = targetLengthProfile.profile(targetProfiled.getLength());
        int elementsLength = elementsLengthProfile.profile(elementsProfiled.getLength());

        targetNACheck.enable(target);
        elementsNACheck.enable(elements);

        if (cachedIndices == UNINTIALIZED_CACHED_INDICES) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            cachedIndices = searchCached(targetProfiled, targetLength, elementsProfiled, elementsLength);
        }
        if (cachedIndices != null) {
            if (!isCacheValid(targetProfiled, targetLength, elementsProfiled, elementsLength, cachedIndices)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedIndices = null; // set to generic
            }
            assert sameVector(searchCached(target, targetLength, elements, elementsLength), cachedIndices);
            return RDataFactory.createIntVector(cachedIndices, true);
        }

        return searchGeneric(targetProfiled, targetLength, elementsProfiled, elementsLength, notFoundStartIndex, false);
    }

    public static SearchFirstStringNode createNode(boolean exactMatch, boolean useNAForNotFound) {
        return new SearchFirstStringNode(exactMatch, useNAForNotFound);
    }

    protected int[] searchCached(RAbstractStringVector target, int targetLength, RAbstractStringVector elements, int elementsLength) {
        if (exactMatch) {
            return (int[]) searchGeneric(target, targetLength, elements, elementsLength, -1, true).getInternalStore();
        }
        return null;
    }

    protected boolean isCacheValid(RAbstractStringVector target, int targetLength, //
                    RAbstractStringVector elements, int elementsLength, int[] cached) {
        int cachedLength = cached.length;
        if (elementsLength != cachedLength) {
            seenInvalid.enter();
            return false;
        }

        for (int i = 0; i < cachedLength; i++) {
            int cachedIndex = cached[i];
            String cachedElement = elements.getDataAt(i);

            if (elementsNACheck.check(cachedElement) || cachedElement.length() == 0) {
                seenInvalid.enter();
                return false;
            }

            int cachedTranslatedIndex = cachedIndex - 1;
            for (int j = 0; j < cachedTranslatedIndex; j++) {
                String targetString = target.getDataAt(j);
                if (!targetNACheck.check(targetString) && stringEquals.executeCompare(targetString, cachedElement)) {
                    seenInvalid.enter();
                    return false;
                }
            }
            if (cachedTranslatedIndex < targetLength) {
                String targetString = target.getDataAt(cachedTranslatedIndex);
                if (!targetNACheck.check(targetString) && !stringEquals.executeCompare(targetString, cachedElement)) {
                    seenInvalid.enter();
                    return false;
                }
            } else {
                seenInvalid.enter();
                return false;
            }
        }
        return true;

    }

    private static boolean sameVector(int[] a, int[] b) {
        if (a == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private final BranchProfile notFoundProfile = BranchProfile.create();

    protected RAbstractIntVector searchGeneric(RAbstractStringVector target, int targetLength, RAbstractStringVector elements, int elementsLength, int notFoundStartIndex, boolean nullOnNotFound) {
        int notFoundIndex = notFoundStartIndex;
        int[] indices = new int[elementsLength];
        boolean resultComplete = true;
        for (int i = 0; i < elementsLength; i++) {
            String element = elements.getDataAt(i);
            boolean isElementNA = elementsNACheck.check(element) || element.length() == 0;
            if (!isElementNA) {
                int index = findIndex(target, targetLength, element);
                if (index >= 0) {
                    indices[i] = index + 1;
                    continue;
                }
            }
            notFoundProfile.enter();
            if (nullOnNotFound) {
                return null;
            } else {
                int prevDuplicateIndex = -1;
                if (!isElementNA) {
                    prevDuplicateIndex = findFirstDuplicate(elements, element, i);
                }
                int nextIndex;
                if (prevDuplicateIndex == -1) {
                    if (useNAForNotFound) {
                        resultComplete = false;
                        nextIndex = RRuntime.INT_NA;
                    } else {
                        nextIndex = ++notFoundIndex;
                    }
                } else {
                    nextIndex = indices[prevDuplicateIndex];
                }
                indices[i] = nextIndex;
            }
        }

        return RDataFactory.createIntVector(indices, resultComplete && elements.isComplete());
    }

    private int findIndex(RAbstractStringVector target, int targetLength, String element) {
        int nonExactIndex = -1;
        for (int j = 0; j < targetLength; j++) {
            String targetValue = target.getDataAt(j);
            if (!targetNACheck.check(targetValue)) {
                if (stringEquals.executeCompare(targetValue, element)) {
                    return j;
                }
                if (!exactMatch) {
                    if (stringStartsWith.executeCompare(targetValue, element)) {
                        if (nonExactIndex == -1) {
                            nonExactIndex = j;
                        } else {
                            nonExactIndex = -2;
                        }
                    }
                }
            }
        }
        return nonExactIndex;
    }

    private int findFirstDuplicate(RAbstractStringVector elements, String element, int currentIndex) {
        if (equalsDuplicate == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            equalsDuplicate = insert(CompareStringNode.createEquals());
        }

        for (int j = 0; j < currentIndex; j++) {
            String otherElement = elements.getDataAt(j);
            if (!targetNACheck.check(otherElement) && equalsDuplicate.executeCompare(element, otherElement)) {
                everFoundDuplicate.enter();
                return j;
            }
        }
        return -1;
    }

    abstract static class CompareStringNode extends Node {

        public abstract boolean executeCompare(String target, String element);

        public static CompareStringNode createEquals() {
            return new StringEqualsNode();
        }

        public static CompareStringNode createStartsWith() {
            return new StringStartsWithNode();
        }

        private static class StringEqualsNode extends CompareStringNode {

            private final ConditionProfile identityEquals = ConditionProfile.createBinaryProfile();

            @Override
            public final boolean executeCompare(String a, String b) {
                assert a != RRuntime.STRING_NA;
                assert b != RRuntime.STRING_NA;
                if (identityEquals.profile(a == b)) {
                    return true;
                } else {
                    return a.equals(b);
                }
            }
        }

        private static class StringStartsWithNode extends CompareStringNode {

            private final ConditionProfile identityEquals = ConditionProfile.createBinaryProfile();

            @Override
            public final boolean executeCompare(String a, String b) {
                assert a != RRuntime.STRING_NA;
                assert b != RRuntime.STRING_NA;
                if (identityEquals.profile(a == b)) {
                    return true;
                } else {
                    return a.startsWith(b);
                }
            }
        }
    }

}
