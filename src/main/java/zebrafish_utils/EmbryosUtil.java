// was coding this but gave up dw will come back later


//package zebrafish_utils;
//
//import ij.IJ;
//import ij.ImagePlus;
//import ij.WindowManager;
//import ij.gui.OvalRoi;
//import ij.gui.Roi;
//import ij.plugin.frame.RoiManager;
//import ij.process.ImageStatistics;
//import net.imagej.Dataset;
//import net.imagej.ImageJ;
//import net.imagej.axis.Axes;
//import net.imagej.ops.OpService;
//import net.imglib2.img.display.imagej.ImageJFunctions;
//import net.imglib2.type.numeric.RealType;
//import net.imglib2.type.numeric.integer.UnsignedByteType;
//import org.scijava.command.Command;
//import org.scijava.log.LogService;
//import org.scijava.plugin.Parameter;
//import org.scijava.plugin.Plugin;
//import org.scijava.table.DefaultResultsTable;
//import org.scijava.table.ResultsTable;
//import org.scijava.ui.UIService;
//
//
///**
// * A SciJava plugin that provides tools for analyzing embryos, based on an original ImageJ macro.
// * This class contains three separate plugins that will appear under the "Embryos Menu Tool" menu in ImageJ.
// *
// * @author Murilo Nespolo Spineli (Original Macro)
// * @author Gemini (SciJava Conversion)
// */
//public class EmbryosMenuTool {
//
//    // This class is a container for the individual plugin classes below.
//
//    /**
//     * A utility method to get the active ROI Manager instance, creating one if it doesn't exist.
//     * @return The active RoiManager instance.
//     */
//    private static RoiManager getRoiManager() {
//        RoiManager rm = RoiManager.getInstance();
//        if (rm == null) {
//            rm = new RoiManager();
//        }
//        return rm;
//    }
//
//    /**
//     * Creates a default set of 6 circular ROIs for a 6-well plate.
//     * This is a shared utility method used by other plugins in this class.
//     * @param rm The active ROI Manager.
//     * @param image The image to base the ROI size and placement on.
//     * @param uiService The UI service for showing dialogs.
//     * @param log The log service for error reporting.
//     */
//    private static void createDefaultRois(RoiManager rm, Dataset image, UIService uiService, LogService log) {
//        // Get pixel dimensions from the image calibration.
//        double pixelWidth = image.axis(Axes.X).scale();
//        if (pixelWidth == 0) {
//            log.error("Pixel width is zero. Cannot create ROIs.");
//            uiService.showDialog("The image's pixel width is zero. Cannot create ROIs.", "Invalid Scale");
//            return;
//        }
//
//        // Calculate the radius for a 16mm well in pixels.
//        double radiusInPixels = (1.0 / pixelWidth) * 16;
//
//        // Create and add 6 ROIs in a 2x3 grid pattern.
//        int roiCounter = 1;
//        for (int i = 1; i < 4; i = i + 2) {
//            for (int j = 1; j < 6; j = j + 2) {
//                double x_pos = radiusInPixels * j;
//                double y_pos = radiusInPixels * i;
//
//                // Create an oval ROI. The position is top-left, so adjust for center.
//                OvalRoi roi = new OvalRoi(x_pos - radiusInPixels, y_pos - radiusInPixels, radiusInPixels * 2, radiusInPixels * 2);
//                rm.addRoi(roi);
//                rm.rename(rm.getCount() - 1, "Group " + roiCounter++);
//            }
//        }
//
//        rm.runCommand("Show All");
//    }
//
//
//    /**
//     * Replicates the functionality of the "Composed Blur" macro command.
//     * This plugin creates a new image by combining two different mean-filtered versions of the original.
//     */
//    @Plugin(type = Command.class, menuPath = "Embryos Menu Tool>Composed Blur")
//    public static class ComposedBlurPlugin implements Command {
//
//        @Parameter
//        private Dataset currentImage;
//
//        @Parameter(label = "Mean Radius (pixels)", min = "0")
//        private double radius = 5.0;
//
//        @Override
//        public void run() {
//            ImagePlus originalImp = ImageJFunctions.wrap(currentImage, currentImage.getName());
//            String originalTitle = originalImp.getTitle();
//
//            // 1. Duplicate the input image twice to create two working copies.
//            ImagePlus imp1 = originalImp.duplicate();
//            imp1.setTitle("Mean_1");
//            ImagePlus imp2 = originalImp.duplicate();
//            imp2.setTitle("Mean_2");
//            imp1.show();
//            imp2.show();
//
//            // 2. Apply a mean filter with the specified radius to the first copy.
//            IJ.run(imp1, "Mean...", "radius=" + radius);
//
//            // 3. Apply a mean filter with double the radius to the second copy.
//            IJ.run(imp2, "Mean...", "radius=" + (radius * 2));
//
//            // 4. Combine the two blurred images using a logical OR operation.
//            IJ.run(imp1, "Image Calculator...", "image1=Mean_1 operation=OR image2=Mean_2 create");
//            ImagePlus result = WindowManager.getCurrentImage();
//            result.setTitle("Composed Blur of " + originalTitle);
//
//            // 5. Invert the final result.
//            IJ.run(result, "Invert", "");
//
//            // 6. Close the intermediate images.
//            imp1.close();
//            imp2.close();
//        }
//    }
//
//    /**
//     * Replicates the functionality of the "Create ROI's" macro command.
//     * This plugin creates a default set of 6 circular ROIs for a 6-well plate.
//     */
//    @Plugin(type = Command.class, menuPath = "Embryos Menu Tool>Create ROI's")
//    public static class CreateRoisPlugin implements Command {
//
//        @Parameter
//        private UIService uiService;
//
//        @Parameter
//        private LogService log;
//
//        @Parameter
//        private Dataset currentImage;
//
//        @Override
//        public void run() {
//            RoiManager rm = getRoiManager();
//
//            // Check if ROIs already exist.
//            if (rm.getCount() != 0) {
//                uiService.showDialog("This command expects an empty ROI Manager. Please clear it before running.", "ROI Manager Not Empty");
//                return;
//            }
//
//            // Check if the image has a spatial calibration (scale).
//            if (!currentImage.isCalibrated()) {
//                uiService.showDialog("Global scale is required. Please set the image scale first (Analyze > Set Scale...).", "Scale Not Set");
//                return;
//            }
//
//            // Create the ROIs using the shared method.
//            createDefaultRois(rm, currentImage, uiService, log);
//
//            uiService.showDialog("6 ROIs have been created. Please move and rename them as needed.", "ROIs Created");
//        }
//    }
//
//    /**
//     * Replicates the main "SUM Quant" functionality of the macro.
//     * This plugin performs a series of image calculations and measurements based on two input images and a set of ROIs.
//     */
//    @Plugin(type = Command.class, menuPath = "Embryos Menu Tool>SUM Quant")
//    public static class SumQuantPlugin implements Command {
//
//        @Parameter
//        private UIService uiService;
//
//        @Parameter
//        private LogService log;
//
//        @Parameter(label = "MIN Image")
//        private Dataset minImage;
//
//        @Parameter(label = "AVG Image")
//        private Dataset avgImage;
//
//        @Override
//        public void run() {
//            // Step 1: Handle ROIs
//            if (!handleROIs()) {
//                return; // Stop if ROI setup fails or is cancelled by the user.
//            }
//            RoiManager rm = getRoiManager();
//
//            // Step 2: Image calculations
//            log.info("Starting image calculations...");
//            ImagePlus minImp = ImageJFunctions.wrap(minImage, minImage.getName());
//            ImagePlus avgImp = ImageJFunctions.wrap(avgImage, avgImage.getName());
//
//            // Duplicate AVG image and invert it
//            ImagePlus avgCopy = avgImp.duplicate();
//            avgCopy.setTitle("AVG_Copy_Inverted");
//            IJ.run(avgCopy, "Invert", "");
//
//            // Add inverted AVG to MIN image
//            IJ.run(minImp, "Image Calculator...", "image1=[" + minImp.getTitle() + "] operation=Add image2=[" + avgCopy.getTitle() + "] create");
//            ImagePlus processedImp = WindowManager.getCurrentImage();
//            avgCopy.close(); // Close the intermediate image
//
//            // Step 3: Convert to 8-bit, apply LUT, and Invert
//            log.info("Converting to 8-bit and adjusting contrast...");
//            IJ.run(processedImp, "8-bit", "");
//            IJ.run(processedImp, "HiLo", ""); // Apply HiLo LUT for visualization
//
//            // Step 4: Auto Brightness & Contrast
//            autoBC(processedImp, 1, 0.7);
//
//            // Invert the result for final measurements
//            IJ.run(processedImp, "Invert", "");
//
//            // Step 5: Extract Results from ROIs
//            log.info("Extracting results from ROIs...");
//            extractResults(processedImp, rm);
//
//            // Show the final processed image
//            processedImp.show();
//            log.info("SUM Quant processing complete.");
//        }
//
//        /**
//         * Manages the setup of ROIs. Checks for existing ROIs, and if none are found,
//         * creates a default set using the shared utility method.
//         * @return true if ROIs are correctly set up, false otherwise.
//         */
//        private boolean handleROIs() {
//            RoiManager rm = getRoiManager();
//            if (rm.getCount() != 0 && rm.getCount() != 6) {
//                uiService.showDialog("Error: Expected either 0 or 6 ROIs in the ROI Manager.", "ROI Error");
//                return false;
//            }
//
//            if (!avgImage.isCalibrated()) {
//                uiService.showDialog("Error: The AVG image requires a global scale. Please set it via Analyze > Set Scale.", "Scale Error");
//                return false;
//            }
//
//            if (rm.getCount() == 0) {
//                uiService.showDialog("No ROIs found. A default set of 6 will be created. Please adjust their positions and names, then run this command again.", "ROI Notice");
//
//                // Create the ROIs using the shared method.
//                createDefaultRois(rm, avgImage, uiService, log);
//
//                return false; // Stop execution to allow user to adjust ROIs.
//            }
//            return true;
//        }
//
//        /**
//         * Performs an automatic brightness and contrast adjustment based on the macro's logic.
//         * @param imp The ImagePlus to adjust.
//         * @param fs Frame skip parameter (from macro, seems to be slice skip).
//         * @param threshold A factor to adjust the auto-threshold value.
//         */
//        private void autoBC(ImagePlus imp, int fs, double threshold) {
//            // This method mimics the autoBC and ajusteMinMaxAuto functions from the macro.
//
//            // First, get an auto-threshold value to estimate a good minimum.
//            IJ.setAutoThreshold(imp, "Triangle dark no-reset");
//            double[] thresholds = imp.getProcessor().getThreshold();
//            double lower = thresholds[0];
//            imp.getProcessor().resetThreshold();
//
//            // Find the maximum pixel value across the specified slices (fs = frame skip).
//            double maxVal = 0;
//            for (int n = 1; n <= imp.getNSlices(); n += fs) {
//                imp.setSlice(n);
//                ImageStatistics stats = imp.getStatistics();
//                if (stats.max > maxVal) {
//                    maxVal = stats.max;
//                }
//            }
//
//            // Set the display range (Min/Max)
//            imp.setDisplayRange(lower * threshold, maxVal);
//            IJ.run(imp, "Apply LUT", "");
//        }
//
//        /**
//         * Measures area and integrated density for each ROI and populates a results table.
//         * @param imp The ImagePlus to measure.
//         * @param rm The RoiManager containing the ROIs.
//         */
//        private void extractResults(ImagePlus imp, RoiManager rm) {
//            ResultsTable table = new DefaultResultsTable();
//            Roi[] rois = rm.getRoisAsArray();
//
//            for (int i = 0; i < rois.length; i++) {
//                imp.setRoi(rois[i]);
//
//                // Use Li thresholding as specified in the macro
//                IJ.setAutoThreshold(imp, "Li dark no-reset");
//
//                // The macro used List.getMeasurements which implies a particle analysis.
//                // A more direct way to get Area and IntDen for a thresholded ROI is to run Analyze Particles.
//                // We will measure on the thresholded area within the ROI.
//                // "limit" ensures measurements are restricted to the thresholded pixels.
//                IJ.run(imp, "Measure", "limit");
//
//                // Get the results from the system ResultsTable which "Measure" just populated.
//                ResultsTable systemResults = ResultsTable.getResultsTable();
//                int lastRow = systemResults.getCounter() - 1;
//
//                table.incrementCounter();
//                table.addValue("Group", rois[i].getName());
//
//                // Check if columns exist before trying to access them
//                if (systemResults.columnExists("Area")) {
//                    table.addValue("Area (mm^2)", systemResults.getValue("Area", lastRow));
//                }
//                if (systemResults.columnExists("IntDen")) {
//                    table.addValue("IntDen", systemResults.getValue("IntDen", lastRow));
//                }
//                if (systemResults.columnExists("RawIntDen")) {
//                    table.addValue("RawIntDen", systemResults.getValue("RawIntDen", lastRow));
//                }
//            }
//
//            // Clear the selection from the image
//            imp.killRoi();
//
//            // Reset the main results window to avoid confusion.
//            IJ.run("Clear Results");
//
//            uiService.show("Embryo SUM Results", table);
//        }
//    }
//}
