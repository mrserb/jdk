/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.awt;

/**
 * This class intentionally has no static dependencies on any other classes.
 */
public final class NativeLibLoader {

    /**
     * Prevent instantiation.
     */
    private NativeLibLoader() { }

    /**
     * This is copied from java.awt.Toolkit since we need the library
     * loaded in sun.awt.image also:
     *
     * WARNING: This is a temporary workaround for a problem in the
     * way the AWT loads native libraries. A number of classes in this
     * package (sun.awt.image) have a native method, initIDs(),
     * which initializes
     * the JNI field and method ids used in the native portion of
     * their implementation.
     *
     * Since the use and storage of these ids is done by the
     * implementation libraries, the implementation of these method is
     * provided by the particular AWT implementations
     * (i.e. "Toolkit"s/Peer), such as Motif, Win32 or Tiny. The
     * problem is that this means that the native libraries must be
     * loaded by the java.* classes, which do not necessarily know the
     * names of the libraries to load. A better way of doing this
     * would be to provide a separate library which defines java.awt.*
     * initIDs, and exports the relevant symbols out to the
     * implementation libraries.
     *
     * For now, we know it's done by the implementation, and we assume
     * that the name of the library is "awt".  -br.
     */
    private static volatile boolean loadAWT;

    public static void loadAWT() {
        if (!loadAWT) {
            jdk.internal.loader.BootLoader.loadLibrary("awt");
            loadAWT = true;
        }
    }

    private static volatile boolean loadLCMS;

    public static void loadLCMS() {
        if (!loadLCMS) {
            jdk.internal.loader.BootLoader.loadLibrary("lcms");
            loadLCMS = true;
        }
    }

    private static volatile boolean loadFREETYPE;

    public static void loadFREETYPE() {
        if (!loadFREETYPE) {
            jdk.internal.loader.BootLoader.loadLibrary("freetype");
            loadFREETYPE = true;
        }
    }

    private static volatile boolean loadFONTMANAGER;

    public static void loadFONTMANAGER() {
        if (!loadFONTMANAGER) {
            jdk.internal.loader.BootLoader.loadLibrary("fontmanager");
            loadFONTMANAGER = true;
        }
    }

    private static volatile boolean loadJAVAJPEG;

    public static void loadJAVAJPEG() {
        if (!loadJAVAJPEG) {
            jdk.internal.loader.BootLoader.loadLibrary("javajpeg");
            loadJAVAJPEG = true;
        }
    }

    private static volatile boolean loadSPLASHSCREEN;

    public static void loadSPLASHSCREEN() {
        if (!loadSPLASHSCREEN) {
            jdk.internal.loader.BootLoader.loadLibrary("splashscreen");
            loadSPLASHSCREEN = true;
        }
    }

    private static volatile boolean loadMLIB;

    public static void loadMLIB() {
        if (!loadMLIB) {
            jdk.internal.loader.BootLoader.loadLibrary("mlib_image");
            loadMLIB = true;
        }
    }

    private static volatile boolean loadOSX;

    public static void loadOSX() {
        if (!loadOSX) {
            jdk.internal.loader.BootLoader.loadLibrary("osx");
            loadOSX = true;
        }
    }

    private static volatile boolean loadOSXUI;

    public static void loadOSXUI() {
        if (!loadOSXUI) {
            jdk.internal.loader.BootLoader.loadLibrary("osxui");
            loadOSXUI = true;
        }
    }
}
