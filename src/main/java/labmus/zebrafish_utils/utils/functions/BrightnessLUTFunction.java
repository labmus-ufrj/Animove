package labmus.zebrafish_utils.utils.functions;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import labmus.zebrafish_utils.ZFHelperMethods;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import java.awt.image.BufferedImage;
import java.util.function.Function;

import static labmus.zebrafish_utils.processing.HeatmapImages.defaultLut;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

public class BrightnessLUTFunction implements Function<Mat, Mat> {

    private final Java2DFrameConverter biConverter = new Java2DFrameConverter();
    private final Roi roi;
    private final String lut;
    private BufferedImage lastBi;

    public BrightnessLUTFunction(Roi roi, String lut) {
        this.roi = roi;
        this.lut = lut;
    }

    @Override
    public Mat apply(Mat sumMat) {
        Mat mat = new Mat(sumMat.rows(), sumMat.cols(), opencv_core.CV_16UC1);
        opencv_core.normalize(
                sumMat,
                mat,
                0,
                Math.pow(2, 16) - 1,
                opencv_core.NORM_MINMAX,
                opencv_core.CV_16UC1,
                null);
        sumMat.close();
        try (OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat()) {
            try (Frame frame = matConverter.convert(mat)) {
                ImagePlus imp = new ImagePlus("LUT", biConverter.convert(frame)); // todo: are you setting the statusBar?
                imp.setRoi(roi);
                ZFHelperMethods.autoAdjustBrightnessStack(imp, true); // todo: or are you?
                imp.deleteRoi();
                if (!lut.contains(defaultLut)) {
//                    yes, there 's a way to apply LUT using opencv_core.LUT();
//                    but there 's no clear path to convert imageJ LUT' s to a valid openCV LUT.
                    IJ.run(imp, this.lut, "");
                }
                ImagePlus impLUT = imp.flatten();

                this.lastBi = impLUT.getBufferedImage();

                // imp.getBufferedImage() returns a RGBA image, and openCV uses a BGR mat
                Mat matRGB = matConverter.convert(biConverter.getFrame(this.lastBi, 1.0, true));
                Mat matBGR = new Mat();

                if (matRGB.channels() == 4) {
                    cvtColor(matRGB, matBGR, COLOR_BGRA2BGR);
                } else {
                    matBGR = matRGB.clone();
                }
                matRGB.close();

                return matBGR;
            }
        }
    }

    public void close() {
        this.biConverter.close();
        this.lastBi.flush();
        this.lastBi = null;
    }

    public BufferedImage getLastBi() {
        return lastBi;
    }
}
