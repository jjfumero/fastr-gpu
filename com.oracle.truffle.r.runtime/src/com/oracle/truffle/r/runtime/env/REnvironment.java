/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.env;

import java.util.*;
import java.util.regex.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RError.RErrorException;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.env.frame.*;

/**
 * Denotes an R {@code environment}.
 *
 * Abstractly, environments consist of a frame (collection of named objects), and a pointer to an
 * enclosing environment.
 *
 * R environments can be named or unnamed. {@code base} is an example of a named environment.
 * Environments associated with function invocations are unnamed. The {@code environmentName}
 * builtin returns "" for an unnamed environment. However, unnamed environments print using a unique
 * numeric id in the place where the name would appear for a named environment. This is finessed
 * using the {@link #getPrintNameHelper} method. Further, environments on the {@code search} path
 * return a yet different name in the result of {@code search}, e.g. ".GlobalEnv", "package:base",
 * which is handled via {@link #getSearchName()}. Finally, environments can be given names using the
 * {@code attr} function, and (then) they print differently again. Of the default set of
 * environments, only "Autoloads" has a {@code name} attribute.
 * <p>
 * Environments can also be locked preventing any bindings from being added or removed. N.B. the
 * empty environment can't be assigned to but is not locked (see GnuR). Further, individual bindings
 * within an environment can be locked, although they can be removed unless the environment is also
 * locked.
 * <p>
 * Environments are used for many different things in R, including something close to a
 * {@link java.util.Map} created in R code using the {@code new.env} function. This is the only case
 * where the {@code size} parameter is specified. All the other instances of environments are
 * implicitly created by the virtual machine, for example, on function call.
 * <p>
 * The different kinds of environments are implemented as subclasses. The variation in behavior
 * regarding access to the "frame" is handled by delegation to an instance of
 * {@link REnvFrameAccess}. Conceptually, variables are searched for by starting in a given
 * environment and searching backwards through the "parent" chain. In practice, variables are
 * accessed in the Truffle environment using {@link Frame} instances which may, in some cases such
 * as compiled code, not even exist as actual objects. Therefore, we have to keep the name lookup in
 * the two worlds in sync. This is an issue during initialization, and when a new environment is
 * attached, cf. {@link #attach}.
 * <p>
 * Packages have three associated environments, "package:xxx", "imports:xxx" and "namespace:xxx",
 * for package "xxx". The {@code base} package is a special case in that it does not have an
 * "imports" environment. The parent of "package:base" is the empty environment, but the parent of
 * "namespace:base" is the global environment.
 *
 * Whereas R types generally use value semantics environments do not; they have reference semantics.
 * In particular in FastR, there is at exactly one environment created for any package frame and at
 * most one for a function frame, allowing equality to be tested using {@code ==}.
 *
 * Multi-tenancy (multiple {@link RContext}s).
 * <p>
 * The logic for implementing the three different forms of
 * {@link com.oracle.truffle.r.runtime.RContext.Kind} is encapsulated in the {@link #createContext}
 * method.
 */
public abstract class REnvironment extends RAttributeStorage implements RAttributable, RTypedValue {

    public interface ContextState extends RContext.ContextState {
        MaterializedFrame getGlobalFrame();

        REnvironment getGlobalEnv();

        Base getBaseEnv();

        REnvironment getNamespaceRegistry();

        REnvironment.SearchPath getSearchPath();

    }

    private static class ContextStateImpl implements ContextState {
        private SearchPath searchPath;
        private final MaterializedFrame globalFrame;
        private Base baseEnv;
        private REnvironment namespaceRegistry;
        private MaterializedFrame parentGlobalFrame; // SHARED_PARENT_RW only

        ContextStateImpl(MaterializedFrame globalFrame, SearchPath searchPath) {
            this.globalFrame = globalFrame;
            this.searchPath = searchPath;
        }

        ContextStateImpl(MaterializedFrame globalFrame, SearchPath searchPath, Base baseEnv, REnvironment namespaceRegistry) {
            this(globalFrame, searchPath);
            this.baseEnv = baseEnv;
            this.namespaceRegistry = namespaceRegistry;
        }

        public REnvironment getGlobalEnv() {
            return RArguments.getEnvironment(globalFrame);
        }

        public MaterializedFrame getGlobalFrame() {
            return globalFrame;
        }

        public SearchPath getSearchPath() {
            return searchPath;
        }

        public Base getBaseEnv() {
            return baseEnv;
        }

