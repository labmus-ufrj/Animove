package labmus.zebrafish_utils.processing;

import io.scif.services.DatasetIOService;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.utils.SimpleRecorder;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
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
    String format = "TIFF";

    @Parameter(label = "Grayscale", persist = false)
    private boolean convertToGrayscale = true;

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
        // this is better than just calling ZProjectOpenCV.applyVideoOperation() on every frame
        // we are using the same accumulator, its less compute-intensive.
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
             Mat accumulator = new Mat();
             OpenCVFrameConverter.ToMat cnv = new OpenCVFrameConverter.ToMat()) {

            int actualStartFrame = Math.max(0, startFrame - 1);
            grabber.setFrameNumber(actualStartFrame);

            grabber.start();

            int totalFrames = grabber.getLengthInFrames() - 1; // frame numbers are 0-indexed
            final boolean wholeVideo = (endFrame <= 0);
            int actualEndFrame = (wholeVideo || endFrame > totalFrames) ? totalFrames : endFrame;
            if (actualStartFrame >= actualEndFrame) {
                throw new Exception("Initial frame must be before end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;

            statusService.showStatus("Processing frames...");

            File tempOutputFile = File.createTempFile(ZFConfigs.pluginName + "_", "." + this.format.toLowerCase());
            log.info("Temp file: " + tempOutputFile.getAbsolutePath());
            tempOutputFile.deleteOnExit();

            SimpleRecorder simpleRecorder = new SimpleRecorder(tempOutputFile, grabber);
            simpleRecorder.start();

            Frame jcvFrame;
            for (int i = actualStartFrame; i < actualEndFrame || wholeVideo; i++) {
                log.info("zero indexed frame n: " + i + " - actual fn: " + (i + 1));
                jcvFrame = grabber.grabImage();
                if (jcvFrame == null || jcvFrame.image == null) {
                    if (wholeVideo){
                        break; // we are done!!
                    }
                    throw new Exception("Read terminated prematurely at frame " + i);
                }
                Mat currentFrameColor = cnv.convert(jcvFrame);
                Mat currentFrame;

                // check if we should be converting to grayscale
                if (convertToGrayscale && currentFrameColor.channels() > 1) {
                    currentFrame = new Mat();
                    cvtColor(currentFrameColor, currentFrame, COLOR_BGR2GRAY);
                } else {
                    currentFrame = currentFrameColor;
                }

                if (i == actualStartFrame) {
                    currentFrame.convertTo(accumulator, convertToGrayscale ? opencv_core.CV_32SC1 : opencv_core.CV_32SC3);
                } else {
                    try (Mat tempIntFrame = new Mat()) {
                        currentFrame.convertTo(tempIntFrame, convertToGrayscale ? opencv_core.CV_32SC1 : opencv_core.CV_32SC3);
                        opencv_core.add(accumulator, tempIntFrame, accumulator);
                    }
                }
                currentFrame.close();

                simpleRecorder.recordMat(accumulator, cnv);
                statusService.showProgress(i + 1, framesToProcess);

            }

            simpleRecorder.close();

            if (openResultInstead) {
                simpleRecorder.openResultinIJ(uiService, datasetIOService);

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

        String newFileName = outputFile.getAbsolutePath();
        String[] a = outputFile.getName().split("\\.");
        String extension = a[a.length - 1]; // todo: replace with outputFile.getName().substring(outputFile.getName().lastIndexOf(".") + 1);
        newFileName = newFileName.replace("." + extension, "." + format.toLowerCase());
        this.outputFile = new File(newFileName);
    }

    public void updateExtensionFile() {
        if (outputFile == null) {
            return;
        }
        String[] a = outputFile.getName().split("\\.");
        String extension = a[a.length - 1]; // todo: replace with outputFile.getName().substring(outputFile.getName().lastIndexOf(".") + 1);
        if (extension.equalsIgnoreCase("tiff") || extension.equalsIgnoreCase("tif")) {
            format = "TIFF";
        } else if (extension.equalsIgnoreCase("mp4")) {
            format = "MP4";
        } else if (extension.equalsIgnoreCase("avi")) {
            format = "AVI";
        }
    }
}
