package labmus.animove.tools;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.AVI_Reader;
import ij.plugin.frame.RoiManager;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
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
import org.scijava.widget.NumberWidget;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A SciJava Command for video processing and conversion using FFmpeg.
 * This plugin provides functionality for:
 * 1. Converting videos between different formats and codecs
 * 2. Applying transformations (crop, rotation, flipping)
 * 3. Adjusting video quality and frame rate
 * 4. Generating preview frames and posview images
 * 5. Processing multiple regions of interest (ROIs)
 * <p>
 * This is an interactive plugin that maintains UI responsiveness during processing
 * by running operations in background threads. This also means it is non-blocking by nature.
 */
@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.ffmpegPath)
public class FFmpegPlugin implements Command, Interactive {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;

    @Parameter(label = "Input File", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File inputFile;

    @Parameter(label = "Open Frame & Get Info", callback = "openFrame")
    private Button previewButton;

    @Parameter(label = "Output File", style = FileWidget.SAVE_STYLE, persist = false, required = false)
    private File outputFile;

    @Parameter(label = "Output Codec", choices = {"libx264", "libx265", "mjpeg"}, callback = "updateOutputFileExtension", persist = false)
    private String outputCodec = "mjpeg";

    @Parameter(label = "Quality (1=Lowest, 10=Highest)", style = NumberWidget.SLIDER_STYLE, min = "1", max = "10", persist = false)
    private int quality = 10;

    @Parameter(label = "Start Frame", min = "0", persist = false)
    private int startFrame = 0;

    @Parameter(label = "End Frame (0 for the last frame)", persist = false)
    private int endFrame = 0;

    @Parameter(label = "Output FPS", min = "1", persist = false)
    private double outputFps = 25.0;

    @Parameter(label = "Horizontal Flip", persist = false)
    private boolean horizontalFlip = false;

    @Parameter(label = "Vertical Flip", persist = false)
    private boolean verticalFlip = false;

    @Parameter(label = "Rotation", choices = {"0°", "90°", "180°", "270°", "-90°", "-180°", "-270°"}, persist = false)
    private String rotation = "0°";

    @Parameter(label = "Crop using active ROI", persist = false)
    private boolean useRoiCrop = false;

    @Parameter(label = "Multi-Crop (one video per ROI)", persist = false)
    private boolean multiCrop = false;

    @Parameter(label = "Preview", callback = "showPosview")
    private Button posviewButton;

    @Parameter(label = "Process", callback = "runProcessing")
    private Button fastRunButton;


    private ImagePlus previewImagePlus;
    private ImagePlus posviewImagePlus;
    private Roi lastCropRoi;

    /**
     * Runs once when the plugin inits, it's a framework feature.
     */
    @Override
    public void run() {
//        FFmpegLogCallback.set();
    }

    /**
     * Callback method for the "Process" button.
     * Initiates video processing with no posview generation.
     */
    private void runProcessing() {
        startProcessingThread(false);
    }

    /**
     * Callback method for the "Posview" button.
     * Initiates video processing with posview generation.
     */
    private void showPosview() {
        startProcessingThread(true);
    }

    /**
     * Callback method to update the output filename when an input file changes.
     * Generates a unique output filename by appending "_processed" and the appropriate extension.
     */
    private void updateOutputName() {
        if (inputFile == null || !inputFile.exists()) {
            return;
        }
        String parentDir = inputFile.getParent();
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        String suggestedExtension = outputCodec.equals("mjpeg") ? ".avi" : ".mp4";
        File testFile = new File(parentDir, baseName + "_processed" + suggestedExtension);

        int count = 2;
        while (testFile.exists()) {
            // naming the file with a sequential number to avoid overwriting
            testFile = new File(parentDir, baseName + "_processed_" + count + suggestedExtension);
            count++;
        }
        outputFile = testFile;
    }

    private void openFrame() {
        try {
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
                    if (lastCropRoi != null) {
                        previewImagePlus.setRoi(lastCropRoi);
                    }
                    uiService.show(previewImagePlus);
                } catch (Exception e) {
                    log.error(e);
                    uiService.showDialog("Could not open video: \n" + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
                }
            });

            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
                grabber.start();
                double inputFps = grabber.getFrameRate();
                if (inputFps > 0) {
                    this.outputFps = inputFps;
                }
            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("Could not open a preview of the file (it may not be a valid video): " + inputFile.getName(),
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            previewImagePlus = null;
        }
    }

