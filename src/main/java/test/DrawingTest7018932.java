package test;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Line2D;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class DrawingTest7018932 extends JPanel {

    static final boolean useAA = false;

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.getContentPane().setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(new DrawingTest7018932(), BorderLayout.CENTER);
        frame.setSize(400, 400);
        frame.setVisible(true);
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        if (useAA) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

// clip - doesn't help
        g2.setClip(0, 0, getWidth(), getHeight());

// this part is just testing the drawing - so I can see I am actually drawing something
// IGNORE
        /**
     g.setColor(Color.GREEN);
     g.fillRect(0, 0, getWidth(), getHeight());
     g.setColor(Color.black);
        g2.setStroke(new BasicStroke(2));
     g2.draw(new Line2D.Double(20, 20, 200, 20));
    
     /**/
// Now we re-create the exact conditions that lead to the system crash in the JDK
// BUG HERE - setting the stroke leads to the crash
        Stroke stroke = new BasicStroke(2.0f, 1, 0, 1.0f, new float[]{0.0f, 4.0f}, 0.0f);
        g2.setStroke(stroke);

        // NOTE: Large values to trigger crash / infinite loop?
        g2.draw(new Line2D.Double(4.0, 1.794369841E9, 567.0, -2.147483648E9));
    }

}