        public REnvironment getNamespaceRegistry() {
            return namespaceRegistry;
        }

        private void setSearchPath(SearchPath searchPath) {
            this.searchPath = searchPath;
        }

        private void setBaseEnv(Base baseEnv) {
            this.baseEnv = baseEnv;
        }

        private void setNamespaceRegistry(REnvironment namespaceRegistry) {
            this.namespaceRegistry = namespaceRegistry;
        }

    }

    /**
     * Since {@REnvironment} is a Truffle value and already has non-zero-arg
     * constructors, we define this class as the mechanism for creating the context-specific state.
     */
    public static class ClassStateFactory implements RContext.StateFactory {
        @Override
        public ContextState newContext(RContext context, Object... objects) {
            return createContext(context, (MaterializedFrame) objects[0]);
        }

        @Override
        public void beforeDestroy(RContext context, RContext.ContextState state) {
            beforeDestroyContext(context, state);
        }
    }

    public static class PutException extends RErrorException {
        private static final long serialVersionUID = 1L;

        @TruffleBoundary
        public PutException(RError.Message msg, Object... args) {
            super(msg, args);
        }
    }

    public static class SearchPath {
        private final ArrayList<REnvironment> list = new ArrayList<>();

        void add(REnvironment env) {
            list.add(env);
        }

        void add(int index, REnvironment env) {
            list.add(index, env);
        }

        int size() {
            return list.size();
        }

        REnvironment get(int index) {
            return list.get(index);
        }

        void remove(int index) {
            list.remove(index);
        }

        void updateGlobal(Global globalEnv) {
            list.set(0, globalEnv);
        }

    }

    private static final REnvFrameAccess defaultFrameAccess = new REnvFrameAccessBindingsAdapter();

    private static final String UNNAMED = new String("");
    private static final String NAME_ATTR_KEY = "name";

    private static final Empty emptyEnv = new Empty();

    /**
     * The environments returned by the R {@code search} function.
     */
    // private static ArrayList<REnvironment> searchPath;

    protected REnvironment parent;
    private final String name;
    protected final REnvFrameAccess frameAccess;
    private boolean locked;

    public RType getRType() {
        return RType.Environment;
    }

    /**
     * Value returned by {@code emptyenv()}.
     */
    public static Empty emptyEnv() {
        return emptyEnv;
    }

    /**
     * Value returned by {@code globalenv()}.
     */
    public static REnvironment globalEnv() {
        return RContext.getREnvironmentState().getGlobalEnv();
    }

    /**
     * Returns {@code true} iff {@code frame} is that associated with {@code env}. N.B. The
     * environment associated with the frame may be {@code null} as {@link Function} environments
     * are created lazily. However, we maintain the invariant that whenever a {@link Function}
     * environment is created the value is stored in the associated frame. Therefore {@code env}
     * could never match lazy {@code null}.
     */
    private static boolean isFrameForEnv(Frame frame, REnvironment env) {
        return RArguments.getEnvironment(frame) == env;
    }

    /**
     * Check whether the given frame is indeed the frame stored in the global environment.
     */
    public static boolean isGlobalEnvFrame(Frame frame) {
        return isFrameForEnv(frame, RContext.getREnvironmentState().getGlobalEnv());
    }

    /**
     * Value returned by {@code baseenv()}. This is the "package:base" environment.
     */
    public static REnvironment baseEnv() {
        Base baseEnv = RContext.getREnvironmentState().getBaseEnv();
        assert baseEnv != null;
        return baseEnv;
    }

    /**
     * Value set in {@code .baseNameSpaceEnv} variable. This is the "namespace:base" environment.
     */
    public static REnvironment baseNamespaceEnv() {
        Base baseEnv = RContext.getREnvironmentState().getBaseEnv();
        assert baseEnv != null;
        return baseEnv.getNamespace();
    }

