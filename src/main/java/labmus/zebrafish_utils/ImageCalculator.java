package labmus.zebrafish_utils;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.app.StatusService;

import java.io.File;

import static org.bytedeco.opencv.global.opencv_core.add;
import static org.bytedeco.opencv.global.opencv_core.subtract;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

@Plugin(type = Command.class, menuPath = ZFConfigs.imgCalcPath)
public class ImageCalculator implements Command {

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

    @Parameter(label = "Output Video", style = "save", persist = false)
    private File outputFile;

    @Parameter(label = "Operation", choices = {"Add", "Subtract"}, persist = false)
    private String operation = "Add";

    @Parameter(label = "Start Frame", min = "0", persist = false)
    private int startFrame = 0;

    @Parameter(label = "End Frame (0 for entire video)", min = "0", persist = false)
    private int endFrame = 0;

    @Override
    public void run() {

        if (!ZFConfigs.checkJavaCV()){
            return;
        }

        ij.IJ.run("Console");

        if (!ensureFilesAreSelected()) {
            return;
        }

        statusService.showStatus("Starting video processing...");

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
             OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat()) {

            grabber.start();

            int totalFrames = grabber.getLengthInFrames() - 1; // frame numbers are 0-indexed

            int actualStartFrame = Math.max(0, startFrame);
            int actualEndFrame = (endFrame <= 0 || endFrame > totalFrames) ? totalFrames : endFrame;
            if (actualStartFrame >= actualEndFrame) {
                throw new Exception("Initial frame must be before end frame.");
            }
            int framesToProcess = actualEndFrame - actualStartFrame;
            statusService.showStatus("Processing " + framesToProcess + " frames from " + videoFile.getName());

            grabber.setFrameNumber(actualStartFrame);

            Mat image = imread(imageFile.getAbsolutePath());
            if (image.empty()) {
                throw new Exception("Failed to load the selected image.");
            }

            Mat grayImage = new Mat();
            cvtColor(image, grayImage, COLOR_BGR2GRAY);

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFile, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels())) {
                recorder.setFrameRate(grabber.getFrameRate());
                recorder.setVideoCodec(grabber.getVideoCodec());
                recorder.start();

                for (int i = 0; i < framesToProcess; i++) {
                    Frame frame = grabber.grab();
                    if (frame == null) {
//                        break;
                        log.info("you still gotta fix thisssss");
                    }

                    Mat matFrame = converter.convert(frame);
                    Mat grayMatFrame = new Mat();
                    cvtColor(matFrame, grayMatFrame, COLOR_BGR2GRAY);

                    Mat resultFrame = new Mat();
                    switch (operation) {
                        case "Add":
                            add(grayMatFrame, grayImage, resultFrame);
                            break;
                        case "Subtract":
                            subtract(grayMatFrame, grayImage, resultFrame);
                            break;
                        default:
                            break; // this is not happening lol
                    }

                    recorder.record(converter.convert(resultFrame));

                    statusService.showProgress(i + 1, framesToProcess);
                }
            }

            uiService.showDialog("Video processing is complete!", "Success");

        } catch (Exception e) {
            log.error("An error occurred during processing", e);
            uiService.showDialog("An error occurred: " + e.getMessage(), "Processing Error", DialogPrompt.MessageType.ERROR_MESSAGE);
        } finally {
            statusService.clearStatus();
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
        String originalExtension = videoFile.getName().substring(videoFile.getName().lastIndexOf("."));
        String parentDir = videoFile.getParent();
        String baseName = videoFile.getName().replaceFirst("[.][^.]+$", "");
        File testFile = new File(parentDir, baseName + "_processed" + originalExtension);

        int count = 2;
        while (testFile.exists()) {
            testFile = new File(parentDir, baseName + "_processed" + count + originalExtension);
            count++;
        }
        outputFile = testFile;
    }
}