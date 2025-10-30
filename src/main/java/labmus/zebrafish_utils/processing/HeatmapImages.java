package labmus.zebrafish_utils.processing;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.tools.ZProjectOpenCV;
import net.imagej.Dataset;
import net.imagej.display.ImageDisplay;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.convert.ConvertService;
import org.scijava.display.DisplayService;
import org.scijava.event.EventService;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.opencv.core.Core.NORM_MINMAX;

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

    @Parameter(label = "Grayscale", persist = false)
    private boolean convertToGrayscale = true;

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

    @Parameter(label = "Interval", initializer = "")
    private String endInterval = "30601-36000";

    @Parameter(label = "Custom")
    private boolean doCustomInterval = false;

    @Parameter(label = "Interval")
    private String customInterval = "5401-6400";

    @Parameter(label = "Process", callback = "generate")
    private Button btn1;

    @Parameter
    private DisplayService ds;
    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private LogService log;

    @Override
    public void run() {
        IJ.run("Console", "");
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
        // ver o que o zprojectCV faz

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
                Mat mat = ZProjectOpenCV.applyVideoOperation(ZProjectOpenCV.OperationMode.SUM,
                        inputFile, convertToGrayscale, invertVideo,Integer.parseInt(a[0]), Integer.parseInt(a[1]), null);

                File file = new File(tempDir.getAbsolutePath() + File.separator + interval + ".tiff");
                imwrite(file.getAbsolutePath(), mat);

                // how will you apply LUT???

                if(openResultInstead){
                    file.deleteOnExit();
                    IJ.open(file.getAbsolutePath());
                    IJ.getImage().setTitle(interval);
                } else {
                    // I'm told moving files across drives (like C: and D:) might fail using the old File.
                    // that's why I'm using nio here but nowhere else
                    Files.move(file.toPath(), outputDir.toPath().resolve(file.getName()));
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
        item.setChoices(Arrays.asList(IJ.getLuts()));
    }
}
