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

import java.io.File;
import java.io.FileInputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;

/**
 * @test
 * @bug 8367384
 * @summary Checks ICC_Profile serialization, especially for invalid streams
 */
public final class SerializationTest {

    public static void main(String[] args) throws Exception {
        test("null_null", true);
        test("null_invalid", true);
        test("invalid_null", true);
        test("invalid_invalid", true);

        test("null_valid", false);
        test("valid_null", false);
        test("valid_valid", false);
        test("valid_invalid", false);
        test("invalid_valid", false);
    }

    private static void test(String test, boolean fail) throws Exception {
        String fileName = test + ".ser";
        File file = new File(System.getProperty("test.src", "."), fileName);
        try (var fis = new FileInputStream(file);
             var ois = new ObjectInputStream(fis))
        {
            try {
                ois.readObject();
                if (fail) {
                    throw new RuntimeException("Expected IOE did not occur");
                }
            } catch (InvalidObjectException e) {
                if (!fail) {
                    throw new RuntimeException("Unexpected IOE occurred", e);
                }
            }
        }
    }
}
