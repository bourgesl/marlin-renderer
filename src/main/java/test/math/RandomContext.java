/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package test.math;

import java.util.Random;

/**
 *
 * @author bourgesl
 */
public final class RandomContext {
    
    public final static long BASE_SEED = 13 * 19 * 1721L;

    public final static long NEXT_SEED = 17 * 11;
    
    // members:
    private final Random[] rv;
    
    RandomContext(int capacity) {
        rv = new Random[capacity];
        
        for (int i = 0; i < capacity; i++) {
            rv[i] = new Random(BASE_SEED + NEXT_SEED * i);
        }
    }
    
    double nextDouble(final int idx) {
        return rv[idx].nextDouble();
    }
}
