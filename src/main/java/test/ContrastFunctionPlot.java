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
import org.marlin.pipe.AlphaLUT;
import org.marlin.pipe.BlendComposite;
import static org.marlin.pipe.BlendComposite.NORM_ALPHA;

/**
 * Basic Plot of constrast functions
 */
public class ContrastFunctionPlot extends ApplicationFrame {

    /**
     * Creates a new demo.
     *
     * @param title  the frame title.
     */
    public ContrastFunctionPlot(final String title) {
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

        // show alphatables:
        final int[][][] alpha_tables = AlphaLUT.ALPHA_LUT.alphaTables;
        if (alpha_tables != null) {
            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    return x;
                }
            }, start, end, samples, "Identity Profile"));

            final int nLuma = alpha_tables.length; // first dim
            final int[] luma_values = AlphaLUT.ALPHA_LUT.lumaValues;

            final int l_ref = 0;

            // show table[ls=0][ld=0..max]
            for (int i = 0; i < nLuma; i++) {
                final int l_idx = i;
                final int[] alphas = alpha_tables[l_ref][i];

                dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                    @Override
                    public double getValue(final double x) {
                        final int n = (int) Math.round(255.0 * x);
                        return alphas[n] / 255.0 + (l_idx / 10000.0);
                    }
                }, start, end, samples, "alpha_tables[Y=" + (luma_values[l_ref] / 1023.0) + "]"
                        + "[Y=" + (luma_values[l_idx] / 1023.0) + "] Profile"));
            }
        }

        if (false) {
            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    return 1.0 - x;
                }
            }, start, end, samples, "Normal Edge Profile"));

            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                @Override
                public double getValue(final double x) {
                    final double alpha = x + (1.0 - x) * 0.25 * x;
                    return 1.0 - alpha;
                }
            }, start, end, samples, "Contrast +25% Edge Profile"));

            dataset.addSeries(DatasetUtils.sampleFunction2DToSeries(new Function2D() {
                private final static double w = 0.56789789;
                private final static double a = 1.0 / (w - 1.0);

                @Override
                public double getValue(final double x) {
                    // y = ax + b
                    if (x <= w) {
                        // y(w) = 1.0 =>    a * w + b = 1
                        return 1.0;
                    }
                    // y(1) = 0 = a + b

                    // solution:
                    // a + b = 0
                    // a * w + b = 1
                    // b = -a
                    // => a * ( w - 1 ) = 1 
                    // => a = 1 / ( w - 1 )
                    return a * (x - 1.0);
                }
            }, start, end, samples, "Shifted Edge profile"));
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
        final ContrastFunctionPlot plot = new ContrastFunctionPlot("Contrast Function Plot");
        plot.pack();
        UIUtils.centerFrameOnScreen(plot);
        plot.setVisible(true);
    }

}
