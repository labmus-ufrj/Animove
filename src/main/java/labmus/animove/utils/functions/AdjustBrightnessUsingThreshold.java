package labmus.animove.utils.functions;

import ij.process.AutoThresholder;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

public class AdjustBrightnessUsingThreshold implements Function<Mat, Mat> {

    private final double factor;
    private final Mat mask;

    public AdjustBrightnessUsingThreshold(double factor, Mat mask) {
        this.factor = factor;
        this.mask = mask;
    }

    @Override
    public Mat apply(Mat mat) {

        int threshold = getYenThreshold(mat);

        DoublePointer max = new DoublePointer(1);
        opencv_core.minMaxLoc(mat, null, max, null, null, mask);

        double minVal = threshold * this.factor;

        double alpha = 255.0 / (max.get() - minVal);
        double beta = -minVal * alpha;

        mat.convertTo(mat, opencv_core.CV_8UC1, alpha, beta);
//        opencv_core.normalize(mat, mat, 0, 255, opencv_core.NORM_MINMAX, opencv_core.CV_8UC1, mask);

        max.close();

        return mat;
    }

    /**
     * @param grayMat The input grayscale Mat (must be 8-bit, 1-channel: CV_8UC1).
     * @return The calculated threshold value as an integer.
     */
    private static int getYenThreshold(Mat grayMat) {
        int[] histSize = { 256 };
        int[] channels = { 0 };

        float[] histRange = { 0f, 256f };

        Mat histMat = new Mat();
        Mat cleanMat = new Mat();
        opencv_imgproc.calcHist(
                grayMat,    // MatVector of images
                1,         // int nimages (This is the explicit param you asked about)
                channels,  // int[] channels
                cleanMat,      // Mat mask
                histMat,   // Mat hist (output)
                1,         // int dims (This is the other explicit param)
                histSize,  // int[] histSize
                histRange  // FloatPointer ranges
        );

        // We must use an Indexer to safely access Mat data in Bytedeco
        // The histogram Mat is 256x1 and of type CV_32F (float)
        FloatIndexer histIndexer = histMat.createIndexer();
        int[] ijHistogram = new int[256];

        for (int i = 0; i < 256; i++) {
            ijHistogram[i] = (int) histIndexer.get(i);
        }

        // --- Step 3: Get Threshold from ImageJ ---
        AutoThresholder thresholder = new AutoThresholder();
        int threshold = thresholder.getThreshold(
                AutoThresholder.Method.Yen,
                ijHistogram
        );

        histIndexer.close();
        histMat.close();
        cleanMat.close();

        return threshold;
    }
}
