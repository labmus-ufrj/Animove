package labmus.animove.tools;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.ZProjector;
import ij.plugin.Zoom;
import io.scif.services.DatasetIOService;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.SimpleRecorder;
import labmus.animove.utils.functions.ZprojectFunction;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.io.File;
import java.util.concurrent.Executors;

@Plugin(type = Command.class, menuPath = ZFConfigs.performanceTest)
public class PerformanceTest implements Command {

    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber and for OpenCV
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
        Executors.newSingleThreadExecutor().submit(OpenCVFrameConverter.ToMat::new);
    }

    // mjpeg codec gets unhappy with lower resolutions
    @Parameter(label = "Width", persist = false, min = "100")
    private int width = 100;

    @Parameter(label = "Height", persist = false, min = "100")
    private int height = 100;

    @Parameter
    LogService log;

    @Parameter
    UIService uiService;

    @Parameter
    DatasetIOService datasetIOService;

    @Parameter
    StatusService statusService;

    @Override
    public void run() {
        try {
            File tempOutputFile = File.createTempFile(ZFConfigs.pluginName + "_", ".avi");
            log.info("Temp file: " + tempOutputFile.getAbsolutePath());
            tempOutputFile.deleteOnExit();

            SimpleRecorder recorder = new SimpleRecorder(tempOutputFile, width, height, 30);
            recorder.start();
            int numFrames = width * height;
            for (int i = 1; i <= numFrames; i++) {
                recorder.recordMat(generateFrame(i));
                statusService.showProgress(i, numFrames);
            }
//            recorder.openResultinIJ(uiService, datasetIOService);
            recorder.close();

            /*
            The benchmark is performed the same way for both methods.
            The image opening time is the crucial part for imageJ. Images need to be on RAM to be used.
            Not the case for OpenCV.
             */

            uiService.showDialog("Test file created. Click OK to start the benchmark", ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);

            long pluginStartTime = System.currentTimeMillis(); // creating the converters is an overhead. preventable, that is, but still is.
            try (Java2DFrameConverter biConverter = new Java2DFrameConverter();
                 OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat()) {

                ZprojectFunction zprojectFunction = new ZprojectFunction(ZprojectFunction.OperationMode.MAX, false);
                ZFHelperMethods.iterateOverFrames(zprojectFunction, tempOutputFile, 1, 0, statusService);

                try (Mat maxFrame = zprojectFunction.getResultMat()) {
                    ImagePlus pluginImp = new ImagePlus(ZFConfigs.pluginName, biConverter.convert(converter.convert(maxFrame)));
                    uiService.show(pluginImp);
                    long pluginEndTime = System.currentTimeMillis();

                    long ijStartTime = System.currentTimeMillis();
                    ImagePlus imagePlus = IJ.openImage(tempOutputFile.getAbsolutePath());
                    ImagePlus ijImp = ZProjector.run(imagePlus, "max");
                    ijImp.setTitle("ImageJ");
                    uiService.show(ijImp);
                    long ijEndTime = System.currentTimeMillis();

                    Zoom.maximize(pluginImp);
                    Zoom.maximize(ijImp);
                    uiService.showDialog("Plugin: " + (pluginEndTime - pluginStartTime) + "ms\n" +
                            "ImageJ: " + (ijEndTime - ijStartTime) + "ms", ZFConfigs.pluginName, DialogPrompt.MessageType.INFORMATION_MESSAGE);
                }
            }
        } catch (Exception e) {
            log.error(e);
        }
    }

    /**
     * Generates an 8-bit grayscale Mat with a specified number of white pixels.
     * <p>
     * The pixels are set in row-major order (top-to-bottom, left-to-right),
     * starting from (row 0, col 0).
     *
     * @param amountOfWhitePixels The total number of white pixels to set in the frame.
     * @return A new Mat object with the specified white pixels.
     */
    private Mat generateFrame(int amountOfWhitePixels) {
        Mat frame = new Mat(this.height, this.width, opencv_core.CV_8UC1, Scalar.all(0));
        try (UByteIndexer indexer = frame.createIndexer()) {
            for (int i = 0; i < amountOfWhitePixels; i++) {

                // Convert the 1D index 'i' into a 2D (row, col) coordinate
                int row = i / this.width; // division gives the row index
                int col = i % this.width; // the remainder gives the column index

                indexer.put(row, col, 255);
            }
        }
        return frame;
    }
}