    /**
     * Invoked on startup to setup the {@code #baseEnv}, {@code namespaceRegistry} and package
     * search path.
     *
     * The base "package" is special, it has no "imports" and the parent of its associated namespace
     * is {@link #globalEnv}. Unlike other packages, there is no difference between the bindings in
     * "package:base" and its associated namespace. The way this is implemented in FastR is that the
     * underlying {@link MaterializedFrame} is shared. The {@link #frameAccess} value for
     * "namespace:base" refers to {@link NSBaseMaterializedFrame}, which delegates all its
     * operations to {@code baseFrame}, but it's "enclosingFrame" field in {@link RArguments}
     * differs, referring to {@code globalFrame}, as required by the R spec.
     */
    public static void baseInitialize(MaterializedFrame baseFrame, MaterializedFrame initialGlobalFrame) {
        // TODO if namespaceRegistry is ever used in an eval an internal env won't suffice.
        REnvironment namespaceRegistry = RDataFactory.createInternalEnv();
        ContextStateImpl state = (ContextStateImpl) RContext.getREnvironmentState();
        state.setNamespaceRegistry(namespaceRegistry);
        Base baseEnv = new Base(baseFrame, initialGlobalFrame);
        namespaceRegistry.safePut("base", baseEnv.namespaceEnv);

        Global globalEnv = new Global(baseEnv, initialGlobalFrame);
        baseEnv.namespaceEnv.parent = globalEnv;
        state.setBaseEnv(baseEnv);
        state.setSearchPath(initSearchList(globalEnv));
    }

    /**
     * {@link RContext} creation, with {@code globalFrame}. If this is a {@code SHARE_NOTHING}
     * context we only create the minimal search path with no packages as the package loading is
     * handled by the engine. For a {@code SHARE_PARENT_RW} context, we keep the existing search
     * path, just replacing the {@code globalenv} component. For a {@code SHARE_PARENT_RO} context
     * we make shallow copies of the package environments.
     *
     * N.B.Calling {@link RContext#getREnvironmentState()} accesses the new, as yet uninitialized
     * {@link ContextStateImpl} object
     */
    private static ContextState createContext(RContext context, MaterializedFrame globalFrame) {
        switch (context.getKind()) {
            case SHARE_PARENT_RW: {
                /*
                 * To share the existing package structure, we create the new globalEnv with the
                 * parent of the previous global env. Then we create a copy of the SearchPath and
                 * patch the global entry.
                 */
                ContextStateImpl parentState = (ContextStateImpl) context.getParent().getThisContextState(RContext.ClassStateKind.REnvironment);
                Base parentBaseEnv = parentState.getBaseEnv();
                NSBaseMaterializedFrame nsBaseFrame = (NSBaseMaterializedFrame) parentBaseEnv.namespaceEnv.frameAccess.getFrame();
                MaterializedFrame prevGlobalFrame = RArguments.getEnclosingFrame(nsBaseFrame);

                Global prevGlobalEnv = (Global) RArguments.getEnvironment(prevGlobalFrame);
                nsBaseFrame.updateGlobalFrame(globalFrame);
                Global newGlobalEnv = new Global(prevGlobalEnv.parent, globalFrame);
                SearchPath searchPath = initSearchList(prevGlobalEnv);
                searchPath.updateGlobal(newGlobalEnv);
                parentState.getBaseEnv().safePut(".GlobalEnv", newGlobalEnv);
                ContextStateImpl result = new ContextStateImpl(globalFrame, searchPath, parentBaseEnv, parentState.getNamespaceRegistry());
                result.parentGlobalFrame = prevGlobalFrame;
                return result;
            }

            case SHARE_PARENT_RO: {
                /* We make shallow copies of all the default package environments in the parent */
                ContextStateImpl parentState = (ContextStateImpl) context.getParent().getThisContextState(RContext.ClassStateKind.REnvironment);
                SearchPath parentSearchPath = parentState.getSearchPath();
                // clone all the environments below global from the parent
                REnvironment e = parentSearchPath.get(1).cloneEnv(globalFrame);
                // create the new Global with clone top as parent
                Global newGlobalEnv = new Global(e, globalFrame);
                // create new namespaceRegistry and populate it while locating "base"
                REnvironment newNamespaceRegistry = RDataFactory.createInternalEnv();
                Base newBaseEnv = null;
                while (e != emptyEnv) {
                    if (e instanceof Base) {
                        newBaseEnv = (Base) e;
                    }
                    e = e.parent;
                }
                assert newBaseEnv != null;
                copyNamespaceRegistry(parentState.namespaceRegistry, newNamespaceRegistry);
                newNamespaceRegistry.safePut("base", newBaseEnv.namespaceEnv);
                newBaseEnv.safePut(".GlobalEnv", newGlobalEnv);
                SearchPath newSearchPath = initSearchList(newGlobalEnv);
                return new ContextStateImpl(globalFrame, newSearchPath, newBaseEnv, newNamespaceRegistry);
            }

            case SHARE_NOTHING: {
                // SHARE_NOTHING: baseInitialize takes care of everything
                return new ContextStateImpl(globalFrame, new SearchPath());
            }

            default:
                throw RInternalError.shouldNotReachHere();
        }
    }

