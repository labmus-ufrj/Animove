package labmus.zebrafish_utils.utils.functions;

import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

public class ThresholdBrightnessFunction implements Function<Mat, Mat> {
    private final double factor;

    public ThresholdBrightnessFunction(double factor) {
        this.factor = factor;
    }

    @Override
    public Mat apply(Mat mat) {
        return null;
    }
}
