
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Bug8264999 {

    public static void main(String[] args) throws IOException {
        BufferedImage bim = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = (Graphics2D) bim.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setBackground(Color.white);
        g.clearRect(0, 0, bim.getWidth(), bim.getHeight());
        GeneralPath path;

        AffineTransform at = g.getTransform();

        g.setColor(Color.red);
        g.transform(AffineTransform.getTranslateInstance(0, -1400));
        g.transform(AffineTransform.getScaleInstance(10, 10));
        path = new GeneralPath();
        path.moveTo(24.954517, 159);
        path.lineTo(21.097446, 157.5);
        path.lineTo(17.61364, 162);
        path.lineTo(13.756569, 163.5);
        path.lineTo(11.890244, 160.5);
        g.draw(path);

        g.setTransform(at);

        g.setColor(Color.blue);
        path = new GeneralPath();
        g.transform(AffineTransform.getTranslateInstance(0, -1300));
        g.transform(AffineTransform.getScaleInstance(10, 10));
        path.moveTo(24.954517, 159);
        path.lineTo(21.097446, 157.5);
        path.lineTo(21.097446, 157.5); // this repeats the previous one
        path.lineTo(17.61364, 162);
        path.lineTo(17.61364, 162); // this repeats the previous one
        path.lineTo(13.756569, 163.5);
        path.lineTo(13.756569, 163.5); // this repeats the previous one
        path.lineTo(11.890244, 160.5);
        g.draw(path);

        g.dispose();

        ImageIO.write(bim, "png", new File(Bug8264999.class.getSimpleName() + ".png"));
    }
}