    private static void beforeDestroyContext(RContext context, RContext.ContextState state) {
        switch (context.getKind()) {
            case SHARE_PARENT_RW: {
                /*
                 * Since we updated the parent's baseEnv with the new .GlobalEnv value we need to
                 * restore that and the frame in NSBaseMaterializedFrame.
                 */
                MaterializedFrame parentGlobalFrame = ((ContextStateImpl) state).parentGlobalFrame;
                Global parentGlobalEnv = (Global) RArguments.getEnvironment(parentGlobalFrame);
                ContextStateImpl parentState = (ContextStateImpl) context.getParent().getThisContextState(RContext.ClassStateKind.REnvironment);
                NSBaseMaterializedFrame nsBaseFrame = (NSBaseMaterializedFrame) parentState.baseEnv.namespaceEnv.frameAccess.getFrame();
                nsBaseFrame.updateGlobalFrame(parentGlobalFrame);
                parentState.baseEnv.safePut(".GlobalEnv", parentGlobalEnv);
                break;
            }

            default:
                // nothing to do
        }

    }

    private static SearchPath initSearchList(Global globalEnv) {
        SearchPath searchPath = new SearchPath();
        REnvironment env = globalEnv;
        do {
            searchPath.add(env);
            env = env.parent;
        } while (env != emptyEnv);
        return searchPath;
    }

    /**
     * Clone an environment for a {@code SHARED_CODE} context. {@link Base} overrides the method,
     * which is why we pass {@code globalFrame} as it needs it for it's creation.
     */
    protected REnvironment cloneEnv(MaterializedFrame globalFrame) {
        REnvironment parentClone = parent;
        if (parent != emptyEnv) {
            parentClone = parent.cloneEnv(globalFrame);
        }
        // N.B. Base overrides this method, so we only get here for package environments
        REnvironment newEnv = RDataFactory.createNewEnv(parentClone, getName());
        if (attributes != null) {
            newEnv.attributes = attributes.copy();
        }
        copyBindings(newEnv);
        return newEnv;
    }

    private static void copyNamespaceRegistry(REnvironment parent, REnvironment child) {
        RStringVector bindings = parent.ls(true, null, false);
        for (int i = 0; i < bindings.getLength(); i++) {
            String name = bindings.getDataAt(i);
            if (name.equals("base")) {
                continue;
            }
            Object value = parent.get(name);
            REnvironment parentNamespace = (REnvironment) value;
            assert parentNamespace.isNamespaceEnv();
            REnvironment newNamespace = RDataFactory.createInternalEnv();
            parentNamespace.copyBindings(newNamespace);
            child.safePut(name, newNamespace);
        }
    }

    /**
     * Copies the bindings from {@code this} environment to {@code newEnv}, recursively copying any
     * bindings are are {@link REnvironment}s.
     */
    protected void copyBindings(REnvironment newEnv) {
        RStringVector bindings = ls(true, null, false);
        for (int i = 0; i < bindings.getLength(); i++) {
            String binding = bindings.getDataAt(i);
            Object value = get(binding);
            newEnv.safePut(binding, value);
        }
    }

    /**
     * Data for the {@code search} function.
     */
    public static String[] searchPath() {
        SearchPath searchPath = RContext.getREnvironmentState().getSearchPath();
        String[] result = new String[searchPath.size()];
        for (int i = 0; i < searchPath.size(); i++) {
            REnvironment env = searchPath.get(i);
            result[i] = env.getSearchName();
        }
        return result;
    }

    /**
     * Lookup an environment by name on the search path.
     *
     * @param name the name as it would appear in R the {@code search} function.
     * @return the environment or {@code null} if not found.
     */
    public static REnvironment lookupOnSearchPath(String name) {
        SearchPath searchPath = RContext.getREnvironmentState().getSearchPath();
        int i = lookupIndexOnSearchPath(name);
        return i <= 0 ? null : searchPath.get(i - 1);
    }

    /**
     * Lookup the index of an environment by name on the search path.
     *
     * @param name the name as it would appear in R the {@code search} function.
     * @return the index (1-based) or {@code 0} if not found.
     */
    public static int lookupIndexOnSearchPath(String name) {
        SearchPath searchPath = RContext.getREnvironmentState().getSearchPath();
        for (int i = 0; i < searchPath.size(); i++) {
            REnvironment env = searchPath.get(i);
            String searchName = env.getSearchName();
            if (searchName.equals(name)) {
                return i + 1;
            }
        }
        return 0;
    }

