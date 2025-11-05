package labmus.zebrafish_utils.processing;

import ij.IJ;
import ij.ImagePlus;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.tools.SimpleRecorder;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.opencv.core.Core.NORM_MINMAX;

@Plugin(type = Command.class, menuPath = ZFConfigs.heatmapVideo)
public class HeatmapVideo implements Command, Interactive {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter(label = "Input Video", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File inputFile;

    @Parameter(label = "Output File", style = FileWidget.SAVE_STYLE, persist = false, required = false)
    private File outputFile;

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

            grabber.start();

            int totalFrames = grabber.getLengthInFrames() - 1; // frame numbers are 0-indexed

            int actualStartFrame = Math.max(0, startFrame - 1);
            int actualEndFrame = (endFrame - 1 <= 0 || endFrame - 1 > totalFrames) ? totalFrames : endFrame - 1;
            if (actualStartFrame >= actualEndFrame) {
                throw new Exception("Initial frame must be before end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;

            statusService.showStatus("Processing " + (framesToProcess) + " frames...");

            grabber.setFrameNumber(actualStartFrame);

            File tempOutputFile = File.createTempFile(ZFConfigs.pluginName + "_", ".avi");
            log.info("Temp file: " + tempOutputFile.getAbsolutePath());
            tempOutputFile.deleteOnExit();

            SimpleRecorder simpleRecorder = new SimpleRecorder(tempOutputFile, grabber);
            simpleRecorder.start();

            Frame jcvFrame;
            for (int i = actualStartFrame; i < actualEndFrame; i++) {
                jcvFrame = grabber.grabImage();
                if (jcvFrame == null || jcvFrame.image == null) {
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

                try (Mat tempFrame = new Mat()) {
                    // ffmpeg can't create videos from 32bit tiff images
                    // converting them (normalizing, like imageJ does) fixes it
                    opencv_core.normalize(
                            accumulator,          // Source Mat
                            tempFrame,          // Destination Mat
                            0,               // Alpha: The minimum value of the target range
                            65535,           // Beta: The maximum for 16-bit unsigned (2^16-1)
                            NORM_MINMAX,     // Norm type: remains the same
                            convertToGrayscale ? opencv_core.CV_16UC1 : opencv_core.CV_16UC3,
                            null        // Mask: optional
                    );
                    simpleRecorder.recordMat(tempFrame, cnv);
                }
                statusService.showProgress(i + 1, framesToProcess);

            }

            simpleRecorder.close();

            if (openResultInstead) {
                new ImagePlus(tempOutputFile.getAbsolutePath()).show();
            } else {
                Files.copy(tempOutputFile.toPath(), outputFile.toPath());
                uiService.showDialog("Heatmap video saved successfully!",
                        ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);
            }


        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

    private File createTempDir() {
        File tempDir = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis());
        if (!tempDir.mkdir()) {
            uiService.showDialog("Could not create temporary directory for processing.",
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            return null;
        }
        tempDir.deleteOnExit();
        return tempDir;
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
        File testFile = new File(parentDir, baseName + "_heatmap" + ".avi");

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + "_processed_" + count + ".avi");
            count++;
        }
        outputFile = testFile;
    }
}
