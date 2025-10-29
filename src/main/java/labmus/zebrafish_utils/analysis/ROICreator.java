package labmus.zebrafish_utils.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import labmus.zebrafish_utils.ZFConfigs;
import net.imagej.Dataset;
import net.imagej.axis.CalibratedAxis;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;

@Plugin(type = Command.class, menuPath = ZFConfigs.roisPath)
public class ROICreator implements Command, Interactive {
    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;
    @Parameter
    private ImageDisplayService imageDisplayService;

    @Parameter(label = "Well ammount", min = "1", persist = false, callback = "refreshUnit" )
    private int wellNum = 1;

    @Parameter(label = "Well size", min = "1", persist = false, callback = "refreshUnit")
    private double wellSize = 1;

    @Parameter(label = "Unit: ", visibility = ItemVisibility.MESSAGE, persist = false)
    private String unit = "";

    @Parameter(label = "Process", callback = "execute")
    private Button executeButton;

    @Override
    public void run() {
        if (!checkForImage()) {
            return;
        }
        initUnit(imageDisplayService.getActiveImageDisplay());
    }

    private boolean checkForImage() {
        ImageDisplay imageDisplay = imageDisplayService.getActiveImageDisplay();
        if (imageDisplay == null) {
            uiService.showDialog("No image found", "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private void execute() {
        ImageDisplay imageDisplay = imageDisplayService.getActiveImageDisplay();
        if (!checkForImage()) {
            return;
        }
        initUnit(imageDisplay);
        distributeROIs(imageDisplay);
    }

    private void refreshUnit(){
        initUnit(imageDisplayService.getActiveImageDisplay());
    }

    private void initUnit(ImageDisplay imageDisplay) {
        Dataset currentDataset = (Dataset) imageDisplay.getActiveView().getData();
        CalibratedAxis xAxis = currentDataset.axis(0);
        this.unit = xAxis.unit();
    }


    /**
     * Distributes ROIs (Regions of Interest) evenly across the image in a grid pattern.
     * The method calculates the optimal arrangement of wells based on the well number and creates
     * circular ROIs with a specified size. The ROIs are added to ImageJ's ROI Manager and named
     * sequentially as "Well_X" where X is the well number.
     *
     * @param imageDisplay The active image display where ROIs will be created
     */
    private void distributeROIs(ImageDisplay imageDisplay) {
        int[] factors = getFactors(wellNum);

        Dataset currentDataset = (Dataset) imageDisplay.getActiveView().getData();
        double width = currentDataset.dimension(0);
        double height = currentDataset.dimension(1);
        CalibratedAxis xAxis = currentDataset.axis(0);

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
