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