    public static REnvironment getNamespaceRegistry() {
        return RContext.getREnvironmentState().getNamespaceRegistry();
    }

    public static void registerNamespace(String name, REnvironment env) {
        RContext.getREnvironmentState().getNamespaceRegistry().safePut(name, env);
    }

    /**
     * Get the registered {@code namespace} environment {@code name}, or {@code null} if not found.
     * N.B. The package loading code in {@code namespace.R} uses a {code new.env} environment for a
     * namespace.
     */
    public static REnvironment getRegisteredNamespace(String name) {
        return (REnvironment) RContext.getREnvironmentState().getNamespaceRegistry().get(name);
    }

    /**
     * Attach (insert) an environment as position {@code pos} in the search path. TODO handle
     * packages
     *
     * @param pos position for insert, {@code pos >= 2}. As per GnuR, values beyond the index of
     *            "base" are truncated to the index before "base".
     */
    public static void attach(int pos, REnvironment env) {
        assert pos >= 2;
        // N.B. pos is 1-based
        int bpos = pos - 1;
        SearchPath searchPath = RContext.getREnvironmentState().getSearchPath();
        if (bpos > searchPath.size() - 1) {
            bpos = searchPath.size() - 1;
        }
        // Insert in the REnvironment search path, adjusting the parent fields appropriately
        // In the default case (pos == 2), envAbove is the Global env
        REnvironment envAbove = searchPath.get(bpos - 1);
        REnvironment envBelow = searchPath.get(bpos);
        env.parent = envBelow;
        envAbove.parent = env;
        searchPath.add(bpos, env);
        // Now must adjust the Frame world so that unquoted variable lookup works
        MaterializedFrame aboveFrame = envAbove.frameAccess.getFrame();
        MaterializedFrame envFrame = env.getFrame();
        RArguments.attachFrame(aboveFrame, envFrame);
    }

    public static class DetachException extends RErrorException {
        private static final long serialVersionUID = 1L;

        DetachException(RError.Message msg, Object... args) {
            super(msg, args);
        }
    }

    /**
     * Detach the environment at search position {@code pos}.
     *
     * @return the {@link REnvironment} that was detached.
     */
    public static REnvironment detach(int pos) throws DetachException {
        SearchPath searchPath = RContext.getREnvironmentState().getSearchPath();
        if (pos == searchPath.size()) {
            detachException(RError.Message.ENV_DETACH_BASE);
        }
        if (pos <= 0 || pos >= searchPath.size()) {
            detachException(RError.Message.INVALID_POS_ARGUMENT);
        }
        assert pos != 1; // explicitly checked in caller
        // N.B. pos is 1-based
        int bpos = pos - 1;
        REnvironment envAbove = searchPath.get(bpos - 1);
        REnvironment envToRemove = searchPath.get(bpos);
        envAbove.parent = envToRemove.parent;
        searchPath.remove(bpos);
        MaterializedFrame aboveFrame = envAbove.frameAccess.getFrame();
        RArguments.detachFrame(aboveFrame);
        if (envToRemove.frameAccess instanceof REnvMapFrameAccess) {
            ((REnvMapFrameAccess) envToRemove.frameAccess).detach();
        }
        return envToRemove;
    }

    @TruffleBoundary
    private static void detachException(RError.Message message) throws DetachException {
        throw new DetachException(message);
    }

    /**
     * Specifically for {@code ls()}, we don't care about the parent, as the use is transient.
     */
    public static REnvironment createLsCurrent(MaterializedFrame frame) {
        Function result = new Function(null, frame);
        return result;
    }

    /**
     * Converts a {@link Frame} to an {@link REnvironment}, which necessarily requires the frame to
     * be materialized.
     */
    public static REnvironment frameToEnvironment(MaterializedFrame frame) {
        REnvironment env = RArguments.getEnvironment(frame);
        if (env == null) {
            if (RArguments.getFunction(frame) == null) {
                throw RInternalError.shouldNotReachHere();
            }
            env = createEnclosingEnvironments(frame);
        }
        return env;
    }

