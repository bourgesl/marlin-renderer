/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.marlin.pisces.stats;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 */
public final class ArraySortDataCollection implements Serializable {

    private static final long serialVersionUID = 1L;

    /* members */
    private final ArrayList<ArraySortData> data;
    private transient String filePath = null;

    public ArraySortDataCollection() {
        data = new ArrayList(1000);
    }

    public synchronized void addData(final int[] x, final int fromIndex, final int toIndex, final int sortedIndex) {
        // make a copy [0 - toIndex]:
        data.add(new ArraySortData(Arrays.copyOf(x, toIndex), fromIndex, toIndex, sortedIndex));
    }

    public synchronized ArrayList<ArraySortData> getData() {
        return data;
    }

    public synchronized void clear() {
        data.clear();
    }

    @Override
    public String toString() {
        return "ArraySortDataCollection[" + ((data != null) ? data.size() : 0) + ']';
    }

    public static ArraySortDataCollection load(final String absFilePath) {
        ObjectInputStream ois = null;
        try {
            final File inputFile = new File(absFilePath);
            ois = new ObjectInputStream(new FileInputStream(inputFile));
            final ArraySortDataCollection dc = (ArraySortDataCollection) ois.readObject();
            dc.filePath = inputFile.getAbsolutePath();

            System.out.println("Loaded: " + dc.toString());

            return dc;
        } catch (Exception e) {
            System.out.println("error while loading data");
            e.printStackTrace(System.out);
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }
        return null;
    }

    public static void save(final String absFilePath, final ArraySortDataCollection dc) {
        final File outputFile = new File(absFilePath);
        dc.filePath = outputFile.getAbsolutePath();
        ObjectOutputStream oos = null;
        try {
            System.out.println("Writing " + dc.toString() + " to: " + dc.filePath);
            oos = new ObjectOutputStream(new FileOutputStream(outputFile));
            oos.writeObject(dc);
        } catch (IOException ioe) {
            System.out.println("error while writing data");
            ioe.printStackTrace(System.out);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
            dc.clear();
        }
    }

}
