package labmus.zebrafish_utils;

import ij.ImagePlus;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.GpuMat;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.io.File;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_cudaarithm.*;
import static org.bytedeco.opencv.global.opencv_cudaarithm.add;
import static org.bytedeco.opencv.global.opencv_cudaarithm.max;
import static org.bytedeco.opencv.global.opencv_cudaarithm.min;
import static org.bytedeco.opencv.global.opencv_cudaimgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;

/**
 * Generated this from the other class, mainly with gemini-2.5pro
 * still testing it, not able to work it out yet.
 *
 * It needs some 2Gb binaries. May ditch the idea.
 *
 * <a href="https://github.com/bytedeco/javacpp-presets/tree/master/opencv#the-pomxml-build-file">docs</a>
 *
 * <a href="https://github.com/bytedeco/javacv/issues/1767">related issue</a>
 */
@Plugin(type = Command.class, menuPath = ZFConfigs.avgPath + " (CUDA)")
public class ZProjectOpenCVCuda implements Command {

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

    @Parameter(label = "Processing Mode", choices = {"Darkest (Min)", "Average", "Brightest (Max)", "Sum"}, persist = false)
    private String mode = "Darkest (Min)";

    @Parameter(label = "Convert to Grayscale", persist = false)
    private boolean convertToGrayscale = false;

    @Parameter(label = "Initial Frame", min = "0", persist = false)
    private int startFrame = 0;

    @Parameter(label = "End Frame (0 = whole video)", min = "0", persist = false)
    private int endFrame = 0;

    @Parameter(label = "Open processed image", persist = false)
    private boolean openResult = true;


