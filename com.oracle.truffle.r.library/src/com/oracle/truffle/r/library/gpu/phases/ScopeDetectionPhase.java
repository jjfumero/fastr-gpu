package com.oracle.truffle.r.library.gpu.phases;

import jdk.vm.ci.hotspot.HotSpotObjectConstantImpl;
import jdk.vm.ci.meta.Constant;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodePosIterator;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.phases.Phase;

public class ScopeDetectionPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        System.out.println("Running SCOPE!!!!");

        checkLoadIndexedNodes(graph);

// for (Node node : graph.getNodes()) {
//
// if (node instanceof ConstantNode) {
// System.out.println("\n ================ ");
// ConstantNode constant = (ConstantNode) node;
// Constant value = constant.getValue();
//
// System.out.println(node);
// System.out.println(value.getClass());
//
// if (value instanceof HotSpotObjectConstantImpl) {
// // I can not do this because HOtSpotObjectConstantImpl is a final class
// System.out.println("VALUE IS HotSpotObjectConstantImpl !!!!!!!!!!!");
// HotSpotObjectConstantImpl constantValue = (HotSpotObjectConstantImpl) value;
// Object object = constantValue.object();
//
// System.out.println("\t -- OBJECT: " + object);
//
// }
//
// String valueString = constant.asJavaConstant().toValueString();
//
// // Parse the String
//
// if (constant.asJavaConstant().getJavaKind() == JavaKind.Object) {
//
// }
// }
// }
    }

    private static void analyseConstant(Constant value) {
        if (value instanceof HotSpotObjectConstantImpl) {
            // I can not do this because HOtSpotObjectConstantImpl is a final class
            System.out.println("VALUE IS HotSpotObjectConstantImpl !!!!!!!!!!!");
            HotSpotObjectConstantImpl constantValue = (HotSpotObjectConstantImpl) value;
            Object object = constantValue.object();

            System.out.println("\t -- OBJECT: " + object);

            if (object.getClass() == double[].class) {
                System.out.println("ArrayScope detected");
                // System.out.println(Arrays.toString((double[]) object));
            }
        }
    }

    private static void checkLoadIndexedNodes(StructuredGraph graph) {

        for (Node node : graph.getNodes()) {

            if (node instanceof LoadIndexedNode) {
                LoadIndexedNode loadIndexed = (LoadIndexedNode) node;
                NodePosIterator iterator = loadIndexed.inputs().iterator();

                while (iterator.hasNext()) {
                    Node scopeNode = iterator.next();
                    if (scopeNode instanceof ConstantNode) {
                        analyseConstant(((ConstantNode) scopeNode).getValue());
                    }
                }
            }
        }
    }
}
