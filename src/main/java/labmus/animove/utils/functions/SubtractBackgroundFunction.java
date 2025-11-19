package labmus.animove.utils.functions;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.global.opencv_imgproc;

public class SubtractBackgroundFunction implements Function<Mat, Mat> {

    private final Mat topHatKernel;
//    private final Mat morphOpenKernel;

    public SubtractBackgroundFunction(int radius) {
        Size size = new Size(2 * radius, 2 * radius);
        this.topHatKernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, size);
//        this.morphOpenKernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(5, 5));
    }

    @Override
    public Mat apply(Mat mat) {
        opencv_imgproc.morphologyEx(mat, mat, opencv_imgproc.MORPH_TOPHAT, topHatKernel);
        // subtracting bg makes the image darker overall
        opencv_core.normalize(mat, mat, 0, 255, opencv_core.NORM_MINMAX, opencv_core.CV_8UC1, null);

//        opencv_core.bitwise_not(mat,mat);
//        opencv_imgproc.morphologyEx(mat, mat, opencv_imgproc.MORPH_OPEN, morphOpenKernel);

        return mat;
    }
}
