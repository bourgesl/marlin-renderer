<%@ page contentType="image/jpeg" import="java.io.*, java.awt.*, java.awt.geom.*, java.awt.image.*,com.sun.image.codec.jpeg.*" %>
<%
    try {

        int x1 = 0;
        int x2 = 100;

        BufferedImage image = new BufferedImage(x2, x2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	
	g.setBackground(Color.WHITE);
	g.clearRect(x1,x1,x2,x2);

	g.setColor(Color.RED);
	g.setStroke(new BasicStroke(8.0f));

            final double offset = 40d;

	    final Path2D.Double d = new Path2D.Double(GeneralPath.WIND_NON_ZERO, 5);
	    d.moveTo(0d, -offset);
	    d.lineTo(-offset, 0d);
	    d.lineTo(0d, offset);
	    d.lineTo(offset, 0d);
	d.closePath();

	g.setTransform(AffineTransform.getTranslateInstance(50d, 50d));
        g.draw(d);

        JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(response.getOutputStream());
        encoder.encode(image);

    } catch (Exception e) {}
%>
