package labmus.zebrafish_utils.utils;

import labmus.zebrafish_utils.tools.ImageCalculator;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

import static org.bytedeco.opencv.global.opencv_core.add;
import static org.bytedeco.opencv.global.opencv_core.subtract;

public class ImageCalculatorFunction implements Function<Mat, Mat> {

    private final ImageCalculator.OperationMode mode;
    private final Mat inputImage;

    public ImageCalculatorFunction(ImageCalculator.OperationMode mode, Mat inputImage) {
        this.mode = mode;
        this.inputImage = inputImage;
    }

    @Override
    public Mat apply(Mat grayMatFrame) {
        Mat resultFrame = new Mat();
        switch (mode) {
            case ADD:
                add(grayMatFrame, inputImage, resultFrame, null, opencv_core.CV_8UC1);
                break;
            case SUBTRACT:
                subtract(grayMatFrame, inputImage, resultFrame, null, opencv_core.CV_8UC1);
                break;
            default:
                break; // this is not happening lol
        }
        return resultFrame;
    }
}
