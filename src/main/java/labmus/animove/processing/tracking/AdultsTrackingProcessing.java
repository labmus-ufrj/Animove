package labmus.animove.processing.tracking;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import io.scif.services.DatasetIOService;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.SimpleRecorder;
import labmus.animove.utils.functions.*;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Plugin(type = Command.class, menuPath = ZFConfigs.adultsTrackingPath)
public class AdultsTrackingProcessing extends DynamicCommand implements Interactive {
    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter(label = "Input Video", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File inputFile;

    @Parameter(label = "Open Frame", callback = "openFrame")
    private Button btn1;

    @Parameter(label = "Output File", style = FileWidget.SAVE_STYLE, callback = "updateExtensionFile", persist = false, required = false)
    private File outputFile;

    @Parameter(label = "Output Format", choices = {"AVI", "TIFF"}, callback = "updateExtensionChoice", persist = false)
    String format = "AVI";

    @Parameter(label = "Save output", persist = false)
    private boolean saveOutput = true;

    @Parameter(label = "Initial Frame", min = "1", persist = false)
    private int startFrame = 1;

    @Parameter(label = "End Frame (0 = whole video)", min = "0", persist = false)
    private int endFrame = 0;

    @Parameter(label = "Preview", callback = "generatePreview")
    private Button btn2;

    @Parameter(label = "Process", callback = "generateFull")
    private Button btn3;

    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private LogService log;
    @Parameter
    private DatasetIOService datasetIOService;

    private ImagePlus previewImagePlus = null;
    private Roi lastRoi = null;

    @Override
    public void run() {
//        IJ.run("Console");
    }

    private void generatePreview() {
        generate(true);
    }

    private void generateFull() {
        generate(false);
    }

    private void generate(boolean doPreview) {
        if (!checkFiles()) {
            return;
        }

        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager();
        }
        Roi singleRoi;
        if (previewImagePlus != null && previewImagePlus.getRoi() != null) {
            singleRoi = previewImagePlus.getRoi();
            singleRoi.setName(ZFConfigs.pluginName);
            rm.addRoi(singleRoi);
        } else {
            int roiIndex = rm.getSelectedIndex() != -1 ? rm.getSelectedIndex() : 0;
            singleRoi = rm.getRoi(roiIndex);
        }

        lastRoi = singleRoi == null ? lastRoi : singleRoi;
        // Validate ROI exists
        if (lastRoi == null) {
            uiService.showDialog("Create a ROI enclosing your target area",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        if (previewImagePlus != null) {
            previewImagePlus.close();
        }
        Executors.newSingleThreadExecutor().submit(() -> this.executeProcessing(doPreview));
    }

    private void executeProcessing(boolean doPreview) {
        try {
            File tempOutputFile = ZFHelperMethods.createPluginTempFile(this.format.toLowerCase());

            double fps;
            int w;
            int h;
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
                grabber.start();
                fps = grabber.getFrameRate();
                w = grabber.getImageWidth();
                h = grabber.getImageHeight();
            }
            SimpleRecorderFunction recorderFunction = new SimpleRecorderFunction(
                    new SimpleRecorder(tempOutputFile, w, h, fps), uiService);

            Mat mask = ZFHelperMethods.getMaskMatFromRoi(w, h, lastRoi);

            Function<Mat, Mat> processFunction = ZFHelperMethods.InvertFunction
                    .andThen(new SubtractBackgroundFunction(25) // todo: hardcoded value
                            .andThen(new AdjustBrightnessUsingThreshold(0.7, mask))
                            .andThen(recorderFunction));

            ZFHelperMethods.iterateOverFrames(processFunction, inputFile, startFrame, doPreview ? this.startFrame + 9 : this.endFrame, statusService);
            recorderFunction.close();
            mask.close();

            recorderFunction.getRecorder().openResultinIJ(uiService, datasetIOService, false, outputFile.getName());
            IJ.getImage().setRoi(lastRoi);

            if (saveOutput && !doPreview){
                Files.copy(tempOutputFile.toPath(), outputFile.toPath());
            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }


    /**
     * Callback method to update the output filename when an input file changes.
     * Generates a unique output filename by appending "_heatmap" and the appropriate extension.
     */
    private void updateOutputName() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        String parentDir = inputFile.getParent();
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_adultsTracking" + "." + format.toLowerCase());

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + "_adultsTracking_" + count + "." + format.toLowerCase());
            count++;
        }
        outputFile = testFile;
        updateExtensionFile();
    }

    public void updateExtensionChoice() {
        if (outputFile == null) {
            return;
        }
        String newFileName = outputFile.getAbsolutePath()
                .substring(0, outputFile.getAbsolutePath().lastIndexOf(".") + 1)
                .concat(this.format.toLowerCase());
        this.outputFile = new File(newFileName);
    }

    public void updateExtensionFile() {
        if (outputFile == null) {
            return;
        }
        String extension = outputFile.getName().substring(outputFile.getName().lastIndexOf(".") + 1);
        if (extension.equalsIgnoreCase("tiff") || extension.equalsIgnoreCase("tif")) {
            format = "TIFF";
        } else if (extension.equalsIgnoreCase("mp4")) {
            format = "MP4";
        } else if (extension.equalsIgnoreCase("avi")) {
            format = "AVI";
        }
    }

    public void initLUT() {
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("lut", String.class);
        item.setChoices(Stream.concat(Stream.of("Don't Change"), Arrays.stream(IJ.getLuts())).collect(Collectors.toList()));
    }

    private boolean checkFiles() {
        if (inputFile == null || !inputFile.exists()) {
            uiService.showDialog("Invalid input file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        if (saveOutput) {
            if (outputFile == null || outputFile.isDirectory()) {
                uiService.showDialog("Invalid output file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
                return false;
            } else if (outputFile.exists()) {
                uiService.showDialog("Output file already exists", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
                return false;
            }
        }
        return true;
    }

    private void openFrame() {
        if (inputFile == null || !inputFile.exists() || !inputFile.isFile()) {
            uiService.showDialog("Could not open video: \n Invalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                if (previewImagePlus != null && previewImagePlus.getWindow() != null) {
                    previewImagePlus.close();
                }
                previewImagePlus = ZFHelperMethods.getFirstFrame(inputFile);
                previewImagePlus.setTitle("First frame");
                if (lastRoi != null) {
                    previewImagePlus.setRoi(lastRoi);
                }
                uiService.show(previewImagePlus);
            } catch (Exception e) {
                log.error(e);
                uiService.showDialog("Could not open video: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            }
        });
    }
}