    /**
     * This chain can be followed back to whichever "base" (i.e. non-function) environment the
     * outermost function was defined in, e.g. "global" or "base". The purpose of this method is to
     * create an analogous lexical parent chain of {@link Function} instances with the correct
     * {@link MaterializedFrame}.
     */
    @TruffleBoundary
    public static REnvironment createEnclosingEnvironments(MaterializedFrame frame) {
        REnvironment env = RArguments.getEnvironment(frame);
        if (env == null) {
            // parent is the env of the enclosing frame
            env = REnvironment.Function.create(createEnclosingEnvironments(RArguments.getEnclosingFrame(frame)), frame);
        }
        return env;
    }

    /**
     * Convert an {@link RList} to an {@link REnvironment}, which is needed in several builtins,
     * e.g. {@code substitute}.
     */
    @TruffleBoundary
    public static REnvironment createFromList(RAttributeProfiles attrProfiles, RList list, REnvironment parent) {
        REnvironment result = RDataFactory.createNewEnv(parent, null, false, 0);
        RStringVector names = list.getNames(attrProfiles);
        for (int i = 0; i < list.getLength(); i++) {
            try {
                result.put(names.getDataAt(i), list.getDataAt(i));
            } catch (PutException ex) {
                throw RError.error((SourceSection) null, ex);
            }
        }
        return result;
    }

    // END of static methods

    private static final RStringVector ENVIRONMENT = RDataFactory.createStringVectorFromScalar(RType.Environment.getName());

    @Override
    public RStringVector getClassAttr(RAttributeProfiles attrProfiles) {
        RStringVector v = RAttributable.super.getClassAttr(attrProfiles);
        if (v == null) {
            return ENVIRONMENT;
        } else {
            return v;
        }
    }

    private static final String NAMESPACE_KEY = ".__NAMESPACE__.";

    /**
     * GnuR creates {@code Namespace} environments in {@code namespace.R} using {@code new.env} and
     * identifies them with the special element {@code .__NAMESPACE__.} which points to another
     * environment with a {@code spec} element. N.B. the {@code base} namespace does <b>not</b> have
     * a {@code .__NAMESPACE__.} entry.
     */
    public boolean isNamespaceEnv() {
        if (this instanceof BaseNamespace) {
            return true;
        } else {
            RStringVector spec = getNamespaceSpec();
            return spec != null;
        }
    }

    /**
     * If this is not a "package" environment return "this", otherwise return the associated
     * "namespace" env.
     */
    public REnvironment getPackageNamespaceEnv() {
        if (this == RContext.getREnvironmentState().getBaseEnv()) {
            return ((Base) this).namespaceEnv;
        }
        String envName = getName();
        if (envName.startsWith("package:")) {
            return REnvironment.getRegisteredNamespace(envName.replace("package:", ""));
        } else {
            return this;
        }
    }

    /**
     * Return the "spec" attribute of the "info" env in a namespace or {@code null} if not found.
     */
    public RStringVector getNamespaceSpec() {
        Object value = frameAccess.get(NAMESPACE_KEY);
        if (value instanceof REnvironment) {
            REnvironment info = (REnvironment) value;
            Object spec = info.frameAccess.get("spec");
            if ((spec != null) && spec instanceof RStringVector) {
                RStringVector infoVec = (RStringVector) spec;
                if (infoVec.getLength() > 0) {
                    return infoVec;
                }
            }
        }
        return null;
    }

    // end of static members

    /**
     * The basic constructor; just assigns the essential fields.
     */
    private REnvironment(REnvironment parent, String name, REnvFrameAccess frameAccess) {
        this.parent = parent;
        this.name = name;
        this.frameAccess = frameAccess;
    }

    /**
     * An environment associated with an already materialized frame.
     */
    private REnvironment(REnvironment parent, String name, MaterializedFrame frame) {
        this(parent, name, new REnvTruffleFrameAccess(frame));
        // Associate frame with the environment
        RArguments.setEnvironment(frame, this);
    }

    public REnvironment getParent() {
        return parent;
    }

    /**
     * Explicity set the parent of an environment. TODO Change the enclosingFrame of (any)
     * associated Truffle frame
     */
    public void setParent(REnvironment env) {
        parent = env;
    }

    /**
     * The "simple" name of the environment. This is the value returned by the R
     * {@code environmentName} function.
     */
    public String getName() {
        String attrName = attributes == null ? null : (String) RRuntime.asString(attributes.get(NAME_ATTR_KEY));
        return attrName != null ? attrName : name;
    }

    /**
     * The "print" name of an environment, i.e. what is output for {@code print(env)}.
     */
    @TruffleBoundary
    public String getPrintName() {
        return new StringBuilder("<environment: ").append(getPrintNameHelper()).append('>').toString();
    }

