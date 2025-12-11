package labmus.animove;

import ij.ImagePlus;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.util.Random;

/**
 * A demo showing how to create a stacked line chart similar to the user's request
 * using JFreeChart's CombinedDomainXYPlot.
 */
public class StackedLineChart {

    /**
     * Creates the combined chart with 3 stacked plots.
     */
    private static JFreeChart createChart() {

        // 1. Create the Shared Domain Axis (X-Axis)
        NumberAxis domainAxis = new NumberAxis("Frame");
        domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        domainAxis.setRange(1, 1000);
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 26));
        domainAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 30));

        // 2. Create the Combined Plot
        CombinedDomainXYPlot parentPlot = new CombinedDomainXYPlot(domainAxis);
        parentPlot.setGap(30.0); // Gap between vertical plots
        parentPlot.setOrientation(PlotOrientation.VERTICAL);

        // 3. Create the 3 sub-plots
        // We pass specific colors to match the image: Blue, Orange, Green
        XYPlot plot1 = createSubPlot(createDummyDataset(1), Color.decode("#1f77b4"), false); // Blue
        XYPlot plot2 = createSubPlot(createDummyDataset(2), Color.decode("#ff7f0e"), false); // Orange
        XYPlot plot3 = createSubPlot(createDummyDataset(3), Color.decode("#2ca02c"), true); // Green

        // 4. Add sub-plots to the parent
        // The weight determines how much vertical space each plot gets (1 is equal share)
        parentPlot.add(plot1, 1);
        parentPlot.add(plot2, 1);
        parentPlot.add(plot3, 1);

        // 5. Create the JFreeChart
        JFreeChart chart = new JFreeChart(
                "Title",                   // Chart title (none per image)
                JFreeChart.DEFAULT_TITLE_FONT,
                parentPlot,             // The plot
                false                   // Legend (disabled per image)
        );

        chart.setBackgroundPaint(Color.WHITE);
        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        chart.setPadding(new RectangleInsets(30.0, 30.0, 30.0, 30.0));

        return chart;
    }

    /**
     * Helper method to create an individual subplot.
     */
    private static XYPlot createSubPlot(XYSeriesCollection dataset, Color lineColor, boolean showYLabel) {

        // Renderer config (Lines only, no shapes)
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, lineColor);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));

        // Range Axis (Y-Axis) config
        // Only set the text if showYLabel is true, otherwise pass null
        NumberAxis rangeAxis = new NumberAxis(showYLabel ? "Distance (mm)" : null);
        rangeAxis.setRange(0.0, 1.05);
        rangeAxis.setTickUnit(new NumberTickUnit(0.5));
        rangeAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 30));
        rangeAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));

        // Construct the plot
        XYPlot plot = new XYPlot(dataset, null, rangeAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(Color.BLACK);

        plot.setBackgroundPaint(Color.decode("#f0f0f0"));
        plot.setRangeGridlinePaint(Color.GRAY);

        // CRITICAL: Force all plots to reserve the same space on the left (e.g., 65 pixels).
        // Without this, the middle plot (with text) will be narrower than the top/bottom plots.
        AxisSpace space = new AxisSpace();
        space.setLeft(65.0);
        space.setRight(2.0); // Optional: consistent right padding
        plot.setFixedRangeAxisSpace(space);

        return plot;
    }

    /**
     * Generates dummy data that resembles the spikes in the provided image.
     */
    private static XYSeriesCollection createDummyDataset(int seed) {
        XYSeries series = new XYSeries("Data " + seed);
        Random rand = new Random(seed * 100L);

        double value = 0.0;
        for (int i = 1; i <= 1000; i++) {
            // Generate spiky data: mostly low noise, occasional spikes
            if (rand.nextDouble() > 0.95) {
                value = rand.nextDouble() * 0.8 + 0.1; // Spike
            } else {
                value = value * 0.5 + (rand.nextDouble() * 0.05); // Decay/Noise
            }
            series.add(i, value);
        }

        return new XYSeriesCollection(series);
    }

    public static void main(String[] args) {
        new ImagePlus("Plot", StackedLineChart.createChart().createBufferedImage(1600, 1200)).show();
    }
}