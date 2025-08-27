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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;

import javax.imageio.ImageIO;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_BINARY;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/**
 * @test
 * @bug 8366208
 * @summary Tests various semi-custom images.
 */
public final class FilterSemiCustomImages {

    private static final int[] TYPES = {
            TYPE_INT_RGB, TYPE_INT_ARGB, TYPE_INT_ARGB_PRE, TYPE_INT_BGR,
            TYPE_3BYTE_BGR, TYPE_4BYTE_ABGR, TYPE_4BYTE_ABGR_PRE,
            TYPE_BYTE_BINARY
    };

    private static final int[] CSS = {
            ColorSpace.CS_CIEXYZ, ColorSpace.CS_GRAY,
            ColorSpace.CS_LINEAR_RGB, ColorSpace.CS_PYCC, ColorSpace.CS_sRGB
    };

    private static final int W = 144;
    private static final int H = 123;

    private static final class CustomRaster extends WritableRaster {
        CustomRaster(SampleModel sampleModel, Point origin) {
            super(sampleModel, origin);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int fromIndex : CSS) {
            for (int toIndex : CSS) {
                for (int type : TYPES) {
                    test(fromIndex, toIndex, type);
                }
            }
        }
    }

    private static void test(int fromIndex, int toIndex, int type)
            throws Exception
    {
        ColorSpace toCS = ColorSpace.getInstance(fromIndex);
        ColorSpace fromCS = ColorSpace.getInstance(toIndex);
        ColorConvertOp op = new ColorConvertOp(fromCS, toCS, null);

        BufferedImage srcGold = new BufferedImage(W, H, type);
        fill(srcGold);
        BufferedImage dstGold = makeCustomBI(srcGold);
        op.filter(srcGold, dstGold);

        BufferedImage srcCustom = makeCustomBI(srcGold);
        fill(srcCustom);
        BufferedImage dst = new BufferedImage(W, H, type);
        op.filter(srcCustom, dst);
        verify(dstGold, dst);

        BufferedImage src = makeCustomBI(srcGold);
        fill(src);
        BufferedImage dstCustom = new BufferedImage(W, H, type);
        op.filter(src, dstCustom);
        verify(dstGold, dstCustom);
    }

    private static BufferedImage makeCustomBI(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        SampleModel sm = cm.createCompatibleSampleModel(W, H);
        CustomRaster cr = new CustomRaster(sm, new Point());
        return new BufferedImage(cm, cr, bi.isAlphaPremultiplied(), null) {
            @Override
            public int getType() {
                return bi.getType();
            }
        };
    }

    private static void fill(Image image) {
        Graphics2D graphics = (Graphics2D) image.getGraphics();
        graphics.setComposite(AlphaComposite.Src);
        for (int i = 0; i < image.getHeight(null); ++i) {
            graphics.setColor(new Color(i, 0, 255 - i));
            graphics.fillRect(0, i, image.getWidth(null), 1);
        }
        graphics.dispose();
    }

    static void verify(BufferedImage dst, BufferedImage dstGold) throws Exception {
        for (int x = 0; x < W; ++x) {
            for (int y = 0; y < H; ++y) {
                if (dst.getRGB(x, y) != dstGold.getRGB(x, y)) {
                    ImageIO.write(dst, "png", new File("custom.png"));
                    ImageIO.write(dstGold, "png", new File("gold.png"));
                    throw new RuntimeException("Test failed.");
                }
            }
        }
    }
}