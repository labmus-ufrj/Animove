package labmus.animove;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

/**
 * Not for use during prod, this class has useful code snippets used
 * in many other Commands in this package
 */
//@Plugin(type = Command.class, menuPath = ZFConfigs.helperPath)
public class ZFHelperMethods {

//    @Parameter
//    ImagePlus imp;
//
//    @Override
//    public void run() {
//        final Java2DFrameConverter biConverter = new Java2DFrameConverter();
//        OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
//        Mat mat = matConverter.convert(biConverter.convert(imp.getBufferedImage()));
//
//        Mat mask = getMaskMatFromRoi(mat.arrayWidth(), mat.arrayHeight(), imp.getRoi());
//
//        DoublePointer max = new DoublePointer(1);
//        opencv_core.minMaxLoc(mat, null, max, null, null, mask);
//        IJ.log("max: " + max.get());
//    }

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
//        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffprobe);
    }

    public static final Function<Mat, Mat> InvertFunction = (mat) -> {
        opencv_core.bitwise_not(mat, mat);
        return mat;
    };

    /**
     * this is expensive. think about it.
     */
    public static Mat getMaskMatFromRoi(int width, int height, Roi roi) {
        if (roi == null) {
            return null; // this is intended behavior
        }
        ImageProcessor bp = new ByteProcessor(width, height);
        ImagePlus imp = new ImagePlus("", bp);
        imp.setRoi(roi);

        Java2DFrameConverter biConverter = new Java2DFrameConverter();
        OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();

        BufferedImage bi = imp.createRoiMask().getBufferedImage();
//        new ImagePlus("roi", bi).show();

        Mat convert = matConverter.convert(biConverter.convert(bi));
        Mat mask8u = new Mat();
        convert.convertTo(mask8u, opencv_core.CV_8U, 255.0, 0.0);
        biConverter.close();
        matConverter.close();

        return mask8u;
    }

    public static ImagePlus getFirstFrame(File inputFile) throws Exception {
        File tempFile = ZFHelperMethods.createPluginTempFile("png");

        // Build FFmpeg command
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add(ZFConfigs.ffmpeg);

        commandList.add("-y");
        commandList.add("-an");
        commandList.add("-noautorotate");
        commandList.add("-i");
        commandList.add("\"" + inputFile.getAbsolutePath() + "\"");

        commandList.add("-vframes");
        commandList.add("1");

        commandList.add("\"" + tempFile.getAbsolutePath() + "\"");

        // Execute FFmpeg command
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while ((reader.readLine()) != null) {
            }
        }
        process.waitFor();
        return new ImagePlus(tempFile.getAbsolutePath());
    }

    public static void iterateOverFrames(Function<Mat, Mat> matFunction,
                                         File inputFile, int startFrame, int endFrame, StatusService statusService) throws Exception {

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
            grabber.start();

            int actualStartFrame = Math.max(0, startFrame - 2);
            grabber.setFrameNumber(actualStartFrame);

            int totalFrames = getExactFrameCount(inputFile) - 1; // frame numbers are 0-indexed.

            int actualEndFrame = (endFrame <= 0 || endFrame > totalFrames) ? totalFrames : endFrame - 1;
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
            for (int i = actualStartFrame; i < actualEndFrame; i++) {
                jcvFrame = grabber.grabImage();
                if (jcvFrame == null || jcvFrame.image == null) {
                    throw new Exception("Read terminated prematurely at frame " + i); // we were NOT done!!
                }

                // No one knows why, and it took a few days to figure out why, but
                // you NEED a new converter every frame here. Dw about it, it doesn't leak.
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

//                    long initTime = System.currentTimeMillis();

                    matFunction.andThen((mat) -> {
                        mat.close();
                        return null;
                    }).apply(currentFrame);

//                    IJ.log("Frame " + i + " processed in " + (System.currentTimeMillis() - initTime) + " ms");

                    currentFrame.close();
                    currentFrameColor.close();
                    if (statusService != null) {
                        statusService.showProgress(i + 1, framesToProcess);
                    }
                }
            }
            System.gc();

        }
        if (statusService != null) {
            statusService.showStatus("Done!");
        }
    }

    public static int getExactFrameCount(File file) throws IOException, InterruptedException {
        // -v error: hide logs
        // -count_frames: actually count them by decoding
        // -select_streams v:0: video stream only
        // -show_entries: print only the number of frames
        ProcessBuilder pb = new ProcessBuilder(
                ZFConfigs.ffprobe,
                "-v", "error",
                "-count_frames",
                "-select_streams", "v:0",
                "-show_entries", "stream=nb_read_frames",
                "-of", "default=nokey=1:noprint_wrappers=1",
                file.getAbsolutePath()
        );
        pb.redirectErrorStream(true); // process may crash without this

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                return Integer.parseInt(line.trim());
            }
        }
        process.waitFor();
        return -1;
    }

    public static File createPluginTempFile(String extension) throws IOException {
        File tempOutputFile = File.createTempFile(ZFConfigs.pluginName + "_", "." + extension);
//        IJ.log("Temp file: " + tempOutputFile.getAbsolutePath());
        tempOutputFile.deleteOnExit();
        return tempOutputFile;
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
