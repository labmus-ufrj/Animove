package labmus.zebrafish_utils;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import net.imagej.Dataset;
import net.imagej.axis.CalibratedAxis;
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
import java.util.ArrayList;
import java.util.Objects;

@Plugin(type = Command.class, menuPath = ZFConfigs.scorePath)
public class ScoreAnalysis implements Command, Interactive, MouseListener, MouseMotionListener {

    @Parameter(label = "XML File", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File xmlFile = new File("E:\\score_auto\\conhecendo\\conhecendo.xml");

    @Parameter(label = "Source video", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File videoFile = new File("E:\\score_auto\\conhecendo\\conhecendo.mp4");

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
        if (!ZFConfigs.checkJavaCV()) {
            return; // if the user chooses to ignore this nothing will work anyway
        }
        IJ.setTool("rectangle"); // zoom and others get in the way

//        ij.IJ.run("Console");
//        IJ.run("Set Scale...", "distance=424.0755 known=130 unit=mm global");
    }

    private void loadFromXML() {
        iterateOverXML(true);
    }

    private void process() {
        ArrayList<ArrayList<Float>> data = iterateOverXML(false);
        ResultsTable rt = new ResultsTable();

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

        rt.show("My Experiment Data");
    }

    private ArrayList<ArrayList<Float>> iterateOverXML(boolean setMinMax) {
        ArrayList<ArrayList<Float>> trackScores = new ArrayList<>();
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

            cal.setFunction(Calibration.STRAIGHT_LINE, cal.getCoefficients(), "mm");

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
                ArrayList<Float> scores = new ArrayList<>();
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
                            scores.add(score);

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

        return trackScores;

    }

    private float getScore(float x, float calMin, float calMax) {
        float a = (x - calMin) / (calMax - calMin);
        return a * Float.parseFloat("10");
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
            displayFirstFrame();

            // maybe get max and min from fish positions?
            min = (int) (videoFrame.getWidth() * 0.9);
            max = (int) (videoFrame.getWidth() * 0.1);

            EventQueue.invokeLater(() -> {
                videoFrame.getCanvas().addMouseListener(this);
                videoFrame.getCanvas().addMouseMotionListener(this);
                drawOverlay();
            });
        } catch (Exception e) {
            log.error(e);
            uiService.showDialog(e.getLocalizedMessage(),
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            videoFrame = null;
        }
    }

    private void displayFirstFrame() throws Exception {
        File currentOutputFile = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis() + ".png");
        currentOutputFile.deleteOnExit();

        // Build FFmpeg command
        // Start by getting the binary path from bytedeco's lib
        // yep that's a thing, and that's how this works
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add(ZFConfigs.ffmpeg);

        commandList.add("-i");
        commandList.add("\"" + videoFile.getAbsolutePath() + "\"");

        // Set number of frames to process
        commandList.add("-vframes");
        commandList.add(String.valueOf(1));

        // Add output file
        commandList.add("\"" + currentOutputFile.getAbsolutePath() + "\"");

        log.info(commandList.toString());

        // Execute FFmpeg command
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        process.waitFor();

        if (videoFrame != null && videoFrame.getWindow() != null) {
            videoFrame.close();
        }
        videoFrame = new ImagePlus(currentOutputFile.getAbsolutePath());
        videoFrame.setTitle("Video frame");
        uiService.show(videoFrame);
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
