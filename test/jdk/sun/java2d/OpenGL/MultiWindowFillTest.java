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

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * @test
 * @bug 8378201
 * @key headful
 * @summary Verifies that window content survives a GL context switch to another
 *          window and back
 * @run main/othervm MultiWindowFillTest
 * @run main/othervm -Dsun.java2d.opengl=True MultiWindowFillTest
 */
public final class MultiWindowFillTest {

    private static final int TOLERANCE = 10;

    public static void main(String[] args) throws Exception {
        Frame f1 = new Frame();
        Frame f2 = new Frame();
        try {
            f1.setUndecorated(true);
            f1.setSize(100, 100);
            f1.setLocation(100, 100);
            f2.setUndecorated(true);
            f2.setSize(100, 100);
            f2.setLocation(300, 100);

            f1.setVisible(true);
            f2.setVisible(true);

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            // Fill both, initializes surfaces
            fill(f1, Color.RED);
            fill(f2, Color.BLUE);

            // Touch both again
            fill(f1, Color.RED, 2, 2);
            fill(f2, Color.BLUE, 2, 2);

            robot.waitForIdle();
            robot.delay(1000);

            check(robot, f1, Color.RED);
            check(robot, f2, Color.BLUE);
        } finally {
            f1.dispose();
            f2.dispose();
        }
    }

    private static void fill(Frame frame, Color c) {
        fill(frame, c, frame.getWidth(), frame.getHeight());
    }

    private static void fill(Frame frame, Color c, int w, int h) {
        Graphics g = frame.getGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
    }

    private static void check(Robot robot, Frame frame, Color exp) {
        int sx = frame.getLocationOnScreen().x + 50;
        int sy = frame.getLocationOnScreen().y + 50;
        Color c = robot.getPixelColor(sx, sy);
        if (!isAlmostEqual(c, exp)) {
            saveImage(robot, frame, frame.getName());
            throw new RuntimeException("%s != %s".formatted(exp, c));
        }
    }

    private static void saveImage(Robot r, Frame f, String name) {
        try {
            Rectangle rect = f.getBounds();
            BufferedImage img = r.createScreenCapture(rect);
            ImageIO.write(img, "png", new File(name + ".png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isAlmostEqual(Color c1, Color c2) {
        return Math.abs(c1.getRed() - c2.getRed()) <= TOLERANCE
                && Math.abs(c1.getGreen() - c2.getGreen()) <= TOLERANCE
                && Math.abs(c1.getBlue() - c2.getBlue()) <= TOLERANCE;
    }
}
