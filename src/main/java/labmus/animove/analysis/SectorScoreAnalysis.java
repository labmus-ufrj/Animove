package labmus.animove.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
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
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.scoreSectorPath)
public class SectorScoreAnalysis implements Command, Interactive {
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
    private boolean displayPlots = true;

    @Parameter(label = "Open Frame", callback = "openFrame")
    private Button btn;

    @Parameter(label = "Process", callback = "process")
    private Button btn5;

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;

    private ImagePlus videoFrame = null;

    private static class PointData {
        public final float x;
        public final float y;

        private PointData(float x, float y, double pixelWidth) {
            this.x = (float) (x / pixelWidth);
            this.y = (float) (y / pixelWidth);
        }
    }

    @Override
    public void run() {
        IJ.setTool("rectangle"); // to create ROIs
    }

    private void process() {
        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }

        if (roiManager.getCount() < 1) {
            uiService.showDialog("No ROIs in RoiManager. At least one is expected.", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        List<ArrayList<PointData>> data = iterateOverXML();
        if (data == null) {
            return;
        }

        String name = "Scores from " + xmlFile.getName() + " and " + videoFile.getName();

        PieChart chart =
                new PieChartBuilder().theme(Styler.ChartTheme.GGPlot2).title(name).build();

        // Customize Chart
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideS);
        chart.getStyler().setLegendLayout(Styler.LegendLayout.Horizontal);

        chart.getStyler().setChartTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        chart.getStyler().setLegendFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        chart.getStyler().setAnnotationsFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));

        ResultsTable rt = new ResultsTable();
        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing
        // NaN just gets deleted when pasting

        int globalCount = 0;
        for (int i = 0; i < roiManager.getCount(); i++) {
            Roi roi = roiManager.getRoi(i);
            int count = 0;
            for (ArrayList<PointData> list : data) {
                for (PointData pointData : list) {
                    if (roi.contains((int) pointData.x, (int) pointData.y)) {
                        count++;
                    }
                }
            }
            globalCount += count;
            rt.setValue("ROI Name", i, roi.getName());
            rt.setValue("Count", i, count);
            chart.addSeries(roi.getName(), count);
        }

        int lastRowIndex = roiManager.getCount();
        rt.setValue("ROI Name", lastRowIndex, "Outside All ROIs");

        Integer spotsCount = data.stream()
                .map(ArrayList::size)
                .reduce(Integer::sum).get(); // there'll always be spots. hopefully.

        rt.setValue("Count", lastRowIndex, spotsCount - globalCount);
        chart.addSeries("Outside All ROIs", spotsCount - globalCount);

        if (this.displayPlots){
            new ImagePlus("Plot", BitmapEncoder.getBufferedImage(chart)).show();
        }

        rt.show(name);
    }

    private List<ArrayList<PointData>> iterateOverXML() {
        if (xmlFile == null || !xmlFile.exists()) {
            uiService.showDialog("Invalid XML file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return null;
        }
        ArrayList<HashMap<Integer, PointData>> trackScores = new ArrayList<>(); // the easy way not the right way
        try {
            Document doc = getXML();
            if (doc.getElementsByTagName("Tracks").getLength() > 0) {
                fromTracksXML(doc, trackScores);
            } else if (doc.getElementsByTagName("Model").getLength() > 0) {
                fromFullXML(doc, trackScores);
            } else {
                throw new Exception("Wrong XML file.");
            }
        } catch (Exception e) {
            log.error(e);
            uiService.showDialog(e.getLocalizedMessage(),
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
        }
        return fixMissingSpots(trackScores);

    }

    private static class SpotData {
        public final float x;
        public final float y;
        public final int frame;

        SpotData(Float x, Float y, String frame) {
            this.x = x;
            this.y = y;
            this.frame = Integer.parseInt(frame);
        }
    }

    private void fromFullXML(Document doc, ArrayList<HashMap<Integer, PointData>> trackScores) throws Exception {
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

        // mapping ID to spot
        HashMap<Integer, SpotData> spotMap = new HashMap<>();
        NodeList xmlSpotList = doc.getElementsByTagName("Spot");
        for (int i = 0; i < xmlSpotList.getLength(); i++) {
            Node spot = xmlSpotList.item(i);
            if (spot.getNodeType() == Node.ELEMENT_NODE) {
                float x = Float.parseFloat(spot.getAttributes().getNamedItem("POSITION_X").getNodeValue());
                float y = Float.parseFloat(spot.getAttributes().getNamedItem("POSITION_Y").getNodeValue());
                spotMap.put(Integer.parseInt(spot.getAttributes().getNamedItem("ID").getNodeValue()),
                        new SpotData(x, y, spot.getAttributes().getNamedItem("FRAME").getNodeValue()));
            }
        }

        NodeList trackList = doc.getElementsByTagName("Track");
        for (int i = 0; i < trackList.getLength(); i++) {
            HashMap<Integer, PointData> scores = new HashMap<>();
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
                            scores.put(spot.frame, new PointData(spot.x, spot.y, cal.pixelWidth));
                        }
                    }

                }
            }
            trackScores.add(scores);
        }

        // if no tracks were found, use all spots
        int randId = 0; // making sure to count all spots and not override them by placing them in the same frame and same track.
        if (trackScores.isEmpty()) {
            this.fixSpots = false; // this won't work in this case. see docs for info
            HashMap<Integer, PointData> scores = new HashMap<>();
            for (SpotData spot : spotMap.values()) {
                scores.put(randId, new PointData(spot.x, spot.y, cal.pixelWidth));
                randId++;
            }
            trackScores.add(scores);
        }
    }

    private void fromTracksXML(Document doc, ArrayList<HashMap<Integer, PointData>> trackScores) throws Exception {
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

        // Get a list of all <particle> elements
        NodeList particleList = doc.getElementsByTagName("particle");

        // Iterate over each <particle> element (iterate over each track)
        for (int i = 0; i < particleList.getLength(); i++) {
            HashMap<Integer, PointData> scores = new HashMap<>();
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

                        float x = Float.parseFloat(detectionElement.getAttribute("x"));
                        float y = Float.parseFloat(detectionElement.getAttribute("y"));
                        scores.put(Integer.parseInt(detectionElement.getAttribute("t")), new PointData(x, y, cal.pixelWidth));

                    }
                }
            }
            trackScores.add(scores);
        }
    }

    private List<ArrayList<PointData>> fixMissingSpots(ArrayList<HashMap<Integer, PointData>> trackScores) {
        if (this.fixSpots) {
            int biggestTime = trackScores.stream().mapToInt(hashmap ->
                    hashmap.keySet().stream().max(Comparator.naturalOrder()).get()).max().getAsInt(); // using this on a spotless track will crash it. dont do it ig
            for (HashMap<Integer, PointData> hashmap : trackScores) {
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

    private void openFrame() {
        if (videoFile == null || !videoFile.exists() || !videoFile.isFile()) {
            uiService.showDialog("Could not open video: \n Invalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        String extension = videoFile.getName().substring(videoFile.getName().lastIndexOf(".") + 1).toLowerCase();
        if (!extension.contentEquals("avi") && !extension.contentEquals("mp4")) {
            uiService.showDialog("Could not open video: \n Invalid extension ." + extension, ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
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

                RoiManager roiManager = RoiManager.getInstance();
                if (roiManager == null) {
                    roiManager = new RoiManager();
                }
                roiManager.runCommand("Show All");

            } catch (Exception e) {
                log.error(e);
                uiService.showDialog("Could not open video: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            }
        });
    }

}
