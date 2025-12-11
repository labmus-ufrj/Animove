package labmus.animove.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.XMLHelper;
import org.jfree.data.general.DefaultPieDataset;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

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

        data.forEach(list -> log.info(list.size()));

        log.info("-----------------");

        log.info(data.get(0).get(0) + " - " + data.get(0).get(1) + " - " + getDistance(data.get(0).get(0), data.get(0).get(1)));
        log.info(data.get(0).get(1) + " - " + data.get(0).get(2) + " - " + getDistance(data.get(0).get(1), data.get(0).get(2)));
        log.info(data.get(0).get(2) + " - " + data.get(0).get(3) + " - " + getDistance(data.get(0).get(2), data.get(0).get(3)));

        log.info("-----------------");

        List<List<Double>> distancesPerTrack = data.stream().map((track) -> IntStream.range(0, track.size() - 1)
                .mapToDouble(i -> getDistance(track.get(i), track.get(i + 1)))
                .boxed()
                .collect(Collectors.toList())).collect(Collectors.toList());

        log.info(distancesPerTrack.get(0).get(0));
        log.info(distancesPerTrack.get(0).get(1));
        log.info(distancesPerTrack.get(0).get(2));

        log.info("-----------------");

//        String name = "Scores from " + xmlFile.getName() + " and " + videoFile.getName();
//
//        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
//
//        ResultsTable rt = new ResultsTable();
//        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing
//        // NaN just gets deleted when pasting
//
//        int globalCount = 0;
//        for (int i = 0; i < roiManager.getCount(); i++) {
//            Roi roi = roiManager.getRoi(i);
//            int count = 0;
//            for (ArrayList<XMLHelper.PointData> list : data) {
//                for (XMLHelper.PointData pointData : list) {
//                    if (roi.contains((int) pointData.x, (int) pointData.y)) {
//                        count++;
//                    }
//                }
//            }
//            globalCount += count;
//            rt.setValue("ROI Name", i, roi.getName());
//            rt.setValue("Count", i, count);
//            dataset.setValue(roi.getName(), count);
//        }
//
//        int lastRowIndex = roiManager.getCount();
//        rt.setValue("ROI Name", lastRowIndex, "Outside All ROIs");
//
//        int spotsCount = data.stream()
//                .map(ArrayList::size)
//                .reduce(Integer::sum).get(); // there'll always be spots. hopefully.
//
//        rt.setValue("Count", lastRowIndex, spotsCount - globalCount);
//        dataset.setValue("Outside All ROIs", spotsCount - globalCount);
//
//        if (this.displayPlots) {
//            new ImagePlus("Plot", getPieChart(dataset, name).createBufferedImage(1600, 1200)).show();
//        }
//
//        rt.show(name);
    }

    //    private double getDistance(double x1, double y1, double x2, double y2) {
    private double getDistance(XMLHelper.PointData point1, XMLHelper.PointData point2) {
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
