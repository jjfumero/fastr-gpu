package com.oracle.truffle.r.library.gpu.phases;

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstantImpl;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.datastructures.common.TypeFactory;

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
    @SuppressWarnings("rawtypes") private PArray parray;
    private JavaKind kind;
    private int size;

    @Override
    protected void run(StructuredGraph graph) {
        checkLoadIndexedNodes(graph);
    }

    @SuppressWarnings("unchecked")
    private void createPArray(Object object) {
        this.rawData = object;
        if (kind == JavaKind.Double) {
            double[] v = (double[]) object;
            parray = new PArray<>(size, TypeFactory.Double());
            for (int i = 0; i < size; i++) {
                parray.put(i, v[i]);
            }
        } else if (kind == JavaKind.Int) {
            parray = new PArray<>(size, TypeFactory.Integer());
            int[] v = (int[]) object;
            for (int i = 0; i < size; i++) {
                parray.put(i, v[i]);
            }
        } else if (kind == JavaKind.Boolean) {
            parray = new PArray<>(size, TypeFactory.Boolean());
            boolean[] v = (boolean[]) object;
            for (int i = 0; i < size; i++) {
                parray.put(i, v[i]);
            }
        }
    }

    public JavaKind getJavaKind() {
        return kind;
    }

    public PArray<?> getPArray() {
        return this.parray;
    }

    public Object getData() {
        return rawData;
    }

    private JavaKind getTypeOfArray(Object object) {
        if (object.getClass() == double[].class) {
            size = ((double[]) object).length;
            kind = JavaKind.Double;
            return JavaKind.Double;
        } else if (object.getClass() == int[].class) {
            size = ((int[]) object).length;
            kind = JavaKind.Int;
            return JavaKind.Int;
        } else if (object.getClass() == boolean[].class) {
            size = ((boolean[]) object).length;
            kind = JavaKind.Boolean;
            return JavaKind.Boolean;
        } else {
            size = -1;
            kind = JavaKind.Illegal;
            return JavaKind.Illegal;
        }
    }

    private void analyseConstant(Constant value) {

        if (value instanceof HotSpotObjectConstant) {
            HotSpotObjectConstantImpl constantValue = (HotSpotObjectConstantImpl) value;
            Object object = constantValue.object();
            JavaKind kind = getTypeOfArray(object);

            if (kind != JavaKind.Illegal) {
                createPArray(object);
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
