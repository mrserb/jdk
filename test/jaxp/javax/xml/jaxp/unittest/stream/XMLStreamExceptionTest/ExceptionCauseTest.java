/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package stream.XMLStreamExceptionTest;

import java.io.IOException;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @test
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm stream.XMLStreamExceptionTest.ExceptionCauseTest
 * @summary Test XMLStreamException constructor initializes chained exception
 */
public class ExceptionCauseTest {

    @Test
    public void testExceptionCause() {

        // Create exception with cause
        Throwable cause = new Throwable("cause");
        Location location = new Location() {
            public int getLineNumber() { return 0; }
            public int getColumnNumber() { return 0; }
            public int getCharacterOffset() { return 0; }
            public String getPublicId() { return null; }
            public String getSystemId() { return null; }
        };
        XMLStreamException e = new XMLStreamException("message", location, cause);

        // Verify cause
        Assert.assertSame(e.getCause(), cause, "XMLStreamException has the wrong cause");
    }
}
