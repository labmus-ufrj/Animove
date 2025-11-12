package labmus.zebrafish_utils.processing;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import io.scif.services.DatasetIOService;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.ZFHelperMethods;
import labmus.zebrafish_utils.utils.SimpleRecorder;
import labmus.zebrafish_utils.utils.functions.BrightnessLUTFunction;
import labmus.zebrafish_utils.utils.functions.ImageCalculatorFunction;
import labmus.zebrafish_utils.utils.functions.SimpleRecorderFunction;
import labmus.zebrafish_utils.utils.functions.ZprojectFunction;
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

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.heatmapVideoPath)
public class HeatmapVideo extends DynamicCommand implements Interactive {

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

    @Parameter(label = "Lookup Table", persist = false, initializer = "initLUT")
    private String lut = "";

    @Parameter(label = "Output Format", choices = {"AVI", "TIFF", "MP4"}, callback = "updateExtensionChoice", persist = false)
    String format = "AVI";

    @Parameter(label = "Don't save, open in ImageJ instead", persist = false)
    private boolean openResultInstead = false;

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
        if (!checkFiles()){
            return;
        }

        Roi singleRoi;
        if (previewImagePlus != null) {
            singleRoi = previewImagePlus.getRoi();
        } else {
            RoiManager rm = RoiManager.getInstance();
            singleRoi = (rm != null && rm.getSelectedIndex() != -1) ? rm.getRoi(rm.getSelectedIndex()) : null;
        }

        lastRoi = singleRoi == null ? lastRoi : singleRoi;
        // Validate ROI exists
        if (lastRoi == null) {
            uiService.showDialog("Create a ROI enclosing your target area",
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        previewImagePlus.close();

        Roi finalRoi = lastRoi;
        Executors.newSingleThreadExecutor().submit(() -> this.executeProcessing(finalRoi, doPreview));
    }

    private void executeProcessing(Roi roi, boolean doPreview) {

        try {
            File tempOutputFile = ZFHelperMethods.createPluginTempFile(this.format.toLowerCase());

            ZprojectFunction zprojectFunctionAvg = new ZprojectFunction(ZprojectFunction.OperationMode.AVG);
            ZFHelperMethods.iterateOverFrames(ZFHelperMethods.InvertFunction.andThen(zprojectFunctionAvg), inputFile, startFrame, doPreview ? startFrame + 10 : endFrame, statusService);
            Mat avgMat = zprojectFunctionAvg.getResultMat();

            Function<Mat, Mat> subtractFunction = new ImageCalculatorFunction(ImageCalculatorFunction.OperationMode.ADD, avgMat);
            Function<Mat, Mat> bcFunction = (mat) -> {
                mat.convertTo(mat, -1, 1, 30); // todo: maybe either calculate beta automatically or let the user choose...
                return mat;
            };
            ZprojectFunction zprojectFunctionSum = new ZprojectFunction(ZprojectFunction.OperationMode.SUM, true);

            BrightnessLUTFunction brightnessLUTFunction = new BrightnessLUTFunction(roi, this.lut);

            double fps;
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
                grabber.start();
                fps = grabber.getFrameRate();
            }
            SimpleRecorderFunction simpleRecorderFunction = new SimpleRecorderFunction(new SimpleRecorder(tempOutputFile, avgMat, fps), uiService);

            ZFHelperMethods.iterateOverFrames(subtractFunction
                    .andThen(bcFunction)
                    .andThen(ZFHelperMethods.InvertFunction)
                    .andThen(zprojectFunctionSum)
                    .andThen(brightnessLUTFunction)
                    .andThen(simpleRecorderFunction), this.inputFile, this.startFrame, doPreview ? this.startFrame + 10 : this.endFrame, this.statusService);

            brightnessLUTFunction.close();
            simpleRecorderFunction.close();

            if (openResultInstead || doPreview) {
                statusService.showStatus("Opening result in ImageJ...");
                simpleRecorderFunction.getRecorder().openResultinIJ(uiService, datasetIOService);
            } else {
                Files.copy(tempOutputFile.toPath(), outputFile.toPath());
                uiService.showDialog("Video saved successfully!",
                        ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }

    }

    private void updateOutputName() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        String parentDir = inputFile.getParent();
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_heatmapVideo" + "." + format.toLowerCase());

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + "_heatmapVideo_" + count + "." + format.toLowerCase());
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

    private void openFrame() {
        if (inputFile == null || !inputFile.exists() || !inputFile.isFile()) {
            uiService.showDialog("Could not open video: \n Invalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        String extension = inputFile.getName().substring(inputFile.getName().lastIndexOf(".") + 1).toLowerCase();
        if (!extension.contentEquals("avi") && !extension.contentEquals("mp4")) {
            uiService.showDialog("Could not open video: \n Invalid extension ." + extension, ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                if (previewImagePlus != null && previewImagePlus.getWindow() != null) {
                    previewImagePlus.close();
                }
                previewImagePlus = ZFHelperMethods.getFirstFrame(inputFile);
                previewImagePlus.setTitle("First frame");
                uiService.show(previewImagePlus);
            } catch (Exception e) {
                log.error(e);
                uiService.showDialog("Could not open video: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            }
        });
    }

    public void initLUT() {
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("lut", String.class);
        item.setChoices(Stream.concat(Stream.of(HeatmapImages.defaultLut), Arrays.stream(IJ.getLuts())).collect(Collectors.toList()));
    }

    private boolean checkFiles() {
        if (inputFile == null || !inputFile.exists()) {
            uiService.showDialog("Invalid input file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        if (outputFile == null || outputFile.isDirectory()) {
            if (!openResultInstead) {
                uiService.showDialog("Invalid output file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
                return false;
            }
        } else if (outputFile.exists()){
            uiService.showDialog("Output file already exists", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        return true;
    }
}
