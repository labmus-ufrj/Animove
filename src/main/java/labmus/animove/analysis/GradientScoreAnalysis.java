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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
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

    @Parameter(label = "Analysis Axis", choices = {"X", "Y"}, persist = true)
    private String axis = "X";

    @Parameter(label = "Fix Missing Spots", persist = false)
    private boolean fixSpots = true;

    @Parameter(label = "Display Plots", persist = false)
    private boolean displayPlots = false;

    @Parameter(label = "Open Frame", callback = "displayImage")
    private Button btn;

    @Parameter(label = "Load Min/Max from XML", callback = "loadFromXML")
    private Button btn6;

    @Parameter(label = "Invert Min/Max values", callback = "invertMinMax")
    private Button btn8;

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

    private void invertMinMax() {
        int temp = max;
        max = min;
        min = temp;
        drawOverlay();
    }

    private void loadFromXML() {
        if (videoFrame != null) {
            applyXmlCalibration(xmlFile, videoFrame);
        }
        iterateOverXML(true);
    }

    private JFreeChart getHorizontalBoxChart(String name, DefaultBoxAndWhiskerCategoryDataset sourceDataset) {
        DefaultBoxAndWhiskerCategoryDataset taggedDataset = new DefaultBoxAndWhiskerCategoryDataset();

        for (int r = 0; r < sourceDataset.getRowCount(); r++) {
            for (int c = 0; c < sourceDataset.getColumnCount(); c++) {

                BoxAndWhiskerItem item = sourceDataset.getItem(r, c);

                // Reconstruct the item but pass an empty list for the outliers
                BoxAndWhiskerItem noOutlierItem = new BoxAndWhiskerItem(
                        item.getMean(),
                        item.getMedian(),
                        item.getQ1(),
                        item.getQ3(),
                        item.getMinRegularValue(),
                        item.getMaxRegularValue(),
                        item.getMinOutlier(),
                        item.getMaxOutlier(),
                        new ArrayList<>() // This empty list hides the circles
                );

                Comparable rowKey = sourceDataset.getRowKey(r);
                Comparable colKey = sourceDataset.getColumnKey(c);

                Number median = item.getMedian();
                double medianVal = (median != null) ? median.doubleValue() : 0.0;

                String newKey = colKey.toString() + "\n" + String.format(Locale.US, "%.2f", medianVal);

                taggedDataset.add(noOutlierItem, rowKey, newKey);
            }
        }

        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(name, "", "", taggedDataset, false);

        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        chart.setPadding(new RectangleInsets(20.0, 20.0, 20.0, 20.0));

        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setOrientation(axis.equals("X") ? PlotOrientation.HORIZONTAL : PlotOrientation.VERTICAL);

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

        List<Color> colors = ZFHelperMethods.generateColors(sourceDataset.getColumnCount());
        BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer() {
            @Override
            public Paint getItemPaint(int row, int column) {
                return colors.get(column % colors.size());
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
            String columnHeader = (floatData.size() == 1 ? "Series " : "Track ") + (colIndex + 1);

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
            if(stack.getNSlices() > 1){
                IJ.run(stack, "Make Montage...", "columns=" + stack.getNSlices() + " rows=1 scale=1");
            } else {
                uiService.show(stack);
            }

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
            XMLHelper.TrackingXMLData trackingData = XMLHelper.iterateOverXML(xmlFile, videoFrame, fixSpots);
            Calibration cal = videoFrame.getCalibration();
            boolean isX = axis.equals("X");

            if (setMinMax) {
                float localMin = Float.MAX_VALUE;
                float localMax = Float.MIN_VALUE;

                for (ArrayList<XMLHelper.PointData> track : trackingData.data) {
                    for (XMLHelper.PointData point : track) {
                        float val = isX ? point.x : point.y;
                        if (val < localMin) localMin = val;
                        if (val > localMax) localMax = val;
                    }
                }

                if (localMin == Float.MAX_VALUE) {
                    localMin = isX ? videoFrame.getWidth() : videoFrame.getHeight();
                    localMax = 0;
                }

                setMinMax(localMin, localMax, cal);
                return null;
            } else {
                List<ArrayList<Float>> resultScores = new ArrayList<>();
                float calMin = (float) min;
                float calMax = (float) max;

                for (ArrayList<XMLHelper.PointData> track : trackingData.data) {
                    ArrayList<Float> trackScores = new ArrayList<>();
                    for (XMLHelper.PointData point : track) {
                        float val = isX ? point.x : point.y;
                        trackScores.add(getScore(val, calMin, calMax));
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
        // PointData already divided the values by pixelWidth/pixelHeight in XMLHelper.
        // Therefore, localMin and localMax are already in pixel coordinates.
        this.max = (int) localMin;
        this.min = (int) localMax;

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

                applyXmlCalibration(xmlFile, videoFrame);

                uiService.show(videoFrame);

                // maybe get max and min from fish positions?
                if (axis.equals("X")) {
                    min = (int) (videoFrame.getWidth() * 0.9);
                    max = (int) (videoFrame.getWidth() * 0.1);
                } else {
                    min = (int) (videoFrame.getHeight() * 0.9);
                    max = (int) (videoFrame.getHeight() * 0.1);
                }

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
        if (videoFrame == null || e.getSource() != videoFrame.getCanvas()) return;

        Point p = videoFrame.getCanvas().getCursorLoc();
        int coord = axis.equals("X") ? p.x : p.y; // Logic switch based on axis

        switch (this.state) {
            case CHANGE_MAX:
                videoFrame.deleteRoi();
                this.max = coord;
                break;
            case CHANGE_MIN:
                videoFrame.deleteRoi();
                this.min = coord;
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

        overlay.clear();

        // Draw lines based on chosen axis
        if (axis.equals("X")) {
            drawLine(min, Color.RED, true);
            drawLine(max, Color.BLUE, true);
        } else {
            drawLine(min, Color.RED, false);
            drawLine(max, Color.BLUE, false);
        }

        videoFrame.draw();
    }

    private void drawLine(int coordinate, Color awtColor, boolean isVertical) {
        GeneralPath path = new GeneralPath();
        if (isVertical) {
            path.moveTo(coordinate, 0f);
            path.lineTo(coordinate, videoFrame.getHeight());
        } else {
            path.moveTo(0f, coordinate);
            path.lineTo(videoFrame.getWidth(), coordinate);
        }

        Roi roi = new ShapeRoi(path);
        roi.setStrokeColor(awtColor);
        roi.setStroke(new BasicStroke(5));
        roi.setName(String.valueOf(coordinate));
        videoFrame.getOverlay().add(roi);
    }


    private void applyXmlCalibration(File xmlFile, ImagePlus img) {
        if (xmlFile == null || !xmlFile.exists() || img == null) return;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
            doc.getDocumentElement().normalize();

            double pWidth = 1.0;
            double pHeight = 1.0;
            String unit = "";

            NodeList imageDataList = doc.getElementsByTagName("ImageData");
            if (imageDataList.getLength() > 0) {
                Element imageData = (Element) imageDataList.item(0);
                String pw = imageData.getAttribute("pixelwidth");
                String ph = imageData.getAttribute("pixelheight");
                String su = imageData.getAttribute("spatialunits");

                if (pw != null && !pw.isEmpty()) {
                    pWidth = Double.parseDouble(pw.replace(',', '.'));
                }
                if (ph != null && !ph.isEmpty()) {
                    pHeight = Double.parseDouble(ph.replace(',', '.'));
                }
                if (su != null && !su.isEmpty()) {
                    unit = su;
                }
            }

            if (unit.isEmpty()) {
                NodeList modelList = doc.getElementsByTagName("Model");
                if (modelList.getLength() > 0) {
                    unit = ((Element) modelList.item(0)).getAttribute("spatialunits");
                }
            }
            if (unit.isEmpty()) {
                NodeList trackList = doc.getElementsByTagName("Tracks");
                if (trackList.getLength() > 0) {
                    unit = ((Element) trackList.item(0)).getAttribute("spaceUnits");
                }
            }

            if (unit == null || unit.isEmpty()) {
                log.warn("No calibration unit found in XML. Using default: mm. This may be incorrect.");
                unit = "mm";
            }

            Calibration cal = img.getCalibration();
            if (cal == null) {
                cal = new Calibration();
                img.setCalibration(cal);
            }
            cal.pixelWidth = pWidth;
            cal.pixelHeight = pHeight;
            cal.setXUnit(unit);
            cal.setYUnit(unit);

            log.info("Successfully applied calibration: " + pWidth + "x" + pHeight + " " + unit);

        } catch (Exception e) {
            log.error("Calibration error: " + e.getMessage());
        }
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
