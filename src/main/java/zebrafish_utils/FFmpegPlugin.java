package zebrafish_utils;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.*;
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

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.ffmpeg.global.avutil.*;

// Using Interactive to achieve the non-modal behaviour
@Plugin(type = Command.class, menuPath = ZFConfigs.ffmpegPath)
public class FFmpegPlugin implements Command, Interactive {

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;
    @Parameter
    private StatusService statusService;

    @Parameter(label = "Input File", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File inputFile;
    @Parameter(label = "Preview Frame & Get Info", callback = "previewFrame")
    private Button previewButton;
    @Parameter(label = "Output File", style = FileWidget.SAVE_STYLE, persist = false, required = false)
    private File outputFile;
    @Parameter(label = "Output Codec", choices = {"libx264", "libx265", "mjpeg"}, callback = "updateOutputFileExtension", persist = false)
    private String outputCodec = "mjpeg";
    @Parameter(label = "Convert to grayscale", persist = false, required = false)
    private boolean grayScale = false;
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
    @Parameter(label = "Remove Audio", persist = false)
    private boolean removeAudio = true;
    @Parameter(label = "Crop using active ROI", persist = false)
    private boolean useRoiCrop = false;
    @Parameter(label = "Multi-Crop (one video per ROI)", persist = false)
    private boolean multiCrop = false;

    @Parameter(label = "Posview", callback = "showPosview")
    private Button posviewButton;

    @Parameter(label = "Process", callback = "runProcessing")
    private Button fastRunButton;


    private ImagePlus previewImagePlus;
    private ImagePlus posviewImagePlus;

    @Override
    public void run() {
        if (!ZFConfigs.checkJavaCV()) {
            return; // if the user chooses to ignore this nothing will work anyway
        }
/*
//          Debugging related stuff
          FFmpegLogCallback.set();
          ij.IJ.run("Console");
*/
    }

    /**
     * Callback method for the "Process" button.
     * Initiates video processing with no posview generation.
     */
    public void runProcessing() {
        startProcessingThread(false);
    }

    /**
     * Callback method for the "Posview" button.
     * Initiates video processing with posview generation.
     */
    public void showPosview() {
        startProcessingThread(true);
    }

    /**
     * Callback method to update the output filename when an input file changes.
     * Generates a unique output filename by appending "_processed" and the appropriate extension.
     */
    protected void updateOutputName() {
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

    /**
     * Callback method for the "Preview" button.
     * Grabs a single frame from video to show a preview and get video data.
     */
    private void previewFrame() {
        try {
            if (!inputFile.exists()) {
                throw new Exception("Input file does not exist.");
            }

            processVideo(null, null, false, true);

            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
                grabber.start();
                double inputFps = grabber.getFrameRate();
                if (inputFps > 0) {
                    this.outputFps = inputFps;
                }
                if (endFrame < 1) {
                    endFrame = grabber.getLengthInFrames();
                }
            }

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("Could not open a preview of the file (it may not be a valid video): " + inputFile.getName(),
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            previewImagePlus = null;
        }

        if (previewImagePlus != null && previewImagePlus.getWindow() != null) {
            previewImagePlus.close();
        }
    }

    /**
     * Callback method to update output file extension when codec changes.
     * Updates extension to .avi for mjpeg codec or .mp4 for other codecs.
     */
    protected void updateOutputFileExtension() {
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
    public void startProcessingThread(boolean isPosview) {
        if (!isInputsValid(isPosview)) return;

        // We can't do this in the same thread as the UI and ImageJ. Everything will freeze.
        new Thread(() -> executeProcessing(isPosview)).start();
    }

    /**
     * Validates the input parameters before video processing.
     * Checks if:
     * - Input and output files are specified
     * - Output file doesn't already exist
     * - Posview mode is not used with Multi-Crop
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
            uiService.showDialog("Can't create posview in Multi-Crop mode.",
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
            // Get ROI manager instance
            RoiManager roiManager = RoiManager.getInstance();

            // Handle Multi-Crop mode
            if (multiCrop) {
                // Validate ROI manager has ROIs
                if (roiManager == null || roiManager.getCount() == 0) {
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
                    processVideo(finalOutputFile, roi, isPosview, false); // isPosview is always false here but idc
                }
            }
            // Handle single ROI mode
            else {
                Roi singleRoi = null;
                // Get active ROI if crop option enabled
                if (useRoiCrop) {
                    if (previewImagePlus != null) {
                        singleRoi = previewImagePlus.getRoi();
                    } else {
                        RoiManager rm = RoiManager.getInstance();
                        singleRoi = (rm != null && rm.getSelectedIndex() != -1) ? rm.getRoi(rm.getSelectedIndex()) : null;
                    }

                    // Validate ROI exists
                    if (singleRoi == null) {
                        uiService.showDialog("The 'Crop using active ROI' option was checked, but no ROI is selected.",
                                "ROI Error", DialogPrompt.MessageType.ERROR_MESSAGE);
                        return;
                    }
                    log.info("Using active ROI: " + singleRoi.getName());
                }

                // Process video with single ROI
                processVideo(outputFile, singleRoi, isPosview, false);
            }

            // Clear status and show completion message
            statusService.clearStatus();
            if (!isPosview) {
                uiService.showDialog("Video conversion finished successfully!",
                        "Process Complete", DialogPrompt.MessageType.INFORMATION_MESSAGE);
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
     * 2. Preview mode: Creates a single frame preview image
     * 3. Posview mode: Creates a single frame with all filters applied for preview
     *
     * @param currentOutputFile The output file where processed video/image will be saved
     * @param cropRoi           Region of interest for cropping the video (can be null)
     * @param isPosview         If true, generates a single frame posview image
     * @param isPreview         If true, generates a single frame preview image
     * @throws Exception If any error occurs during processing
     */
    private void processVideo(File currentOutputFile, Roi cropRoi, boolean isPosview, boolean isPreview) throws Exception {
        // Get video properties from input file
        double fps;
        int totalFramesToProcess;
        double exifRotation;
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
            grabber.start();
            fps = grabber.getFrameRate();
            exifRotation = grabber.getDisplayRotation();
            int finalFrame = (endFrame <= 0 || endFrame > grabber.getLengthInFrames()) ? grabber.getLengthInFrames() : endFrame;
            totalFramesToProcess = Math.max(0, finalFrame - startFrame);
        }

        // For preview/posview modes, set output to temporary file and process single frame
        if (isPosview || isPreview) {
            totalFramesToProcess = 1;
            currentOutputFile = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis() + ".png");
            currentOutputFile.deleteOnExit();
        }

        // Build FFmpeg command
        // Start by getting the binary path from bytedeco's lib
        // yep that's a thing, and that's how this works
        String ffmpeg = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add(ffmpeg);

        // Add input file and start time
        commandList.add("-ss");
        commandList.add(convertFrameToTimestamp((float) fps, startFrame));
        if (removeAudio) {
            commandList.add("-an");
        }
        commandList.add("-i");
        commandList.add("\"" + inputFile.getAbsolutePath() + "\"");

        // Set codec and quality for normal video processing
        if (!isPosview && !isPreview) {
            commandList.add("-vcodec");
            commandList.add(outputCodec);

            if (outputCodec.equals("mjpeg")) {
                commandList.add("-q");
            } else {
                commandList.add("-crf");
            }
            commandList.add(mapQualityToCodec(outputCodec, quality));
        }

        // Set number of frames to process
        commandList.add("-vframes");
        commandList.add(String.valueOf(totalFramesToProcess));

        // Add filters if not preview mode
        if (!isPreview) {
            commandList.add("-vf");
            commandList.add("\"" + buildVideoFilter(cropRoi, AV_PIX_FMT_BGR24, exifRotation) + "\"");
        }

        // Add output file
        commandList.add("\"" + currentOutputFile.getAbsolutePath() + "\"");

        log.info(commandList.toString());

        // Execute FFmpeg command
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);

        log.info("Starting processing for: " + currentOutputFile.getName());

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

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
        reader.close();
        process.waitFor();

        // Show preview/posview if needed
        if (isPosview) {
            if (posviewImagePlus != null && posviewImagePlus.getWindow() != null) {
                posviewImagePlus.close();
            }
            posviewImagePlus = new ImagePlus(currentOutputFile.getAbsolutePath());
            posviewImagePlus.setTitle("Posview Image");
            uiService.show(posviewImagePlus);
        } else if (isPreview) {
            if (previewImagePlus != null && previewImagePlus.getWindow() != null) {
                previewImagePlus.close();
            }
            previewImagePlus = new ImagePlus(currentOutputFile.getAbsolutePath());
            previewImagePlus.setTitle("Preview Image");
            uiService.show(previewImagePlus);
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
     * Creates a preview image (posview) from a single frame of the video with applied filters and transformations.
     * This method:
     * 1. Opens the input video file at the specified start frame
     * 2. Applies video filters (crop, rotation, flips, etc.) to the frame
     * 3. Converts the processed frame to a BufferedImage
     * 4. Displays the resulting image
     *
     * @param cropRoi The region of interest to crop the frame (can be null for no cropping)
     * @throws FrameGrabber.Exception If there's an error grabbing frames from the video
     * @throws FrameFilter.Exception  If there's an error applying filters to the frame
     */
    private void createPosView(Roi cropRoi) throws FrameGrabber.Exception, FrameFilter.Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
            grabber.setFrameNumber(startFrame);
            grabber.start();

            String videoFilter = buildVideoFilter(cropRoi, AV_PIX_FMT_ABGR, grabber.getDisplayRotation());

            try (FFmpegFrameFilter filter = new FFmpegFrameFilter(videoFilter, grabber.getImageWidth(), grabber.getImageHeight())) {
                filter.start();
                Frame frame = grabber.grabImage();
                filter.push(frame);
                Frame filteredFrame = filter.pullImage();

                if (posviewImagePlus != null && posviewImagePlus.getWindow() != null) {
                    posviewImagePlus.close();
                }

                Java2DFrameConverter converter = new Java2DFrameConverter();
                BufferedImage bufferedImage = converter.convert(filteredFrame);

                if (bufferedImage != null) {
                    posviewImagePlus = new ImagePlus("Posview - " + outputFile.getName(), bufferedImage);
                    uiService.show(posviewImagePlus);
                } else {
                    log.error("Something went wrong.");
                }

                converter.close();
            }
        }
    }

