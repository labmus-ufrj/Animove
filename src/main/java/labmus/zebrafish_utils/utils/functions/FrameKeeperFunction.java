package labmus.zebrafish_utils.utils.functions;

import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

public class FrameKeeperFunction implements Function<Mat, Mat> {
    private Mat keptMat;
// todo: hmmmmmmmmmmmmmmmmmmmmmm is this it?
    @Override
    public Mat apply(Mat mat) {
        Mat newMat = new Mat(mat.rows(), mat.cols(), mat.type());
        mat.copyTo(newMat); // as clone() seems to be leaking
        this.keptMat = newMat;
        return null;
    }

    public Mat getKeptMat() {
        return keptMat;
    }
}
