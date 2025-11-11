package labmus.zebrafish_utils.processing;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.ZFHelperMethods;
import labmus.zebrafish_utils.utils.functions.HeatmapFunction;
import labmus.zebrafish_utils.utils.functions.ImageCalculatorFunction;
import labmus.zebrafish_utils.utils.functions.ZprojectFunction;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.heatmapImagesPath)
public class HeatmapImages extends DynamicCommand implements Interactive {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter(label = "Input Video", style = FileWidget.OPEN_STYLE, callback = "updateOutputFolder", persist = false, required = false)
    private File inputFile;

    @Parameter(label = "Open Frame", callback = "previewFrame")
    private Button btn1;

    @Parameter(label = "Output Folder", style = FileWidget.DIRECTORY_STYLE, persist = false, required = false)
    private File outputDir;

//    @Parameter(label = "Invert before operation", persist = false)
    // todo: docs. plugin expects dark subjects on light background. add this to ffmpeg or something...
//    private boolean invertVideo = false;

    @Parameter(label = "Lookup Table", persist = false, initializer = "initLUT")
    private String lut = "";

    @Parameter(label = "Don't save, open in ImageJ instead", persist = false, description = "Files won't be saved to the specified Output Folder. Instead, they'll be opened in ImageJ")
    private boolean openResultInstead = false;

    @Parameter(label = "Start")
    private boolean doStartInterval = true;

    @Parameter(label = "Interval")
    private String startInterval = "1-5400";

    @Parameter(label = "Middle")
    private boolean doMiddleInterval = true;

    @Parameter(label = "Interval")
    private String middleInterval = "15301-20700";

    @Parameter(label = "End")
    private boolean doEndInterval = true;

    @Parameter(label = "Interval")
    private String endInterval = "30601-36000";

    @Parameter(label = "Custom")
    private boolean doCustomInterval = false;

    @Parameter(label = "Interval")
    private String customInterval = "5401-6400";

    @Parameter(label = "Process", callback = "generate")
    private Button btn2;

    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private LogService log;

    private ImagePlus previewImagePlus = null;
    private Roi lastRoi = null;

    @Override
    public void run() {
//        IJ.run("Console", "");
    }

    private void generate() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        if ((outputDir == null || !Files.isDirectory(outputDir.toPath())) && !openResultInstead) {
            return;
        }

        Roi singleRoi;
        if (previewImagePlus != null) {
            singleRoi = previewImagePlus.getRoi();
        } else {
            RoiManager rm = RoiManager.getInstance();
            singleRoi = (rm != null && rm.getSelectedIndex() != -1) ? rm.getRoi(rm.getSelectedIndex()) : null;
        }

        lastRoi = singleRoi == null ? lastRoi : singleRoi;
        // Validate ROI exists
        if (lastRoi == null) {
            uiService.showDialog("Create a ROI enclosing your target area",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        previewImagePlus.close();

        Roi finalRoi = lastRoi;
        Executors.newSingleThreadExecutor().submit(() -> this.executeProcessing(finalRoi));

    }

    private void executeProcessing(Roi roi) {
        try {
            // this just allows to re-use the same code for all intervals.
            // looks messy, ik
            for (String interval : Arrays.asList(doStartInterval ? startInterval : "",
                    doMiddleInterval ? middleInterval : "",
                    doEndInterval ? endInterval : "",
                    doCustomInterval ? customInterval : "")) {

                if (interval.isEmpty()) continue;

                String[] a = interval.split("-");
                if (a.length != 2) {
                    throw new Exception("Invalid interval: " + interval);
                }

                int startFrame = Integer.parseInt(a[0]);
                int endFrame = Integer.parseInt(a[1]);

                ZprojectFunction zprojectFunctionAvg = new ZprojectFunction(ZprojectFunction.OperationMode.AVG);
                ZFHelperMethods.iterateOverFrames(ZFHelperMethods.InvertFunction.andThen(zprojectFunctionAvg), inputFile, startFrame, endFrame, statusService);
                Mat avgMat = zprojectFunctionAvg.getResultMat();

                Function<Mat, Mat> subtractFunction = new ImageCalculatorFunction(ImageCalculatorFunction.OperationMode.ADD, avgMat);
                Function<Mat, Mat> bcFunction = (mat) -> {
                    mat.convertTo(mat, -1, 1, 30); // todo: maybe either calculate beta automatically or let the user choose...
                    return mat;
                };
                ZprojectFunction zprojectFunctionSum = new ZprojectFunction(ZprojectFunction.OperationMode.SUM);
                ZFHelperMethods.iterateOverFrames(subtractFunction.andThen(bcFunction).andThen(ZFHelperMethods.InvertFunction).andThen(zprojectFunctionSum), inputFile, startFrame, endFrame, statusService);
                Mat sumMat = zprojectFunctionSum.getResultMat();

                HeatmapFunction heatmapFunction = new HeatmapFunction(roi, this.lut);
                heatmapFunction.apply(sumMat);

                ImagePlus imp = new ImagePlus("", heatmapFunction.getLastBi());
                heatmapFunction.close();

                if (openResultInstead) {
                    imp.setTitle(interval);
                    imp.show();
                } else {
                    IJ.save(imp, outputDir.toPath().resolve(interval + ".tif").toString());
                    imp.close();
                    uiService.showDialog("Processing done", ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);
                }
                sumMat.close();
                avgMat.close();
            }
        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }

    }

    private void previewFrame() {
        if (inputFile == null || !inputFile.exists() || !inputFile.isFile()) {
            uiService.showDialog("Could not open video: \n Invalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        String extension = inputFile.getName().substring(inputFile.getName().lastIndexOf(".") + 1).toLowerCase();
        if (!extension.contentEquals("avi") && !extension.contentEquals("mp4")) {
            uiService.showDialog("Could not open video: \n Invalid extension ." + extension, ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                if (previewImagePlus != null && previewImagePlus.getWindow() != null) {
                    previewImagePlus.close();
                }
                previewImagePlus = ZFHelperMethods.getFirstFrame(inputFile);
                previewImagePlus.setTitle("First frame");
                uiService.show(previewImagePlus);
            } catch (Exception e) {
                log.error(e);
                uiService.showDialog("Could not open video: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            }
        });
    }

    private void updateOutputFolder() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        String parentDir = inputFile.getParent();
        outputDir = new File(parentDir);
    }

    public void initLUT() {
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("lut", String.class);
        item.setChoices(Stream.concat(Stream.of(defaultLut), Arrays.stream(IJ.getLuts())).collect(Collectors.toList()));
    }

    public static final String defaultLut = "Don't Change";

}