    /**
     * Builds the FFmpeg video filter string by combining various transformations and effects.
     *
     * @param roi          Region of interest to crop the video (can be null for no cropping)
     * @param pixelFormat  The pixel format to use (e.g. AV_PIX_FMT_BGR24)
     * @param exifRotation EXIF rotation value from the input video
     * @return A string containing the FFmpeg video filter chain
     */
    private String buildVideoFilter(Roi roi, int pixelFormat, double exifRotation) {
        StringJoiner filterChain = new StringJoiner(",");
        filterChain.add("setpts=N/(" + outputFps + "*TB)");
        if (horizontalFlip) filterChain.add("hflip");
        if (verticalFlip) filterChain.add("vflip");

        if (roi != null) {
            java.awt.Rectangle bounds = roi.getBounds();
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

        if (grayScale) {
            pixelFormat = AV_PIX_FMT_GRAY8;
        }
        String pixFmtName = org.bytedeco.ffmpeg.global.avutil.av_get_pix_fmt_name(pixelFormat).getString();
        if (pixFmtName == null) {
            pixFmtName = "bgr24"; //default
        }
        filterChain.add("format=" + pixFmtName);
        return filterChain.toString();
    }

    /**
     * Maps quality settings to codec-specific parameters.
     *
     * @param codec   The video codec being used (libx264, libx265, or mjpeg)
     * @param quality Quality value from 1-10 where 1 is lowest and 10 is highest
     * @return The mapped quality parameter value for the specific codec
     */
    public static String mapQualityToCodec(String codec, int quality) {
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
