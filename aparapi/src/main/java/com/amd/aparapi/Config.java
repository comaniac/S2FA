/*
Copyright (c) 2010-2011, Advanced Micro Devices, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following
disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided with the distribution.

Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

If you use the software (in whole or in part), you shall adhere to all applicable U.S., European, and other export
laws, including but not limited to the U.S. Export Administration Regulations ("EAR"), (15 C.F.R. Sections 730 through
774), and E.U. Council Regulation (EC) No 1334/2000 of 22 June 2000.  Further, pursuant to Section 740.6 of the EAR,
you hereby certify that, except pursuant to a license granted by the United States Department of Commerce Bureau of
Industry and Security or as otherwise permitted pursuant to a License Exception under the U.S. Export Administration
Regulations ("EAR"), you will not (1) export, re-export or release to a national of a country in Country Groups D:1,
E:1 or E:2 any restricted technology, software, or source code you receive hereunder, or (2) export to Country Groups
D:1, E:1 or E:2 the direct product of such technology or software, if such foreign produced direct product is subject
to national security controls as identified on the Commerce Control List (currently found in Supplement 1 to Part 774
of EAR).  For the most current Country Group listings, or for additional information about the EAR or your obligations
under those regulations, please refer to the U.S. Bureau of Industry and Security's website at http://www.bis.doc.gov/.

 */
package com.amd.aparapi;

import java.util.logging.*;
import java.util.Locale;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.amd.aparapi.internal.instruction.Instruction;
import com.amd.aparapi.internal.tool.InstructionViewer;

/**
 * A central location for holding all runtime configurable properties as well as logging configuration.
 *
 * Ideally we will find all properties used by <code>Aparapi</code> here. Please consider updating this class if you wish
 * to add new properties which control <code>Aparapi</code>s behavior.
 *
 * @author gfrost
 *
 */
public class Config {

    /**
     * Value defaults to com.amd.aparapi.config if not overridden by extending classes
     */
    protected static final String propPkgName = Config.class.getPackage().getName();

    // Logging setup
    private static final String logPropName = propPkgName + ".logLevel";

    public static final Logger logger = Logger.getLogger(Config.getLoggerName());

    /**
     * Disable Unsafe
     */
    public static final boolean disableUnsafe = Boolean.getBoolean(
                propPkgName + ".disableUnsafe");

    // Pragma/OpenCL codegen related flags
    public static final boolean enableAtomic32 = Boolean.getBoolean(
                propPkgName + ".enableAtomic32");

    public static final boolean enableAtomic64 = Boolean.getBoolean(
                propPkgName + ".enableAtomic64");

    // Debugging related flags
    public static final boolean verboseComparitor = Boolean.getBoolean(propPkgName +
            ".verboseComparitor");

    // Individual bytecode support related flags
    public static final boolean enablePUTFIELD = Boolean.getBoolean(
                propPkgName + ".enable.PUTFIELD");

    public static final boolean enableARETURN = !Boolean.getBoolean(
                propPkgName + ".disable.ARETURN");

    public static final boolean enablePUTSTATIC = Boolean.getBoolean(
                propPkgName + ".enable.PUTSTATIC");

    // Allow static array accesses
    public static final boolean enableGETSTATIC =
        true; //Boolean.getBoolean(propPkgName + ".enable.GETSTATIC");

    public static final boolean enableMONITOR = Boolean.getBoolean(
                propPkgName + ".enable.MONITOR");

    public static final boolean enableATHROW = Boolean.getBoolean(
                propPkgName + ".enable.ATHROW");

    public static final boolean enableMETHODARRAYPASSING = !Boolean.getBoolean(
                propPkgName + ".disable.METHODARRAYPASSING");

    public static final boolean enableARRAYLENGTH = Boolean.getBoolean(propPkgName +
            ".enable.ARRAYLENGTH");

    public static final boolean enableSWITCH = Boolean.getBoolean(
                propPkgName + ".enable.SWITCH");

    public static boolean enableShowFakeLocalVariableTable = Boolean.getBoolean(
                propPkgName + ".enableShowFakeLocalVariableTable");

    public static final boolean enableInstructionDecodeViewer = Boolean.getBoolean(
                propPkgName + ".enableInstructionDecodeViewer");

    public static String instructionListenerClassName = System.getProperty(
                propPkgName + ".instructionListenerClass");

    public static InstructionListener instructionListener = null;

    public interface InstructionListener {
        void showAndTell(String message, Instruction _start, Instruction _instruction);
    }

    // Kernel specified parameters
    /**
     * User specified kernel type
     *
     *  Usage -Dcom.amd.aparapi.kernelType={General|Map|MapPartitions}
     *
     */
    public final String kernelType;

    public final boolean enableMerlinKernel;

    static {
        try {
            final Level level = Level.parse(System.getProperty(getLoggerName(), "INFO"));

            logger.setLevel(level);
            logger.setUseParentHandlers(false);
            LogFormatter formatter = new LogFormatter();
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(level);
            handler.setFormatter(formatter);
            logger.addHandler(handler);
        } catch (final Exception e) {
            System.err.println("Exception " + e + " in Aparapi logging setup");
            e.printStackTrace();
        }
    };

    static {
        if (enableInstructionDecodeViewer && ((instructionListenerClassName == null) ||
                                              instructionListenerClassName.equals("")))
            instructionListenerClassName = InstructionViewer.class.getName();

        if ((instructionListenerClassName != null) &&
                !instructionListenerClassName.equals("")) {
            try {
                final Class<?> instructionListenerClass = Class.forName(
                            instructionListenerClassName);
                instructionListener = (InstructionListener)
                                      instructionListenerClass.newInstance();
            } catch (final ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (final InstantiationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (final IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public Config() {
        kernelType = System.getProperty(propPkgName + ".kernelType");
        enableMerlinKernel = Boolean.getBoolean(propPkgName + ".enable.MERLIN");
    }

    public static String getLoggerName() {
        return logPropName;
    }
}

class LogFormatter extends Formatter {
    private static final DateFormat df = new
    SimpleDateFormat("dd/MM/yy HH:mm:ss.SSS");

    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append(df.format(new Date(record.getMillis()))).append(" ");
        builder.append("[").append(record.getLevel()).append("]");
        String className = record.getSourceClassName();
        if (className.lastIndexOf(".") != -1)
            className = className.substring(className.lastIndexOf(".") + 1);
        builder.append("[").append(className).append(".");
        builder.append(record.getSourceMethodName()).append("] ");
        builder.append(formatMessage(record));
        builder.append("\n");
        return builder.toString();
    }
}

