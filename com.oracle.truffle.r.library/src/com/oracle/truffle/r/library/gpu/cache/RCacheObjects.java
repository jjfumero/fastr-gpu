package com.oracle.truffle.r.library.gpu.cache;

import com.oracle.truffle.api.RootCallTarget;

public class RCacheObjects {

    private RootCallTarget rootCallTarget;
    private String[] scopeVars;
    private Object[] lexicalScopeVars;

    private RFunctionMetadata rfunctionMetadata;

    public RCacheObjects(RootCallTarget rootCallTarget) {
        this.rootCallTarget = rootCallTarget;
    }

    public RCacheObjects(RootCallTarget rootCallTarget, String[] scopeVarsName, Object[] lexicalScopeVars) {
        this(rootCallTarget);
        this.scopeVars = scopeVarsName;
        this.lexicalScopeVars = lexicalScopeVars;
    }

    public RootCallTarget getRootCallTarget() {
        return rootCallTarget;
    }

    public String[] getScopeVars() {
        return scopeVars;
    }

    public Object[] getLexicalScopeVars() {
        return lexicalScopeVars;
    }

    public void insertRFuctionMetadata(RFunctionMetadata metadata) {
        this.rfunctionMetadata = metadata;
    }

    public RFunctionMetadata getRFunctionMetadata() {
        return this.rfunctionMetadata;
    }
}
