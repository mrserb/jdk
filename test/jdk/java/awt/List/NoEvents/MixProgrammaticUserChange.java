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
import java.awt.Robot;
import java.awt.event.ItemEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
    private static final BlockingQueue<ItemEvent> events =
            new ArrayBlockingQueue<>(10);

    public static void main(String[] args) throws Exception {
        Frame frame = new Frame();
        try {
            robot = new Robot();

            List list = new List(1, true);
            list.add("Item");
            list.addItemListener(e -> {
                if (e.getID() == ITEM_STATE_CHANGED) {
                    events.offer(e);
                }
            });

            frame.add(list);
            frame.setUndecorated(true);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            robot.waitForIdle(100);

            Point loc = list.getLocationOnScreen();
            loc.translate(list.getWidth() / 2, list.getHeight() / 2);

            test(() -> click(loc), ItemEvent.SELECTED);
            test(() -> list.deselect(0), -1);
            test(() -> click(loc), ItemEvent.SELECTED);
            test(() -> click(loc), ItemEvent.DESELECTED);
            test(() -> list.select(0), -1);
            test(() -> click(loc), ItemEvent.DESELECTED);
        } finally {
            frame.dispose();
        }
        if (!events.isEmpty()) {
            throw new RuntimeException("Unexpected events: " + events);
        }
    }

    private static void test(Runnable action, int state) throws Exception {
        action.run();
        // Large delay, we are waiting for unexpected events
        ItemEvent e = events.poll(1, SECONDS);
        if (e == null && state == -1) {
            return; // no events as expected
        } else if (e != null && e.getStateChange() == state) {
            return; // expected event received
        }
        String text = (state == -1) ? "null" : state == ItemEvent.SELECTED
                                             ? "SELECTED" : "DESELECTED";
        throw new RuntimeException("Expected: %s, got: %s".formatted(text, e));
    }

    private static void click(Point p) {
        robot.mouseMove(p.x, p.y);
        robot.click();
    }
}
