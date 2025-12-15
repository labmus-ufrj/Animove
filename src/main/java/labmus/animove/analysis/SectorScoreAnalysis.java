package labmus.animove.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.XMLHelper;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;
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
import java.util.concurrent.Executors;

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
        IJ.setTool("rectangle"); // to create ROIs
    }

    private JFreeChart getPieChart(DefaultPieDataset<String> dataset, String name) {
        JFreeChart chart = ChartFactory.createPieChart(
                name,
                dataset,
                true,
                true,
                false
        );

        chart.setBackgroundPaint(Color.WHITE);
        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 48));
        chart.setPadding(new RectangleInsets(20.0, 20.0, 20.0, 20.0));

        TextTitle title = chart.getTitle();
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        title.setPaint(Color.BLACK);

        PiePlot<String> plot = (PiePlot) chart.getPlot();

        List<String> keys = dataset.getKeys();
        List<Color> colors = ZFHelperMethods.generateColors(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            plot.setSectionPaint(keys.get(i), colors.get(i));
        }

        plot.setBackgroundPaint(Color.decode("#f0f0f0"));
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);

        plot.setLabelGenerator(null);

        plot.setSimpleLabels(true);

        plot.setLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        plot.setLabelPaint(Color.WHITE);

        plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
                "{2}", new DecimalFormat("0"), new DecimalFormat("0.0%", new DecimalFormatSymbols(Locale.US))
        ) {
            @Override
            public String generateSectionLabel(PieDataset dataset, Comparable key) {
                // Get the value for the specific section
                Number value = dataset.getValue(key);

                // If value is null or 0, return null (which prevents the label from drawing)
                if (value == null || value.doubleValue() == 0.0) {
                    return null;
                }

                // Otherwise, generate the label normally
                return super.generateSectionLabel(dataset, key);
            }
        });

        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);

        plot.setLegendItemShape(new Ellipse2D.Double(0, 0, 25, 25));

        LegendTitle legend = chart.getLegend();
        legend.setPosition(RectangleEdge.BOTTOM);
        legend.setItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
        legend.setFrame(BlockBorder.NONE);
        legend.setItemLabelPadding(new RectangleInsets(0, 10, 0, 40));

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

        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }

        if (roiManager.getCount() < 1) {
            uiService.showDialog("No ROIs in RoiManager. At least one is expected.", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        List<ArrayList<XMLHelper.PointData>> data;
        try {
            data = XMLHelper.iterateOverXML(xmlFile, videoFrame, fixSpots).data;
        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("An error occured during processing:\n" + e.getLocalizedMessage(),
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        String name = "Scores from " + xmlFile.getName() + " and " + videoFile.getName();

        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();

        ResultsTable rt = new ResultsTable();
        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing
        // NaN just gets deleted when pasting

        int globalCount = 0;
        for (int i = 0; i < roiManager.getCount(); i++) {
            Roi roi = roiManager.getRoi(i);
            int count = 0;
            for (ArrayList<XMLHelper.PointData> list : data) {
                for (XMLHelper.PointData pointData : list) {
                    if (roi.contains((int) pointData.x, (int) pointData.y)) {
                        count++;
                    }
                }
            }
            globalCount += count;
            rt.setValue("ROI Name", i, roi.getName());
            rt.setValue("Count", i, count);
            dataset.setValue(roi.getName(), count);
        }

        int spotsCount = data.stream()
                .map(ArrayList::size)
                .reduce(Integer::sum).get(); // there'll always be spots. hopefully.

        int outsideCount = spotsCount - globalCount;
        if (outsideCount > 0){
            int lastRowIndex = roiManager.getCount();
            rt.setValue("ROI Name", lastRowIndex, "Outside All ROIs");
            rt.setValue("Count", lastRowIndex, outsideCount);
            dataset.setValue("Outside All ROIs", outsideCount);
        }

        if (this.displayPlots) {
            new ImagePlus("Plot", getPieChart(dataset, name).createBufferedImage(1600, 1200)).show();
        }

        rt.show(name);
    }

    private float getScore(float x, float calMin, float calMax) {
        float a = (x - calMin) / (calMax - calMin);
        float b = a * 10f;
        return Math.max(0, Math.min(10, b));
    }

    private void openFrame() {
        if (videoFile == null || !videoFile.exists() || !videoFile.isFile()) {
            uiService.showDialog("Could not open video:\nInvalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
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
