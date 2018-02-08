package test;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class JavaOutlineBug extends JPanel {

    private final static int SIZE = 900;
    private final static double D = 0.5 * SIZE;
    private final static double sqrt2 = Math.sqrt(2);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                JPanel p = new JavaOutlineBug();
                p.setPreferredSize(new Dimension(SIZE, SIZE));
                JFrame f = new JFrame();
                f.add(p);
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                f.pack();
                f.setVisible(true);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        double r = 1843200.0;
        double c = D - r / sqrt2;

        Ellipse2D e2d = new Ellipse2D.Double(c - r, c - r, r * 2, r * 2);

        BasicStroke stroke
                    = new BasicStroke(2.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
                        new float[]{10.0f, 5.0f}, 0.0f);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(stroke);
        g2d.setColor(Color.GRAY);
        g2d.fill(e2d);
        g2d.setColor(Color.BLACK);
        g2d.draw(e2d);
    }
}
