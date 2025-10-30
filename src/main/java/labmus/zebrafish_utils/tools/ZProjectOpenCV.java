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
import java.util.stream.Collectors;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
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
        // reduces loading time for FFmpegFrameGrabber
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
    }

    public enum OperationMode {
        MIN("Darkest (Min)"),
        MAX("Brightest (Max)"),
        AVG("Average"),
        SUM("Sum");

        private final String text;

        OperationMode(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }

        /**
         * Finds an OperationMode by its user-facing text.
         * This method is case-insensitive.
         *
         * @param text The text to search for (e.g., "Average")
         * @return The matching OperationMode (null if nothing is found)
         */
        public static OperationMode fromText(String text) {
            for (OperationMode mode : OperationMode.values()) {
                if (mode.getText().equalsIgnoreCase(text)) {
                    return mode;
                }
            }
            return null;
        }
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

    @Parameter(label = "Initial Frame", min = "1", description = "inclusive", persist = false)
    private int startFrame = 1;

    @Parameter(label = "End Frame (0 = whole video)", min = "0", description = "inclusive", persist = false)
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

        try {
            Mat resultMat = applyVideoOperation(OperationMode.fromText(mode), inputFile, convertToGrayscale, invertVideo, startFrame, endFrame, statusService);

            imwrite(outputFile.getAbsolutePath(), resultMat);

            if (openResult) {
                uiService.show(new ImagePlus(outputFile.getAbsolutePath()));
            }

            resultMat.close();

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
     * Processes video frames from an input file according to the specified operation mode
     * (e.g., finding the darkest, brightest, average, or summing frames) and writes the
     * resulting image to the output file.
     * Optionally converts the frames to grayscale, operates on a specific frame range,
     * and displays the result.
     * <p>
     * YOU ABSOLUTELY NEED TO CALL .close() ON THE Mat WHEN YOU ARE DONE WITH IT!!!!
     *
     * @param mode               The operation mode to apply to the frames (MIN, MAX, AVG, SUM).
     * @param inputFile          The input video file.
     * @param convertToGrayscale Whether to convert the frames to grayscale before processing.
     * @param startFrame         The starting frame of the range to process (one-indexed, inclusive).
     * @param endFrame           The ending frame of the range to process (one-indexed, inclusive).
     * @param statusService      Service for displaying status and progress updates. (nullable)
     * @return The resulting processed image as a Mat object.
     * @throws Exception If the operation cannot be completed (e.g., invalid frame range, no frames processed).
     */
    public static Mat applyVideoOperation(OperationMode mode, File inputFile, boolean convertToGrayscale, boolean invertVideo, int startFrame, int endFrame, StatusService statusService) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {

            grabber.start();

            int totalFrames = grabber.getLengthInFrames() - 1; // frame numbers are 0-indexed

            int actualStartFrame = Math.max(0, startFrame - 1);
            int actualEndFrame = (endFrame-1 <= 0 || endFrame-1 > totalFrames) ? totalFrames : endFrame-1;
            if (actualStartFrame >= actualEndFrame) {
                throw new Exception("Initial frame must be before end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;

            if (statusService != null)
                statusService.showStatus("Processing " + (framesToProcess) + " frames from " + inputFile.getName());

            grabber.setFrameNumber(actualStartFrame);


            // it's better to declare these two here
            // for secret and random memory things
            Frame jcvFrame;
            Mat currentFrame;
            Mat accumulator = null;
            int framesProcessedCount = 0;
            int frameType = convertToGrayscale ? opencv_core.CV_32FC1 : opencv_core.CV_32FC3; // using float for everyone is safer

            for (int i = actualStartFrame; i < actualEndFrame; i++) {
//                log.info("zero indexed frame n: "+i + " - actual fn: "+(i+1));
                jcvFrame = grabber.grabImage();
                if (jcvFrame == null || jcvFrame.image == null) {
                    throw new Exception("Read terminated prematurely at frame " + i);
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

                    if (invertVideo)
                        opencv_core.bitwise_not(currentFrame, currentFrame);

                    if (accumulator == null) {
                        accumulator = new Mat();
                        switch (mode) {
                            case AVG:
                                currentFrame.convertTo(accumulator, frameType);
                                break;
                            case SUM:
                                currentFrame.convertTo(accumulator, frameType);
                                break;
                            default: // Darkest and Brightest
                                // using convertTo() instead of clone() fixes the 180° flipping issue
                                currentFrame.convertTo(accumulator, currentFrame.type());
                                break;
                        }
                    } else {
                        switch (mode) {
                            case MIN:
                                opencv_core.min(accumulator, currentFrame, accumulator);
                                break;
                            case MAX:
                                opencv_core.max(accumulator, currentFrame, accumulator);
                                break;
                            case AVG:
                            case SUM:
                                try (Mat tempFloatFrame = new Mat()) {
                                    currentFrame.convertTo(tempFloatFrame, frameType);
                                    opencv_core.add(accumulator, tempFloatFrame, accumulator);
                                }
                                break;
                        }
                    }

                    currentFrame.close();
                    currentFrameColor.close();
                    framesProcessedCount++;
                    if (statusService != null) {
                        statusService.showProgress(framesProcessedCount, framesToProcess);
                        statusService.showStatus(String.format("Processing frame %d/%d...", framesProcessedCount, framesToProcess));
                    }
                }
            }

            if (accumulator == null) {
                throw new Exception("No frames were processed.");
            }

            Mat resultMat;
            if (mode == OperationMode.AVG) {
                resultMat = new Mat();
                double scale = 1.0 / framesProcessedCount;
                accumulator.convertTo(resultMat, convertToGrayscale ? opencv_core.CV_32SC1 : opencv_core.CV_32SC3, scale, 0);
            } else {
                resultMat = accumulator;
            }

            if (resultMat != accumulator) {
                accumulator.close();
            }

            return resultMat;

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

    public void initProc() {
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("mode", String.class);
        item.setChoices(Arrays.stream(OperationMode.values()).map(OperationMode::getText).collect(Collectors.toList()));
//        this.mode = OperationMode.MIN.getText();
    }
}
