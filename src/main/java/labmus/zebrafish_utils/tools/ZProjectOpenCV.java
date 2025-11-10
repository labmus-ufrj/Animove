package labmus.zebrafish_utils.tools;

import ij.ImagePlus;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.ZFHelperMethods;
import labmus.zebrafish_utils.utils.ZprojectConsumer;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

/**
 * This plugin implements a video-to-image processing pipeline as a SciJava Command.
 * It allows users to process video frames and generate a resultant image using various modes such as
 * "Darkest (Min)", "Brightest (Max)", "Average", and "Sum". The plugin also supports optional conversion
 * to grayscale and can handle specific frame ranges for processing.
 */
@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.avgPath)
public class ZProjectOpenCV extends DynamicCommand {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private LogService log;

    @Parameter(label = "Input Video", style = "file", callback = "updateOutputName", persist = false)
    private File inputFile;

    @Parameter(label = "Output Image", style = "save", persist = false)
    private File outputFile;

    @Parameter(label = "Processing Mode", callback = "updateOutputName", initializer = "initProc", persist = false)
    private String mode = "";

    @Parameter(label = "Convert to Grayscale", persist = false)
    private boolean convertToGrayscale = true;

    @Parameter(label = "Invert before operation", persist = false)
    private boolean invertVideo = true;

    @Parameter(label = "Open processed image", persist = false)
    private boolean openResult = true;

    @Parameter(label = "Initial Frame", min = "1", persist = false)
    private int startFrame = 1;

    @Parameter(label = "End Frame (0 = whole video)", min = "0", persist = false)
    private int endFrame = 0;


    /**
     * Runs everytime the user clicks the OK button
     */
    @Override
    public void run() {

        if (inputFile == null || !inputFile.exists()) {
            uiService.showDialog("Select a valid video file.", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        statusService.showStatus(0, 100, "Starting processing...");

        try {
            Function<Mat, Mat> inverter = invertVideo ? ZFHelperMethods.InvertFunction : Function.identity();
            ZprojectConsumer zprojectConsumer = new ZprojectConsumer(ZprojectConsumer.OperationMode.fromText(mode));
            ZFHelperMethods.iterateOverFrames(inverter.andThen(zprojectConsumer), inputFile, startFrame, endFrame, statusService);
            Mat resultMat = zprojectConsumer.getResultMat();

            imwrite(outputFile.getAbsolutePath(), resultMat);
            resultMat.close();

            if (openResult) {
                uiService.show(new ImagePlus(outputFile.getAbsolutePath()));
            } else {
                uiService.showDialog("Image saved as " + outputFile.getAbsolutePath(),
                        "Processing Done", DialogPrompt.MessageType.INFORMATION_MESSAGE);
            }


        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }


    /**
     * Callback method to update the output filename when an input file changes.
     * Generates a unique output filename by appending a mode-matching suffix and the appropriate extension.
     */
    protected void updateOutputName() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }

        String suffix = "_";

        switch (mode) {
            case "Darkest (Min)":
                suffix += "darkest";
                break;
            case "Brightest (Max)":
                suffix += "brightest";
                break;
            case "Average":
                suffix += "avg";
                break;
            case "Sum":
                suffix += "sum";
                break;
        }

        String parentDir = inputFile.getParent();
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + suffix + ".tif");

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + suffix + count + ".tif");
            count++;
        }
        outputFile = testFile;
    }

    public void initProc() {
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("mode", String.class);
        item.setChoices(Arrays.stream(ZprojectConsumer.OperationMode.values()).map(ZprojectConsumer.OperationMode::getText).collect(Collectors.toList()));
    }

}
