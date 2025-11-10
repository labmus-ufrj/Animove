package labmus.zebrafish_utils.processing;

import ij.IJ;
import io.scif.services.DatasetIOService;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.ZFHelperMethods;
import labmus.zebrafish_utils.utils.SimpleRecorder;
import labmus.zebrafish_utils.utils.functions.ImageCalculatorFunction;
import labmus.zebrafish_utils.utils.functions.SimpleRecorderFunction;
import labmus.zebrafish_utils.utils.functions.ZprojectFunction;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;
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
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

@Plugin(type = Command.class, menuPath = ZFConfigs.heatmapVideoPath)
public class HeatmapVideo implements Command, Interactive {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter(label = "Input Video", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File inputFile;

    @Parameter(label = "Output File", style = FileWidget.SAVE_STYLE, callback = "updateExtensionFile", persist = false, required = false)
    private File outputFile;

    @Parameter(label = "Output Format", choices = {"AVI", "TIFF", "MP4"}, callback = "updateExtensionChoice", persist = false)
    String format = "AVI";

    @Parameter(label = "Don't save, open in ImageJ instead", persist = false)
    private boolean openResultInstead = false;

    @Parameter(label = "Initial Frame", min = "1", persist = false)
    private int startFrame = 1;

    @Parameter(label = "End Frame (0 = whole video)", min = "0", persist = false)
    private int endFrame = 0;

    @Parameter(label = "Process", callback = "generate")
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
        IJ.run("Console");
//        FFmpegLogCallback.setLevel(avutil.AV_LOG_ERROR);
//        log.info(FFmpegLogCallback.getLevel());
    }

    private void generate() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        if (outputFile == null && !openResultInstead) {
            return;
        }

        Executors.newSingleThreadExecutor().submit(this::executeProcessing);
    }

    private void executeProcessing() {

        try {
            File tempOutputFile = File.createTempFile(ZFConfigs.pluginName + "_", "." + this.format.toLowerCase());
            log.info("Temp file: " + tempOutputFile.getAbsolutePath());
            tempOutputFile.deleteOnExit();

            ZprojectFunction zprojectFunctionAvg = new ZprojectFunction(ZprojectFunction.OperationMode.AVG);
            ZFHelperMethods.iterateOverFrames(ZFHelperMethods.InvertFunction.andThen(zprojectFunctionAvg), inputFile, startFrame, endFrame, statusService);
            Mat avgMat = zprojectFunctionAvg.getResultMat();

            Function<Mat, Mat> subtractFunction = new ImageCalculatorFunction(ImageCalculatorFunction.OperationMode.ADD, avgMat);
            Function<Mat, Mat> bcFunction = (mat) -> {
                mat.convertTo(mat, -1, 1, 30); // todo: maybe either calculate beta automatically or let the user choose...
                return mat;
            };
            ZprojectFunction zprojectFunctionSum = new ZprojectFunction(ZprojectFunction.OperationMode.SUM, true);

            double fps;
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
                grabber.start();
                fps = grabber.getFrameRate();
            }
            SimpleRecorderFunction simpleRecorderFunction = new SimpleRecorderFunction(new SimpleRecorder(tempOutputFile, avgMat, fps), uiService);

            ZFHelperMethods.iterateOverFrames(subtractFunction.andThen(bcFunction).andThen(zprojectFunctionSum).andThen(simpleRecorderFunction), inputFile, this.startFrame, this.endFrame, this.statusService);
            simpleRecorderFunction.close();

            if (openResultInstead) {
                statusService.showStatus("Opening result in ImageJ...");
                simpleRecorderFunction.getRecorder().openResultinIJ(uiService, datasetIOService);

            } else {
                Files.copy(tempOutputFile.toPath(), outputFile.toPath());
                uiService.showDialog("Heatmap video saved successfully!",
                        ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }

    }

    /**
     * Callback method to update the output filename when an input file changes.
     * Generates a unique output filename by appending "_heatmap" and the appropriate extension.
     */
    private void updateOutputName() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        String parentDir = inputFile.getParent();
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_heatmap" + "." + format.toLowerCase());

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + "_heatmap_" + count + "." + format.toLowerCase());
            count++;
        }
        outputFile = testFile;
        updateExtensionFile();
    }

    public void updateExtensionChoice() {
        if (outputFile == null) {
            return;
        }
        String newFileName = outputFile.getAbsolutePath()
                .substring(0, outputFile.getAbsolutePath().lastIndexOf(".") + 1)
                .concat(this.format.toLowerCase());
        this.outputFile = new File(newFileName);
    }

    public void updateExtensionFile() {
        if (outputFile == null) {
            return;
        }
        String extension = outputFile.getName().substring(outputFile.getName().lastIndexOf(".") + 1);
        if (extension.equalsIgnoreCase("tiff") || extension.equalsIgnoreCase("tif")) {
            format = "TIFF";
        } else if (extension.equalsIgnoreCase("mp4")) {
            format = "MP4";
        } else if (extension.equalsIgnoreCase("avi")) {
            format = "AVI";
        }
    }
}
