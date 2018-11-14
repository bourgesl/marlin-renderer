package test;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;

import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * JDK8:
 * - noAA ie not Marlin renderer (native C renderer ?) 
 * drawLine(1.0E8) [AA=false]: 3631.4947909999996 ms.
 * - AA org.marlin.pisces.DMarlinRenderingEngine:
drawLine(1.0E8) [AA=true]: 42.381105 ms.
drawLine(1.0E8) [AA=true]: 1.86741 ms.
drawLine(1.0E8) [AA=true]: 4.550681 ms.
drawLine(1.0E8) [AA=true]: 1.9914479999999999 ms.
 * - AA org.marlin.pisces.MarlinRenderingEngine:
drawLine(1.0E8) [AA=true]: 50.357248999999996 ms.
drawLine(1.0E8) [AA=true]: 1.935198 ms.

drawLine(7.0E15) [AA=true]: 48.779185999999996 ms.
drawLine(7.0E15) [AA=true]: 1.946686 ms.
 */
public class EndlessLoop extends JFrame {

    private static final long serialVersionUID = 465093760123849968L;

    private static final boolean USE_AA = true;
    private static final double LEN = 7e15;

    public EndlessLoop(final String title) {
        super(title);
        setContentPane(createPanel());
    }

    private JPanel createPanel() {
        JPanel panel = new JPanel() {
            private static final long serialVersionUID = 1L;

            protected void paintComponent(java.awt.Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        (USE_AA) ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);

                g2.setStroke(new BasicStroke(0.5f, 0, 2, 0.0f, new float[]{2, 2}, 0));
//				Line2D line = new Line2D.Double(10, 10, Double.NaN, Double.NaN);
                Line2D line = new Line2D.Double(10, 10, 0, LEN);
                System.out.println("If you don't see 'Not hanging.' then you hang :)");
                final long start = System.nanoTime();
                g2.draw(line);
                System.out.println("drawLine(" + LEN + ") [AA=" + USE_AA + "]: " + (1e-6d * (System.nanoTime() - start)) + " ms.");
                System.out.println("Not hanging.");
            }
        };
        panel.setLayout(null);
        panel.setPreferredSize(new Dimension(600, 400));

        return panel;

    }

    public static void main(final String[] args) {
        final EndlessLoop demo = new EndlessLoop("Trigger endless loop on AdoptOpenJDK");
        demo.pack();
        demo.setVisible(true);
    }
}
