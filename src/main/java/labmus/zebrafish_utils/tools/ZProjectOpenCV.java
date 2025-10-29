package labmus.zebrafish_utils.tools;

import ij.ImagePlus;
import labmus.zebrafish_utils.ZFConfigs;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.io.File;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

/**
 * This plugin implements a video-to-image processing pipeline as a SciJava Command.
 * It allows users to process video frames and generate a resultant image using various modes such as
 * "Darkest (Min)", "Brightest (Max)", "Average", and "Sum". The plugin also supports optional conversion
 * to grayscale and can handle specific frame ranges for processing.
 */
@Plugin(type = Command.class, menuPath = ZFConfigs.avgPath)
public class ZProjectOpenCV implements Command {

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

    @Parameter(label = "Processing Mode", choices = {"Darkest (Min)", "Average", "Brightest (Max)", "Sum"}, callback = "updateOutputName", persist = false)
    private String mode = "Darkest (Min)";

    @Parameter(label = "Convert to Grayscale", persist = false)
    private boolean convertToGrayscale = true;

    @Parameter(label = "Initial Frame", min = "0", persist = false)
    private int startFrame = 0;

    @Parameter(label = "End Frame (0 = whole video)", min = "0", persist = false)
    private int endFrame = 0;

    @Parameter(label = "Open processed image", persist = false)
    private boolean openResult = true;


    /**
     * Runs everytime the user clicks the OK button
     */
    @Override
    public void run() {

        if (inputFile == null || !inputFile.exists()) {
            uiService.showDialog("Select a valid video file.", "Input Error");
            return;
        }
        statusService.showStatus(0, 100, "Starting processing...");

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {

            grabber.start();

            int totalFrames = grabber.getLengthInFrames() - 1; // frame numbers are 0-indexed

            int actualStartFrame = Math.max(0, startFrame);
            int actualEndFrame = (endFrame <= 0 || endFrame > totalFrames) ? totalFrames : endFrame;
            if (actualStartFrame >= actualEndFrame) {
                throw new Exception("Initial frame must be before end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;

            statusService.showStatus("Processing " + (framesToProcess) + " frames from " + inputFile.getName());

            grabber.setFrameNumber(actualStartFrame);


            // it's better to declare these two here
            // for secret and random memory things
            Frame jcvFrame;
            Mat currentFrame;
            Mat accumulator = null;
            int framesProcessedCount = 0;

            for (int i = actualStartFrame; i < actualEndFrame; i++) {
                jcvFrame = grabber.grabImage();
                if (jcvFrame == null || jcvFrame.image == null) {
                    uiService.showDialog("Read terminated prematurely at frame " + i, "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
                    break;
                }

                // No one knows why, and it took a few days to figure out why, but
                // you NEED a new converter every frame here. Dw about it, it doesn't leak.
                try (OpenCVFrameConverter.ToMat cnv = new OpenCVFrameConverter.ToMat()) {
                    Mat currentFrameColor = cnv.convert(jcvFrame);

                    // check if we should be converting to grayscale
                    if (convertToGrayscale && currentFrameColor.channels() > 1) {
                        currentFrame = new Mat();
                        cvtColor(currentFrameColor, currentFrame, COLOR_BGR2GRAY);
                    } else {
                        currentFrame = currentFrameColor;
                    }

                    if (currentFrame == null || currentFrame.isNull()) continue;

                    if (accumulator == null) {
                        accumulator = new Mat();
                        switch (mode) {
                            case "Average":
                                currentFrame.convertTo(accumulator, convertToGrayscale ? opencv_core.CV_32FC1 : opencv_core.CV_32FC3);
                                break;
                            case "Sum":
                                currentFrame.convertTo(accumulator, convertToGrayscale ? opencv_core.CV_32SC1 : opencv_core.CV_32SC3);
                                break;
                            default: // Darkest and Brightest
                                // using convertTo() instead of clone() fixes the 180° flipping issue
                                currentFrame.convertTo(accumulator, currentFrame.type());
                                break;
                        }
                    } else {
                        switch (mode) {
                            case "Darkest (Min)":
                                opencv_core.min(accumulator, currentFrame, accumulator);
                                break;
                            case "Brightest (Max)":
                                opencv_core.max(accumulator, currentFrame, accumulator);
                                break;
                            case "Average":
                                try (Mat tempFloatFrame = new Mat()) {
                                    currentFrame.convertTo(tempFloatFrame, convertToGrayscale ? opencv_core.CV_64FC1 : opencv_core.CV_64FC3);
                                    opencv_core.add(accumulator, tempFloatFrame, accumulator);
                                }
                                break;
                            case "Sum":
                                try (Mat tempIntFrame = new Mat()) {
                                    currentFrame.convertTo(tempIntFrame, convertToGrayscale ? opencv_core.CV_32SC1 : opencv_core.CV_32SC3);
                                    opencv_core.add(accumulator, tempIntFrame, accumulator);
                                }
                                break;
                        }
                    }

                    currentFrame.close();
                    framesProcessedCount++;
                    statusService.showProgress(framesProcessedCount, framesToProcess);
                    statusService.showStatus(String.format("Processing frame %d/%d...", framesProcessedCount, framesToProcess));
                }
            }

            if (accumulator == null) {
                throw new Exception("No frames were processed.");
            }

            Mat resultMat;
            if (mode.equals("Average")) {
                resultMat = new Mat();
                double scale = 1.0 / framesProcessedCount;
                accumulator.convertTo(resultMat, opencv_core.CV_8UC3, scale, 0);
            } else {
                resultMat = accumulator;
            }

            imwrite(outputFile.getAbsolutePath(), resultMat);

            if (resultMat != accumulator) {
                resultMat.close();
            }
            accumulator.close();


            if (openResult) {
                uiService.show(new ImagePlus(outputFile.getAbsolutePath()));
            }

            log.info("Processing done.");
            uiService.showDialog("Image saved as " + outputFile.getAbsolutePath(),
                    "Processing Done", DialogPrompt.MessageType.INFORMATION_MESSAGE);
            statusService.clearStatus();

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
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
        File testFile = new File(parentDir, baseName + suffix + ".tiff");

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + suffix + count + ".tiff");
            count++;
        }
        outputFile = testFile;
    }
}
