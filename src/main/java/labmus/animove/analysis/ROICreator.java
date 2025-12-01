package labmus.animove.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import labmus.animove.ZFConfigs;
import net.imagej.Dataset;
import net.imagej.axis.CalibratedAxis;
import net.imagej.display.ImageDisplayService;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.roisPath)
public class ROICreator extends DynamicCommand implements Interactive {

    @Parameter(label = "Number of wells", min = "1", persist = false, callback = "refreshUnit" )
    private int wellNum = 1;

    @Parameter(label = "Well size", min = "1", persist = false, callback = "refreshUnit")
    private double wellSize = 1;

    @Parameter(label = "Unit: ", visibility = ItemVisibility.MESSAGE, persist = false)
    private String unit = "";

    @Parameter(label = "Process", callback = "execute")
    private Button executeButton;

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;
    @Parameter
    private ImageDisplayService imageDisplayService;

    @Parameter
    Dataset dataset;

    @Override
    public void run() {
        initUnit();
    }

    private void execute() {
        initUnit();
        distributeROIs();
    }

    private void refreshUnit(){
        initUnit();
    }

    private void initUnit() {
        CalibratedAxis xAxis = dataset.axis(0);
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("unit", String.class);
        item.setValue(this, xAxis.unit()); // this fixes the delayed refresh
    }


    /**
     * Distributes ROIs (Regions of Interest) evenly across the image in a grid pattern.
     * The method calculates the optimal arrangement of wells based on the well number and creates
     * circular ROIs with a specified size. The ROIs are added to ImageJ's ROI Manager and named
     * sequentially as "Well_X" where X is the well number.
     *
     */
    private void distributeROIs() {
        int[] factors = getFactors(wellNum);

        double width = dataset.dimension(0);
        double height = dataset.dimension(1);
        CalibratedAxis xAxis = dataset.axis(0);

        double calibratedSize = wellSize / xAxis.calibratedValue(1);

        ImagePlus imp = IJ.getImage();

        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
//        roiManager.runCommand("Show All");

        for (int row = 0; row < factors[0]; row++) {
            for (int col = 0; col < factors[1]; col++) {

                // put the rectangle the right way
                if (width >= height) {
                    IJ.makeOval((width * (col + 1) / (factors[1] + 1)) - calibratedSize / 2, (height * (row + 1) / (factors[0] + 1)) - calibratedSize / 2, calibratedSize, calibratedSize);
                } else { // heigth > width
                    IJ.makeOval((width * (row + 1) / (factors[0] + 1)) - calibratedSize / 2, (height * (col + 1) / (factors[1] + 1)) - calibratedSize / 2, calibratedSize, calibratedSize);
                }

                imp.getRoi().setName("Well_" + (row * factors[1] + col + 1));

                roiManager.addRoi(imp.getRoi());
            }
        }
        imp.deleteRoi();
        imp.updateAndDraw();
    }

    /**
     * Finds two factors for an integer number.
     *
     * @param num The number for which factors will be found.
     * @return An array with two factors (factor1, factor2). With factor2 >= factor1
     */
    public static int[] getFactors(int num) {
        if (num <= 0) {
            return null;
        }

        int raiz = (int) Math.sqrt(num);

        for (int i = raiz; i >= 1; i--) {
            if (num % i == 0) {
                return new int[]{i, num / i};
            }
        }

        return new int[]{1, num}; // maybe its prime
    }
}
