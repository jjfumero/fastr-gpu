package com.oracle.truffle.r.library.gpu.phases;

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstantImpl;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
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

    private Object rawData;
    private PArray<?> parray;

    @Override
    protected void run(StructuredGraph graph) {
        checkLoadIndexedNodes(graph);
    }

    private void handle(Object object) {
        this.rawData = object;

    }

    public PArray<?> getPArray() {
        return this.parray;
    }

    public Object getData() {
        return rawData;
    }

    private static JavaKind getTypeOfArray(Object object) {
        if (object.getClass() == double[].class) {
            return JavaKind.Double;
        } else if (object.getClass() == int[].class) {
            return JavaKind.Int;
        } else if (object.getClass() == boolean[].class) {
            return JavaKind.Boolean;
        } else {
            return JavaKind.Illegal;
        }
    }

    private void analyseConstant(Constant value) {

        if (value instanceof HotSpotObjectConstant) {

            HotSpotObjectConstantImpl constantValue = (HotSpotObjectConstantImpl) value;
            Object object = constantValue.object();
            JavaKind kind = getTypeOfArray(object);

            if (kind != JavaKind.Illegal) {
                // we can handle it
                handle(object);
            } else {
                throw new RuntimeException("Data type not supported as scope variable");
            }
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
