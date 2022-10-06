/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package test.math;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.math.BigDecimal;

/**
 *
 * @author @mickleness @bourgesl
 */
public final class BaseTest {

    /**
     * Convert a shape into SVG-ish notation for debugging/readability.
     * @param shape shape to convert
     * @return String (SVG-ish notation)
     */
    public static String toString(Shape shape) {
        final StringBuilder sb = new StringBuilder(256);
        final double[] coords = new double[6];

        for (final PathIterator pi = shape.getPathIterator(null); !pi.isDone(); pi.next()) {
            switch (pi.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO:
                    sb.append("m ");
                    toString(sb, 2, coords);
                    break;
                case PathIterator.SEG_LINETO:
                    sb.append("l ");
                    toString(sb, 2, coords);
                    break;
                case PathIterator.SEG_QUADTO:
                    sb.append("q ");
                    toString(sb, 4, coords);
                    break;
                case PathIterator.SEG_CUBICTO:
                    sb.append("c ");
                    toString(sb, 6, coords);
                    break;
                case PathIterator.SEG_CLOSE:
                    sb.append("z");
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    public static  String toString(final double... coords) {
        final int len = coords.length;

        final StringBuilder sb = new StringBuilder(20 * len);
        toString(sb, len, coords);
        return sb.toString();
    }

    private static void toString(final StringBuilder sb, final int type, final double... coords) {
        switch (type) {
            case 2:
                sb.append(coords[0]).append(" ").append(coords[1]).append(" ");
                break;
            case 4:
                sb.append(coords[0]).append(" ").append(coords[1]).append(" ")
                        .append(coords[2]).append(" ").append(coords[3]).append(" ");
                break;
            case 6:
                sb.append(coords[0]).append(" ").append(coords[1]).append(" ")
                        .append(coords[2]).append(" ").append(coords[3]).append(" ")
                        .append(coords[4]).append(" ").append(coords[5]).append(" ");
                break;
            case 8:
                sb.append(coords[0]).append(" ").append(coords[1]).append(" ")
                        .append(coords[2]).append(" ").append(coords[3]).append(" ")
                        .append(coords[4]).append(" ").append(coords[5]).append(" ")
                        .append(coords[6]).append(" ").append(coords[7]).append(" ");
                break;
            default:
                break;
        }
    }

    public static  String toUniformString(BigDecimal decimal) {
        int DIGIT_COUNT = 40;
        String str = decimal.toPlainString();
        if (str.length() >= DIGIT_COUNT) {
            str = str.substring(0, DIGIT_COUNT - 1) + "…";
        }
        while (str.length() < DIGIT_COUNT) {
            str += " ";
        }
        return str;
    }

    public static  String toComparisonString(BigDecimal target, String compareAgainst) {
        final String str = toUniformString(target);

        for (int i = 0; i < str.length(); i++) {
            char ch1 = str.charAt(i);
            char ch2 = compareAgainst.charAt(i);
            if (ch1 != ch2) {
                return str.substring(0, i) + createCircleDigit(ch1) + str.substring(i + 1);
            }
        }
        return str;
    }

    /**
     * Convert a digit 0-9 into a "circle digit". Really we just want any unobtrusive way to
     * highlight a character.
     * @param ch char to convert
     * @return unicode character "circle digit"
     */
    public static  char createCircleDigit(char ch) {
        if (ch >= '1' && ch <= '9') {
            return (char) (ch - '1' + '\u2460');
        }
        if (ch == '0') {
            return '\u24ea';
        }
        return ch;
    }

    private BaseTest() {
        // forbidden constructor
    }

}
