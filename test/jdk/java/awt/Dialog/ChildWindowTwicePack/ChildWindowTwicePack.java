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

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Label;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;

import javax.swing.Box;
import javax.swing.BoxLayout;

/**
 * @test
 * @key headful
 * @bug 8261725
 * @summary We shouldn't get different sizes when we call Frame.pack() twice
 * @run main/othervm ChildWindowTwicePack
 * @run main/othervm -Dsun.java2d.uiScale=1 ChildWindowTwicePack
 * @run main/othervm -Dsun.java2d.uiScale=2.25 ChildWindowTwicePack
 */
public final class ChildWindowTwicePack {

    public static void main(String[] args) {
        test("window");
        test("dialog");
        test("frame");
    }

    private static void test(String top) {
        var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            Rectangle bounds = gd.getDefaultConfiguration().getBounds();
            Point point = bounds.getLocation();
            point.translate(100, 100);
            Window main = getTopLevel(top, null);
            try {
                main.setLocation(point);
                main.setVisible(true);

                test(main, top);
            } finally {
                main.dispose();
            }
        }
    }

    private static void test(Window parent, String top) {
        Box box = new Box(BoxLayout.PAGE_AXIS);
        box.add(new Label("This is line one".repeat(2)));
        box.add(new Label("This is line two".repeat(2)));
        box.add(new Label("This is line three".repeat(2)));

        Window title = getTopLevel(top, parent);
        title.add(box);

        title.pack();
        Dimension before = title.getSize();
        title.pack();
        Dimension after = title.getSize();
        title.dispose();

        if (!before.equals(after)) {
            System.err.println("Expected: " + after);
            System.err.println("Actual: " + before);
            throw new RuntimeException("Wrong insets");
        }
    }

    private static Window getTopLevel(String top, Window parent) {
        return switch (top) {
            case "window" -> new Window(parent);
            case "dialog" -> new Dialog(parent);
            case "frame" -> new Frame();
            default -> throw new IllegalArgumentException("Unexpected: " + top);
        };
    }
}
