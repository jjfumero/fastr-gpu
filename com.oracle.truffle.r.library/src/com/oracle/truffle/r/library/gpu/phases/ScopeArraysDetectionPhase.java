/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.library.gpu.phases;

import java.util.ArrayList;

import jdk.vm.ci.meta.Constant;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.phases.Phase;

public class ScopeArraysDetectionPhase extends Phase {

    private ArrayList<Object> rawData;

    @Override
    protected void run(StructuredGraph graph) {
        if (rawData == null) {
            rawData = new ArrayList<>();
        }

        checkLoadArrayNodes(graph);
    }

    private static void checkLoadArrayNodes(StructuredGraph graph) {

        for (Node node : graph.getNodes()) {
            if (node instanceof LoadFieldNode) {
                // inspect loadFieldNode
                LoadFieldNode loadFieldNode = (LoadFieldNode) node;

                Constant asConstant = loadFieldNode.asConstant();

                String fieldName = loadFieldNode.field().getName();
                ValueNode value = loadFieldNode.getValue();
                String stamp = value.stamp().toString();

                System.out.println("VALUE GETCLASS: " + value.getClass());
                if (value instanceof PiNode) {
                    System.out.println("THIS IS A PINODE");
                    PiNode piNode = (PiNode) value;
                    ValueNode object = piNode.object();
                    System.out.println(object);
                    if (object instanceof UnsafeLoadNode) {
                        UnsafeLoadNode unsafe = (UnsafeLoadNode) object;
                    }

                }

                if (stamp.endsWith("RDoubleVector;")) {
                    if (fieldName.equals("data")) {
                        System.out.println("SCOPE VAR!!!!!!!!");
                    }
                }

// ValueNode value = loadFieldNode.getValue();
// if (value instanceof PiNode) {
// PiNode piNode = (PiNode) value;
// System.out.println(piNode.asJavaConstant());
// System.out.println(piNode.getGuard().toString());
// }
//
// Map<Object, Object> debugProperties = loadFieldNode.getDebugProperties();
// System.out.println(debugProperties);
            }
        }
    }
}
