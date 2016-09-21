/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;

/**
 * Repository for environment variables, including those set by FastR itself, e.g.
 * {@code R_LIBS_USER}.
 */
public final class REnvVars implements RContext.ContextState {

    private final Map<String, String> envVars = new HashMap<>(System.getenv());

    private REnvVars(RContext context) {
        // set the standard vars defined by R
        String rHome = rHome();

        // Check any external setting is consistent
        String envRHomePath = envVars.get("R_HOME");
        if (envRHomePath != null) {
            new File(envRHomePath).getAbsolutePath();
            if (!envRHomePath.equals(rHomePath)) {
                Utils.fail("R_HOME set to unexpected value in the environment");
            }
        }
        envVars.put("R_HOME", rHome);
        // Always read the system file
        FileSystem fileSystem = FileSystems.getDefault();
        safeReadEnvironFile(fileSystem.getPath(rHome, "etc", "Renviron").toString());
        envVars.put("R_DOC_DIR", fileSystem.getPath(rHome, "doc").toString());
        envVars.put("R_INCLUDE_DIR", fileSystem.getPath(rHome, "include").toString());
        envVars.put("R_SHARE_DIR", fileSystem.getPath(rHome, "share").toString());
        String rLibsUserProperty = System.getenv("R_LIBS_USER");
        if (rLibsUserProperty == null) {
            String os = System.getProperty("os.name");
            if (os.contains("Mac OS")) {
                rLibsUserProperty = "~/Library/R/%v/library";
            } else {
                rLibsUserProperty = "~/R/%p-library/%v";
            }
            envVars.put("R_LIBS_USER", rLibsUserProperty);
            // This gets expanded by R code in the system profile
        }

        if (!context.getOptions().getBoolean(RCmdOption.NO_ENVIRON)) {
            String siteFile = envVars.get("R_ENVIRON");
            if (siteFile == null) {
                siteFile = fileSystem.getPath(rHome, "etc", "Renviron.site").toString();
            }
            if (new File(siteFile).exists()) {
                safeReadEnvironFile(siteFile);
            }
            String userFile = envVars.get("R_ENVIRON_USER");
            if (userFile == null) {
                String dotRenviron = ".Renviron";
                userFile = fileSystem.getPath(RFFIFactory.getRFFI().getBaseRFFI().getwd(), dotRenviron).toString();
                if (!new File(userFile).exists()) {
                    userFile = fileSystem.getPath(System.getProperty("user.home"), dotRenviron).toString();
                }
            }
            if (userFile != null && new File(userFile).exists()) {
                safeReadEnvironFile(userFile);
            }
        }
        // Check for http proxies
        String httpProxy = getEitherCase("http_proxy");
        if (httpProxy != null) {
            String port = null;
            int portIndex = httpProxy.lastIndexOf(':');
            if (portIndex > 0) {
                port = httpProxy.substring(portIndex + 1);
                httpProxy = httpProxy.substring(0, portIndex);
            }
            httpProxy = httpProxy.replace("http://", "");
            System.setProperty("http.proxyHost", httpProxy);
            if (port != null) {
                System.setProperty("http.proxyPort", port);
            }
        }
    }

    public static REnvVars newContext(RContext context) {
        return new REnvVars(context);
    }

    private String getEitherCase(String var) {
        String val = envVars.get(var);
        return val != null ? val : envVars.get(var.toUpperCase());
    }

    private static String rHomePath;

    public static String rHome() {
        // This can be called before initialize, "R RHOME"
        if (rHomePath == null) {
            String path = System.getProperty("rhome.path");
            if (path != null) {
                rHomePath = path;
            } else {
                File file = new File(System.getProperty("user.dir"));
                do {
                    File binR = new File(new File(file, "bin"), "R");
                    if (binR.exists()) {
                        break;
                    } else {
                        file = file.getParentFile();
                    }
                } while (file != null);
                if (file != null) {
                    rHomePath = file.getAbsolutePath();
                } else {
                    Utils.fail("cannot find a valid R_HOME");
                }
            }
        }
        return rHomePath;
    }

    public String put(String key, String value) {
        // TODO need to set value for sub-processes
        return envVars.put(key, value);
    }

    public String get(String key) {
        return envVars.get(key);
    }

    public boolean unset(String key) {
        // TODO remove at the system level
        envVars.remove(key);
        return true;
    }

    public Map<String, String> getMap() {
        return envVars;
    }

    public void readEnvironFile(String path) throws IOException {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line = null;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.length() == 0) {
                    continue;
                }
                // name=value
                int ix = line.indexOf('=');
                if (ix < 0) {
                    throw invalid(path, line);
                }
                String var = line.substring(0, ix);
                String value = expandParameters(line.substring(ix + 1)).trim();
                // GnuR does not seem to remove quotes, although the spec says it should
                envVars.put(var, value);
            }
        }
    }

    protected String expandParameters(String value) {
        StringBuffer result = new StringBuffer();
        int x = 0;
        int paramStart = value.indexOf("${", x);
        while (paramStart >= 0) {
            result.append(value.substring(x, paramStart));
            int paramEnd = value.lastIndexOf('}');
            String param = value.substring(paramStart + 2, paramEnd);
            String paramDefault = "";
            String paramName = param;
            int dx = param.indexOf('-');
            if (dx > 0) {
                paramName = param.substring(0, dx);
                paramDefault = expandParameters(param.substring(dx + 1));
            }
            String paramValue = envVars.get(paramName);
            if (paramValue == null || paramValue.length() == 0) {
                paramValue = paramDefault;
            }
            result.append(paramValue);
            x = paramEnd + 1;
            paramStart = value.indexOf("${", x);
        }
        result.append(value.substring(x));
        return result.toString();
    }

    @TruffleBoundary
    private static IOException invalid(String path, String line) throws IOException {
        throw new IOException("   File " + path + " contains invalid line(s)\n      " + line + "\n   They were ignored\n");
    }

    public void safeReadEnvironFile(String path) {
        try {
            readEnvironFile(path);
        } catch (IOException ex) {
            // CheckStyle: stop system..print check
            System.out.println(ex.getMessage());
        }
    }

}
