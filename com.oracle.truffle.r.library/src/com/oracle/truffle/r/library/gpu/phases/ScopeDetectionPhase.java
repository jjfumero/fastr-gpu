package com.oracle.truffle.r.library.gpu.phases;

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
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
        checkLoadIndexedNodes(graph);
    }

    private static void analyseConstant(Constant value) {

        if (value instanceof HotSpotObjectConstant) {
            HotSpotObjectConstantImpl constantValue = (HotSpotObjectConstantImpl) value;
            Object object = constantValue.object();

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
