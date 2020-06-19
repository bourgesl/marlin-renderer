package test;

import java.awt.Color;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.UIUtils;
import org.jfree.data.function.Function2D;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeriesCollection;
import org.marlin.pipe.BlendComposite;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;

/**
 * Basic Plot of sRGB, Y to L and gamma functions
 */
public class ColorFunctionPlot extends ApplicationFrame {

    /**
     * Creates a new demo.
     *
     * @param title  the frame title.
     */
    public ColorFunctionPlot(final String title) {
        super(title);
        JPanel chartPanel = createDemoPanel();
        chartPanel.setPreferredSize(new java.awt.Dimension(2000, 2000));
        setContentPane(chartPanel);
    }

    /**
     * Creates a panel for the demo (used by SuperDemo.java).
     *
     * @return A panel.
     */
    public static JPanel createDemoPanel() {
        JFreeChart chart = createChart(createDataset());
        ChartPanel panel = new ChartPanel(chart, 1000, 1000, 100, 100, 1000, 1000, true,
                true, // properties
                true, // save
                true, // print
                true, // zoom
                true // tooltips
        );
        panel.setMouseWheelEnabled(true);
        return panel;
    }

    /**
     * Creates a sample dataset.
     *
     * @return A sample dataset.
     */
    public static XYDataset createDataset() {

        final int samples = 1000;

        final double start = 0.0;
        final double end = 1.0;

        final XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
            @Override
            public double getValue(final double x) {
                return x;
            }
        }, start, end, samples, "Linear"));

        if (true) {
            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    return BlendComposite.GAMMA_LUT.sRGB_to_RGB(x);
                }
            }, start, end, samples, "sRGB dir"));

            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    return BlendComposite.GAMMA_LUT.RGB_to_sRGB(x);
                }
            }, start, end, samples, "sRGB inv"));
        }
        if (true) {
            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    return BlendComposite.GAMMA_LUT.Y_to_L(x);
                }
            }, start, end, samples, "Y2L dir"));
            if (false) {
                dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                    @Override
                    public double getValue(final double x) {
                        return x / 9.033;
                    }
                }, start, end, samples, "Y2L dir LIN"));
            }

            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    return BlendComposite.GAMMA_LUT.L_to_Y(x);
                }
            }, start, end, samples, "Y2L inv"));
            if (false) {
                dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                    @Override
                    public double getValue(final double x) {
                        return x * 9.033;
                    }
                }, start, end, samples, "Y2L inv LIN"));
            }
        }

        if (true) {
            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    return BlendComposite.LUMA_LUT.dir(x * NORM_ALPHA) / NORM_ALPHA;
                }
            }, start, end, samples, "Luma dir"));

            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    return BlendComposite.LUMA_LUT.inv(x * NORM_ALPHA) / NORM_ALPHA;
                }
            }, start, end, samples, "Luma inv"));
        }

        return dataset;
    }

    /**
     * Creates a chart.
     *
     * @param dataset  the dataset.
     *
     * @return A chart instance.
     */
    private static JFreeChart createChart(XYDataset dataset) {
        // create the chart...
        final JFreeChart chart = ChartFactory.createXYLineChart(
                "Color Functions ", // chart title
                "X", // x axis label
                "Y", // y axis label
                dataset, // data
                PlotOrientation.VERTICAL,
                true, // include legend
                true, // tooltips
                false // urls
        );

        final XYPlot plot = (XYPlot) chart.getPlot();
        plot.setDomainPannable(true);
        plot.setRangePannable(true);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.GRAY);
        plot.setRangeGridlinePaint(Color.GRAY);

        final ValueAxis xAxis = plot.getDomainAxis();
        xAxis.setLowerMargin(0.0);
        xAxis.setUpperMargin(0.0);
        xAxis.setRange(0.0, 1.0);

        final ValueAxis yAxis = plot.getRangeAxis();
        yAxis.setLowerMargin(0.0);
        yAxis.setUpperMargin(0.0);
        yAxis.setRange(0.0, 1.0);
        
        return chart;
    }

    /**
     * Starting point for the demonstration application.
     *
     * @param args  ignored.
     */
    public static void main(String[] args) {
        
        System.setProperty("sun.java2d.renderer.blend.gamma", "2.401");
        
        final ColorFunctionPlot plot = new ColorFunctionPlot("Color Function Plot");
        plot.pack();
        UIUtils.centerFrameOnScreen(plot);
        plot.setVisible(true);
    }

}
