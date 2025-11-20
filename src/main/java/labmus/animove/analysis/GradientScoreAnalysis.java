package labmus.animove.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;
import org.w3c.dom.*;
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
@Plugin(type = Command.class, menuPath = ZFConfigs.scorePath)
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
        CHANGE_MAX, CHANGE_MIN, NONE;
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

    private void process() {
        List<ArrayList<Float>> data = iterateOverXML(false);
        ResultsTable rt = new ResultsTable();
        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing
        // NaN just gets deleted when pasting

        // Iterate through each inner list, treating it as a column.
        for (int colIndex = 0; colIndex < data.size(); colIndex++) {

            // Get the data for the current column.
            ArrayList<Float> columnData = data.get(colIndex);

            // Define a name for the column header.
            String columnHeader = "Track-" + (colIndex + 1);

            // Iterate through the values in the current column.
            for (int rowIndex = 0; rowIndex < columnData.size(); rowIndex++) {
                float value = columnData.get(rowIndex);

                rt.setValue(columnHeader, rowIndex, value);
            }
        }

        rt.show("Scores from " + xmlFile.getName().split("\\.")[0]);
    }

    private List<ArrayList<Float>> iterateOverXML(boolean setMinMax) {
        ArrayList<HashMap<Integer, Float>> trackScores = new ArrayList<>(); // the easy way not the right way
        try {
            if (!xmlFile.exists()) {
                throw new Exception("Input file does not exist.");
            }
            if (videoFrame == null) {
                throw new Exception("No available video frame.");
            }

            Document doc = getXML();
            if (doc.getElementsByTagName("Tracks").getLength() < 1) {
                throw new Exception("Wrong XML file.");
            }

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

            // Iterate over each <particle> element
            for (int i = 0; i < particleList.getLength(); i++) {
                HashMap<Integer, Float> scores = new HashMap<>();
//                ArrayList<Float> scores = new ArrayList<>();
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
                            String xValue = detectionElement.getAttribute("x");
                            float x = Float.parseFloat(xValue);
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
                log.info("localMin = " + localMin + " localMax = " + localMax);
                log.info(cal.pixelWidth);
                this.max = (int) (localMin / cal.pixelWidth);
                this.min = (int) (localMax / cal.pixelWidth);
                log.info("min = " + min + " max = " + max);
                drawOverlay();
            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog(e.getLocalizedMessage(),
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            state = STATE.NONE;
        }

        return fixMissingSpots(trackScores);

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
        KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow().setAlwaysOnTop(true);
        try {
            if (!videoFile.exists()) {
                throw new Exception("Input file does not exist.");
            }

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
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
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

        addRoi(min, Color.RED);
        addRoi(max, Color.BLUE);

        videoFrame.draw();
    }

    private void addRoi(int value, Color awtColor) {
        GeneralPath path = new GeneralPath();
        path.moveTo(value, 0f);
        path.lineTo(value, videoFrame.getHeight());

        Roi roi = new ShapeRoi(path);
        roi.setStrokeColor(awtColor);
        roi.setStroke(new BasicStroke(5));
        roi.setName(String.valueOf(value));
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
