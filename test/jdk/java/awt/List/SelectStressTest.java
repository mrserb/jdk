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
import java.util.concurrent.TimeUnit;

/**
 * @test
 * @key headful
 * @bug 8201307
 * @summary Stress test for List select, add and removeAll methods
 */
public final class SelectStressTest {

    private static volatile long endtime;
    static volatile boolean failed;

    public static void main(String[] args) throws Exception {
        // Try to run the test no more than 5 seconds
        endtime = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Frame f = new Frame();
        try {
            List list = new List();
            f.add(list);
            f.setSize(400, 400);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            test(list);
        } finally {
            f.dispose();
        }
        if (failed) {
            throw new RuntimeException();
        }
    }

    private static void test(List list) throws Exception {
        Thread[] addGroup = task(() -> {
            if (list.getItemCount() < 90) {
                list.add("item");
            }
        });
        Thread[] clearGroup = task(list::removeAll);
        Thread[] selectGroup = task(() -> {
            for (int index = 0; index < 100; index++) {
                list.select(index);
            }
        });
        join(addGroup);
        join(clearGroup);
        join(selectGroup);
    }

    private static void join(Thread[] group) throws Exception {
        for (Thread t : group) {
            t.start();
        }
        for (Thread t : group) {
            t.join();
        }
    }

    private static Thread[] task(Runnable execute) {
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                try {
                    while (!isComplete()) {
                        Thread.yield();
                        execute.run();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    failed = true;
                }
            });
        }
        return ts;
    }

    private static boolean isComplete() {
        return endtime - System.nanoTime() < 0;
    }
}
