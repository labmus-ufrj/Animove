package labmus.zebrafish_utils.processing;

import ij.IJ;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.tools.ImageCalculator;
import labmus.zebrafish_utils.tools.ZProjectOpenCV;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.heatmapImages)
public class HeatmapImages extends DynamicCommand implements Interactive {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter(label = "Input Video", style = FileWidget.OPEN_STYLE, callback = "updateOutputFolder", persist = false, required = false)
    private File inputFile;

    @Parameter(label = "Output Folder", style = FileWidget.DIRECTORY_STYLE, persist = false, required = false)
    private File outputDir;

    @Parameter(label = "Invert before operation", persist = false)
    private boolean invertVideo = true;

    @Parameter(label = "Lookup Table", persist = false, initializer = "initLUT")
    private String lut = "";

    @Parameter(label = "Don't save, open in ImageJ instead", persist = false, description = "Files won't be saved to the specified Output Folder. Instead, they'll be opened in ImageJ")
    private boolean openResultInstead = false;

    @Parameter(label = "Start")
    private boolean doStartInterval = true;

    @Parameter(label = "Interval")
    private String startInterval = "1-5400";

    @Parameter(label = "Middle")
    private boolean doMiddleInterval = true;

    @Parameter(label = "Interval")
    private String middleInterval = "15301-20700";

    @Parameter(label = "End")
    private boolean doEndInterval = true;

    @Parameter(label = "Interval")
    private String endInterval = "30601-36000";

    @Parameter(label = "Custom")
    private boolean doCustomInterval = false;

    @Parameter(label = "Interval")
    private String customInterval = "5401-6400";

    @Parameter(label = "Process", callback = "generate")
    private Button btn1;

    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private LogService log;

    @Override
    public void run() {
//        IJ.run("Console", "");
    }

    private void generate() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        if ((outputDir == null || !Files.isDirectory(outputDir.toPath())) && !openResultInstead) {
            return;
        }

        Executors.newSingleThreadExecutor().submit(this::executeProcessing);

    }

    private void executeProcessing() {

        try {

            File tempDir = createTempDir();
            log.info("Temp dir: " + tempDir.getAbsolutePath());

            // this just allows to re-use the same code for all intervals.
            // looks messy, ik
            for (String interval : Arrays.asList(doStartInterval ? startInterval : "",
                    doMiddleInterval ? middleInterval : "",
                    doEndInterval ? endInterval : "",
                    doCustomInterval ? customInterval : "")) {

                if (interval.isEmpty()) continue;

                String[] a = interval.split("-");
                if (a.length != 2) {
                    throw new Exception("Invalid interval: " + interval);
                }

                int startFrame = Integer.parseInt(a[0]);
                int endFrame = Integer.parseInt(a[1]);

                // invert
                // ?? needs to be done inside the functions

                // create avg
//                boolean invertBehaviour = invertVideo;
                boolean invertBehaviour = true;

                Mat avg = ZProjectOpenCV.applyVideoOperation(ZProjectOpenCV.OperationMode.AVG,
                        inputFile, true, invertBehaviour, startFrame, endFrame, statusService);

                Mat avg8bit = new Mat();
                opencv_core.normalize(
                        avg,
                        avg8bit,
                        0,
                        Math.pow(2, 8) - 1,
                        opencv_core.NORM_MINMAX,
                        opencv_core.CV_8UC1,
                        null
                );

                // subtract avg from inverted stack
                File tempOutputFile = File.createTempFile(ZFConfigs.pluginName + "_", ".avi"); // todo: change to tiff? let user decide?
                log.info("Temp file: " + tempOutputFile.getAbsolutePath());
                tempOutputFile.deleteOnExit();
                ImageCalculator.calculateVideoOperation(ImageCalculator.OperationMode.SUBTRACT,
                        inputFile, avg8bit, tempOutputFile, startFrame, endFrame, null);

                // invert result

                // somehow adjust brightness and contrast (we were prompting user to do so)
                // it's a math thing i gess

                // Sum slices Z projection
                Mat sum = ZProjectOpenCV.applyVideoOperation(ZProjectOpenCV.OperationMode.SUM,
                        tempOutputFile, true, invertBehaviour, startFrame, endFrame, statusService);

                // invert result

                Mat mat = new Mat();
                // convert to 16-bits (normalize dont forget)
                opencv_core.normalize(
                        sum,
                        mat,
                        0,
                        Math.pow(2, 16) - 1,
                        opencv_core.NORM_MINMAX,
                        opencv_core.CV_16UC1,
                        null
                );

                // auto brightness adjust


//                Mat mat = ZProjectOpenCV.applyVideoOperation(ZProjectOpenCV.OperationMode.SUM,
//                        inputFile, convertToGrayscale, invertVideo, startFrame, endFrame, statusService);

                File file = new File(tempDir.getAbsolutePath() + File.separator + interval + ".tif");
                imwrite(file.getAbsolutePath(), mat);

//               yes, there's a way to apply LUT using opencv_core.LUT();
//               but there's no clear path to convert imageJ LUT's to a valid openCV LUT.
//               maybe one day...

                if (lut.contains("Don't Change") && !openResultInstead) {
                    // I'm told moving files across drives (like C: and D:) might fail using the old java.io.File.
                    // that's why I'm using java.nio here but nowhere else
                    Files.move(file.toPath(), outputDir.toPath().resolve(file.getName()));
                } else {
                    file.deleteOnExit();
                    IJ.open(file.getAbsolutePath());
                    IJ.getImage().setTitle(interval); // only needed if openResultInstead but costs nothing if ran rn
                    if (!lut.contains("Don't Change")) {
                        IJ.run(IJ.getImage(), this.lut, "");
                    }
//                    IJ.getImage().duplicate().show();
                    if (!openResultInstead) {
                        // save image and close IJ window
                        IJ.save(IJ.getImage(), outputDir.toPath().resolve(file.getName()).toString());
                        IJ.getImage().close();
                    }
                }

                if (!openResultInstead) {
                    uiService.showDialog("Processing done", ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);
                }

                mat.close();

            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
        }

    }

    private File createTempDir() throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis());
        if (!tempDir.mkdir()) {
            throw new Exception("Could not create temporary directory for processing.");
        }
        tempDir.deleteOnExit();
        return tempDir;
    }

    private void updateOutputFolder() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        String parentDir = inputFile.getParent();
        outputDir = new File(parentDir);
    }

    public void initLUT() {
        final MutableModuleItem<String> item =
                getInfo().getMutableInput("lut", String.class);
        item.setChoices(Stream.concat(Stream.of("Don't Change"), Arrays.stream(IJ.getLuts())).collect(Collectors.toList()));
    }
}
