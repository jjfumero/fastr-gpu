package com.oracle.truffle.r.library.gpu.phases;

import java.util.ArrayList;

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstantImpl;
import jdk.vm.ci.meta.Constant;
import uk.ac.ed.datastructures.common.PArray;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodePosIterator;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.phases.Phase;

/**
 * The array constant node "always" (based on experimentation) is the input for
 * {@link LoadIndexedNode}. One way of detecting is to analyse the inputs for LoadIndexNode. If it
 * receives a @{@link ConstantNode} with its value as an array of primitive data types, we can
 * handle it in the code generation for GPUs and manage it as {@link PArray}.
 *
 */
public class ScopeDetectionPhase extends Phase {

    private ArrayList<Object> rawData;

    @Override
    protected void run(StructuredGraph graph) {
        if (rawData == null) {
            rawData = new ArrayList<>();
        }
        checkLoadIndexedNodes(graph);
    }

    public Object[] getDataArray() {
        return rawData.toArray();
    }

    private void analyseConstant(Constant value) {
        if (value instanceof HotSpotObjectConstant) {
            HotSpotObjectConstantImpl constantValue = (HotSpotObjectConstantImpl) value;
            Object object = constantValue.object();
            rawData.add(object);
        }
    }

    private void checkLoadIndexedNodes(StructuredGraph graph) {
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
