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
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.statistics.BoxAndWhiskerCategoryDataset;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.GeneralPath;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private JFreeChart getHorizontalBoxChart(String name, BoxAndWhiskerCategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(name, "", "", dataset, false);

        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));

        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setOrientation(PlotOrientation.HORIZONTAL);

        plot.setRangeGridlineStroke(new BasicStroke(
                1.5f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));

        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));

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
        chart.getStyler().setAxisTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        chart.getStyler().setAxisTitlePadding(60);
        chart.getStyler().setAxisTickValues(new double[]{0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1});
        chart.getStyler().setAxisTickLabels(new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"});
        return chart;
    }

    private void process() {
        List<ArrayList<Float>> data = iterateOverXML(false);
        if (data == null) {
            return;
        }
        String name = "Scores from " + xmlFile.getName() + " and " + videoFile.getName();

        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        ArrayList<DialChart> dialCharts = new ArrayList<>();

        ResultsTable rt = new ResultsTable();
        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing
        // NaN just gets deleted when pasting

        // Iterate through each inner list, treating it as a column.
        for (int colIndex = 0; colIndex < data.size(); colIndex++) {

            // Get the data for the current column.
            ArrayList<Float> columnData = data.get(colIndex);

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
            DialChart dialChart = getDialChart(columnHeader);
            dialChart.addSeries("Score", calculateMedian(values) * 0.1);
            dialCharts.add(dialChart);
        }

        if (this.displayPlots) {
            new ImagePlus("Plot", getHorizontalBoxChart(name, dataset).createBufferedImage(1600, 1200)).show();

            ImagePlus stack = ImagesToStack.run(
                    dialCharts.stream()
                            .map(chart -> new ImagePlus("Plot", BitmapEncoder.getBufferedImage(chart))).toArray(ImagePlus[]::new)
            );
            stack.show();

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
        if (!checkFiles()) {
            return null;
        }
        ArrayList<HashMap<Integer, Float>> trackScores = new ArrayList<>(); // the easy way not the right way
        try {
            if (videoFrame == null) {
                throw new Exception("No available video frame, click \"Open Frame\" first.");
            }

            Document doc = getXML();
            if (doc.getElementsByTagName("Tracks").getLength() > 0) {
                fromTracksXML(setMinMax, doc, trackScores);
            } else if (doc.getElementsByTagName("Model").getLength() > 0) {
                fromFullXML(setMinMax, doc, trackScores);
            } else {
                throw new Exception("Wrong XML file.");
            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog(e.getLocalizedMessage(),
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            state = STATE.NONE;
        }

        return fixMissingSpots(trackScores);

    }

    private static class SpotData {
        public final float score;
        public final float x;
        public final int frame;

        SpotData(Float score, Float x, String frame) {
            this.score = score;
            this.x = x;
            this.frame = Integer.parseInt(frame);
        }
    }

    private void fromFullXML(boolean setMinMax, Document doc, ArrayList<HashMap<Integer, Float>> trackScores) throws Exception {
        /*
            data is stored as Spots:
            <Spot ID="176402" FRAME="0" POSITION_X="465.7599892443151" ... />

            and in Edges within Tracks:
            <Track ...>
                <Edge SPOT_SOURCE_ID="177087" SPOT_TARGET_ID="177083" ... />
            </Track>
         */
        Node tracks = doc.getElementsByTagName("Model").item(0);
        Calibration cal = videoFrame.getCalibration();

        String xmlUnit = tracks.getAttributes().getNamedItem("spatialunits").getNodeValue();
        if (!Objects.equals(cal.getXUnit(), xmlUnit)) {
            throw new Exception("Calibrate the image frame to match the XML file's space units: " + xmlUnit);
        }
        float calMin = (float) cal.getX(min); // min * cal.pixelWidth
        float calMax = (float) cal.getX(max);

        float localMin = videoFrame.getWidth();
        float localMax = 0;

        // mapping ID to spot
        HashMap<Integer, SpotData> spotMap = new HashMap<>();
        NodeList xmlSpotList = doc.getElementsByTagName("Spot");
        for (int i = 0; i < xmlSpotList.getLength(); i++) {
            Node spot = xmlSpotList.item(i);
            if (spot.getNodeType() == Node.ELEMENT_NODE) {
                float x = Float.parseFloat(spot.getAttributes().getNamedItem("POSITION_X").getNodeValue());
                float score = getScore(x, calMin, calMax);
                spotMap.put(Integer.parseInt(spot.getAttributes().getNamedItem("ID").getNodeValue()),
                        new SpotData(score, x, spot.getAttributes().getNamedItem("FRAME").getNodeValue()));
            }
        }

        NodeList trackList = doc.getElementsByTagName("Track");
        for (int i = 0; i < trackList.getLength(); i++) {
            HashMap<Integer, Float> scores = new HashMap<>();
            Node trackNode = trackList.item(i);
            if (trackNode.getNodeType() == Node.ELEMENT_NODE) {
                Element trackElement = (Element) trackNode;

                NodeList edgeList = trackElement.getElementsByTagName("Edge");
                for (int j = 0; j < edgeList.getLength(); j++) {
                    Node edgeNode = edgeList.item(j);

                    if (edgeNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element edgeElement = (Element) edgeNode;

                        for (String s : Arrays.asList("SPOT_SOURCE_ID", "SPOT_TARGET_ID")) {
                            String attribute = edgeElement.getAttribute(s);
                            Integer spotId = Integer.parseInt(attribute);

                            SpotData spot = spotMap.get(spotId);
                            scores.put(spot.frame, spot.score);

                            // this is here so that only filtered spots get used,
                            // usually spots that are not in tracks have random undesirable positions,
                            // that threw these min and max values everywhere.
                            if (spot.x < localMin) {
                                localMin = spot.x;
                            }
                            if (spot.x > localMax) {
                                localMax = spot.x;
                            }
                        }
                    }

                }
            }
            trackScores.add(scores);
        }

        // if no tracks were found, use all spots
        if (trackScores.isEmpty()) {
            // we must average the scores in each frame
            final HashMap<Integer, List<Float>> allScores = new HashMap<>();
            for (SpotData spot : spotMap.values()) {
                if (spot.x < localMin) {
                    localMin = spot.x;
                }
                if (spot.x > localMax) {
                    localMax = spot.x;
                }
                if (!allScores.containsKey(spot.frame)) {
                    allScores.put(spot.frame, new ArrayList<>());
                }
                allScores.get(spot.frame).add(spot.score);
            }
            final HashMap<Integer, Float> scores = new HashMap<>();
            allScores.keySet().forEach(frame -> {
                Float sum = allScores.get(frame).stream().reduce(Float::sum).get(); // if we are here, there is at least one score in the list
                scores.put(frame, sum / allScores.get(frame).size());
            });
            trackScores.add(scores);
        }

        if (setMinMax) {
            setMinMax(localMin, localMax, cal);
        }

    }

    private void fromTracksXML(boolean setMinMax, Document doc, ArrayList<HashMap<Integer, Float>> trackScores) throws Exception {
         /*
            data is stored as detections within particles:
            <particle ... >
                <detection t="0" x="377.10679301985425" ... />
            </particle>
         */
        Node tracks = doc.getElementsByTagName("Tracks").item(0);
        Calibration cal = videoFrame.getCalibration();

        String xmlUnit = tracks.getAttributes().getNamedItem("spaceUnits").getNodeValue();
        if (!Objects.equals(cal.getXUnit(), xmlUnit)) {
            throw new Exception("Calibrate the image frame to match the XML file's space units: " + xmlUnit);
        }
        float calMin = (float) cal.getX(min); // min * cal.pixelWidth
        float calMax = (float) cal.getX(max);

        // Get a list of all <particle> elements
        NodeList particleList = doc.getElementsByTagName("particle");

        float localMin = videoFrame.getWidth();
        float localMax = 0;

        // Iterate over each <particle> element (iterate over each track)
        for (int i = 0; i < particleList.getLength(); i++) {
            HashMap<Integer, Float> scores = new HashMap<>();
            Node particleNode = particleList.item(i);

            if (particleNode.getNodeType() == Node.ELEMENT_NODE) {
                Element particleElement = (Element) particleNode;

                // Get all <detection> elements within the current particle
                NodeList detectionList = particleElement.getElementsByTagName("detection");

                // Iterate over each <detection> element
                for (int j = 0; j < detectionList.getLength(); j++) {
                    Node detectionNode = detectionList.item(j);

                    if (detectionNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element detectionElement = (Element) detectionNode;

                        // Get the value of the "x" attribute
                        float x = Float.parseFloat(detectionElement.getAttribute("x"));
                        if (x < localMin) {
                            localMin = x;
                        }
                        if (x > localMax) {
                            localMax = x;
                        }
                        float score = getScore(x, calMin, calMax);
                        scores.put(Integer.parseInt(detectionElement.getAttribute("t")), score);

                    }
                }
            }
            trackScores.add(scores);
        }
        if (setMinMax) {
            setMinMax(localMin, localMax, cal);
        }
    }

    private void setMinMax(float localMin, float localMax, Calibration cal) {
        log.info("localMin = " + localMin + " localMax = " + localMax);
        log.info(cal.pixelWidth);
        this.max = (int) (localMin / cal.pixelWidth);
        this.min = (int) (localMax / cal.pixelWidth);
        log.info("min = " + min + " max = " + max);
        drawOverlay();
    }

    private List<ArrayList<Float>> fixMissingSpots(ArrayList<HashMap<Integer, Float>> trackScores) {
        if (fixSpots) {
            int biggestTime = trackScores.stream().mapToInt(hashmap ->
                    hashmap.keySet().stream().max(Comparator.naturalOrder()).get()).max().getAsInt(); // using this on a spotless track will crash it. dont do it ig
            for (HashMap<Integer, Float> hashmap : trackScores) {
                for (int i = 0; i <= biggestTime; i++) {
                    if (!hashmap.containsKey(i) && hashmap.containsKey(i - 1)) {
                        hashmap.put(i, hashmap.get(i - 1));
                    }
                }
                for (int i = biggestTime; i >= 0; i--) {
                    if (!hashmap.containsKey(i)) {
                        hashmap.put(i, hashmap.get(i + 1));
                    }
                }
            }
        }
        return trackScores.stream().map(hashmap -> new ArrayList<>(hashmap.values())).collect(Collectors.toList());
    }

    private float getScore(float x, float calMin, float calMax) {
        float a = (x - calMin) / (calMax - calMin);
        float b = a * 10f;
        return Math.max(0, Math.min(10, b));
    }

    private Document getXML() throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private void displayImage() {
        if (!checkFiles()) {
            return;
        }
//        KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow().setAlwaysOnTop(true);
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

    private boolean checkFiles() {
        if (xmlFile == null || !xmlFile.exists()) {
            uiService.showDialog("Invalid XML file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        if (!videoFile.exists()) {
            uiService.showDialog("Input video does not exist", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }
        return true;
    }
}
