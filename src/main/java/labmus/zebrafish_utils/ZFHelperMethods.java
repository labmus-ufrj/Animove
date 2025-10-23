package labmus.zebrafish_utils;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Not for use during prod, this class has useful code snippets used
 * in many other Commands in this package
 */
@Plugin(type = Command.class, menuPath = ZFConfigs.helperPath)
public class ZFHelperMethods implements Command {
    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;
    @Parameter
    private ImageDisplayService imageDisplayService;
    @Parameter
    private StatusService statusService;

    @Parameter(label = "min", min = "0", persist = false)
    private int min = 0;

    @Parameter(label = "max", min = "0", persist = false)
    private int max = 255;

    @Parameter(label = "value", min = "0", persist = false)
    private int value = 128;

    @Override
    public void run() {
        IJ.run("Console");

        log.info((int)map(value, 0, 255, min, max));

//        ImageDisplay activeDisplay = imageDisplayService.getActiveImageDisplay();
//
//        if (activeDisplay == null) {
//            IJ.createImage("Untitled", "8-bit noise", 512, 512, 100).show();
//            activeDisplay = imageDisplayService.getActiveImageDisplay();
//        }
//
//        setMinMax(activeDisplay, 0, 255, log);

    }

    /**
     *  We need this to keep the abstraction T
     *  i didnt get it as well dw
     */
    private static <T extends RealType<T>> ImagePlus wrapWithType(Dataset datasetToWrap) {
        RandomAccessibleInterval<T> raiTyped = (RandomAccessibleInterval<T>) datasetToWrap;
        return ImageJFunctions.wrap(raiTyped, datasetToWrap.getName());
    }


    public static void setMinMax(ImageDisplay activeDisplay, long min, long max, LogService log ) {
        Dataset dataset = (Dataset) activeDisplay.getActiveView().getData();
        ImagePlus imagePlus = wrapWithType(dataset);

        double displayRangeMin = imagePlus.getDisplayRangeMin();
        double displayRangeMax = imagePlus.getDisplayRangeMax();

        log.info(displayRangeMin + displayRangeMax);

        LoopBuilder.setImages(dataset).multiThreaded()
                .forEachChunk(chunk -> {
                    chunk.forEachPixel(realType -> {
//                        valor atual: realType.getRealDouble()
//                        novo min: min
//                        novo max: max

//                        COMO MAPEAR ESSA MUDANÇA????????
                        realType.setReal(map(realType.getRealDouble(), displayRangeMin, displayRangeMax, min, max));
                    });
                    return null;
                });
    }

    /**
     * Transforms a value from one numeric scale to another.
     *
     * @param value              The number to be transformed.
     * @param originalScaleStart The minimum value of the original scale (X).
     * @param originalScaleEnd   The maximum value of the original scale (Y).
     * @param newScaleStart      The minimum value of the new scale (Z).
     * @param newScaleEnd        The maximum value of the new scale (W).
     * @return The equivalent value in the new scale.
     */
    public static double map(double value, double originalScaleStart, double originalScaleEnd, double newScaleStart, double newScaleEnd) {
        // linear interpolation
        return newScaleStart + ((value - originalScaleStart) * (newScaleEnd - newScaleStart)) / (originalScaleEnd - originalScaleStart);
    }

    /**
     * Calculates the global minimum and maximum pixel values across all dimensions of an image.
     * Uses multithreaded processing for better performance (when not using a ROI).
     * Providing a ROI slows down this tremendously.
     *
     * <p>Searches all slices/frames in a stack. The ROI is for x,y positions only.</p>
     *
     * @param imageDisplay  The ImageDisplay object containing the image data to analyze
     * @param roi           ROI for searching within (can be null)
     * @param statusService Optional service to display processing status/speed (can be null)
     * @return long array containing [globalMin, globalMax] pixel values
     */
    // there's absolutely no need for a 64bit return here. let's ignore that. I like 64bit images.
    public static long[] getRealMinMax(ImageDisplay imageDisplay, Roi roi, StatusService statusService) {
        long millis = System.currentTimeMillis();

        long[] minMaxCount = null;
        if (roi == null) {
            // use the much faster multiThreaded alternative
            minMaxCount = getRealMinMaxMultiThreaded(imageDisplay);
        } else {
            // use the slower multithreaded alternative
            minMaxCount = getRealMinMaxSingleThreaded(imageDisplay, roi);
        }

        // Display processing speed if statusService is available
        if (statusService != null) {
            statusService.showStatus("min/max at " + Math.round(minMaxCount[2] / ((System.currentTimeMillis() - millis) * 1000.0)) + " pixels/us");
        }
        return new long[]{minMaxCount[0], minMaxCount[1]};
    }

    private static long[] getRealMinMaxSingleThreaded(ImageDisplay imageDisplay, Roi roi) {
        Dataset dataset = (Dataset) imageDisplay.getActiveView().getData();

        long[] dimensions = new long[dataset.numDimensions()];
        dataset.dimensions(dimensions);

        Cursor<RealType<?>> cursor = dataset.localizingCursor();

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long count = 0;

        while (cursor.hasNext()) {
            cursor.fwd();
            if (roi.contains((int) cursor.getLongPosition(0), (int) cursor.getLongPosition(1))) {
                count++;
                long value = (long) cursor.get().getRealDouble();
                if (value < min) min = value;
                if (value > max) max = value;
            }
        }

        return new long[]{min, max, count};
    }

    private static long[] getRealMinMaxMultiThreaded(ImageDisplay imageDisplay) {
        Dataset dataset = (Dataset) imageDisplay.getActiveView().getData();

        long[] dimensions = new long[dataset.numDimensions()];
        dataset.dimensions(dimensions);

        List<long[]> minMaxValues = LoopBuilder.setImages(dataset).multiThreaded()
                .forEachChunk(chunk -> {
                    final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
                    final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
                    chunk.forEachPixel(realType -> {
                        min.set(Math.min(min.get(), (long) realType.getRealDouble()));
                        max.set(Math.max(max.get(), (long) realType.getRealDouble()));
                    });
                    return new long[]{min.get(), max.get()};
                });

        long globalMin = Long.MAX_VALUE;
        long globalMax = Long.MIN_VALUE;
        for (long[] minMaxCount : minMaxValues) {
            globalMin = Math.min(globalMin, minMaxCount[0]);
            globalMax = Math.max(globalMax, minMaxCount[1]);
        }

        // multiplying the dimensions together gets the total pixel count
        long totalPixels = Arrays.stream(dimensions).reduce(1, (a, b) -> a * (b + 1));

        return new long[]{globalMin, globalMax, totalPixels};
    }
}
