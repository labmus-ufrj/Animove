package labmus.animove.utils.functions;

import org.bytedeco.opencv.global.opencv_core;
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
        opencv_core.normalize(mat, mat, 0, 255, opencv_core.NORM_MINMAX, opencv_core.CV_8UC1, null);
        return mat;
    }
}
