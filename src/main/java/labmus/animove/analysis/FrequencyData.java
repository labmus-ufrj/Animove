package labmus.animove.analysis;

import ij.ImagePlus;
import ij.measure.ResultsTable;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.XMLHelper;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.analyzeFrequencyEmbryos)
public class FrequencyData implements Command, Interactive {
    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
    }

    @Parameter(label = "XML File", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File xmlFile;

    @Parameter(label = "Source Video", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File videoFile;

    @Parameter(label = "Fix Missing Spots", persist = false)
    private boolean fixSpots = true;

    @Parameter(label = "Display Plots", persist = false)
    private boolean displayPlots = false;

    @Parameter(label = "Open Frame", callback = "openFrame")
    private Button btn;

    @Parameter(label = "Process", callback = "process")
    private Button btn5;

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;

    private ImagePlus videoFrame = null;

    @Override
    public void run() {

    }

    private void process() {
        if (videoFrame == null || videoFrame.getWindow() == null) {
            uiService.showDialog("Click \"Open Frame\" button first.", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        if (xmlFile == null || !xmlFile.exists()) {
            uiService.showDialog("Invalid XML file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        List<ArrayList<XMLHelper.PointData>> data;
        try {
            data = XMLHelper.iterateOverXML(xmlFile, videoFrame, this.fixSpots);
        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("An error occured during processing:\n" + e.getLocalizedMessage(),
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        List<List<Double>> distancesPerTrack = data.stream().map((track) -> IntStream.range(0, track.size() - 1)
                .mapToDouble(i -> getDistance(track.get(i), track.get(i + 1)) * videoFrame.getCalibration().pixelWidth)
                .boxed()
                .collect(Collectors.toList())).collect(Collectors.toList());

        String name = "Frequency from " + xmlFile.getName() + " and " + videoFile.getName();
        ResultsTable rt = new ResultsTable();
        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing
        // NaN just gets deleted when pasting

        // Iterate through each inner list, treating it as a column.
        for (int colIndex = 0; colIndex < distancesPerTrack.size(); colIndex++) {

            // Get the data for the current column.
            List<Double> distances = distancesPerTrack.get(colIndex);

            // Define a name for the column header.
            String columnHeader = "Track " + (colIndex + 1);

            // Iterate through the values in the current column.
            for (int rowIndex = 0; rowIndex < distances.size(); rowIndex++) {
                rt.setValue(columnHeader, rowIndex, distances.get(rowIndex));
            }
        }

        if (this.displayPlots) {
            new ImagePlus("Plot", createChart(name, distancesPerTrack, videoFrame.getCalibration().getXUnit()).createBufferedImage(1600, 1200)).show();
        }
        rt.show(name);
    }

    private static JFreeChart createChart(String title, List<List<Double>> distancesPerTrack, String axisUnit) {
        NumberAxis domainAxis = new NumberAxis("Frame");
        domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        domainAxis.setRange(1, distancesPerTrack.get(0).size());
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 30));
        domainAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 36));

        CombinedDomainXYPlot parentPlot = new CombinedDomainXYPlot(domainAxis);
        parentPlot.setGap(30.0);
        parentPlot.setOrientation(PlotOrientation.VERTICAL);

        List<Color> colors = generateColors(distancesPerTrack.size());
        for (int i = 0; i < distancesPerTrack.size(); i++) {
            List<Double> distances = distancesPerTrack.get(i);
            boolean isLast = (i == distancesPerTrack.size() - 1);
            parentPlot.add(createSubPlot(createDataset(distances), colors.get(i), isLast, axisUnit));
        }

        JFreeChart chart = new JFreeChart(
                title,
                JFreeChart.DEFAULT_TITLE_FONT,
                parentPlot,
                false
        );

        chart.setBackgroundPaint(Color.WHITE);
        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        chart.setPadding(new RectangleInsets(30.0, 30.0, 30.0, 30.0));

        return chart;
    }

    private static XYSeriesCollection createDataset(List<Double> distances) {
        XYSeries series = new XYSeries("");

        for (int i = 0; i < distances.size(); i++) {
            series.add(i + 1, distances.get(i));
        }

        return new XYSeriesCollection(series);
    }

    private static XYPlot createSubPlot(XYSeriesCollection dataset, Color lineColor, boolean showYLabel, String axisUnit) {

        // Renderer config (Lines only, no shapes)
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, lineColor);
        renderer.setSeriesStroke(0, new BasicStroke(3.0f));

        // Range Axis (Y-Axis) config
        // Only set the text if showYLabel is true, otherwise pass null
        NumberAxis rangeAxis = new NumberAxis(showYLabel ? "Distance (" + axisUnit + ")" : null);
        rangeAxis.setRange(0.0, 1.25);
        rangeAxis.setTickUnit(new NumberTickUnit(0.3));
        rangeAxis.setLabelFont(new Font("SansSerif", Font.BOLD, 36));
        rangeAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));

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

    public static List<Color> generateColors(int numColors) {
        List<Color> colors = new ArrayList<>(numColors);

        final float GOLDEN_RATIO = 0.618033988749895f;

        final float SATURATION = 0.75f;
        final float BRIGHTNESS = 0.75f;

        float currentHue = 0.5f;

        for (int i = 0; i < numColors; i++) {
            Color c = Color.getHSBColor(currentHue, SATURATION, BRIGHTNESS);
            colors.add(c);

            currentHue += GOLDEN_RATIO;

            currentHue %= 1.0f;
        }

        return colors;
    }

    //    private double getDistance(double x1, double y1, double x2, double y2) {
    private static double getDistance(XMLHelper.PointData point1, XMLHelper.PointData point2) {
        return Math.hypot(point2.x - point1.x, point2.y - point1.y);
    }

    private void openFrame() {
        if (videoFile == null || !videoFile.exists() || !videoFile.isFile()) {
            uiService.showDialog("Could not open video: \nInvalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                if (videoFrame != null && videoFrame.getWindow() != null) {
                    videoFrame.close();
                }
                videoFrame = ZFHelperMethods.getFirstFrame(videoFile);
                videoFrame.setTitle("Video frame");
                uiService.show(videoFrame);

            } catch (Exception e) {
                log.error(e);
                uiService.showDialog("Could not open video: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            }
        });
    }
}
