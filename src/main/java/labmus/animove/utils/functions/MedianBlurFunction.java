package labmus.animove.utils.functions;

import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

public class MedianBlurFunction implements Function<Mat, Mat> {

    private final int ksize;

    public MedianBlurFunction(int radius) {
        this.ksize = (radius * 2) + 1;;
    }
    @Override
    public Mat apply(Mat mat) {
        opencv_imgproc.medianBlur(mat, mat, ksize);
        return mat;
    }
}