    protected String getPrintNameHelper() {
        String attrName = getName();
        if (name.equals(UNNAMED) && attrName.equals(UNNAMED)) {
            /*
             * namespaces are a special case; they have no name attribute, but they print with the
             * name which is buried.
             */
            RStringVector spec = getNamespaceSpec();
            if (spec != null) {
                return "namespace:" + spec.getDataAt(0);
            } else {
                return String.format("%#x", hashCode());
            }
        } else {
            return attrName;
        }
    }

    /**
     * Name returned by the {@code search()} function. The default is just the simple name, but
     * globalenv() is different.
     */
    protected String getSearchName() {
        String result = getName();
        return result;
    }

    /**
     * Return the {@link MaterializedFrame} associated with this environment, installing one if
     * there is none in the case of {@link NewEnv} environments.
     */
    public MaterializedFrame getFrame() {
        MaterializedFrame envFrame = frameAccess.getFrame();
        if (envFrame == null) {
            envFrame = getMaterializedFrame(this);
        }
        return envFrame;
    }

    /**
     * Ensures that {@code env} and all its parents have a {@link MaterializedFrame}. Used for
     * {@link NewEnv} environments that only need frames when they are used in {@code eval} etc.
     */
    @TruffleBoundary
    private static MaterializedFrame getMaterializedFrame(REnvironment env) {
        MaterializedFrame envFrame = env.frameAccess.getFrame();
        if (envFrame == null && env.parent != null) {
            MaterializedFrame parentFrame = getMaterializedFrame(env.parent);
            envFrame = new REnvMaterializedFrame((UsesREnvMap) env);
            RArguments.setEnclosingFrame(envFrame, parentFrame);
            if (parentFrame == null) {
                assert env.parent == emptyEnv;
                parentFrame = globalEnv().getFrame();
            }
        }
        return envFrame;
    }

    public void lock(boolean bindings) {
        locked = true;
        if (bindings) {
            frameAccess.lockBindings();
        }
    }

    public boolean isLocked() {
        return locked;
    }

    public Object get(String key) {
        return frameAccess.get(key);
    }

    public void put(String key, Object value) throws PutException {
        if (locked) {
            // if the binding exists already, can try to update it
            if (frameAccess.get(key) == null) {
                throw new PutException(RError.Message.ENV_ADD_BINDINGS);
            }
        }
        frameAccess.put(key, value);
    }

    public void safePut(String key, Object value) {
        try {
            put(key, value);
        } catch (PutException ex) {
            Utils.fail("exception in safePut");
        }
    }

    public void rm(String key) throws PutException {
        if (locked) {
            throw new PutException(RError.Message.ENV_REMOVE_BINDINGS);
        }
        frameAccess.rm(key);
    }

    /**
     * Explicit search for a function {@code name}; used in startup sequence.
     *
     * @return the value of the function or {@code null} if not found.
     */
    public Object findFunction(String varName) {
        REnvironment env = this;
        while (env != emptyEnv) {
            Object value = env.get(varName);
            if (value != null && (value instanceof RFunction || value instanceof RPromise)) {
                return value;
            }
            env = env.parent;
        }
        return null;
    }

    public RStringVector ls(boolean allNames, Pattern pattern, boolean sorted) {
        return frameAccess.ls(allNames, pattern, sorted);
    }

    public void lockBinding(String key) {
        frameAccess.lockBinding(key);
    }

    public void unlockBinding(String key) {
        frameAccess.unlockBinding(key);

    }

    public boolean bindingIsLocked(String key) {
        return frameAccess.bindingIsLocked(key);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return getPrintName();
    }

    private static final class BaseNamespace extends REnvironment {
        private BaseNamespace(REnvironment parent, String name, REnvFrameAccess frameAccess) {
            super(parent, name, frameAccess);
            RArguments.setEnvironment(frameAccess.getFrame(), this);
        }

        @Override
        protected String getPrintNameHelper() {
            return "namespace:" + getName();
        }
    }

    private static final class Base extends REnvironment {
        private final BaseNamespace namespaceEnv;

        private Base(MaterializedFrame baseFrame, MaterializedFrame globalFrame) {
            super(emptyEnv, "base", baseFrame);
            /*
             * We create the NSBaseMaterializedFrame using globalFrame as the enclosing frame. The
             * namespaceEnv parent field will change to globalEnv after the latter is created
             */
            REnvFrameAccess baseFrameAccess = new REnvTruffleFrameAccess(new NSBaseMaterializedFrame(baseFrame, globalFrame));
            this.namespaceEnv = new BaseNamespace(emptyEnv, "base", baseFrameAccess);
        }

