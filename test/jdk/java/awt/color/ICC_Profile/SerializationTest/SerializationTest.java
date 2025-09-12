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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @test
 * @bug 8367384
 * @summary Checks that ICC_Profile serialization works as expected
 */
public final class SerializationTest {

    private static final ICC_Profile[] PROFILES = {
            ICC_Profile.getInstance(ColorSpace.CS_sRGB),
            ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB),
            ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ),
            ICC_Profile.getInstance(ColorSpace.CS_PYCC),
            ICC_Profile.getInstance(ColorSpace.CS_GRAY)
    };


    public static void main(String[] args) throws Exception {
        // Serialization form for ICC_Profile contains version, profile name,
        // and profile data. If the name is invalid or does not match a standard
        // profile, the data is used. An exception is thrown only if both the
        // name and the data are invalid.

        test("null", "null", true);
        test("null", "invalid", true);
        test("invalid", "null", true);
        test("invalid", "invalid", true);

        test("null", "valid", false);
        test("valid", "null", false);
        test("valid", "valid", false);
        test("valid", "invalid", false);
        test("invalid", "valid", false);

        roundTripOneByOne();
        roundTripAllAtOnce();
    }

    private static void roundTripOneByOne() throws Exception {
        for (ICC_Profile profile : PROFILES) {
            byte[] serialized = serialize(profile);
            if (profile != deserialize(serialized)) {
                throw new RuntimeException("Wrong deserialized object");
            }
        }
    }

    private static void roundTripAllAtOnce() throws Exception {
        byte[] data;

        try (var bos = new ByteArrayOutputStream();
             var oos = new ObjectOutputStream(bos))
        {
            for (ICC_Profile profile : PROFILES) {
                oos.writeObject((Object) profile);
            }
            oos.flush();
            data = bos.toByteArray();
        }

        try (var ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            for (ICC_Profile profile : PROFILES) {
                if (profile != ois.readObject()) {
                    throw new RuntimeException("Wrong deserialized object");
                }
            }
        }
    }

    private static void test(String csName, String data, boolean fail)
            throws Exception
    {
        String fileName = csName + "_" + data + ".ser";
        File file = new File(System.getProperty("test.src", "."), fileName);
        try (var fis = new FileInputStream(file);
             var ois = new ObjectInputStream(fis))
        {
            try {
                ois.readObject();
                if (fail) {
                    throw new RuntimeException("IOE did not occur");
                }
            } catch (InvalidObjectException e) {
                if (!fail) {
                    throw new RuntimeException("Unexpected IOE occurs", e);
                }
            }
        }
    }

    private static byte[] serialize(Object obj) throws Exception {
        try (var bos = new ByteArrayOutputStream();
             var oos = new ObjectOutputStream(bos))
        {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        }
    }

    private static ICC_Profile deserialize(byte[] data) throws Exception {
        try (var ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (ICC_Profile) ois.readObject();
        }
    }
}