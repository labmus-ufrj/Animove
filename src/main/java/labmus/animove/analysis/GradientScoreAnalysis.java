package labmus.animove.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.ImagesToStack;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.XMLHelper;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.statistics.BoxAndWhiskerItem;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.DialChart;
import org.knowm.xchart.DialChartBuilder;
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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.GeneralPath;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.scoreGradientPath)
public class GradientScoreAnalysis implements Command, Interactive, MouseListener, MouseMotionListener {

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

    @Parameter(label = "Open Frame", callback = "displayImage")
    private Button btn;

    @Parameter(label = "Load Min/Max from XML", callback = "loadFromXML")
    private Button btn6;

    @Parameter(label = "Change Max Score", callback = "changeMax")
    private Button btn2;

    @Parameter(label = "Change Min Score", callback = "changeMin")
    private Button btn3;

    @Parameter(label = "Stop", callback = "stop")
    private Button btn4;

    @Parameter(label = "Process", callback = "process")
    private Button btn5;

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;

    enum STATE {
        CHANGE_MAX, CHANGE_MIN, NONE
    }

    private ImagePlus videoFrame = null;
    private STATE state = STATE.NONE;
    private int max = 0;
    private int min = 0;

    @Override
    public void run() {
        IJ.setTool("rectangle"); // zoom and others get in the way
    }

    private void loadFromXML() {
        iterateOverXML(true);
    }

