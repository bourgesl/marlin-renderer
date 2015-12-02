/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import marlin.BoundsTest;
import marlin.RenderingTest;
import org.junit.Test;
import org.marlin.pisces.MergeSortTest;

/**
 * Simple wrapper on Marlin tests
 * @author bourgesl
 */
public class RunJUnitTest {
    
    @Test
    public void boundsTest() {
        BoundsTest.main(null);
    }
    
    @Test
    public void renderingTest() {
        RenderingTest.main(null);
    }
    
    @Test
    public void crashTest() {
        CrashTest.main(null);
    }
}
