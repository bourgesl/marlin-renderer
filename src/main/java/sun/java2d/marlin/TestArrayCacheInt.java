/*******************************************************************************
 * TODO
 ******************************************************************************/
package sun.java2d.marlin;

import java.util.Arrays;

/**
 * TODO

INFO: ArrayCache.BUCKETS        = 8
INFO: ArrayCache.MIN_ARRAY_SIZE = 4096
INFO: ArrayCache.MAX_ARRAY_SIZE = 16777216
INFO: ArrayCache.ARRAY_SIZES = [4096, 16384, 65536, 262144, 1048576, 4194304, 8388608, 16777216]
INFO: ArrayCache.THRESHOLD_ARRAY_SIZE = 16777216
INFO: ArrayCache.THRESHOLD_HUGE_ARRAY_SIZE = 50331648


 */
public final class TestArrayCacheInt {

    static final int INITIAL_CAPACITY = 1024;

    static final int MIN_CACHE_CAPACITY = 4096;
    static final int MAX_CACHE_CAPACITY = 16777216;

    static final int RATIO_8TH = 9; // 7 to avoid alloc, 9 to force alloc (cache exhausted)

    static final boolean TRACE = false;

    private TestArrayCacheInt() {
        // forbidden
    }

    public static int testWidenDirtyArrayCacheInt(final ContextWidenArray ctx, final int n) {
        return ctx.testWidenDirtyArrayCacheInt(n);
    }

    public static int testWidenCleanArrayCacheInt(final ContextWidenArray ctx, final int n) {
        return ctx.testWidenCleanArrayCacheInt(n);
    }
    
    public static int testWidenArrayAlloc(final ContextWidenArray ctx, final int n) {
        return ctx.testWidenArrayAlloc(n);
    }

    public final static class ContextWidenArray {

        // From RendererContext:
        // Array caches:
        /* clean int[] cache (zero-filled) = 5 refs */
        private final ArrayCacheIntClean cleanIntCache = new ArrayCacheIntClean(5);
        /* dirty int[] cache = 5 refs */
        private final ArrayCacheInt dirtyIntCache = new ArrayCacheInt(5);

        // From Renderer:
        // edgePtrs ref (dirty)
        private final ArrayCacheInt.Reference edgePtrs_ref;
        // indices into the segment pointer lists. They indicate the "active"
        // sublist in the segment lists (the portion of the list that contains
        // all the segments that cross the next scan line).
        private int[] edgePtrs;

        // clean variants:
        private final ArrayCacheIntClean.Reference edgePtrs_ref_Cl;
        private int[] edgePtrs_Cl;
        
        
        public ContextWidenArray() {
            edgePtrs_ref = /* rdrCtx. */ newDirtyIntArrayRef(INITIAL_CAPACITY);
            edgePtrs = edgePtrs_ref.initial;
            
            edgePtrs_ref_Cl = /* rdrCtx. */ newCleanIntArrayRef(INITIAL_CAPACITY);
            edgePtrs_Cl = edgePtrs_ref_Cl.initial;
        }

        public int testWidenDirtyArrayCacheInt(final int max) {
            int[] _edgePtrs = this.edgePtrs;
            int edgePtrsLen = _edgePtrs.length;

            // real work:
            int sum = 0;
            int ptrEnd;

            for (int i = edgePtrsLen; i <= max; i *= 2) {
                ptrEnd = i;

                if (TRACE) {
                    System.out.println("ptrEnd: " + ptrEnd);
                }

                if (edgePtrsLen < ptrEnd) {
                    if (TRACE) {
                        System.out.println("testWidenDirtyArrayCacheInt (" + edgePtrsLen + ", " + ptrEnd + ")");
                    }
                    this.edgePtrs = _edgePtrs = edgePtrs_ref.widenArray(_edgePtrs, edgePtrsLen, ptrEnd);
                    edgePtrsLen = _edgePtrs.length;

                    if (TRACE) {
                        System.out.println("testWidenDirtyArrayCacheInt() new size: " + edgePtrsLen);
                    }
                }
                // array capacity is valid:
                // consume / do work:
                sum += _edgePtrs[i - 1] = i;
            }

            disposeDirtyArrayCacheInt();

            return sum;
        }

        private void disposeDirtyArrayCacheInt() {
            if (edgePtrs_ref.doCleanRef(edgePtrs)) {
                edgePtrs = edgePtrs_ref.putArray(edgePtrs);
            }
        }

