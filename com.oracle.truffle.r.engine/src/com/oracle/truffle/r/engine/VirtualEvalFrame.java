package com.oracle.truffle.r.engine;

import java.util.*;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

/**
 * A "fake" {@link VirtualFrame}, to be used by {@link REngine}.eval only!
 */
public final class VirtualEvalFrame implements VirtualFrame, MaterializedFrame {

    private final MaterializedFrame originalFrame;
    private final Object[] arguments;

    private VirtualEvalFrame(MaterializedFrame originalFrame, RFunction function, SourceSection callSrc) {
        this.originalFrame = originalFrame;
        this.arguments = Arrays.copyOf(originalFrame.getArguments(), originalFrame.getArguments().length);
        RArguments.setFunction(this, function);
        RArguments.setCallSourceSection(this, callSrc);
    }

    protected static VirtualEvalFrame create(MaterializedFrame originalFrame, RFunction function, SourceSection callSrc) {
        return new VirtualEvalFrame(originalFrame, function, callSrc);
    }

    public FrameDescriptor getFrameDescriptor() {
        return originalFrame.getFrameDescriptor();
    }

    public Object[] getArguments() {
        return arguments;
    }

    /*
     * Delegates to #originalFrame
     */

    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        return originalFrame.getObject(slot);
    }

    public void setObject(FrameSlot slot, Object value) {
        originalFrame.setObject(slot, value);
    }

    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        return originalFrame.getByte(slot);
    }

    public void setByte(FrameSlot slot, byte value) {
        originalFrame.setByte(slot, value);
    }

    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        return originalFrame.getBoolean(slot);
    }

    public void setBoolean(FrameSlot slot, boolean value) {
        originalFrame.setBoolean(slot, value);
    }

    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        return originalFrame.getInt(slot);
    }

    public void setInt(FrameSlot slot, int value) {
        originalFrame.setInt(slot, value);
    }

    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        return originalFrame.getLong(slot);
    }

    public void setLong(FrameSlot slot, long value) {
        originalFrame.setLong(slot, value);
    }

    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        return originalFrame.getFloat(slot);
    }

    public void setFloat(FrameSlot slot, float value) {
        originalFrame.setFloat(slot, value);
    }

    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        return originalFrame.getDouble(slot);
    }

    public void setDouble(FrameSlot slot, double value) {
        originalFrame.setDouble(slot, value);
    }

    public Object getValue(FrameSlot slot) {
        return originalFrame.getValue(slot);
    }

    public MaterializedFrame materialize() {
        return this;
    }

    public boolean isObject(FrameSlot slot) {
        return originalFrame.isObject(slot);
    }

    public boolean isByte(FrameSlot slot) {
        return originalFrame.isByte(slot);
    }

    public boolean isBoolean(FrameSlot slot) {
        return originalFrame.isBoolean(slot);
    }

    public boolean isInt(FrameSlot slot) {
        return originalFrame.isInt(slot);
    }

    public boolean isLong(FrameSlot slot) {
        return originalFrame.isLong(slot);
    }

    public boolean isFloat(FrameSlot slot) {
        return originalFrame.isFloat(slot);
    }

    public boolean isDouble(FrameSlot slot) {
        return originalFrame.isDouble(slot);
    }
}
