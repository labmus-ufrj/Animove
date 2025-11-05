package labmus.zebrafish_utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
import labmus.zebrafish_utils.tools.SimpleRecorder;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.bytedeco.opencv.global.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.opencv.global.opencv_core.copyMakeBorder;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

/**
 * Not for use during prod, this class has useful code snippets used
 * in many other Commands in this package
 */
@Plugin(type = Command.class, menuPath = ZFConfigs.helperPath)
public class ZFHelperMethods implements Command {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
    }

    @Parameter
    private LogService log;
//    @Parameter
//    private ImagePlus imagePlus;


    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;

    @Parameter(label = "Input Video", style = "file", persist = false)
    private File inputFile = new File("C:\\Users\\Murilo\\Desktop\\completacomprimida.mp4");

    @Parameter(label = "Output Video", style = "save", persist = false)
    private File outputFile = new File("C:\\Users\\Murilo\\Desktop\\aaa.mp4");

    @Parameter(label = "Initial Frame", min = "1", description = "inclusive", persist = false)
    private int startFrame = 1;

    @Parameter(label = "End Frame (0 = whole video)", min = "0", description = "inclusive", persist = false)
    private int endFrame = 1000;

    @Override
    public void run() {
        IJ.run("Console");
//        autoAdjustBrightnessStack(imagePlus, true, log);
        FFmpegLogCallback.set();
        test();
    }

    private void test() {
        boolean convertToGrayscale = true;
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
            grabber.start();

            int totalFrames = grabber.getLengthInFrames() - 1; // frame numbers are 0-indexed

            int actualStartFrame = Math.max(0, startFrame - 1);
            int actualEndFrame = (endFrame - 1 <= 0 || endFrame - 1 > totalFrames) ? totalFrames : endFrame - 1;
            if (actualStartFrame >= actualEndFrame) {
                throw new Exception("Initial frame must be before end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;

            if (statusService != null)
                statusService.showStatus("Processing " + (framesToProcess) + " frames...");

            grabber.setFrameNumber(actualStartFrame);

            SimpleRecorder simpleRecorder = new SimpleRecorder(outputFile, grabber);
            simpleRecorder.start();

            // it's better to declare these two here
            // for secret and random memory things
            Frame jcvFrame;
            Mat currentFrame;
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

                    simpleRecorder.recordMat(currentFrame, cnv);

                    currentFrame.close();
                    currentFrameColor.close();
                    framesProcessedCount++;
                    if (statusService != null) {
                        statusService.showProgress(framesProcessedCount, framesToProcess);
                        statusService.showStatus(String.format("Processing frame %d/%d...", framesProcessedCount, framesToProcess));
                    }
                }
            }
            simpleRecorder.close();
        } catch (Exception e) {
            log.error(e);
        }
    }

    public static void autoAdjustBrightnessStack(ImagePlus imp, boolean useROI, LogService log) {
        if (!useROI) {
            IJ.run(imp, "Select None", "");
        }
        ImageStatistics stats = new StackStatistics(imp);
        apply(imp, stats.min, stats.max);
    }

    /**
     * modified version of ij/plugin/filter/LutApplier.java
     */
    private static void apply(ImagePlus imp, double min, double max) {
        int depth = imp.getBitDepth();
        if (imp.getType() == ImagePlus.COLOR_RGB) {
            applyRGBStack(imp, min, max);
            return;
        }
        ImageProcessor ip = imp.getProcessor();
        ip.resetMinAndMax();
        int range = 256;
        if (depth == 16) {
            range = 65536;
            int defaultRange = ImagePlus.getDefault16bitRange();
            if (defaultRange > 0)
                range = (int) Math.pow(2, defaultRange) - 1;
        }
        int tableSize = depth == 16 ? 65536 : 256;
        int[] table = new int[tableSize];
        for (int i = 0; i < tableSize; i++) {
            if (i <= min)
                table[i] = 0;
            else if (i >= max)
                table[i] = range - 1;
            else
                table[i] = (int) (((double) (i - min) / (max - min)) * range);
        }
        ImageProcessor mask = imp.getMask();
        applyOtherStack(imp, mask, table);
        if (depth == 16) {
            imp.setDisplayRange(0, range - 1);
        }
        imp.updateAndDraw();
    }

    private static void applyRGBStack(ImagePlus imp, double min, double max) {
        ImageStack stack = imp.getStack();
        IntStream.rangeClosed(1, stack.getSize())
                .forEach(i -> {
                    ImageProcessor ip = stack.getProcessor(i);
                    ip.setMinAndMax(min, max);
                });
    }

    private static void applyOtherStack(ImagePlus imp, ImageProcessor mask, int[] table) {
        ImageStack stack = imp.getStack();
        IntStream.rangeClosed(1, stack.getSize())
                .forEach(i -> {
                    ImageProcessor ip = stack.getProcessor(i);
                    if (mask != null) ip.snapshot();
                    ip.applyTable(table);
                    ip.reset(mask);
                });
    }

}
