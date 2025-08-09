package zebrafish_utils;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
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
import org.scijava.app.StatusService;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
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

    @Parameter(label = "Arquivo de Entrada", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File inputFile;
    @Parameter(label = "Abrir um frame e completar dados", callback = "previewFrame")
    private Button previewButton;
    @Parameter(label = "Arquivo de Saída", style = FileWidget.SAVE_STYLE, persist = false, required = false)
    private File outputFile;
    @Parameter(label = "Codec de Saída", choices = {"libx264", "libx265", "mjpeg"}, callback = "updateOutputFileExtension", persist = false)
    private String outputCodec = "mjpeg";
    @Parameter(label = "Convert to grayscale", persist = false, required = false)
    private boolean grayScale = false;
    @Parameter(label = "Qualidade (1=Baixa, 10=Alta)", style = NumberWidget.SLIDER_STYLE, min = "1", max = "10", persist = false)
    private int quality = 10;
    @Parameter(label = "Frame Inicial", min = "0", persist = false)
    private int startFrame = 0;
    @Parameter(label = "Frame Final (0 para ir até o fim)", persist = false)
    private int endFrame = 0;
    @Parameter(label = "FPS de Saída", min = "1", persist = false)
    private double outputFps = 25.0;
    @Parameter(label = "Flip Horizontal", persist = false)
    private boolean horizontalFlip = false;
    @Parameter(label = "Flip Vertical", persist = false)
    private boolean verticalFlip = false;
    @Parameter(label = "Rotação", choices = {"0°", "90°", "180°", "270°", "-90°", "-180°", "-270°"}, persist = false)
    private String rotation = "0°";
    @Parameter(label = "Remover Áudio", persist = false)
    private boolean removeAudio = true;
    @Parameter(label = "Cortar usando a ROI ativa", persist = false)
    private boolean useRoiCrop = false;
    @Parameter(label = "Multi-Crop (um vídeo por ROI)", persist = false)
    private boolean multiCrop = false;

    @Parameter(label = "Posview", callback = "showPosview")
    private Button posviewButton;

    @Parameter(label = "Process", callback = "runProcessing")
    private Button fastRunButton;


    private ImagePlus previewImagePlus;
    private ImagePlus posviewImagePlus;

    @Override
    public void run() {
//        FFmpegLogCallback.set();
        if (!ZFConfigs.checkJavaCV()) {
            return; // if the user chooses to ignore this he might as well see some errors
        }
//        ij.IJ.run("Console");
    }

    public void runProcessing() {
        startProcessingThread(false);
    }

    public void showPosview() {
        if (multiCrop) {
            uiService.showDialog("Can't create posview in Multi-Crop mode.",
                    "Error on Multi-Crop", DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        startProcessingThread(true);
    }

    public void startProcessingThread(boolean isPosview) {

        if (inputFile == null || outputFile == null) {
            uiService.showDialog("Select an input file and an output file.",
                    "Missing files", DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        } else if (outputFile.exists()) {
            uiService.showDialog("Output file already exists. Please delete it or choose a different name.",
                    "Overwriting", DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }
        new Thread(() -> executeProcessing(isPosview)).start();
    }

    private void executeProcessing(boolean isPosview) {
        try {
            RoiManager roiManager = RoiManager.getInstance();
            if (multiCrop) {
                if (roiManager == null || roiManager.getCount() == 0) {
                    uiService.showDialog("O modo Multi-Crop requer ROIs no ROI Manager.",
                            "Erro no Multi-Crop", DialogPrompt.MessageType.ERROR_MESSAGE);
                    return;
                }
                File outputDirectory = outputFile.getParentFile();
                String baseName = outputFile.getName().replaceFirst("[.][^.]+$", "");

                log.info("Starting Multi-Crop processing for " + roiManager.getCount() + " ROIs...");

                int roiIndex = 0;
                for (Roi roi : roiManager.getRoisAsArray()) {
                    statusService.showStatus(roiIndex++, roiManager.getCount(), "Processando Multi-Crop: " + roi.getName());
                    String outputName = baseName + "_" + roi.getName() + (outputCodec.equals("mjpeg") ? ".avi" : ".mp4");
                    File finalOutputFile = new File(outputDirectory, outputName);
                    processVideo(finalOutputFile, roi, isPosview, false); // isPosview is always false here but idc
                }
            } else {
                Roi singleRoi = null;
                if (useRoiCrop) {
                    if (previewImagePlus != null) {
                        singleRoi = previewImagePlus.getRoi();
                    } else {
                        RoiManager rm = RoiManager.getInstance();
                        singleRoi = (rm != null && rm.getSelectedIndex() != -1) ? rm.getRoi(rm.getSelectedIndex()) : null;
                    }
                    if (singleRoi == null) {
                        uiService.showDialog("A opção 'Cortar usando a ROI ativa' foi marcada, mas nenhuma ROI está selecionada.",
                                "Erro de ROI", DialogPrompt.MessageType.ERROR_MESSAGE);
                        return;
                    }
                    log.info("Using active ROI: " + singleRoi.getName());
                }
                processVideo(outputFile, singleRoi, isPosview, false);
            }
            statusService.clearStatus();
            if (!isPosview) {
                uiService.showDialog("Conversão de vídeo finalizada com sucesso!",
                        "Processo Concluído", DialogPrompt.MessageType.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            log.error("A fatal error occurred during processing.", e);
            uiService.showDialog("Ocorreu um erro: " + e.getMessage(),
                    "Erro Inesperado", DialogPrompt.MessageType.ERROR_MESSAGE);
        }
    }

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
            testFile = new File(parentDir, baseName + "_processed_" + count + suggestedExtension);
            count++;
        }
        outputFile = testFile;
    }

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
            log.warn("Could not open a preview of the file (it may not be a valid video): " + inputFile.getName());
            log.error(e);
            uiService.showDialog("Não foi possível abrir um preview do arquivo (pode não ser um vídeo válido): " + inputFile.getName(),
                    "Erro", DialogPrompt.MessageType.ERROR_MESSAGE);
            previewImagePlus = null;
        }

        if (previewImagePlus != null && previewImagePlus.getWindow() != null) {
            previewImagePlus.close();
        }
    }

    protected void updateOutputFileExtension() {
        if (outputFile == null) return;
        String baseName = outputFile.getName().replaceFirst("[.][^.]+$", "");
        String newExtension = outputCodec.equals("mjpeg") ? ".avi" : ".mp4";
        outputFile = new File(outputFile.getParent(), baseName + newExtension);
    }

    private void processVideo(File currentOutputFile, Roi cropRoi, boolean isPosview, boolean isPreview) throws Exception {

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

        if (isPosview || isPreview) {
            totalFramesToProcess = 1;
            currentOutputFile = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis() + ".png");
            currentOutputFile.deleteOnExit();
        }

        String ffmpeg = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add(ffmpeg);

        commandList.add("-ss");
        commandList.add(convertFrameToTimestamp((float) fps, startFrame));
        if (removeAudio) {
            commandList.add("-an");
        }
        commandList.add("-i");
        commandList.add("\"" + inputFile.getAbsolutePath() + "\"");

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

        commandList.add("-vframes");
        commandList.add(String.valueOf(totalFramesToProcess));

        if (!isPreview) {
            commandList.add("-vf");
            commandList.add("\"" + buildVideoFilter(cropRoi, AV_PIX_FMT_BGR24, exifRotation) + "\"");
        }

        commandList.add("\"" + currentOutputFile.getAbsolutePath() + "\"");

        log.info(commandList.toString());

        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);

        log.info("Starting processing for: " + currentOutputFile.getName());

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {

            // Compile the regex pattern once for efficiency
            String regex = "frame=\\s*(\\d+)";
            Pattern pattern = Pattern.compile(regex);

            Matcher matcher = pattern.matcher(line);

            // Check if the pattern was found in the line
            if (matcher.find()) {
                // group(1) returns the first captured group (the digits)
                statusService.showStatus(Integer.parseInt(matcher.group(1)), totalFramesToProcess, "Processing " + currentOutputFile.getName());
            }

            log.info(line);
        }
        reader.close();
        process.waitFor(); // dn if this is necessary

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

    private String convertFrameToTimestamp(float fps, int initialFrame) {
        // Calculate the total number of seconds from the beginning of the video to the initial frame.
        // We use double for precision in calculations involving time.
        double totalSeconds = initialFrame / (double) fps;

        // Calculate hours
        int hours = (int) (totalSeconds / 3600);
        double remainingSecondsAfterHours = totalSeconds % 3600;

        // Calculate minutes
        int minutes = (int) (remainingSecondsAfterHours / 60);
        double remainingSecondsAfterMinutes = remainingSecondsAfterHours % 60;

        // Calculate seconds and milliseconds
        int seconds = (int) remainingSecondsAfterMinutes;
        // Calculate milliseconds, rounding to 3 decimal places for consistency
        // Multiply by 1000 to get total milliseconds, then take the remainder for the fractional part.
        int milliseconds = (int) Math.round((remainingSecondsAfterMinutes - seconds) * 1000);

        // Handle cases where rounding might push milliseconds to 1000, e.g., 0.9999 seconds becoming 1.000 seconds
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

        // Use DecimalFormat for formatting milliseconds to ensure exactly 3 digits,
        // and using Locale.US to ensure a dot as the decimal separator for consistency with FFmpeg.
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat decimalFormat = new DecimalFormat("000", symbols); // Formats to three digits, e.g., "005", "123"

        // Format the output string using String.format for HH:MM:SS part and DecimalFormat for milliseconds.
        // %02d ensures two digits with leading zeros (e.g., 5 becomes 05).
        return String.format(Locale.US, "%02d:%02d:%02d.%s",
                hours,
                minutes,
                seconds,
                decimalFormat.format(milliseconds));
    }

    private void createPosView(Roi cropRoi) throws FrameGrabber.Exception, FrameFilter.Exception {

        // save a single image (first from processing)
        // open that image in posviewimageplus

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