        @Override
        public void rm(String key) throws PutException {
            throw new PutException(RError.Message.ENV_REMOVE_VARIABLES, getPrintNameHelper());
        }

        @Override
        protected String getSearchName() {
            return "package:base";
        }

        public BaseNamespace getNamespace() {
            return namespaceEnv;
        }

        @Override
        protected REnvironment cloneEnv(MaterializedFrame globalFrame) {
            Base newBase = new Base(RRuntime.createNonFunctionFrame().materialize(), globalFrame);
            this.copyBindings(newBase);
            return newBase;
        }
    }

    /**
     * The users workspace environment (so called global). The parent depends on the set of default
     * packages loaded.
     */
    public static final class Global extends REnvironment {

        static final String SEARCHNAME = ".GlobalEnv";

        private Global(REnvironment parent, MaterializedFrame frame) {
            super(parent, "R_GlobalEnv", frame);
            RArguments.setEnclosingFrame(frame, parent.getFrame());
        }

        @Override
        protected String getSearchName() {
            return SEARCHNAME;
        }

    }

    /**
     * When a function is invoked a {@link Function} environment may be created in response to the R
     * {@code environment()} base package function, and it will have an associated frame. We hide
     * the creation of {@link Function} environments to ensure the <i>at most one>/i> invariant and
     * store the value in the frame immediately.
     */
    private static final class Function extends REnvironment {

        private Function(REnvironment parent, MaterializedFrame frame) {
            // function environments are not named
            super(parent, UNNAMED, frame);
        }

        private static Function create(REnvironment parent, MaterializedFrame frame) {
            Function result = (Function) RArguments.getEnvironment(frame);
            if (result == null) {
                result = new Function(parent, frame);
            }
            return result;
        }
    }

    public interface UsesREnvMap {
        REnvMapFrameAccess getFrameAccess();
    }

    /**
     * An environment explicitly created with, typically, {@code new.env}, but also used internally.
     * Such environments are always {@link #UNNAMED} but can later be given a name as an attribute.
     * This is the class used by the {@code new.env} function. We record but do not interpret the
     * {@code hash} input, as we always use a hashmap, for possible use by the serialization code
     * (GnuR generates different output format for hash environments).
     *
     */
    public static final class NewEnv extends REnvironment {
        private final boolean hash;
        private final int size;

        public NewEnv(REnvironment parent, MaterializedFrame frame, String name, boolean hash, int size) {
            super(parent, UNNAMED, frame);
            this.hash = hash;
            this.size = size;
            if (parent != null) {
                RArguments.setEnclosingFrame(frame, parent.getFrame());
            }
            if (name != null) {
                setAttr(NAME_ATTR_KEY, name);
            }
        }

        public boolean hashed() {
            return hash;
        }

        public int createdSize() {
            return size;
        }

    }

    /**
     * A temporary environment used to perform internal operations, e.g. {@code substitute}. Such an
     * environment must never escape into the R world as it does not support {@code eval}.
     */
    public static final class NewInternalEnv extends REnvironment implements UsesREnvMap {

        public NewInternalEnv() {
            super(null, UNNAMED, new REnvMapFrameAccess(0));
        }

        public REnvMapFrameAccess getFrameAccess() {
            return (REnvMapFrameAccess) frameAccess;
        }
    }

    /**
     * Helper function for implementations of {@link REnvFrameAccess#ls}.
     */
    public static boolean includeName(String nameToMatch, boolean allNames, Pattern pattern) {
        if (!allNames && nameToMatch.charAt(0) == '.') {
            return false;
        }
        if (pattern != null && !pattern.matcher(nameToMatch).matches()) {
            return false;
        }
        if (AnonymousFrameVariable.isAnonymous(nameToMatch)) {
            return false;
        }
        return true;
    }

    /**
     * The empty environment has no runtime state and so can be allocated statically. TODO Attempts
     * to assign should cause an R error, if not prevented in caller. TODO check.
     */
    private static final class Empty extends REnvironment {

        private Empty() {
            super(null, "R_EmptyEnv", defaultFrameAccess);
        }

        @Override
        public void put(String key, Object value) throws PutException {
            throw new PutException(RError.Message.ENV_ASSIGN_EMPTY);
        }

    }
}
