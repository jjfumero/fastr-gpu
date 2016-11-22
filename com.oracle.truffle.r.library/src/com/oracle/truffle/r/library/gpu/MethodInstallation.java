package com.oracle.truffle.r.library.gpu;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.env.REnvironment;

public abstract class MethodInstallation extends RExternalBuiltinNode.Arg0 {

    private static final String R_EVAL_DESCRIPTION = "<eval>";
    public static final String PATH_TO_MAPPLY = System.getProperty("rhome.path") + "/parallelRFunction/mapply.R";
    private boolean isMethodInstalled = false;
    private RFunction newRFunctionDefined;

    public static class Installation {

        public static String getRSourceForFunction(String path) {
            try (BufferedReader buffer = new BufferedReader(new FileReader(path))) {
                StringBuilder source = new StringBuilder();
                String line = buffer.readLine();
                while (line != null) {
                    source.append(line);
                    source.append(System.lineSeparator());
                    line = buffer.readLine();
                }
                return source.toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public static RFunction installMApply() {
            String newFunction = getRSourceForFunction(PATH_TO_MAPPLY);
            Source newSourceFunction = Source.fromText(newFunction, R_EVAL_DESCRIPTION).withMimeType(RRuntime.R_APP_MIME);
            try {
                RFunction newRFunction = (RFunction) RContext.getEngine().parseAndEval(newSourceFunction, false);
                MaterializedFrame enclosingFrame = newRFunction.getEnclosingFrame();
                REnvironment parentEnviroment = REnvironment.frameToEnvironment(enclosingFrame);
                REnvironment newEnvironment = RDataFactory.createNewEnv(parentEnviroment, "mapply");
                newEnvironment.safePut("mapply", newRFunction);
                newRFunction.setEnclosingFrame(newEnvironment.getFrame());
                REnvironment.attach(2, newEnvironment);
                return newRFunction;
            } catch (ParseException e) {
                throw new RuntimeException("[Fatal error] the function could not be rewritten");
            }
        }
    }

    @Specialization
    public Object installMethod() {
        if (!isMethodInstalled) {
            newRFunctionDefined = Installation.installMApply();
            isMethodInstalled = true;
        }
        return newRFunctionDefined;
    }
}
