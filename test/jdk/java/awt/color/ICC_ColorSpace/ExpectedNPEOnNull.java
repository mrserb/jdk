/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;

/**
 * @test
 * @bug 6211126 6211139
 * @summary IllegalArgumentException in ColorSpace.getInstance for wrong cspace
 */
public final class ExpectedNPEOnNull {

    public static void main(String[] args) {
        try {
            ICC_ColorSpace icc = new ICC_ColorSpace(null);
            throw new RuntimeException("NPE is expected: " + icc);
        } catch (NullPointerException ignored) {
            // expected
        }
        // standard
        test(ColorSpace.getInstance(ColorSpace.CS_sRGB));
        test(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB));
        test(ColorSpace.getInstance(ColorSpace.CS_CIEXYZ));
        test(ColorSpace.getInstance(ColorSpace.CS_PYCC));
        test(ColorSpace.getInstance(ColorSpace.CS_GRAY));
        // data based
        test(new ICC_ColorSpace(ICC_Profile.getInstance(ColorSpace.CS_sRGB)));
    }

    private static void test(ColorSpace icc) {
        try {
            icc.toRGB(null);
            throw new RuntimeException("NPE is expected");
        } catch (NullPointerException ignored) {
            // expected
        }
        try {
            icc.fromRGB(null);
            throw new RuntimeException("NPE is expected");
        } catch (NullPointerException ignored) {
            // expected
        }
        try {
            icc.toCIEXYZ(null);
            throw new RuntimeException("NPE is expected");
        } catch (NullPointerException ignored) {
            // expected
        }
        try {
            icc.fromCIEXYZ(null);
            throw new RuntimeException("NPE is expected");
        } catch (NullPointerException ignored) {
            // expected
        }
    }
}
