package labmus.zebrafish_utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
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
import org.scijava.ui.UIService;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.IntStream;

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

    public static final Function<Mat, Mat> InvertFunction = (mat) -> {
        opencv_core.bitwise_not(mat, mat);
        return mat;
    };

    @Parameter
    private LogService log;

    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;

    @Override
    public void run() {
        IJ.run("Console");
//        autoAdjustBrightnessStack(imagePlus, true, log);
//        FFmpegLogCallback.set();
    }

    public static void iterateOverFrames(Function<Mat, Mat> matFunction,
                                         File inputFile, int startFrame, int endFrame, StatusService statusService) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {

            int actualStartFrame = Math.max(0, startFrame - 1);
            grabber.setFrameNumber(actualStartFrame);

            grabber.start();

            int totalFrames = grabber.getLengthInFrames() - 1; // frame numbers are 0-indexed.
            /*
             this is NOT precise
             see https://javadoc.io/static/org.bytedeco/javacv/1.4.4/org/bytedeco/javacv/FFmpegFrameGrabber.html#getLengthInVideoFrames--
             that's why we are increasing the precision when its set for the entire video.
             */
            final boolean wholeVideo = (endFrame <= 0);
            int actualEndFrame = (wholeVideo || endFrame > totalFrames) ? totalFrames : endFrame;
            if (actualStartFrame >= actualEndFrame) {
                throw new Exception("Initial frame must be before end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;

            if (statusService != null) {
                statusService.showStatus("Processing frames... ");
            }

            // it's better to declare these two here
            // for secret and random memory things
            Frame jcvFrame;
            Mat currentFrame;
            for (int i = actualStartFrame; i < actualEndFrame || wholeVideo; i++) {
                jcvFrame = grabber.grabImage();
                if (jcvFrame == null || jcvFrame.image == null) {
                    if (wholeVideo) {
                        break; // we are done!!
                    }
                    throw new Exception("Read terminated prematurely at frame " + i); // we were NOT done!!
                }

                // No one knows why, and it took a few days to figure out why, but
                // you NEED a new converter every frame here. Dw about it, it doesn't leak.
                // todo: test without a new one every frame
                try (OpenCVFrameConverter.ToMat cnv = new OpenCVFrameConverter.ToMat()) {
                    Mat currentFrameColor = cnv.convert(jcvFrame);

                    // check if we should be converting to grayscale
                    if (currentFrameColor.channels() > 1) {
                        currentFrame = new Mat();
                        cvtColor(currentFrameColor, currentFrame, COLOR_BGR2GRAY);
                    } else {
                        currentFrame = currentFrameColor;
                    }

                    if (currentFrame.isNull()) continue;

//                    currentFrame = matTransformer.apply(currentFrame);

                    matFunction.andThen((mat) -> {
                        mat.close();
                        return null;
                    }).apply(currentFrame);

                    currentFrame.close();
                    currentFrameColor.close();
                    if (statusService != null) {
                        statusService.showProgress(i + 1, framesToProcess);
                    }
                }
                if (i % 100 == 0) {
                    System.gc();
                }
            }
            System.gc();

        }
        if (statusService != null) {
            statusService.showStatus("Done!");
        }
    }


    public static void autoAdjustBrightnessStack(ImagePlus imp, boolean useROI) {
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
//        ImageProcessor mask = imp.getMask();
        ImageProcessor mask = imp.createRoiMask(); // same resolution as image
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
