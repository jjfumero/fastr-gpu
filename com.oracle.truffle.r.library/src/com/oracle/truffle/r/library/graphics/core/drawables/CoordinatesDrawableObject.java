/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (C) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (C) 1998 Ross Ihaka
 * Copyright (c) 1998--2014, The R Core Team
 * Copyright (c) 2002--2010, The R Foundation
 * Copyright (C) 2005--2006, Morten Welinder
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.library.graphics.core.drawables;

import com.oracle.truffle.r.library.graphics.core.geometry.CoordinateSystem;
import com.oracle.truffle.r.library.graphics.core.geometry.Coordinates;
import com.oracle.truffle.r.library.graphics.core.geometry.IntCoordinates;

/**
 * Denotes an object which drawing depends only from {@link Coordinates}. And automates conversion
 * from <code>srcCoordinates</code> to <code>dstCoordinates</code>.
 */
public abstract class CoordinatesDrawableObject extends DrawableObject {
    private final Coordinates srcCoordinates;

    private Coordinates dstCoordinates;

    protected CoordinatesDrawableObject(CoordinateSystem coordinateSystem, Coordinates coordinates) {
        super(coordinateSystem);
        this.srcCoordinates = coordinates;
    }

    @Override
    public void recalculateForDrawingIn(CoordinateSystem dstCoordinateSystem) {
        Coordinates converted = dstCoordinateSystem.convertCoordinatesFrom(getSrcCoordinateSystem(), srcCoordinates);
        dstCoordinates = new IntCoordinates(converted.getXCoordinatesAsInts(), converted.getYCoordinatesAsInts());
    }

    protected final Coordinates getDstCoordinates() {
        return dstCoordinates;
    }
}
