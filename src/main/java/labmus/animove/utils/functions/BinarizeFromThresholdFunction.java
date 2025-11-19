package labmus.animove.utils.functions;

import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;


public class BinarizeFromThresholdFunction implements Function<Mat, Mat> {
    private final boolean darkBg;

    public BinarizeFromThresholdFunction(boolean darkBg) {
        this.darkBg = darkBg;
    }

    @Override
    public Mat apply(Mat mat) {
        opencv_imgproc.threshold(mat, mat, 0, 255, (darkBg ? opencv_imgproc.THRESH_BINARY_INV : opencv_imgproc.THRESH_BINARY) | opencv_imgproc.THRESH_OTSU);
        return mat;
    }
}
