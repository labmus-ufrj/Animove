package labmus.animove.tools;

import ij.ImagePlus;
import io.scif.services.DatasetIOService;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.SimpleRecorder;
import labmus.animove.utils.functions.ImageCalculatorFunction;
import labmus.animove.utils.functions.SimpleRecorderFunction;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
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

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.imgCalcPath)
public class ImageCalculator extends DynamicCommand implements Interactive {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter(label = "Input Video", style = "file", callback = "updateOutputName", persist = false, required = false)
    private File inputVideoFile;

    @Parameter(label = "Open Frame", callback = "openFrame")
    private Button btn1;

    @Parameter(label = "Input Image", style = "file", persist = false, required = false)
    private File imageFile;

    @Parameter(label = "Output File", style = "save", persist = false, required = false)
    private File outputFile;

    @Parameter(label = "Output Format", choices = {"AVI", "TIFF", "MP4"}, callback = "updateExtensionChoice", persist = false)
    String format = "AVI";

    @Parameter(label = "Don't save, open in ImageJ instead", persist = false)
    private boolean openResultInstead = false;

    @Parameter(label = "Invert before operation", persist = false)
    private boolean invertVideo = false;

    @Parameter(label = "Operation", callback = "updateOutputName", initializer = "initOperation", persist = false)
    private String operation = "";

    @Parameter(label = "Start Frame", min = "1", persist = false)
    private int startFrame = 1;

    @Parameter(label = "End Frame (0 for entire video)", min = "0", persist = false)
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
        if (previewImagePlus != null) {
            previewImagePlus.close();
        }
        Executors.newSingleThreadExecutor().submit(() -> this.executeProcessing(doPreview));
    }

    private void executeProcessing(boolean doPreview) {
        try {
            File tempOutputFile = ZFHelperMethods.createPluginTempFile(this.format.toLowerCase());

            Mat grayImage = imread(imageFile.getAbsolutePath(), opencv_imgcodecs.IMREAD_GRAYSCALE);

            if (grayImage.empty()) {
                throw new Exception("Failed to load the selected image.");
            }

            Function<Mat, Mat> inverter = invertVideo ? ZFHelperMethods.InvertFunction : Function.identity();
            double fps;
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputVideoFile)) {
                grabber.start();
                fps = grabber.getFrameRate();
            }
            SimpleRecorderFunction simpleRecorderFunction = new SimpleRecorderFunction(new SimpleRecorder(tempOutputFile, grayImage, fps), uiService);
            ImageCalculatorFunction imageCalculatorFunction = new ImageCalculatorFunction(ImageCalculatorFunction.OperationMode.fromText(this.operation), grayImage);

            ZFHelperMethods.iterateOverFrames(inverter
                    .andThen(imageCalculatorFunction)
                    .andThen(simpleRecorderFunction), inputVideoFile, this.startFrame, doPreview ? this.startFrame + 9 : this.endFrame, this.statusService);

            simpleRecorderFunction.close();

            if (openResultInstead || doPreview) {
                statusService.showStatus("Opening result in ImageJ...");
                simpleRecorderFunction.getRecorder().openResultinIJ(uiService, datasetIOService, false);
            } else {
                Files.copy(tempOutputFile.toPath(), outputFile.toPath());
                tempOutputFile.deleteOnExit();
                uiService.showDialog("Video saved successfully!",
                        ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);
            }
            grayImage.close();

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("An error occurred: " + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

    private boolean checkFiles() {
        if (inputVideoFile == null || !inputVideoFile.exists() || !inputVideoFile.isFile()) {
            uiService.showDialog("Invalid input video", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        if (imageFile == null || !imageFile.exists() || !imageFile.isFile()) {
            uiService.showDialog("Invalid input image", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        if (outputFile == null || outputFile.isDirectory()) {
            if (!openResultInstead) {
                uiService.showDialog("Invalid output file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
                return false;
            }
        } else if (outputFile.exists()) {
            uiService.showDialog("Output file already exists", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    protected void updateOutputName() {
        if (inputVideoFile == null || !inputVideoFile.exists()) {
            return;
        }
        String parentDir = inputVideoFile.getParent();
        String baseName = inputVideoFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_" + this.operation.toLowerCase() + "." + format.toLowerCase());

        int count = 2;
        while (testFile.exists()) {
            testFile = new File(parentDir, baseName + "_" + this.operation.toLowerCase() + "_" + count + "." + format.toLowerCase());
            count++;
        }
        outputFile = testFile;
        updateExtensionFile();
    }

    public void initOperation() {
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("operation", String.class);
        item.setChoices(Arrays.stream(ImageCalculatorFunction.OperationMode.values()).map(ImageCalculatorFunction.OperationMode::getText).collect(Collectors.toList()));
    }

    public void updateExtensionChoice() {
        if (outputFile == null) {
            return;
        }

        String newFileName = outputFile.getAbsolutePath();
        String[] a = outputFile.getName().split("\\.");
        String extension = a[a.length - 1];
        newFileName = newFileName.replace("." + extension, "." + format.toLowerCase());
        this.outputFile = new File(newFileName);
    }

    public void updateExtensionFile() {
        if (outputFile == null) {
            return;
        }
        String[] a = outputFile.getName().split("\\.");
        String extension = a[a.length - 1];
        if (extension.equalsIgnoreCase("tiff") || extension.equalsIgnoreCase("tif")) {
            format = "TIFF";
        } else if (extension.equalsIgnoreCase("mp4")) {
            format = "MP4";
        } else if (extension.equalsIgnoreCase("avi")) {
            format = "AVI";
        }
    }

    private void openFrame() {
        if (inputVideoFile == null || !inputVideoFile.exists() || !inputVideoFile.isFile()) {
            uiService.showDialog("Could not open video: \n Invalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        String extension = inputVideoFile.getName().substring(inputVideoFile.getName().lastIndexOf(".") + 1).toLowerCase();
        if (!extension.contentEquals("avi") && !extension.contentEquals("mp4")) {
            uiService.showDialog("Could not open video: \n Invalid extension ." + extension, ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                if (previewImagePlus != null && previewImagePlus.getWindow() != null) {
                    previewImagePlus.close();
                }
                previewImagePlus = ZFHelperMethods.getFirstFrame(inputVideoFile);
                previewImagePlus.setTitle("First frame");
                uiService.show(previewImagePlus);
            } catch (Exception e) {
                log.error(e);
                uiService.showDialog("Could not open video: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            }
        });
    }
}