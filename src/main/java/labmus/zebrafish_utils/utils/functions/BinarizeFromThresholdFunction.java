package labmus.zebrafish_utils.utils.functions;

import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

public class BinarizeFromThresholdFunction implements Function<Mat, Mat> {
    @Override
    public Mat apply(Mat mat) {
//        -> iterar sobre frames
//                - binarizar o frame (normalizar entre 0 e 1 ou fazer um threshold)
//        - acumular o darkest
        return null;
    }
}
