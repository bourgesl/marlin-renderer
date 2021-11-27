/*******************************************************************************
 * TODO
 ******************************************************************************/
package sun.java2d.marlin;

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

    public static int testWidenArray(final ContextWidenArray ctx, final int n) {
        return ctx.test(n);
    }

    public final static class ContextWidenArray {

        // From RendererContext:
        /* dirty int[] cache = 5 refs */
        private final ArrayCacheInt dirtyIntCache = new ArrayCacheInt(5);

        // From Renderer:
        // edgePtrs ref (dirty)
        private final ArrayCacheInt.Reference edgePtrs_ref;
        // indices into the segment pointer lists. They indicate the "active"
        // sublist in the segment lists (the portion of the list that contains
        // all the segments that cross the next scan line).
        private int[] edgePtrs;

        public ContextWidenArray() {
            edgePtrs_ref = /* rdrCtx. */ newDirtyIntArrayRef(INITIAL_CAPACITY);
            edgePtrs = edgePtrs_ref.initial;
        }

        public int test(final int max) {
            int[] _edgePtrs = this.edgePtrs;
            int edgePtrsLen = _edgePtrs.length;

            // real work:
            int sum = 0;

            for (int i = edgePtrsLen; i <= max; i *= 2) {
                final int ptrEnd = i;

                if (TRACE) {
                    System.out.println("ptrEnd: " + ptrEnd);
                }

                if (edgePtrsLen < ptrEnd) {
                    if (TRACE) {
                        System.out.println("widenArray (" + edgePtrsLen + ", " + ptrEnd + ")");
                    }
                    this.edgePtrs = _edgePtrs = edgePtrs_ref.widenArray(_edgePtrs, edgePtrsLen, ptrEnd);
                    edgePtrsLen = _edgePtrs.length;

                    if (TRACE) {
                        System.out.println("widenArray() new size: " + edgePtrsLen);
                    }
                }
                // array capacity is valid:
                // consume / do work:
                sum += _edgePtrs[i - 1] = i;
            }

            dispose();

            return sum;
        }

        private void dispose() {
            if (edgePtrs_ref.doCleanRef(edgePtrs)) {
                edgePtrs = edgePtrs_ref.putArray(edgePtrs);
            }
        }

        private ArrayCacheInt.Reference newDirtyIntArrayRef(final int initialSize) {
            return dirtyIntCache.createRef(initialSize);
        }
    }

    public static void main(String[] unused) {
        final ContextWidenArray ctx = new ContextWidenArray();

        final int max = (TRACE) ? (MAX_CACHE_CAPACITY + 1) : (MAX_CACHE_CAPACITY * RATIO_8TH) / 8;
        final int N = (TRACE) ? 1 : 1000;

        System.out.println("testWidenArray(" + max + ")");

        for (int i = 0; i < N; i++) {
            final int res = TestArrayCacheInt.testWidenArray(ctx, max);
            System.out.println("testWidenArray[" + i + "] : " + res);
        }
    }
}
