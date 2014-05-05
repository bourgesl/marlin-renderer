package marlin;


import java.awt.geom.FastPath2D;
import java.awt.geom.Path2D;



/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author llooo
 */
public class FastPath2DTest {
        
  public static void main(String[] args) {
        System.out.println("Test Path2D:"); 
        
        FastPath2D p2d = new FastPath2D(1000);
        
        Path2D copy = p2d.trimmedCopy(); 
        
//        System.out.println("p2d [numTypes= " + copy.numTypes + "] numCoords= " + copy.numCoords+"]");
//        System.out.println("copy [numTypes= " + copy.numTypes + "] numCoords= " + copy.numCoords+"]");
 
        // System.out.println("kernel = " + Arrays.toString(getKernel(8)));
    }    
}
