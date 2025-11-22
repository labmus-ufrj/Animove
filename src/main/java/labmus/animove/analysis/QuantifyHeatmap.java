package labmus.animove.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import io.scif.services.DatasetIOService;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.functions.BinarizeFromThresholdFunction;
import labmus.animove.utils.functions.ImageCalculatorFunction;
import labmus.animove.utils.functions.ShowEveryFrameFunction;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.PieChart;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;

import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.concurrent.Executors;

import static org.bytedeco.opencv.global.opencv_core.CMP_EQ;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.quantifyEmbryos)
public class QuantifyHeatmap implements Command, Interactive {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter(label = "Input Binary Heatmap Image", style = "file", persist = false, required = false)
    private File heatmapFile;

    @Parameter(label = "Input Average Image", style = "file", persist = false, required = false)
    private File avgFile;

    @Parameter(label = "Display Plots", persist = false)
    private boolean displayPlots = false;

    @Parameter(label = "Process", callback = "process")
    private Button btn1;

    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private LogService log;
    @Parameter
    private DatasetIOService datasetIOService;

    @Override
    public void run() {

    }

    private void process() {
        if (!checkFiles()) {
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

        Mat hetmapMat = imread(heatmapFile.getAbsolutePath(), opencv_imgcodecs.IMREAD_GRAYSCALE);
        Mat avgMat = imread(avgFile.getAbsolutePath(), opencv_imgcodecs.IMREAD_GRAYSCALE);

        Mat result = new BinarizeFromThresholdFunction(false)
                .andThen(ZFHelperMethods.InvertFunction)
                .andThen(new ImageCalculatorFunction(ImageCalculatorFunction.OperationMode.ADD, hetmapMat))
//                .andThen(new ShowEveryFrameFunction())
                .apply(avgMat);

        String name = "Quantified from " + heatmapFile.getName();

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        ResultsTable rt = new ResultsTable();
        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing

        try (Mat tempCompare = new Mat();
             Mat tempIntersection = new Mat();
             Scalar scalarBlack = new Scalar(0);
             Mat matBlack = new Mat(hetmapMat.rows(), hetmapMat.cols(), hetmapMat.type(), scalarBlack)) {

            for (int i = 0; i < roiManager.getCount(); i++) {
                Roi roi = roiManager.getRoi(i);
                try (Mat maskMat = ZFHelperMethods.getMaskMatFromRoi(result.arrayWidth(), result.arrayHeight(), roi)) {
                    opencv_core.compare(hetmapMat, matBlack, tempCompare, CMP_EQ);
                    opencv_core.bitwise_and(tempCompare, maskMat, tempIntersection);

                    int roiPixelCount = opencv_core.countNonZero(maskMat); // or roi.getContainedPoints().length
                    int blackPixelCount = opencv_core.countNonZero(tempIntersection);
                    double index = (double) blackPixelCount / roiPixelCount;

                    rt.setValue("ROI Name", i, roi.getName());
                    rt.setValue("Count", i, String.format("%.2f", index * 100));
                    dataset.addValue(index * 100, "", roi.getName());
                }
            }
        }

        if (this.displayPlots) {
            new ImagePlus("Plot", getBarChart(name, dataset).createBufferedImage(1600, 1200)).show();
        }

        rt.show(name);

        result.close();
        hetmapMat.close();
        avgMat.close();
    }

    private JFreeChart getBarChart(String title, DefaultCategoryDataset dataset){
        JFreeChart chart = ChartFactory.createBarChart(title, "", "Area Percentage (%)", dataset, PlotOrientation.VERTICAL, false, true, false);

        chart.setBackgroundPaint(Color.WHITE);

        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 48));
        chart.setPadding(new RectangleInsets(20.0, 20.0, 20.0, 20.0));

        CategoryPlot plot = chart.getCategoryPlot();

        plot.setRangeGridlineStroke(new BasicStroke(
                0.5f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
        ));

        plot.setBackgroundPaint(Color.decode("#f0f0f0"));
        plot.setRangeGridlinePaint(Color.GRAY);
        plot.setOutlineVisible(false);

        Font axisLabelFont = new Font("SansSerif", Font.BOLD, 36);
        Font tickLabelFont = new Font("SansSerif", Font.PLAIN, 36);

        plot.getDomainAxis().setLabelFont(axisLabelFont);
        plot.getDomainAxis().setTickLabelFont(tickLabelFont);
        plot.getDomainAxis().setLowerMargin(0.02);
        plot.getDomainAxis().setUpperMargin(0.02);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 100.0);
        rangeAxis.setLabelFont(axisLabelFont);
        rangeAxis.setTickLabelFont(tickLabelFont);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelGenerator(
                new StandardCategoryItemLabelGenerator("{2}%", new DecimalFormat("0.#"))
        );

        renderer.setDefaultItemLabelFont(new Font("SansSerif", Font.BOLD, 36));
        renderer.setDefaultItemLabelPaint(Color.BLACK);

        renderer.setBarPainter(new StandardBarPainter());
        renderer.setSeriesPaint(0, Color.decode("#4F81BD")); // Nice blue color
        renderer.setShadowVisible(false);
        return chart;
    }

    private boolean checkFiles() {
        if (heatmapFile == null || !heatmapFile.exists() || !heatmapFile.isFile()) {
            uiService.showDialog("Invalid input heatmap image", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        if (avgFile == null || !avgFile.exists() || !avgFile.isFile()) {
            uiService.showDialog("Invalid input average image", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        return true;
    }
}
