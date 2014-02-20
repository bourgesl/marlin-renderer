<%@ page contentType="text/html" %>
<html>
<body>
<h2>Current RenderingEngine implementation:</h2>
<pre>
<%= sun.java2d.pipe.RenderingEngine.getInstance().getClass().getName() %>
</pre>
<hr/>

<h2>System properties:</h3>
<pre>
sun.java2d.renderer = <%= System.getProperty("sun.java2d.renderer", "") %>
</pre>
<h3>Marlin options (if enabled)</h3>
<pre>
sun.java2d.renderer.useThreadLocal = <%= System.getProperty("sun.java2d.renderer.useThreadLocal", "true") %>
</pre>
<pre>
sun.java2d.renderer.pixelsize = <%= System.getProperty("sun.java2d.renderer.pixelsize", "2048") %>
</pre>
</body>
</html>
