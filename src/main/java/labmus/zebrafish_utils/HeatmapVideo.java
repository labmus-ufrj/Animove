package labmus.zebrafish_utils;

import ij.ImagePlus;
import ij.plugin.FolderOpener;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.opencv.global.opencv_core.CV_16UC1;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.opencv.core.Core.NORM_MINMAX;

@Plugin(type = Command.class, menuPath = ZFConfigs.heatmapVideo)
public class HeatmapVideo implements Command, Interactive {

    @Parameter(label = "Input Video", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File inputFile;

    @Parameter(label = "Output File", style = FileWidget.SAVE_STYLE, persist = false, required = false)
    private File outputFile;

    @Parameter(label = "Grayscale", persist = false)
    private boolean convertToGrayscale = false;

    @Parameter(label = "Don't save, open in ImageJ instead", persist = false)
    private boolean openResultInstead = false;

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
        if (!ZFConfigs.checkJavaCV()) {
            return; // if the user chooses to ignore this nothing will work anyway
        }
//        ij.IJ.run("Console");
    }

    private void generate() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        if (outputFile == null && !openResultInstead) {
            return;
        }

        Executors.newSingleThreadExecutor().submit(this::executeProcessing);

        // juntar frames em um video
    }

    private void executeProcessing() {
        File tempDir = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis());
        if (!tempDir.mkdir()) {
            uiService.showDialog("Could not create temporary directory for processing.",
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        tempDir.deleteOnExit();

        log.info("Temp dir: " + tempDir.getAbsolutePath());

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
             Mat accumulator = new Mat()) {

            grabber.start();

            Frame frame;
            int count = 0;
            while ((frame = grabber.grabImage()) != null) {
                try (OpenCVFrameConverter.ToMat cnv = new OpenCVFrameConverter.ToMat()) {

                    Mat currentFrameColor = cnv.convert(frame);
                    Mat currentFrame;

                    // check if we should be converting to grayscale
                    if (convertToGrayscale && currentFrameColor.channels() > 1) {
                        currentFrame = new Mat();
                        cvtColor(currentFrameColor, currentFrame, COLOR_BGR2GRAY);
                    } else {
                        currentFrame = currentFrameColor;
                    }

                    if (count == 0) {
                        currentFrame.convertTo(accumulator, convertToGrayscale ? opencv_core.CV_32SC1 : opencv_core.CV_32SC3);
                    } else {
                        try (Mat tempIntFrame = new Mat()) {
                            currentFrame.convertTo(tempIntFrame, convertToGrayscale ? opencv_core.CV_32SC1 : opencv_core.CV_32SC3);
                            opencv_core.add(accumulator, tempIntFrame, accumulator);
                        }
                    }
                    currentFrame.close();

                    String filename = tempDir.getAbsolutePath() + File.separator + count + ".tiff";
                    try (Mat tempFrame = new Mat()) {
                        // ffmpeg can't create videos from 32bit tiff images
                        // converting them (normalizing, like imageJ does) fixes it
                        opencv_core.normalize(
                                accumulator,          // Source Mat
                                tempFrame,          // Destination Mat
                                0,               // Alpha: The minimum value of the target range
                                65535,           // Beta: The maximum for 16-bit unsigned (2^16-1)
                                NORM_MINMAX,     // Norm type: remains the same
                                convertToGrayscale ? opencv_core.CV_16UC1 : opencv_core.CV_16UC3,        // Dtype: changed to 16-bit unsigned single channel
                                null        // Mask: optional
                        );
                        imwrite(filename, tempFrame);
                    }
                    new File(filename).deleteOnExit(); // tempDir.deleteOnExit is not working
                    statusService.showStatus(count, grabber.getLengthInFrames() - 1, "Saving images");

                    count++;
                }
            }

            if (openResultInstead){
                uiService.show(FolderOpener.open(tempDir.getAbsolutePath(), "virtual"));
            } else {
                createVideo(grabber.getFrameRate(), tempDir, grabber.getLengthInFrames() - 1);
                uiService.showDialog("Heatmap video saved successfully!",
                        "Process Complete", DialogPrompt.MessageType.INFORMATION_MESSAGE);
            }


        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("A fatal error occurred during processing: \n" + e.getMessage(), "Plugin Error", DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

    private void createVideo(double fps, File tempDir, int totalFramesToProcess) throws IOException, InterruptedException {
        Process process = createFFmpegProcess(fps, tempDir);
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))){
            // Monitor progress
            String line;
            while ((line = reader.readLine()) != null) {
                String regex = "frame=\\s*(\\d+)";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    statusService.showStatus(Integer.parseInt(matcher.group(1)), totalFramesToProcess,
                            "Creating Video");
                }

                log.info(line);
            }
        }
        process.waitFor();
    }

    private Process createFFmpegProcess(double fps, File tempDir) throws IOException {
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add(ZFConfigs.ffmpeg);

        commandList.add("-framerate");
        commandList.add(String.valueOf(fps));

        commandList.add("-i");
        commandList.add(tempDir.getAbsolutePath() + File.separator + "%d.tiff");

        commandList.add("-vcodec");
        commandList.add("mjpeg");

        commandList.add("-q");
        commandList.add("2");

        commandList.add(outputFile.getAbsolutePath());

        // Execute FFmpeg command
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        return process;
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
        File testFile = new File(parentDir, baseName + "_heatmap" + ".avi");

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + "_processed_" + count + ".avi");
            count++;
        }
        outputFile = testFile;
    }
}
