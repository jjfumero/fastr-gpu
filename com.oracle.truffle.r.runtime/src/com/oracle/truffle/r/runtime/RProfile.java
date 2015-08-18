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
package com.oracle.truffle.r.runtime;

import static com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.FileSystem;

import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.runtime.context.*;
import com.oracle.truffle.r.runtime.ffi.*;

/**
 * Handles the setup of system, site and user profile code. N.B. this class only reads the files and
 * leaves the evaluation to the caller, using {@link #siteProfile()} and {@link #userProfile()}.
 */
public final class RProfile implements RContext.ContextState {

    private RProfile(RContext context, REnvVars envVars) {
        String rHome = REnvVars.rHome();
        FileSystem fileSystem = FileSystems.getDefault();
        Source newSiteProfile = null;
        Source newUserProfile = null;

        if (!context.getOptions().getBoolean(NO_SITE_FILE)) {
            String siteProfilePath = envVars.get("R_PROFILE");
            if (siteProfilePath == null) {
                siteProfilePath = fileSystem.getPath(rHome, "etc", "Rprofile.site").toString();
            } else {
                siteProfilePath = Utils.tildeExpand(siteProfilePath);
            }
            File siteProfileFile = new File(siteProfilePath);
            if (siteProfileFile.exists()) {
                newSiteProfile = getProfile(siteProfilePath);
            }
        }

        if (!context.getOptions().getBoolean(NO_INIT_FILE)) {
            String userProfilePath = envVars.get("R_PROFILE_USER");
            if (userProfilePath == null) {
                String dotRenviron = ".Rprofile";
                userProfilePath = fileSystem.getPath(RFFIFactory.getRFFI().getBaseRFFI().getwd(), dotRenviron).toString();
                if (!new File(userProfilePath).exists()) {
                    userProfilePath = fileSystem.getPath(System.getProperty("user.home"), dotRenviron).toString();
                }
            } else {
                userProfilePath = Utils.tildeExpand(userProfilePath);
            }
            if (userProfilePath != null) {
                File userProfileFile = new File(userProfilePath);
                if (userProfileFile.exists()) {
                    newUserProfile = getProfile(userProfilePath);
                }
            }
        }
        siteProfile = newSiteProfile;
        userProfile = newUserProfile;
    }

    private final Source siteProfile;
    private final Source userProfile;

    public static Source systemProfile() {
        Path path = FileSystems.getDefault().getPath(REnvVars.rHome(), "library", "base", "R", "Rprofile");
        Source source = getProfile(path.toString());
        if (source == null) {
            Utils.fail("can't find system profile");
        }
        return source;
    }

    public Source siteProfile() {
        return siteProfile;
    }

    public Source userProfile() {
        return userProfile;
    }

    private static Source getProfile(String path) {
        try {
            return Source.fromFileName(path);
        } catch (IOException ex) {
            // GnuR does not report an error, just ignores
            return null;
        }
    }

    public static RProfile newContext(RContext context, REnvVars envVars) {
        return new RProfile(context, envVars);
    }
}
