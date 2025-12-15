package labmus.animove.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.RoiScaler;
import ij.plugin.frame.RoiManager;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.XMLHelper;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
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
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.thigmotaxisPath)
public class Thigmotaxis implements Command, Interactive {
    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
    }

    @Parameter(label = "XML File", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File xmlFile;

    @Parameter(label = "Source Video", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File videoFile;

    @Parameter(label = "Area Percentage", min = "1", max = "99", persist = false)
    private int percentage = 50;

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
    private Roi lastRoi;

    @Override
    public void run() {

    }

    private static class SpotsData {
        public final double inside;
        public final double outside;

        private SpotsData(double inside, double outside) {
            this.inside = inside;
            this.outside = outside;
        }
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

        getRoi();
        // Validate ROI exists
        if (lastRoi == null) {
            uiService.showDialog("Create a ROI enclosing your target area",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        double reduceDimensions = Math.sqrt(1 - (percentage / 100.0));
        Roi reducedRoi = RoiScaler.scale(lastRoi, reduceDimensions, reduceDimensions, true);
        videoFrame.setRoi(reducedRoi);

        XMLHelper.TrackingXMLData trackingXMLData;
        try {
            trackingXMLData = XMLHelper.iterateOverXML(xmlFile, videoFrame, this.fixSpots);
            if (trackingXMLData.onlySpots) {
                uiService.showDialog("No tracks found in XML file.\nThe plugin eill resume processing, but won't generate results that are related to tracks.\nPlease make sure you have complete the Trackmate tracking routine if that's your intended result.",
                        ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("An error occured during processing:\n" + e.getLocalizedMessage(),
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        String name = "Thigmotaxis Analysis from " + xmlFile.getName() + " and " + videoFile.getName();

        List<SpotsData> timeData = trackingXMLData.data.stream().map(track -> {
            long inside = track.stream().filter(spot -> reducedRoi.contains((int) spot.x, (int) spot.y)).count();
            long outside = track.stream().filter(spot ->
                    lastRoi.contains((int) spot.x, (int) spot.y) &&
                            !reducedRoi.contains((int) spot.x, (int) spot.y)).count();
            return new SpotsData(inside, outside);
        }).collect(Collectors.toList());

        DefaultCategoryDataset timeDataset = new DefaultCategoryDataset();
        ResultsTable timeRt = new ResultsTable();
        for (int i = 0; i < timeData.size(); i++) {
            SpotsData data = timeData.get(i);
            timeDataset.addValue(data.inside, "Center Area", "Track " + (i + 1));
            timeDataset.addValue(data.outside, "Peripheral Area", "Track " + (i + 1));
            timeRt.setValue("Track", i, "Track " + (i + 1));
            timeRt.setValue("Center Area", i, data.inside); // todo: String.format(Locale.US, "%.2f", index * 100)
            timeRt.setValue("Peripheral Area", i, data.outside);
        }
        timeRt.show("(time) "+name);
        if (this.displayPlots) {
            new ImagePlus("Plot", getStackedBarChart(name, timeDataset).createBufferedImage(1600, 1200)).show();
        }

        List<SpotsData> distanceData = trackingXMLData.data.stream().map((track) ->
        {
            double inside = IntStream.range(0, track.size() - 1).mapToDouble(i -> {
                if (reducedRoi.contains((int) track.get(i).x, (int) track.get(i).y) &&
                        reducedRoi.contains((int) track.get(i + 1).x, (int) track.get(i + 1).y)) {
                    return getDistance(track.get(i), track.get(i + 1)) * videoFrame.getCalibration().pixelWidth;
                }
                return 0.0;
            }).reduce(0, Double::sum);

            double outside = IntStream.range(0, track.size() - 1).mapToDouble(i -> {
                if (lastRoi.contains((int) track.get(i).x, (int) track.get(i).y) &&
                        lastRoi.contains((int) track.get(i + 1).x, (int) track.get(i + 1).y) &&
                        !reducedRoi.contains((int) track.get(i).x, (int) track.get(i).y) &&
                        !reducedRoi.contains((int) track.get(i + 1).x, (int) track.get(i + 1).y)) {
                    return getDistance(track.get(i), track.get(i + 1)) * videoFrame.getCalibration().pixelWidth;
                }
                return 0.0;
            }).reduce(0, Double::sum);
            return new SpotsData(inside, outside);
        }).collect(Collectors.toList());
        DefaultCategoryDataset distanceDataset = new DefaultCategoryDataset();
        ResultsTable distanceRt = new ResultsTable();
        for (int i = 0; i < timeData.size(); i++) {
            SpotsData data = timeData.get(i);
            distanceDataset.addValue(data.inside, "Center Area", "Track " + (i + 1));
            distanceDataset.addValue(data.outside, "Peripheral Area", "Track " + (i + 1));
            distanceRt.setValue("Track", i, "Track " + (i + 1));
            distanceRt.setValue("Center Area", i, data.inside);
            distanceRt.setValue("Peripheral Area", i, data.outside);
        }
        distanceRt.show("(distance) "+name);
        if (this.displayPlots) {
            new ImagePlus("Plot", getStackedBarChart(name, distanceDataset).createBufferedImage(1600, 1200)).show();
        }


    }

    private static JFreeChart getStackedBarChart(String title, CategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createStackedBarChart(
                title,
                "",
                "Distance Percentage (%)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                false,
                false
        );

        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();

        renderer.setBarPainter(new StandardBarPainter());

        chart.setBackgroundPaint(Color.WHITE);

        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 48));
        chart.setPadding(new RectangleInsets(20.0, 20.0, 20.0, 20.0));

        plot.setRangeGridlineStroke(new BasicStroke(
                0.5f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));

        plot.setBackgroundPaint(Color.decode("#f0f0f0"));
        plot.setRangeGridlinePaint(Color.GRAY);
        plot.setOutlineVisible(false);

        Font axisLabelFont = new Font("SansSerif", Font.BOLD, 36);
        Font tickLabelFont = new Font("SansSerif", Font.PLAIN, 36);

        plot.getDomainAxis().setLabelFont(axisLabelFont);
        plot.getDomainAxis().setTickLabelFont(tickLabelFont);
        plot.getDomainAxis().setLowerMargin(0.02);
        plot.getDomainAxis().setUpperMargin(0.02);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 100.0);
        rangeAxis.setTickUnit(new NumberTickUnit(10.0));
        rangeAxis.setLabelFont(axisLabelFont);
        rangeAxis.setTickLabelFont(tickLabelFont);

        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelGenerator(
                new StandardCategoryItemLabelGenerator("{2}%", new DecimalFormat("0.#", new DecimalFormatSymbols(Locale.US)))
        );

        renderer.setDefaultItemLabelFont(new Font("SansSerif", Font.BOLD, 36));
        renderer.setDefaultItemLabelPaint(Color.WHITE);


        List<Color> colors = ZFHelperMethods.generateColors(dataset.getColumnCount());
        for (int i = 0; i < colors.size(); i++) {
            renderer.setSeriesPaint(i, colors.get(i));
            renderer.setLegendShape(i, new Ellipse2D.Double(0, 0, 25, 25));
        }

        LegendTitle legend = chart.getLegend();
        legend.setPosition(RectangleEdge.BOTTOM);
        legend.setItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
        legend.setFrame(BlockBorder.NONE);
        legend.setItemLabelPadding(new RectangleInsets(0, 10, 0, 40));
        legend.setMargin(new RectangleInsets(30.0, 0.0, 0.0, 0.0));

        return chart;
    }

    private static double getDistance(XMLHelper.PointData point1, XMLHelper.PointData point2) {
        return Math.hypot(point2.x - point1.x, point2.y - point1.y);
    }

    private void getRoi() {
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager();
        }
        Roi singleRoi;
        if (videoFrame != null && videoFrame.getRoi() != null) {
            singleRoi = videoFrame.getRoi();
            singleRoi.setName(ZFConfigs.pluginName);
            rm.addRoi(singleRoi);
        } else {
            int roiIndex = rm.getSelectedIndex() != -1 ? rm.getSelectedIndex() : 0;
            singleRoi = rm.getRoi(roiIndex);
        }
        lastRoi = singleRoi == null ? lastRoi : singleRoi;
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
