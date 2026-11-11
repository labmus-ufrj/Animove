package labmus.animove.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.GeneralPath;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.cutTracksPath)
public class CutTracks implements Command, Interactive, MouseListener, MouseMotionListener {

    static {
        // Reduce FFmpegFrameGrabber loading time
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
    }

    @Parameter(label = "XML File", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File xmlFile;

    @Parameter(label = "Output XML File", style = FileWidget.SAVE_STYLE, persist = false, required = false)
    private File outputXmlFile;

    @Parameter(label = "Source Video", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File videoFile;

    @Parameter(label = "Analysis Axis", choices = {"X", "Y"}, persist = true)
    private String axis = "Y";

    @Parameter(label = "Open Frame", callback = "displayImage")
    private Button btn;

    @Parameter(label = "Change Threshold Value", callback = "changeTop")
    private Button btn2;

    @Parameter(label = "Stop", callback = "stop")
    private Button btn3;

    @Parameter(label = "Process", callback = "process")
    private Button btn4;

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;

    enum STATE { CHANGE_TOP, NONE }

    private ImagePlus videoFrame = null;
    private STATE state = STATE.NONE;
    private int top = 0;

    @Override
    public void run() {
        IJ.setTool("rectangle");
    }

    private void changeTop() {
        this.state = STATE.CHANGE_TOP;
    }

    private void stop() {
        this.state = STATE.NONE;
    }

    // -------------------------------------------------------------------------
    // Video frame / overlay
    // -------------------------------------------------------------------------

    private void displayImage() {
        if (videoFile == null || !videoFile.exists() || !videoFile.isFile()) {
            uiService.showDialog("Could not open video: \nInvalid file",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                if (videoFrame != null && videoFrame.getWindow() != null) {
                    videoFrame.close();
                }
                videoFrame = ZFHelperMethods.getFirstFrame(videoFile);
                videoFrame.setTitle("Video frame");

                applyXmlCalibration(xmlFile, videoFrame);
                uiService.show(videoFrame);

                // Default top = 10% from the top edge (or left edge if axis = X)
                if (axis.equals("X")) {
                    top = (int) (videoFrame.getWidth() * 0.1);
                } else {
                    top = (int) (videoFrame.getHeight() * 0.1);
                }

                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
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

    @Override
    public void mouseDragged(MouseEvent e) {
        if (videoFrame == null || e.getSource() != videoFrame.getCanvas()) return;
        Point p = videoFrame.getCanvas().getCursorLoc();
        int coord = axis.equals("X") ? p.x : p.y;

        if (state == STATE.CHANGE_TOP) {
            videoFrame.deleteRoi();
            this.top = coord;
        }
        drawOverlay();
    }

    private void drawOverlay() {
        if (videoFrame == null) return;
        Overlay overlay = videoFrame.getOverlay();
        if (overlay == null) {
            overlay = new Overlay();
            videoFrame.setOverlay(overlay);
        }
        overlay.clear();
        drawLine(top, Color.GREEN, axis.equals("X"));
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
        roi.setName("Top: " + coordinate);
        videoFrame.getOverlay().add(roi);
    }

    @Override public void mousePressed(MouseEvent e) { mouseDragged(e); }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseMoved(MouseEvent e) {}

    // -------------------------------------------------------------------------
    // Processing
    // -------------------------------------------------------------------------

    private void process() {
        // 1) A frame MUST be open first (mirrors GradientScoreAnalysis behaviour)
        if (videoFrame == null || videoFrame.getWindow() == null) {
            uiService.showDialog(
                    "Click \"Open Frame\" button first.",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        // 2) XML must be a valid file
        if (xmlFile == null || !xmlFile.exists()) {
            uiService.showDialog(
                    "Invalid XML file",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        // 3) Output must be set
        if (outputXmlFile == null) {
            uiService.showDialog(
                    "Please select an output XML file.",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        // 4) Image calibration must match the XML calibration
        //    (unit + pixel sizes). If not, tell the user to calibrate / reopen.
        String mismatch = describeCalibrationMismatch(videoFrame, xmlFile);
        if (mismatch != null) {
            uiService.showDialog(
                    "The opened frame is not calibrated consistently with the XML file.\n\n"
                            + mismatch + "\n\n"
                            + "Re-open the frame (\"Open Frame\" re-applies the XML calibration) "
                            + "or manually calibrate the image via Image > Properties so that "
                            + "unit and pixel sizes match the XML, then try again.",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
            doc.getDocumentElement().normalize();

            // Fallback: derive a reasonable top if user never set it
            if (top == 0) {
                NodeList imageDataList = doc.getElementsByTagName("ImageData");
                if (imageDataList.getLength() > 0) {
                    Element imageData = (Element) imageDataList.item(0);
                    int w = Integer.parseInt(imageData.getAttribute("width"));
                    int h = Integer.parseInt(imageData.getAttribute("height"));
                    top = axis.equals("X") ? (int) (w * 0.1) : (int) (h * 0.1);
                    log.info("Top not set by user - defaulting to " + top + " (10% of image dimension).");
                }
            }

            Calibration imgCal = videoFrame.getCalibration();
            if (imgCal == null) {
                uiService.showDialog("Image has no calibration.", ZFConfigs.pluginName,
                        DialogPrompt.MessageType.ERROR_MESSAGE);
                return;
            }
            double pxToUnit   = axis.equals("X") ? imgCal.pixelWidth : imgCal.pixelHeight;
            String unitLabel  = axis.equals("X") ? imgCal.getXUnit()   : imgCal.getYUnit();
            if (pxToUnit <= 0) {
                uiService.showDialog(
                        "Image calibration has an invalid pixel size (" + pxToUnit
                                + "). Please recalibrate the image.",
                        ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
                return;
            }
            final float topInXmlUnits = (float) (top * pxToUnit);
            log.info("Top = " + top + " px → " + topInXmlUnits + " "
                    + (unitLabel == null || unitLabel.isEmpty() ? "<unit>" : unitLabel)
                    + " (axis " + axis + ", pixelSize=" + pxToUnit + ")");

            // ------------------------------------------------------------------
            // Build spot map from <AllSpots>
            // ------------------------------------------------------------------
            Map<String, SpotInfo> spotMap = new HashMap<>();
            NodeList spotsInFrameList = doc.getElementsByTagName("SpotsInFrame");
            for (int i = 0; i < spotsInFrameList.getLength(); i++) {
                Element sif = (Element) spotsInFrameList.item(i);
                NodeList spots = sif.getElementsByTagName("Spot");
                for (int j = 0; j < spots.getLength(); j++) {
                    Element spot = (Element) spots.item(j);
                    String id = spot.getAttribute("ID");
                    int frame = Integer.parseInt(spot.getAttribute("FRAME"));
                    float x = Float.parseFloat(spot.getAttribute("POSITION_X"));
                    float y = Float.parseFloat(spot.getAttribute("POSITION_Y"));
                    spotMap.put(id, new SpotInfo(id, frame, x, y, spot));
                }
            }

            // ------------------------------------------------------------------
            // Iterate tracks - find cutoff per track
            // ------------------------------------------------------------------
            NodeList tracks = doc.getElementsByTagName("Track");
            Set<String> removedSpotIds = new HashSet<>();
            List<Element> emptyTracks = new ArrayList<>();

            for (int t = 0; t < tracks.getLength(); t++) {
                Element track = (Element) tracks.item(t);
                String trackId = track.getAttribute("TRACK_ID");

                // Collect all spot IDs belonging to this track (via edges)
                NodeList edges = track.getElementsByTagName("Edge");
                Set<String> trackSpotIds = new LinkedHashSet<>();
                for (int e = 0; e < edges.getLength(); e++) {
                    Element edge = (Element) edges.item(e);
                    trackSpotIds.add(edge.getAttribute("SPOT_SOURCE_ID"));
                    trackSpotIds.add(edge.getAttribute("SPOT_TARGET_ID"));
                }
                if (trackSpotIds.isEmpty()) continue;

                List<SpotInfo> trackSpots = new ArrayList<>();
                for (String id : trackSpotIds) {
                    SpotInfo info = spotMap.get(id);
                    if (info != null) trackSpots.add(info);
                }
                trackSpots.sort(Comparator.comparingInt(s -> s.frame));

                // Find FIRST frame where animal crosses the "top" line.
                // axis X  ->  compare POSITION_X to top (left)
                // axis Y  ->  compare POSITION_Y to top (top of image = small Y)
                int cutoffFrame = Integer.MAX_VALUE;
                for (SpotInfo s : trackSpots) {
                    float val = axis.equals("X") ? s.x : s.y;
                    if (val <= topInXmlUnits) {
                        cutoffFrame = s.frame;
                        break; // once reached, the rest is cut, regardless of what happens later
                    }
                }

                // Mark spots after cutoff for removal
                Set<String> toRemove = new HashSet<>();
                for (SpotInfo s : trackSpots) {
                    if (s.frame > cutoffFrame) {
                        toRemove.add(s.id);
                        removedSpotIds.add(s.id);
                    }
                }

                if (toRemove.isEmpty()) continue; // nothing to do for this track

                // Safety: never empty a track
                if (toRemove.size() == trackSpots.size()) {
                    log.warn("Track " + trackId + " would lose all spots. Skipping.");
                    continue;
                }

                // Remove edges that touch a removed spot
                List<Element> edgesToRemove = new ArrayList<>();
                for (int e = 0; e < edges.getLength(); e++) {
                    Element edge = (Element) edges.item(e);
                    String src = edge.getAttribute("SPOT_SOURCE_ID");
                    String tgt = edge.getAttribute("SPOT_TARGET_ID");
                    if (toRemove.contains(src) || toRemove.contains(tgt)) {
                        edgesToRemove.add(edge);
                    }
                }
                for (Element edge : edgesToRemove) {
                    edge.getParentNode().removeChild(edge);
                }

                // Update track attributes that are easy / meaningful
                List<SpotInfo> remaining = new ArrayList<>();
                for (SpotInfo s : trackSpots) {
                    if (!toRemove.contains(s.id)) remaining.add(s);
                }

                track.setAttribute("NUMBER_SPOTS", String.valueOf(remaining.size()));

                if (!remaining.isEmpty()) {
                    int newStop = remaining.get(remaining.size() - 1).frame;
                    double trackStart = Double.parseDouble(track.getAttribute("TRACK_START"));
                    track.setAttribute("TRACK_STOP", String.valueOf((double) newStop));
                    track.setAttribute("TRACK_DURATION", String.valueOf((double) newStop - trackStart));

                    // Recompute track mean location (cheap & accurate)
                    float sumX = 0f, sumY = 0f;
                    for (SpotInfo s : remaining) { sumX += s.x; sumY += s.y; }
                    track.setAttribute("TRACK_X_LOCATION", String.valueOf(sumX / remaining.size()));
                    track.setAttribute("TRACK_Y_LOCATION", String.valueOf(sumY / remaining.size()));
                }

                log.info("Track " + trackId + ": cutoff at frame " + cutoffFrame
                        + " (top=" + topInXmlUnits + " " + unitLabel + ", axis=" + axis + "),"
                        + " removed " + toRemove.size() + " spot(s), "
                        + remaining.size() + " remaining.");
            }

            // ------------------------------------------------------------------
            // Remove empty tracks from <AllTracks> and <FilteredTracks>
            // ------------------------------------------------------------------
            for (Element track : emptyTracks) {
                String trackId = track.getAttribute("TRACK_ID");
                track.getParentNode().removeChild(track);

                NodeList filtered = doc.getElementsByTagName("TrackID");
                List<Element> toDrop = new ArrayList<>();
                for (int i = 0; i < filtered.getLength(); i++) {
                    Element tid = (Element) filtered.item(i);
                    if (tid.getAttribute("TRACK_ID").equals(trackId)) toDrop.add(tid);
                }
                for (Element tid : toDrop) tid.getParentNode().removeChild(tid);
            }

            // ------------------------------------------------------------------
            // Remove spots from <AllSpots>
            // ------------------------------------------------------------------
            for (String id : removedSpotIds) {
                SpotInfo info = spotMap.get(id);
                if (info != null && info.spot.getParentNode() != null) {
                    info.spot.getParentNode().removeChild(info.spot);
                }
            }

            // Remove now-empty <SpotsInFrame> nodes
            List<Element> emptyFrames = new ArrayList<>();
            for (int i = 0; i < spotsInFrameList.getLength(); i++) {
                Element sif = (Element) spotsInFrameList.item(i);
                if (sif.getElementsByTagName("Spot").getLength() == 0) emptyFrames.add(sif);
            }
            for (Element sif : emptyFrames) sif.getParentNode().removeChild(sif);

            // Update nspots on <AllSpots>
            NodeList allSpotsList = doc.getElementsByTagName("AllSpots");
            if (allSpotsList.getLength() > 0) {
                Element allSpots = (Element) allSpotsList.item(0);
                int newTotal = 0;
                NodeList sif = allSpots.getElementsByTagName("SpotsInFrame");
                for (int i = 0; i < sif.getLength(); i++) {
                    newTotal += ((Element) sif.item(i)).getElementsByTagName("Spot").getLength();
                }
                allSpots.setAttribute("nspots", String.valueOf(newTotal));
            }

            // ------------------------------------------------------------------
            // Write output XML
            // ------------------------------------------------------------------
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(doc), new StreamResult(outputXmlFile));

            uiService.showDialog(
                    "Removed " + removedSpotIds.size() + " spot(s).\nSaved to:\n"
                            + outputXmlFile.getAbsolutePath(),
                    ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog(
                    "An error occurred during processing:\n" + e.getLocalizedMessage(),
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // XML calibration (identical to GradientScoreAnalysis)
    // -------------------------------------------------------------------------

    /** Extracts pixel size + spatial unit from a TrackMate XML file. */
    private XmlCalibration extractXmlCalibration(File xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml);
        doc.getDocumentElement().normalize();

        double pWidth = 1.0, pHeight = 1.0;
        String unit = "";

        NodeList imageDataList = doc.getElementsByTagName("ImageData");
        if (imageDataList.getLength() > 0) {
            Element imageData = (Element) imageDataList.item(0);
            String pw = imageData.getAttribute("pixelwidth");
            String ph = imageData.getAttribute("pixelheight");
            String su = imageData.getAttribute("spatialunits");
            if (pw != null && !pw.isEmpty()) pWidth  = Double.parseDouble(pw.replace(',', '.'));
            if (ph != null && !ph.isEmpty()) pHeight = Double.parseDouble(ph.replace(',', '.'));
            if (su != null && !su.isEmpty()) unit    = su;
        }
        if (unit.isEmpty()) {
            NodeList modelList = doc.getElementsByTagName("Model");
            if (modelList.getLength() > 0) {
                unit = ((Element) modelList.item(0)).getAttribute("spatialunits");
            }
        }
        if (unit == null || unit.isEmpty()) unit = "mm"; // same fallback as applyXmlCalibration

        return new XmlCalibration(pWidth, pHeight, unit);
    }

    /**
     * Returns null if the image calibration matches the XML calibration,
     * or a human-readable description of the mismatch otherwise.
     */
    private String describeCalibrationMismatch(ImagePlus img, File xml) {
        if (img == null || xml == null || !xml.exists()) return "Image or XML file missing.";

        XmlCalibration xmlCal;
        try {
            xmlCal = extractXmlCalibration(xml);
        } catch (Exception e) {
            log.error("Could not read XML calibration: " + e.getMessage());
            return "Could not read calibration from XML: " + e.getLocalizedMessage();
        }

        Calibration imgCal = img.getCalibration();
        if (imgCal == null) {
            return "The opened frame has no calibration.";
        }

        String imgUnit = imgCal.getXUnit() == null ? "" : imgCal.getXUnit();

        boolean unitMatches = xmlCal.unit.equalsIgnoreCase(imgUnit);
        boolean pwMatches   = Math.abs(imgCal.pixelWidth  - xmlCal.pixelWidth)  < 1e-6;
        boolean phMatches   = Math.abs(imgCal.pixelHeight - xmlCal.pixelHeight) < 1e-6;

        if (unitMatches && pwMatches && phMatches) return null;

        StringBuilder sb = new StringBuilder();
        if (!unitMatches) {
            sb.append("• Unit mismatch: XML = \"").append(xmlCal.unit)
                    .append("\", image = \"").append(imgUnit.isEmpty() ? "<none>" : imgUnit).append("\"\n");
        }
        if (!pwMatches || !phMatches) {
            sb.append("• Pixel size mismatch: XML = ")
                    .append(xmlCal.pixelWidth).append(" × ").append(xmlCal.pixelHeight)
                    .append(", image = ")
                    .append(imgCal.pixelWidth).append(" × ").append(imgCal.pixelHeight)
                    .append(" (px).\n");
        }
        return sb.toString().trim();
    }

    /** Small immutable holder for XML-side calibration values. */
    private static class XmlCalibration {
        final double pixelWidth;
        final double pixelHeight;
        final String unit;
        XmlCalibration(double pw, double ph, String u) {
            this.pixelWidth = pw;
            this.pixelHeight = ph;
            this.unit = u;
        }
    }

    private void applyXmlCalibration(File xmlFile, ImagePlus img) {
        if (xmlFile == null || !xmlFile.exists() || img == null) return;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
            doc.getDocumentElement().normalize();

            double pWidth = 1.0, pHeight = 1.0;
            String unit = "";

            NodeList imageDataList = doc.getElementsByTagName("ImageData");
            if (imageDataList.getLength() > 0) {
                Element imageData = (Element) imageDataList.item(0);
                String pw = imageData.getAttribute("pixelwidth");
                String ph = imageData.getAttribute("pixelheight");
                String su = imageData.getAttribute("spatialunits");
                if (pw != null && !pw.isEmpty()) pWidth = Double.parseDouble(pw.replace(',', '.'));
                if (ph != null && !ph.isEmpty()) pHeight = Double.parseDouble(ph.replace(',', '.'));
                if (su != null && !su.isEmpty()) unit = su;
            }
            if (unit.isEmpty()) {
                NodeList modelList = doc.getElementsByTagName("Model");
                if (modelList.getLength() > 0) unit = ((Element) modelList.item(0)).getAttribute("spatialunits");
            }
            if (unit.isEmpty()) {
                NodeList trackList = doc.getElementsByTagName("Tracks");
                if (trackList.getLength() > 0) unit = ((Element) trackList.item(0)).getAttribute("spaceUnits");
            }
            if (unit == null || unit.isEmpty()) {
                log.warn("No calibration unit found in XML. Using default: mm.");
                unit = "mm";
            }

            Calibration cal = img.getCalibration();
            if (cal == null) { cal = new Calibration(); img.setCalibration(cal); }
            cal.pixelWidth = pWidth;
            cal.pixelHeight = pHeight;
            cal.setXUnit(unit);
            cal.setYUnit(unit);

            log.info("Applied calibration: " + pWidth + "x" + pHeight + " " + unit);
        } catch (Exception e) {
            log.error("Calibration error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private static class SpotInfo {
        final String id;
        final int frame;
        final float x, y;
        final Element spot;
        SpotInfo(String id, int frame, float x, float y, Element spot) {
            this.id = id; this.frame = frame; this.x = x; this.y = y; this.spot = spot;
        }
    }
}