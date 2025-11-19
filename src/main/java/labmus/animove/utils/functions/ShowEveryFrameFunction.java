package labmus.animove.utils.functions;

import ij.ImagePlus;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

public class ShowEveryFrameFunction implements Function<Mat, Mat> {

    private final Java2DFrameConverter biConverter = new Java2DFrameConverter();

    @Override
    public Mat apply(Mat mat) {
        try (OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat()) {
            try (Frame frame = matConverter.convert(mat)) {
                new ImagePlus("Mat", biConverter.convert(frame)).show();
            }
        }
        return mat;
    }
}