    private JFreeChart getHorizontalBoxChart(String name, DefaultBoxAndWhiskerCategoryDataset sourceDataset) {
        DefaultBoxAndWhiskerCategoryDataset taggedDataset = new DefaultBoxAndWhiskerCategoryDataset();

        for (int r = 0; r < sourceDataset.getRowCount(); r++) {
            for (int c = 0; c < sourceDataset.getColumnCount(); c++) {

                BoxAndWhiskerItem item = sourceDataset.getItem(r, c);
                Comparable rowKey = sourceDataset.getRowKey(r);
                Comparable colKey = sourceDataset.getColumnKey(c);

                Number median = item.getMedian();
                double medianVal = (median != null) ? median.doubleValue() : 0.0;

                String newKey = colKey.toString() + "\n" + String.format(Locale.US, "%.2f", medianVal);

                taggedDataset.add(item, rowKey, newKey);
            }
        }

        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(name, "", "", taggedDataset, false);

        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        chart.setPadding(new RectangleInsets(20.0, 20.0, 20.0, 20.0));

        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setOrientation(PlotOrientation.HORIZONTAL);

        plot.setRangeGridlineStroke(new BasicStroke(
                0.5f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));

        plot.setBackgroundPaint(Color.decode("#f0f0f0"));
        plot.setRangeGridlinePaint(Color.GRAY);

        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        domainAxis.setMaximumCategoryLabelLines(3);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        rangeAxis.setRange(0.0, 10.0);
        rangeAxis.setTickUnit(new NumberTickUnit(1.0));

        domainAxis.setCategoryMargin(0.1);
        domainAxis.setLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        rangeAxis.setLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));

        BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer() {
            // Define your colors here
            Paint[] colors = {new Color(255, 80, 80), new Color(80, 80, 255), new Color(80, 255, 80)};

            @Override
            public Paint getItemPaint(int row, int column) {
                // Use column index to pick color, cycling if we have more columns than colors
                return colors[column % colors.length];
            }
        };

        renderer.setDefaultOutlinePaint(Color.BLACK);
        renderer.setUseOutlinePaintForWhiskers(true);
        renderer.setItemMargin(0);
        renderer.setMaximumBarWidth(0.2);
        renderer.setWhiskerWidth(0.8);
        renderer.setMedianVisible(true);
        renderer.setMeanVisible(false);

        plot.setRenderer(renderer);
        return chart;
    }

    private DialChart getDialChart(String title) {
        DialChart chart =
                new DialChartBuilder()
                        .width(1600).height(1200)
                        .title(title)
                        .build();

        chart.getStyler().setChartTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));

        chart.getStyler().setDonutThickness(.33);
        chart.getStyler().setCircular(true);

        chart.getStyler().setLabelVisible(false);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setArcAngle(180);

        chart.getStyler().setDonutThickness(.33);
        chart.getStyler().setCircular(true);

        // arrow
        chart.getStyler().setArrowArcAngle(40);
        chart.getStyler().setArrowArcPercentage(.1);
        chart.getStyler().setArrowLengthPercentage(.5);
        chart.getStyler().setArrowColor(Color.decode("#01148c"));

        chart.getStyler().setLowerFrom(0);
        chart.getStyler().setLowerTo(.2);
        chart.getStyler().setLowerColor(Color.decode("#D43521"));
        chart.getStyler().setMiddleFrom(.2);
        chart.getStyler().setMiddleTo(.8);
        chart.getStyler().setMiddleColor(Color.LIGHT_GRAY);
        chart.getStyler().setUpperFrom(.8);
        chart.getStyler().setUpperTo(1);
        chart.getStyler().setUpperColor(Color.decode("#9CC300"));

        chart.getStyler().setAxisTickLabelsVisible(true);
        chart.getStyler().setAxisTicksMarksVisible(true);
        chart.getStyler().setAxisTickMarksColor(Color.DARK_GRAY);
        chart.getStyler().setAxisTickMarksStroke(new BasicStroke(2.0f));
        chart.getStyler().setAxisTitleVisible(false);
        chart.getStyler().setAxisTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        chart.getStyler().setAxisTitlePadding(60);
        chart.getStyler().setAxisTickValues(new double[]{0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1});
        chart.getStyler().setAxisTickLabels(new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"});
        return chart;
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

        List<ArrayList<Float>> floatData = iterateOverXML(false);

        String name = "Scores from " + xmlFile.getName() + " and " + videoFile.getName();

        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        ArrayList<DialChart> dialCharts = new ArrayList<>();

        ResultsTable rt = new ResultsTable();
        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing
        // NaN just gets deleted when pasting

        // Iterate through each inner list, treating it as a column.
        for (int colIndex = 0; colIndex < floatData.size(); colIndex++) {

            // Get the data for the current column.
            ArrayList<Float> columnData = floatData.get(colIndex);

            // Define a name for the column header.
            String columnHeader = "Track " + (colIndex + 1);

            ArrayList<Float> values = new ArrayList<>();
            // Iterate through the values in the current column.
            for (int rowIndex = 0; rowIndex < columnData.size(); rowIndex++) {
                float value = columnData.get(rowIndex);
                rt.setValue(columnHeader, rowIndex, value);
                values.add(value);
            }
            dataset.add(values, "All series", columnHeader);
            float median = calculateMedian(values);
            DialChart dialChart = getDialChart(columnHeader + " - " + String.format(Locale.US, "%.2f", median));
            dialChart.addSeries("Score", median * 0.1);
            dialCharts.add(dialChart);
        }

        if (this.displayPlots) {
            new ImagePlus("Plot", getHorizontalBoxChart(name, dataset).createBufferedImage(1600, 1200)).show();

            ImagePlus stack = ImagesToStack.run(
                    dialCharts.stream()
                            .map(chart -> new ImagePlus("Plot", BitmapEncoder.getBufferedImage(chart))).toArray(ImagePlus[]::new)
            );
            IJ.run(stack, "Make Montage...", "columns=" + stack.getNSlices() + " rows=1 scale=1");

        }

        rt.show(name);
    }

    public static float calculateMedian(List<Float> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List cannot be null or empty.");
        }

        // 1. Sort the list
        // Collections.sort() modifies the list in place, so we create a copy first.
        List<Float> sortedList = new ArrayList<>(list);
        Collections.sort(sortedList); // Sorts the list in ascending order

        int n = sortedList.size();

        // 2. Find the middle element(s)
        if (n % 2 != 0) {
            // Case 1: Odd length
            int middleIndex = n / 2;
            return sortedList.get(middleIndex);
        } else {
            // Case 2: Even length
            int midIndex1 = n / 2 - 1;
            int midIndex2 = n / 2;

            float val1 = sortedList.get(midIndex1);
            float val2 = sortedList.get(midIndex2);

            // Calculate the average (mean) of the two middle values
            return (val1 + val2) / 2.0f;
        }
    }

    private List<ArrayList<Float>> iterateOverXML(boolean setMinMax) {
        try {
            // Calls the new utility method to get parsed data
            XMLHelper.TrackingXMLData trackingData = XMLHelper.iterateOverXML(xmlFile, videoFrame, fixSpots);

            if (setMinMax) {
                // If setting min/max, we iterate over all points to find the bounds
                float localMin = Float.MAX_VALUE;
                float localMax = Float.MIN_VALUE;

                for (ArrayList<XMLHelper.PointData> track : trackingData.data) {
                    for (XMLHelper.PointData point : track) {
                        // PointData.x is already scaled by pixelWidth in XMLHelper,
                        // effectively converting units back to pixel coordinates.
                        if (point.x < localMin) localMin = point.x;
                        if (point.x > localMax) localMax = point.x;
                    }
                }

                // If no data was found, reset or use video bounds
                if (localMin == Float.MAX_VALUE) {
                    localMin = videoFrame.getWidth();
                    localMax = 0;
                }

                setMinMax(localMin, localMax, videoFrame.getCalibration());

                return null;
            } else {
                // Convert PointData objects to Float scores
                List<ArrayList<Float>> resultScores = new ArrayList<>();
                Calibration cal = videoFrame.getCalibration();
                float calMin = (float) cal.getX(min); // min * cal.pixelWidth
                float calMax = (float) cal.getX(max);

                for (ArrayList<XMLHelper.PointData> track : trackingData.data) {
                    ArrayList<Float> trackScores = new ArrayList<>();
                    for (XMLHelper.PointData point : track) {
                        trackScores.add(getScore(point.x, calMin, calMax));
                    }
                    resultScores.add(trackScores);
                }
                return resultScores;
            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("An error occured during processing:\n" + e.getLocalizedMessage(),
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return null;
        }

    }

    private void setMinMax(float localMin, float localMax, Calibration cal) {
//        log.info("localMin = " + localMin + " localMax = " + localMax);
//        log.info(cal.pixelWidth);
        this.max = (int) (localMin / cal.pixelWidth);
        this.min = (int) (localMax / cal.pixelWidth);
//        log.info("min = " + min + " max = " + max);
        drawOverlay();
    }

    private float getScore(float x, float calMin, float calMax) {
        float a = (x - calMin) / (calMax - calMin);
        float b = a * 10f;
        return Math.max(0, Math.min(10, b));
    }

    private void displayImage() {
        if (videoFile == null || !videoFile.exists() || !videoFile.isFile()) {
            uiService.showDialog("Could not open video: \nInvalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

//        KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow().setAlwaysOnTop(true);
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                if (videoFrame != null && videoFrame.getWindow() != null) {
                    videoFrame.close();
                }
                videoFrame = ZFHelperMethods.getFirstFrame(videoFile);
                videoFrame.setTitle("Video frame");
                uiService.show(videoFrame);

                // maybe get max and min from fish positions?
                min = (int) (videoFrame.getWidth() * 0.9);
                max = (int) (videoFrame.getWidth() * 0.1);

                // this is the nice way of doing this instead of Thread.sleep()
                ScheduledExecutorService scheduler =
                        Executors.newSingleThreadScheduledExecutor();
                scheduler.schedule(() -> {
                    if (videoFrame.getCanvas() != null) {
                        videoFrame.getCanvas().addMouseListener(this);
                        videoFrame.getCanvas().addMouseMotionListener(this);
                        drawOverlay();
                        scheduler.shutdown();
                    }
                }, 100, TimeUnit.MILLISECONDS);


            } catch (Exception e) {
                log.error(e);
                uiService.showDialog(e.getLocalizedMessage(),
                        ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
                videoFrame = null;
            }
        });

    }

    private void changeMax() {
        this.state = STATE.CHANGE_MAX;
    }

    private void changeMin() {
        this.state = STATE.CHANGE_MIN;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        Object src = e.getSource();
        if (src != videoFrame.getCanvas()) {
            return;
        }
        Point p = videoFrame.getCanvas().getCursorLoc();
        switch (this.state) {
            case CHANGE_MAX:
                videoFrame.deleteRoi(); // delete user selection (rectangle)
                this.max = p.x;
                break;
            case CHANGE_MIN:
                videoFrame.deleteRoi(); // delete user selection (rectangle)
                this.min = p.x;
                break;
            default:
                break;
        }

        drawOverlay();

    }

    private void drawOverlay() {
        if (videoFrame == null) {
            return;
        }
        Overlay overlay = videoFrame.getOverlay();
        if (overlay == null) {
            overlay = new Overlay();
            videoFrame.setOverlay(overlay);
        }

        // just deleting the whole thing
        for (int i = overlay.size() - 1; i >= 0; i--) {
            overlay.remove(i);
        }

        drawLine(min, Color.RED);
        drawLine(max, Color.BLUE);

        videoFrame.draw();
    }

    private void drawLine(int xCoordinate, Color awtColor) {
        GeneralPath path = new GeneralPath();
        path.moveTo(xCoordinate, 0f);
        path.lineTo(xCoordinate, videoFrame.getHeight());

        Roi roi = new ShapeRoi(path);
        roi.setStrokeColor(awtColor);
        roi.setStroke(new BasicStroke(5));
        roi.setName(String.valueOf(xCoordinate));
        videoFrame.getOverlay().add(roi);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        mouseDragged(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    private void stop() {
        state = STATE.NONE;
    }

}
