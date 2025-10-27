package labmus.zebrafish_utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

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
    private ImagePlus imagePlus;
    /*
    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;
     */

    @Override
    public void run() {
        IJ.run("Console");
        autoAdjustBrightnessStack(imagePlus, false, log);
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