    /**
     * Executes the processing pipeline using CUDA for acceleration.
     * This method processes a video file by performing calculations on the GPU.
     */
    @Override
    public void run() {

        if (!ZFConfigs.checkJavaCV()) {
            return;
        }

/*        // Check for CUDA-enabled devices
// soooo you dont do that
        if (getCudaEnabledDeviceCount() == 0) {
            uiService.showDialog("No CUDA-enabled GPU found. This plugin requires a compatible NVIDIA GPU.", "CUDA Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }*/

        if (inputFile == null || !inputFile.exists()) {
            uiService.showDialog("Select a valid video file.", "Input Error");
            return;
        }
        statusService.showStatus(0, 100, "Starting GPU processing...");

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {

            grabber.start();

            int totalFrames = grabber.getLengthInFrames() - 1;

            int actualStartFrame = Math.max(0, startFrame);
            int actualEndFrame = (endFrame <= 0 || endFrame > totalFrames) ? totalFrames : endFrame;
            if (actualStartFrame >= actualEndFrame) {
                throw new Exception("Initial frame must be before end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;

            statusService.showStatus("Processing " + framesToProcess + " frames from " + inputFile.getName() + " using CUDA.");

            grabber.setFrameNumber(actualStartFrame);

            Frame jcvFrame;
            GpuMat accumulatorGpu = null;
            int framesProcessedCount = 0;

            // Use a single converter instance
            try (OpenCVFrameConverter.ToMat cnv = new OpenCVFrameConverter.ToMat()) {
                for (int i = actualStartFrame; i < actualEndFrame; i++) {
                    jcvFrame = grabber.grabImage();
                    if (jcvFrame == null || jcvFrame.image == null) {
                        uiService.showDialog("Read terminated prematurely at frame " + i, "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
                        break;
                    }

                    try (Mat currentFrameCpu = cnv.convert(jcvFrame);
                         GpuMat currentFrameGpu = new GpuMat()) {

                        // Upload frame to GPU
                        currentFrameGpu.upload(currentFrameCpu);

                        GpuMat processedFrameGpu;
                        // Convert to grayscale on the GPU if needed
                        if (convertToGrayscale && currentFrameGpu.channels() > 1) {
                            processedFrameGpu = new GpuMat();
                            cvtColor(currentFrameGpu, processedFrameGpu, COLOR_BGR2GRAY);
                        } else {
                            processedFrameGpu = currentFrameGpu;
                        }

                        if (processedFrameGpu.isNull()) continue;

                        if (accumulatorGpu == null) {
                            accumulatorGpu = new GpuMat();
                            switch (mode) {
                                case "Average":
                                    processedFrameGpu.convertTo(accumulatorGpu, convertToGrayscale ? CV_64FC1 : CV_64FC3);
                                    break;
                                case "Sum":
                                    processedFrameGpu.convertTo(accumulatorGpu, convertToGrayscale ? CV_32SC1 : CV_32SC3);
                                    break;
                                default: // Darkest and Brightest
                                    processedFrameGpu.copyTo(accumulatorGpu);
                                    break;
                            }
                        } else {
                            switch (mode) {
                                case "Darkest (Min)":
                                    min(accumulatorGpu, processedFrameGpu, accumulatorGpu);
                                    break;
                                case "Brightest (Max)":
                                    max(accumulatorGpu, processedFrameGpu, accumulatorGpu);
                                    break;
                                case "Average":
                                    try (GpuMat tempFloatFrame = new GpuMat()) {
                                        processedFrameGpu.convertTo(tempFloatFrame, convertToGrayscale ? CV_64FC1 : CV_64FC3);
                                        add(accumulatorGpu, tempFloatFrame, accumulatorGpu);
                                    }
                                    break;
                                case "Sum":
                                    try (GpuMat tempIntFrame = new GpuMat()) {
                                        processedFrameGpu.convertTo(tempIntFrame, convertToGrayscale ? CV_32SC1 : CV_32SC3);
                                        add(accumulatorGpu, tempIntFrame, accumulatorGpu);
                                    }
                                    break;
                            }
                        }

                        // Release the intermediate grayscale frame if it was created
                        if (processedFrameGpu != currentFrameGpu) {
                            processedFrameGpu.close();
                        }
                    }

                    framesProcessedCount++;
                    statusService.showProgress(framesProcessedCount, framesToProcess);
                    statusService.showStatus(String.format("Processing frame %d/%d on GPU...", framesProcessedCount, framesToProcess));
                }
            }

            if (accumulatorGpu == null) {
                throw new Exception("No frames were processed.");
            }

            // Prepare the final result Mat on CPU
            try (Mat resultMat = new Mat()) {
                if (mode.equals("Average")) {
                    try (GpuMat tempResultGpu = new GpuMat()) {
                        double scale = 1.0 / framesProcessedCount;
                        accumulatorGpu.convertTo(tempResultGpu, CV_8UC3, scale, 0);
                        // Download the final result from GPU to CPU
                        tempResultGpu.download(resultMat);
                    }
                } else {
                    // Download the final result from GPU to CPU
                    accumulatorGpu.download(resultMat);
                }

                imwrite(outputFile.getAbsolutePath(), resultMat);
            }

            accumulatorGpu.close();

            if (openResult) {
                uiService.show(new ImagePlus(outputFile.getAbsolutePath()));
            }

            log.info("Processing done.");
            uiService.showDialog("Image saved as " + outputFile.getAbsolutePath(),
                    "Processing Done", DialogPrompt.MessageType.INFORMATION_MESSAGE);
            statusService.clearStatus();

        } catch (Exception e) {
            log.error("A fatal error occurred during CUDA processing", e);
            uiService.showDialog("A fatal error occurred during CUDA processing: \n" + e.getMessage(), "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

    /**
     * Callback method to update the output filename when an input file changes.
     */
    protected void updateOutputName() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        String parentDir = inputFile.getParent();
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_processed_cuda.tiff");

        int count = 2;
        while (testFile.exists()) {
            testFile = new File(parentDir, baseName + "_processed_cuda" + count + ".tiff");
            count++;
        }
        outputFile = testFile;
    }
}
