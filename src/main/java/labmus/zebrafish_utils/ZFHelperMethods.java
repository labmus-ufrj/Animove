package labmus.zebrafish_utils;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.plugin.frame.ContrastAdjuster;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
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
import java.util.stream.IntStream;

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
    private ImagePlus imagePlus;
    @Parameter
    private StatusService statusService;

//    @Parameter(label = "min", min = "0", persist = false)
//    private int min = 0;
//
//    @Parameter(label = "max", min = "0", persist = false)
//    private int max = 255;
//
//    @Parameter(label = "value", min = "0", persist = false)
//    private int value = 128;

    @Override
    public void run() {
        IJ.run("Console");


//        IJ.run(imagePlus, "Select None", "");
//        otherwise we'll have a ROI-only histogram
        ImageStatistics stats = new StackStatistics(imagePlus);
        log.info(Arrays.toString(getMinAndMaxFromHistogram(stats.getHistogram())));

//        apply(imagePlus, imagePlus.getProcessor(), min, max);

    }

    long[] getMinAndMaxFromHistogram(long[] data) {
        long firstIndex = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != 0) {
                firstIndex = i;
                break;
            }
        }
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] != 0) {
                return new long[]{firstIndex, i};
            }
        }
        return null; // won't happen
    }

    /**
     * modified version of ij/plugin/filter/LutApplier.java
     */
    void apply(ImagePlus imp, double min, double max) {
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

    void applyRGBStack(ImagePlus imp, double min, double max) {
        ImageStack stack = imp.getStack();
        IntStream.rangeClosed(1, stack.getSize())
                .forEach(i -> {
                    ImageProcessor ip = stack.getProcessor(i);
                    ip.setMinAndMax(min, max);
                });
    }

    void applyOtherStack(ImagePlus imp, ImageProcessor mask, int[] table) {
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
