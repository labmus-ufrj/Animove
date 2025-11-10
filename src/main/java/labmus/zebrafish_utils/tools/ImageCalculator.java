package labmus.zebrafish_utils.tools;

import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.ZFHelperMethods;
import labmus.zebrafish_utils.utils.SimpleRecorder;
import labmus.zebrafish_utils.utils.functions.ImageCalculatorFunction;
import labmus.zebrafish_utils.utils.functions.SimpleRecorderFunction;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

@Plugin(type = Command.class, menuPath = ZFConfigs.imgCalcPath)
public class ImageCalculator extends DynamicCommand {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private LogService log;

    @Parameter(label = "Input Video", style = "file", callback = "updateOutputName", persist = false)
    private File videoFile;

    @Parameter(label = "Input Image", style = "file", persist = false)
    private File imageFile;

    @Parameter(label = "Output File", style = "save", persist = false)
    private File outputFile;

    @Parameter(label = "Output Format", choices = {"AVI", "TIFF", "MP4"}, callback = "updateExtensionChoice", persist = false)
    String format = "AVI";

    @Parameter(label = "Operation", callback = "updateOutputName", initializer = "initOperation", persist = false)
    private String operation = "";

    @Parameter(label = "Start Frame", min = "1", persist = false)
    private int startFrame = 1;

    @Parameter(label = "End Frame (0 for entire video)", min = "0", persist = false)
    private int endFrame = 0;

    @Override
    public void run() {
        if (!ensureFilesAreSelected()) {
            return;
        }
        Executors.newSingleThreadExecutor().submit(this::executeProcessing);
    }

    private void executeProcessing() {
        statusService.showStatus("Starting video processing...");
        try {
            Mat image = imread(imageFile.getAbsolutePath());
            Mat grayImage;
            if (image.channels() > 1) {
                grayImage = new Mat();
                cvtColor(image, grayImage, COLOR_BGR2GRAY);
            } else {
                grayImage = image;
            }

            if (image.empty()) {
                throw new Exception("Failed to load the selected image.");
            }

            double fps;
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                grabber.start();
                fps = grabber.getFrameRate();
            }
            SimpleRecorderFunction simpleRecorderFunction = new SimpleRecorderFunction(new SimpleRecorder(this.outputFile, image, fps), uiService);
            ImageCalculatorFunction imageCalculatorFunction = new ImageCalculatorFunction(ImageCalculatorFunction.OperationMode.fromText(this.operation), grayImage);

            ZFHelperMethods.iterateOverFrames(imageCalculatorFunction.andThen(simpleRecorderFunction),videoFile,this.startFrame, this.endFrame, this.statusService);
            simpleRecorderFunction.close();

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("An error occurred: " + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

    private boolean ensureFilesAreSelected() {
        if (videoFile == null || !videoFile.exists()) {
            uiService.showDialog("Please select a valid video file.", "File Error");
            return false;
        }
        if (imageFile == null || !imageFile.exists()) {
            uiService.showDialog("Please select a valid image file.", "File Error");
            return false;
        }
        if (outputFile == null) {
            uiService.showDialog("Please specify a valid output file.", "File Error");
            return false;
        }
        return true;
    }

    protected void updateOutputName() {
        if (videoFile == null || !videoFile.exists()) {
            return;
        }
        String parentDir = videoFile.getParent();
        String baseName = videoFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_" + this.operation.toLowerCase() + "." + format.toLowerCase());

        int count = 2;
        while (testFile.exists()) {
            testFile = new File(parentDir, baseName + "_" + this.operation.toLowerCase() + count + "." + format.toLowerCase());
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
}