    /**
     * Callback method to update output file extension when codec changes.
     * Updates extension to .avi for mjpeg codec or .mp4 for other codecs.
     */
    private void updateOutputFileExtension() {
        if (outputFile == null) return;
        String baseName = outputFile.getName().replaceFirst("[.][^.]+$", "");
        String newExtension = outputCodec.equals("mjpeg") ? ".avi" : ".mp4";
        outputFile = new File(outputFile.getParent(), baseName + newExtension);
    }

    /**
     * This method validates inputs and starts the processing in a background thread
     * to maintain UI responsiveness during long operations.
     *
     * @param isPosview true if processing should generate a posview image,
     *                  false for normal video processing
     */
    private void startProcessingThread(boolean isPosview) {
        if (!isInputsValid(isPosview)) return;

        // We can't do this in the same thread as the UI and ImageJ. Everything will freeze.
        Executors.newSingleThreadExecutor().submit(() -> executeProcessing(isPosview));
//        new Thread(() -> executeProcessing(isPosview)).start();
    }

    /**
     * Validates the input parameters before video processing.
     * Checks if:
     * 1. Input and output files are specified
     * 2. Output file doesn't already exist
     * 3. Posview mode is not used with Multi-Crop
     *
     * @param isPosview true if processing is for posview generation, false otherwise
     * @return true if all inputs are valid, false if any validation fails
     */
    private boolean isInputsValid(boolean isPosview) {
        if (inputFile == null || outputFile == null) {
            uiService.showDialog("Select an input file and an output file.",
                    "Missing files", DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        } else if (outputFile.exists()) {
            uiService.showDialog("Output file already exists. Please delete it or choose a different name.",
                    "Overwriting", DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        if (isPosview && multiCrop) {
            uiService.showDialog("Can't create preview in Multi-Crop mode.",
                    "Multi-Crop Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Executes the video processing operation based on user configuration.
     * This method handles both single ROI and multi-ROI (Multi-Crop) processing modes.
     * In Multi-Crop mode, it processes the video once for each ROI in the ROI Manager,
     * creating separate output files. In single ROI mode, it processes the video once
     * with an optional active ROI crop.
     *
     * @param isPosview true if generating a posview image, false for normal video processing.
     *                  Note: Multi-Crop mode ignores posview setting.
     */
    private void executeProcessing(boolean isPosview) {
        try {
            RoiManager roiManager = RoiManager.getInstance();
            if (roiManager == null && (multiCrop || useRoiCrop)) { // if we are not using ROI's, dont open the ROIManager
                roiManager = new RoiManager();
            }


            // Handle Multi-Crop mode
            if (multiCrop) {
                // Validate ROI manager has ROIs
                if (roiManager.getCount() == 0) {
                    uiService.showDialog("Multi-Crop mode requires ROIs in the ROI Manager.",
                            "Multi-Crop Error", DialogPrompt.MessageType.ERROR_MESSAGE);
                    return;
                }

                // Setup output directory and base filename
                File outputDirectory = outputFile.getParentFile();
                String baseName = outputFile.getName().replaceFirst("[.][^.]+$", "");

                log.info("Starting Multi-Crop processing for " + roiManager.getCount() + " ROIs...");

                // Process each ROI separately
                int roiIndex = 0;
                for (Roi roi : roiManager.getRoisAsArray()) {
                    // Update status for current ROI
                    statusService.showStatus(roiIndex++, roiManager.getCount(), "Processing Multi-Crop: " + roi.getName());

                    // Create output filename for this ROI
                    String outputName = baseName + "_" + roi.getName() + (outputCodec.equals("mjpeg") ? ".avi" : ".mp4");
                    File finalOutputFile = new File(outputDirectory, outputName);

                    // Process video for this ROI
                    processVideo(finalOutputFile, isPosview); // isPosview is always false here
                }
            }
            // Handle single ROI mode
            else {
                // Get active ROI if crop option enabled
                if (useRoiCrop) {
                    Roi singleRoi;
                    if (previewImagePlus != null && previewImagePlus.getRoi() != null) {
                        singleRoi = previewImagePlus.getRoi();
                        singleRoi.setName(ZFConfigs.pluginName);
                        roiManager.addRoi(singleRoi);
                    } else {
                        int roiIndex = roiManager.getSelectedIndex() != -1 ? roiManager.getSelectedIndex() : 0;
                        singleRoi = roiManager.getRoi(roiIndex);
                    }

                    lastCropRoi = singleRoi == null ? lastCropRoi : singleRoi;
                    // Validate ROI exists
                    if (lastCropRoi == null) {
                        uiService.showDialog("The 'Crop using active ROI' option was checked, but no ROI was found.",
                                "ROI Error", DialogPrompt.MessageType.ERROR_MESSAGE);
                        return;
                    }
                }

                // Process video with single ROI
                processVideo(outputFile, isPosview);
            }

            // Clear status and show completion message
            statusService.clearStatus();
            if (!isPosview) {
                if (this.outputCodec.contentEquals("mjpeg")) {
                    ImagePlus imp = AVI_Reader.open(outputFile.getAbsolutePath(), true);
                    imp.setTitle(outputFile.getName());
                    imp.show();
                }
            }
        } catch (Exception e) {
            // Log and show any errors
            log.error("A fatal error occurred during processing.", e);
            uiService.showDialog("An error occurred: " + e.getMessage(),
                    "Unexpected Error", DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

    /**
     * Processes a video file by applying specified filters and transformations.
     * This method handles different processing modes:
     * 1. Normal video processing: Converts video with specified codec and filters
     * 2. Posview mode: Creates a single frame with all filters applied for preview
     *
     * @param currentOutputFile The output file where processed video/image will be saved
     * @param isPosview         If true, generates a single frame posview image
     * @throws Exception If any error occurs during processing
     */
    private void processVideo(File currentOutputFile, boolean isPosview) throws Exception {
//        if (previewImagePlus != null) {
//            previewImagePlus.close();
//        }
//        the user may have forgotten to use mark the crop option, we don't want that ROI gone
        // Get video properties from input file
        double fps;
        int totalFramesToProcess;
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
            grabber.start();
            fps = grabber.getFrameRate();
            int finalFrame = (endFrame <= 0 || endFrame > grabber.getLengthInFrames()) ? grabber.getLengthInFrames() : endFrame;
            totalFramesToProcess = Math.max(0, finalFrame - startFrame);
        }

        // For posview modes, set output to temporary file and process single frame
        if (isPosview) {
            totalFramesToProcess = 1;
            currentOutputFile = File.createTempFile(ZFConfigs.pluginName + "_", ".png");
            currentOutputFile.deleteOnExit();
        }

        // Build FFmpeg command
        // Start by getting the binary path from bytedeco's lib
        // yep that's a thing, and that's how this works
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add(ZFConfigs.ffmpeg);

        // Add input file and start time
        commandList.add("-ss");
        commandList.add(convertFrameToTimestamp((float) fps, startFrame));
        commandList.add("-an");
        commandList.add("-y");
        commandList.add("-noautorotate");
        commandList.add("-i");
        commandList.add(inputFile.getAbsolutePath());

        commandList.add("-pix_fmt");
        if (outputCodec.equals("mjpeg")) {
            // for some unknown reason color_range setting does not work as expected. using the deprecated one instead.
            commandList.add("yuvj420p");
            commandList.add("-strict");
            commandList.add("unofficial");
        } else {
            // maximizing compatibility with players. bgr24 works but breaks avi (VLC works, but it's an exception)
            commandList.add("yuv420p");
            commandList.add("-color_range");
            commandList.add("pc");
        }


        // Set codec and quality for normal video processing
        if (!isPosview) {
            commandList.add("-vcodec");
            commandList.add(outputCodec);

            if (outputCodec.equals("mjpeg")) {
                commandList.add("-q");
            } else {
                commandList.add("-crf");
            }
            commandList.add(mapQualityToCodec(outputCodec, quality));
        }

        commandList.add("-vf");
        commandList.add(buildVideoFilter());

        // Set number of frames to process
        commandList.add("-vframes");
        commandList.add(String.valueOf(totalFramesToProcess));

        // Add output file
        commandList.add(currentOutputFile.getAbsolutePath());

        log.info(commandList.toString());

        // Execute FFmpeg command
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);

        log.info("Starting processing for: " + currentOutputFile.getName());

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            // Monitor progress
            String line;
            while ((line = reader.readLine()) != null) {
                String regex = "frame=\\s*(\\d+)";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    statusService.showStatus(Integer.parseInt(matcher.group(1)), totalFramesToProcess,
                            "Processing " + currentOutputFile.getName());
                }

                log.info(line);
            }
        }
        process.waitFor();

        // Show posview if needed
        if (isPosview) {
            if (posviewImagePlus != null && posviewImagePlus.getWindow() != null) {
                posviewImagePlus.close();
            }
            posviewImagePlus = new ImagePlus(currentOutputFile.getAbsolutePath());
            posviewImagePlus.setTitle("Preview Image");
            uiService.show(posviewImagePlus);
        }
    }

    /**
     * Converts a frame number to an FFmpeg-compatible timestamp string in the format "HH:MM:SS.mmm"
     *
     * @param fps          The frames per second of the video
     * @param initialFrame The frame number to convert to timestamp
     * @return A string timestamp in the format "HH:MM:SS.mmm" (e.g., "01:23:45.678")
     */
    private String convertFrameToTimestamp(float fps, int initialFrame) {
        // Convert frame number to total seconds by dividing by fps
        // Using double for better precision in time calculations
        double totalSeconds = initialFrame / (double) fps;

        // Convert total seconds to hours (3600 seconds per hour)
        int hours = (int) (totalSeconds / 3600);
        double remainingSecondsAfterHours = totalSeconds % 3600;

        // Convert remaining seconds to minutes (60 seconds per minute)
        int minutes = (int) (remainingSecondsAfterHours / 60);
        double remainingSecondsAfterMinutes = remainingSecondsAfterHours % 60;

        // Extract whole seconds and calculate milliseconds from the fractional part
        int seconds = (int) remainingSecondsAfterMinutes;
        // Convert fractional seconds to milliseconds (multiply by 1000 and round)
        int milliseconds = (int) Math.round((remainingSecondsAfterMinutes - seconds) * 1000);

        // Handle edge case where rounding pushes milliseconds to 1000
        // In this case, we need to increment seconds and cascade the carry
        if (milliseconds == 1000) {
            milliseconds = 0;
            seconds++;
            if (seconds == 60) {
                seconds = 0;
                minutes++;
                if (minutes == 60) {
                    minutes = 0;
                    hours++;
                }
            }
        }

        // Create formatter for milliseconds that ensures exactly 3 digits
        // Using US locale to guarantee decimal point instead of comma
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat decimalFormat = new DecimalFormat("000", symbols); // Formats to three digits, e.g., "005", "123"

        // Format final timestamp string with hours, minutes, seconds having leading zeros
        // and milliseconds formatted to exactly 3 digits
        return String.format(Locale.US, "%02d:%02d:%02d.%s",
                hours,
                minutes,
                seconds,
                decimalFormat.format(milliseconds));
    }

    /**
     * Builds the FFmpeg video filter string by combining various transformations and effects.
     *
     * @return A string containing the FFmpeg video filter chain
     */
    private String buildVideoFilter() {
        StringJoiner filterChain = new StringJoiner(",");
        filterChain.add("setpts=N/(" + outputFps + "*TB)");
        if (horizontalFlip) filterChain.add("hflip");
        if (verticalFlip) filterChain.add("vflip");

        if (lastCropRoi != null) {
            Rectangle bounds = lastCropRoi.getBounds();
            String cropString = "crop=" + bounds.width + ":" + bounds.height + ":" + bounds.x + ":" + bounds.y;
            log.info("cropString: " + cropString);
            filterChain.add(cropString);
        }

        switch (rotation) {
            case "90°":
            case "-270°":
                filterChain.add("transpose=1");
                break;
            case "270°":
            case "-90°":
                filterChain.add("transpose=2");
                break;
            case "180°":
            case "-180°":
                filterChain.add("transpose=2,transpose=2");
                break;
        }

//        if (grayScale) {
//            pixelFormat = AV_PIX_FMT_GRAY8;
//        }
//        if (pixelFormat > -1) {
//            String pixFmtName = org.bytedeco.ffmpeg.global.avutil.av_get_pix_fmt_name(pixelFormat).getString();
//            if (pixFmtName == null) {
//                pixFmtName = "bgr24"; //default
//            }
//            filterChain.add("format=" + pixFmtName);
//        }
//        filterChain.add("format=yuvj420p");
        return filterChain.toString();
    }

    /**
     * Maps quality settings to codec-specific parameters.
     *
     * @param codec   The video codec being used (libx264, libx265, or mjpeg)
     * @param quality Quality value from 1-10 where 1 is lowest and 10 is highest
     * @return The mapped quality parameter value for the specific codec
     */
    private static String mapQualityToCodec(String codec, int quality) {
        switch (codec) {
            case "libx264":
            case "libx265":
                // using crf
                return String.valueOf((int) Math.round(51.0 - (((Math.min(10.0, Math.max(1.0, quality)) - 1.0) / 9.0) * 51.0)));
            case "mjpeg":
                return String.valueOf(32 - (quality * 3));
            default:
                return ""; // this won't happen
        }
    }
}