        private ArrayCacheInt.Reference newDirtyIntArrayRef(final int initialSize) {
            return dirtyIntCache.createRef(initialSize);
        }

        public int testWidenCleanArrayCacheInt(final int max) {
            int[] _edgePtrs = this.edgePtrs_Cl;
            int edgePtrsLen = _edgePtrs.length;

            // real work:
            int sum = 0;
            int ptrEnd = 0;
            
            for (int i = edgePtrsLen; i <= max; i *= 2) {
                ptrEnd = i;

                if (TRACE) {
                    System.out.println("ptrEnd: " + ptrEnd);
                }

                if (edgePtrsLen < ptrEnd) {
                    if (TRACE) {
                        System.out.println("testWidenArrayCacheInt (" + edgePtrsLen + ", " + ptrEnd + ")");
                    }
                    this.edgePtrs_Cl = _edgePtrs = edgePtrs_ref_Cl.widenArray(_edgePtrs, edgePtrsLen, ptrEnd);
                    edgePtrsLen = _edgePtrs.length;

                    if (TRACE) {
                        System.out.println("testWidenArrayCacheInt() new size: " + edgePtrsLen);
                    }
                }
                // array capacity is valid:
                // consume / do work:
                sum += _edgePtrs[i - 1] = i;
            }

            disposeCleanArrayCacheInt(ptrEnd);

            return sum;
        }

        private void disposeCleanArrayCacheInt(final int max) {
            edgePtrs_Cl = edgePtrs_ref_Cl.putArray(edgePtrs_Cl, 0, max);
        }

        private ArrayCacheIntClean.Reference newCleanIntArrayRef(final int initialSize) {
            return cleanIntCache.createRef(initialSize);
        }

        public int testWidenArrayAlloc(final int max) {
            int[] _edgePtrs = this.edgePtrs_ref.initial;
            int edgePtrsLen = _edgePtrs.length;

            // real work:
            int sum = 0;
            int ptrEnd;

            for (int i = edgePtrsLen; i <= max; i *= 2) {
                ptrEnd = i;

                if (TRACE) {
                    System.out.println("ptrEnd: " + ptrEnd);
                }

                if (edgePtrsLen < ptrEnd) {
                    if (TRACE) {
                        System.out.println("testWidenArrayAlloc (" + edgePtrsLen + ", " + ptrEnd + ")");
                    }
                    this.edgePtrs = _edgePtrs = widenArray(_edgePtrs, edgePtrsLen, ptrEnd - edgePtrsLen);
                    edgePtrsLen = _edgePtrs.length;

                    if (TRACE) {
                        System.out.println("testWidenArrayAlloc() new size: " + edgePtrsLen);
                    }
                }
                // array capacity is valid:
                // consume / do work:
                sum += _edgePtrs[i - 1] = i;
            }

            return sum;
        }
        
        /* from OpenJDK8u: 
            https://github.com/openjdk/jdk8u/blob/master/jdk/src/share/classes/sun/java2d/pisces/Helpers.java
*/
        static int[] widenArray(int[] in, final int cursize, final int numToAdd) {
             if (in.length >= cursize + numToAdd) {
                 return in;
             }
             return Arrays.copyOf(in, 2 * (cursize + numToAdd));
         }    
    }

    public static void main(String[] unused) {
        final ContextWidenArray ctx = new ContextWidenArray();

        final int max = (TRACE) ? (MAX_CACHE_CAPACITY + 1) : (MAX_CACHE_CAPACITY * RATIO_8TH) / 8;
        final int N = (TRACE) ? 1 : 100;

        System.out.println("testWidenDirtyArrayCacheInt(" + max + ")");

        for (int i = 0; i < N; i++) {
            final int res = TestArrayCacheInt.testWidenDirtyArrayCacheInt(ctx, max);
            System.out.println("testWidenDirtyArrayCacheInt[" + i + "] : " + res);
        }

        System.out.println("testWidenCleanArrayCacheInt(" + max + ")");

        for (int i = 0; i < N; i++) {
            final int res = TestArrayCacheInt.testWidenCleanArrayCacheInt(ctx, max);
            System.out.println("testWidenCleanArrayCacheInt[" + i + "] : " + res);
        }

        System.out.println("testWidenArrayAlloc(" + max + ")");

        for (int i = 0; i < N; i++) {
            final int res = TestArrayCacheInt.testWidenArrayAlloc(ctx, max);
            System.out.println("testWidenArrayAlloc[" + i + "] : " + res);
        }
    }
}
