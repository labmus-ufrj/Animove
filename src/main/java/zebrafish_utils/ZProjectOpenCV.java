package zebrafish_utils;

import ij.ImagePlus;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.log.LogService;
import org.scijava.app.StatusService;

import java.io.File;

import org.bytedeco.opencv.global.opencv_core;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;

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

    @Parameter(label = "Processing Mode", choices = {"Darkest (Min)", "Average", "Brightest (Max)"}, persist = false)
    private String mode = "Darkest (Min)";

    @Parameter(label = "Initial Frame", min = "0", persist = false)
    private int startFrame = 0;

    @Parameter(label = "End Frame (0 = whole video)", min = "0", persist = false)
    private int endFrame = 0;

    @Parameter(label = "Open processed image", persist = false)
    private boolean openResult = true;

    @Override
    public void run() {

        if (!ZFConfigs.checkJavaCV()){
            return;
        }

//        ij.IJ.run("Console");

        if (inputFile == null || !inputFile.exists()) {
            uiService.showDialog("Select a valid video file.", "Input Error");
            return;
        }
        statusService.showStatus(0, 100, "Starting processing...");

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
             OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat()) {

            grabber.start();

            int totalFrames = grabber.getLengthInFrames();

            int actualStartFrame = Math.max(0, startFrame);
            int actualEndFrame = (endFrame <= 0 || endFrame > totalFrames) ? totalFrames : endFrame;
            if (actualStartFrame > actualEndFrame) {
                throw new Exception("Initial frame can't be after end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;

            statusService.showStatus("Processing " + framesToProcess + " frames from " + inputFile.getName());

            grabber.setFrameNumber(actualStartFrame);

            Frame jcvFrame = grabber.grabImage();
            if (jcvFrame == null || jcvFrame.image == null) {
                throw new Exception("Failed to read the initial frame (" + actualStartFrame + "). The video may be shorter than expected or the frame may be corrupt.");
            }
            Mat frame = converter.convert(jcvFrame);

            Mat accumulator;
            if (mode.equals("Average")) {
                accumulator = new Mat();
                frame.convertTo(accumulator, opencv_core.CV_64FC3);
            } else {
                accumulator = frame.clone();
            }

            int framesProcessedCount = 1;
            statusService.showProgress(framesProcessedCount, framesToProcess);

            for (int i = actualStartFrame; i < actualEndFrame - 2; i++) {

                Frame currentJcvFrame = grabber.grabImage();
                if (currentJcvFrame == null) {
                    uiService.showDialog("Read terminated prematurely at frame " + (i), "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
                    break;
                }

                Mat currentFrame = converter.convert(currentJcvFrame);
                if (currentFrame == null || currentFrame.isNull()) continue;

                switch (mode) {
                    case "Darkest (Min)":
                        opencv_core.min(accumulator, currentFrame, accumulator);
                        break;
                    case "Brightest (Max)":
                        opencv_core.max(accumulator, currentFrame, accumulator);
                        break;
                    case "Average":
                        try (Mat tempFloatFrame = new Mat()) {
                            currentFrame.convertTo(tempFloatFrame, opencv_core.CV_64FC3);
                            opencv_core.add(accumulator, tempFloatFrame, accumulator);
                        }
                        break;
                }

                framesProcessedCount++;
                statusService.showProgress(framesProcessedCount, framesToProcess);
                statusService.showStatus(String.format("Processing frame %d/%d...", (i + 1), actualEndFrame));
            }

            Mat resultMat = new Mat();
            if (mode.equals("Average")) {
                double scale = 1.0 / framesProcessedCount;
                accumulator.convertTo(resultMat, opencv_core.CV_8UC3, scale, 0);

            } else {
                resultMat = accumulator;
            }

            imwrite(outputFile.getAbsolutePath(), resultMat);

//            statusService.showStatus(String.format("Done! Image saved as " + outputFile.getName()));

            frame.close();
            accumulator.close();
            if (resultMat != accumulator) resultMat.close();

            if (openResult){
                uiService.show(new ImagePlus(outputFile.getAbsolutePath()));
            }

            log.info("Processing done.");
            uiService.showDialog("Image saved as " + outputFile.getAbsolutePath(),
                    "Processing Done", DialogPrompt.MessageType.INFORMATION_MESSAGE);
            statusService.clearStatus();

        } catch (Exception e) {
            log.error("A fatal error occurred during processing", e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

    protected void updateOutputName() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        String parentDir = inputFile.getParent();
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_processed.tiff");

        int count = 2;
        while (testFile.exists()) {
            testFile = new File(parentDir, baseName + "_processed" + count + ".tiff");
            count++;
        }
        outputFile = testFile;
    }

}