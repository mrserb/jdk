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
import java.awt.color.ICC_Profile;

/**
 * @test
 * @bug 1234567
 */
public final class BrokenProfileIAE {

    public static void main(String[] args) {
        byte[] data = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        ICC_Profile profile = ICC_Profile.getInstance(data);

        byte[] header = profile.getData(ICC_Profile.icSigHead);
        header[ICC_Profile.icHdrPcs] = 0;
        header[ICC_Profile.icHdrColorSpace] = 0;
        header[ICC_Profile.icHdrDeviceClass] = 0;
        profile.setData(ICC_Profile.icSigHead, header);

        try {
            profile.getPCSType();
            throw new RuntimeException("IllegalArgumentException is expected");
        } catch (IllegalArgumentException ignore) {
            // expected
        }
        try {
            profile.getProfileClass();
            throw new RuntimeException("IllegalArgumentException is expected");
        } catch (IllegalArgumentException ignore) {
            // expected
        }
        try {
            profile.getColorSpaceType();
            throw new RuntimeException("IllegalArgumentException is expected");
        } catch (IllegalArgumentException ignore) {
            // expected
        }
    }
}
