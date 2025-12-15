package labmus.animove.utils;

import ij.ImagePlus;
import ij.measure.Calibration;
import labmus.animove.analysis.SectorScoreAnalysis;
import org.scijava.ui.DialogPrompt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class XMLHelper {

    // we could be using records in a newer version of java maybe?
    public static class PointData {
        public final float x;
        public final float y;

        public PointData(float x, float y, double pixelWidth) {
            this.x = (float) (x / pixelWidth);
            this.y = (float) (y / pixelWidth);
        }

        @Override
        public String toString() {
            return "x: " + this.x + ", y:" + this.y;
        }
    }

    public static class SpotData {
        public final float x;
        public final float y;
        public final int frame;

        public SpotData(Float x, Float y, String frame) {
            this.x = x;
            this.y = y;
            this.frame = Integer.parseInt(frame);
        }
    }

    public static class TrackingXMLData{
        public final List<ArrayList<PointData>> data;
        public final boolean onlySpots;

        public TrackingXMLData(List<ArrayList<PointData>> data, boolean onlySpots) {
            this.data = data;
            this.onlySpots = onlySpots;
        }
    }

    public static TrackingXMLData iterateOverXML(File xmlFile, ImagePlus videoFrame, boolean fixSpots) throws Exception {
        boolean spotsOnly = false;
        Document doc = getXML(xmlFile);
        ArrayList<HashMap<Integer, PointData>> trackScores = new ArrayList<>(); // the easy way not the right way
        if (doc.getElementsByTagName("Tracks").getLength() > 0) {
            fromTracksXML(doc, trackScores, videoFrame);
        } else if (doc.getElementsByTagName("Model").getLength() > 0) {
            // if there were no tracks, this returns false
            spotsOnly = !fromFullXML(doc, trackScores, videoFrame);
        } else {
            throw new Exception("Wrong XML file.");
        }
        return fixMissingSpots(trackScores, !spotsOnly && fixSpots);
    }

    private static void fromTracksXML(Document doc, ArrayList<HashMap<Integer, XMLHelper.PointData>> trackScores, ImagePlus videoFrame) throws Exception {
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
            HashMap<Integer, XMLHelper.PointData> scores = new HashMap<>();
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
                        scores.put(Integer.parseInt(detectionElement.getAttribute("t")), new XMLHelper.PointData(x, y, cal.pixelWidth));

                    }
                }
            }
            trackScores.add(scores);
        }
    }

    private static boolean fromFullXML(Document doc, ArrayList<HashMap<Integer, XMLHelper.PointData>> trackScores, ImagePlus videoFrame) throws Exception {
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
        HashMap<Integer, XMLHelper.SpotData> spotMap = new HashMap<>();
        NodeList xmlSpotList = doc.getElementsByTagName("Spot");
        for (int i = 0; i < xmlSpotList.getLength(); i++) {
            Node spot = xmlSpotList.item(i);
            if (spot.getNodeType() == Node.ELEMENT_NODE) {
                float x = Float.parseFloat(spot.getAttributes().getNamedItem("POSITION_X").getNodeValue());
                float y = Float.parseFloat(spot.getAttributes().getNamedItem("POSITION_Y").getNodeValue());
                spotMap.put(Integer.parseInt(spot.getAttributes().getNamedItem("ID").getNodeValue()),
                        new XMLHelper.SpotData(x, y, spot.getAttributes().getNamedItem("FRAME").getNodeValue()));
            }
        }

        NodeList trackList = doc.getElementsByTagName("Track");
        for (int i = 0; i < trackList.getLength(); i++) {
            HashMap<Integer, XMLHelper.PointData> scores = new HashMap<>();
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

                            XMLHelper.SpotData spot = spotMap.get(spotId);
                            scores.put(spot.frame, new XMLHelper.PointData(spot.x, spot.y, cal.pixelWidth));
                        }
                    }

                }
            }
            trackScores.add(scores);
        }

        // if no tracks were found, use all spots
        int randId = 0; // making sure to count all spots and not override them by placing them in the same frame and same track.
        if (trackScores.isEmpty()) {
//            this.fixSpots = false; // this won't work in this case. see docs for info
            HashMap<Integer, XMLHelper.PointData> scores = new HashMap<>();
            for (XMLHelper.SpotData spot : spotMap.values()) {
                scores.put(randId, new XMLHelper.PointData(spot.x, spot.y, cal.pixelWidth));
                randId++;
            }
            trackScores.add(scores);
            return false;
        }
        return true;
    }

    private static TrackingXMLData fixMissingSpots(ArrayList<HashMap<Integer, XMLHelper.PointData>> trackScores, boolean fixSpots) {
        if (fixSpots) {
            int biggestTime = trackScores.stream().mapToInt(hashmap ->
                    hashmap.keySet().stream().max(Comparator.naturalOrder()).get()).max().getAsInt(); // using this on a spotless track will crash it. dont do it ig
            for (HashMap<Integer, XMLHelper.PointData> hashmap : trackScores) {
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
        return new TrackingXMLData(trackScores.stream().map(hashmap -> new ArrayList<>(hashmap.values())).collect(Collectors.toList()), !fixSpots);
    }

    private static Document getXML(File xmlFile) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();
        return doc;
    }
}

