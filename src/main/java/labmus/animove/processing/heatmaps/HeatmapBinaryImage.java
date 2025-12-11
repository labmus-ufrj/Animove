package labmus.animove.processing.heatmaps;

import ij.ImagePlus;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.functions.*;
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

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.heatmapBinaryImagePath)
public class HeatmapBinaryImage extends DynamicCommand implements Interactive {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter(label = "Input Video", style = "file", callback = "updateOutputName", persist = false, required = false)
    private File inputFile;

    @Parameter(label = "Open Frame", callback = "openFrame")
    private Button btn1;

    @Parameter(label = "Output Image", style = "save", persist = false, required = false)
    private File outputFile;

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
        if (previewImagePlus != null){
            previewImagePlus.close();
        }
        Executors.newSingleThreadExecutor().submit(() -> this.executeProcessing(doPreview));
    }

    private void executeProcessing(boolean doPreview) {
        try {
            File tempOutputFile = ZFHelperMethods.createPluginTempFile(
                    outputFile.getName().substring(outputFile.getName().lastIndexOf(".") + 1));
            // whatever the user chooses if imwrite supports it

            ZprojectFunction zprojectFunction = new ZprojectFunction(ZprojectFunction.OperationMode.MIN);

            Function<Mat, Mat> processFunction = new BinarizeFromThresholdFunction(false)
                    .andThen(zprojectFunction);

            ZFHelperMethods.iterateOverFrames(processFunction, inputFile, startFrame, doPreview ? this.startFrame + 9 : this.endFrame, statusService);
            Mat heatmapMat = zprojectFunction.getResultMat();

            ZprojectFunction zprojectFunctionAvg = new ZprojectFunction(ZprojectFunction.OperationMode.AVG);
            ZFHelperMethods.iterateOverFrames(zprojectFunctionAvg, inputFile, startFrame, doPreview ? this.startFrame + 9 : this.endFrame, statusService);
            Mat avgMat = zprojectFunctionAvg.getResultMat();

            Mat resultMat = new BinarizeFromThresholdFunction(false)
                    .andThen(ZFHelperMethods.InvertFunction)
                    .andThen(new ImageCalculatorFunction(ImageCalculatorFunction.OperationMode.ADD, heatmapMat))
//                .andThen(new ShowEveryFrameFunction())
                    .apply(avgMat);

            imwrite(tempOutputFile.getAbsolutePath(), resultMat);
            resultMat.close();
            heatmapMat.close();
            avgMat.close();

            ImagePlus imp = new ImagePlus(tempOutputFile.getAbsolutePath());
            imp.setTitle(outputFile.getName());
            imp.show();

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
     * Generates a unique output filename by appending a mode-matching suffix and the appropriate extension.
     */
    protected void updateOutputName() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }

        String parentDir = inputFile.getParent();
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_binaryHeatmap" + ".tif");

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + "_binaryHeatmap_" + count + ".tif");
            count++;
        }
        outputFile = testFile;
    }

    public void initProc() {
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("mode", String.class);
        item.setChoices(Arrays.stream(ZprojectFunction.OperationMode.values()).map(ZprojectFunction.OperationMode::getText).collect(Collectors.toList()));
    }

    private void openFrame() {
        if (inputFile == null || !inputFile.exists() || !inputFile.isFile()) {
            uiService.showDialog("Could not open video:\nInvalid file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
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

}
