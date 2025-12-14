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

import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ItemEvent;
import java.util.concurrent.CountDownLatch;

import static java.awt.event.ItemEvent.ITEM_STATE_CHANGED;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @test
 * @bug 1234567
 * @key headful
 * @summary Checks that programmatic changes to a List do not fire events,
 *          only user interactions do
 */
public final class MixProgrammaticUserChange {

    private static Robot robot;
    private static volatile ItemEvent event;
    private static volatile CountDownLatch go;

    public static void main(String[] args) throws Exception {
        Frame frame = new Frame();
        try {
            robot = new Robot();

            List list = new List(1, true);
            list.add("Item");
            list.addItemListener(e -> {
                if (e.getID() == ITEM_STATE_CHANGED) {
                    event = e;
                }
                go.countDown();
            });

            frame.add(list);
            frame.setUndecorated(true);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            robot.waitForIdle(100);

            Rectangle r = new Rectangle(list.getLocationOnScreen(),
                                        list.getSize());
            Point loc = new Point(r.x + r.width / 2, r.y + r.height / 2);

            test(() -> click(loc), ItemEvent.SELECTED, "SELECTED");
            test(() -> list.deselect(0), -1, "null");
            test(() -> click(loc), ItemEvent.SELECTED, "SELECTED");
            test(() -> click(loc), ItemEvent.DESELECTED, "DESELECTED");
            test(() -> list.select(0), -1, "null");
            test(() -> click(loc), ItemEvent.DESELECTED, "DESELECTED");
        } finally {
            frame.dispose();
        }
    }

    private static void test(Runnable action, int state, String expected)
            throws Exception
    {
        event = null;
        go = new CountDownLatch(1);
        action.run();
        // Large delay, we are waiting for unexpected events
        if (go.await(1, SECONDS) || state != -1) {
            if (event == null || event.getStateChange() != state) {
                throw new RuntimeException(
                        "Expected: %s, actual: %s".formatted(expected, event));
            }
        }
    }

    private static void click(Point p) {
        robot.mouseMove(p.x, p.y);
        robot.click();
    }
}
