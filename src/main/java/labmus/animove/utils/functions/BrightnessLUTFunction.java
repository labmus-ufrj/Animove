package labmus.animove.utils.functions;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import labmus.animove.ZFHelperMethods;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import java.awt.image.BufferedImage;
import java.util.function.Function;

import static labmus.animove.processing.heatmaps.HeatmapSumImages.defaultLut;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

public class BrightnessLUTFunction implements Function<Mat, Mat> {

    /**
     * Number of histogram bins used to locate the tolerance percentile.
     * The 16 bit output range is represented exactly, so no precision is lost.
     */
    private static final int HISTOGRAM_BINS = 1 << 16;

    /**
     * Top of the working range, i.e. 65535 for 16 bit data.
     */
    private static final double RANGE_MAX = Math.pow(2, 16) - 1;

    private final Roi roi;
    private final String lut;

    /**
     * Percentage of the ROI pixels allowed to sit above the brightness upper bound.
     * The bound becomes the {@code (100 - tolerancePercent)}-th percentile of the ROI, so a few
     * bright outliers stop defining the whole stretch. {@code 0} reproduces the plain ROI max.
     */
    private final double tolerancePercent;

    private BufferedImage lastBi;

    public BrightnessLUTFunction(Roi roi, String lut) {
        this(roi, lut, 0);
    }

    public BrightnessLUTFunction(Roi roi, String lut, double tolerancePercent) {
        this.roi = roi;
        this.lut = lut;
        this.tolerancePercent = tolerancePercent;
    }

    @Override
    public Mat apply(Mat sumMat) {
        // Pinned to CV_32FC1 so the indexer used to read the pixels is known statically: the
        // concrete Indexer type is derived from the Mat depth at runtime, and guessing it wrong
        // fails with a ClassCastException rather than at compile time.
        Mat floatMat = new Mat();
        sumMat.convertTo(floatMat, opencv_core.CV_32FC1);

        Mat mat = new Mat(floatMat.rows(), floatMat.cols(), opencv_core.CV_16UC1);
        try {
            // The stretch is resolved once per frame (or once per image, when a single frame is
            // processed) and applied in a single pass, so the whole ROI gets exactly one stretch.
            double[] bounds = resolveStretchBounds(floatMat);
            double min = bounds[0];
            double upper = bounds[1];

            // The stretch is linear, so it is one convertTo: dst = src * alpha + beta, with
            // saturation outside [min, upper]. cv::LUT cannot be used here, it only supports
            // 8 bit sources and a 256 entry table.
            double alpha = RANGE_MAX / (upper - min);
            double beta = -min * alpha;
            floatMat.convertTo(mat, opencv_core.CV_16UC1, alpha, beta);
        } catch (RuntimeException | Error e) {
            floatMat.close();
            mat.close();
            throw e;
        }
        floatMat.close();
        sumMat.close();

        try (OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
             Java2DFrameConverter biConverter = new Java2DFrameConverter();
             Frame frame = matConverter.convert(mat)) {

            ImagePlus imp = new ImagePlus("LUT", biConverter.convert(frame));
            imp.setRoi(roi);
            // The mat already holds the final stretched values, so nothing has to be recomputed
            // here: the stretch would be an identity mapping, and the ROI min/max is deliberately
            // not used anymore. Only the display range has to be pinned to the full 16 bit range,
            // because flatten() below renders through it.
            //
            // autoAdjustBrightnessStack() is NOT used on purpose: with useROI=false it runs
            // "Select None", which deletes the ROI, and with useROI=true it would redo the stretch
            // from the ROI min/max, reintroducing the bright outlier problem this class exists to
            // solve. It would also scan the whole image a second time for statistics we ignore.
            imp.setDisplayRange(0, RANGE_MAX);
            imp.updateAndDraw();
            imp.deleteRoi();
            if (!lut.contains(defaultLut)) {
//                    the stretch itself is a linear convertTo now, but the ImageJ LUT still has to be
//                    applied through ImageJ: there 's no clear path to convert imageJ LUT' s to a
//                    valid openCV LUT, and cv::LUT only handles 8 bit sources anyway.
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

            if (!lut.contains(defaultLut)) {
                return matBGR;
            } else {
                Mat matGrey = new Mat();
                cvtColor(matBGR, matGrey, COLOR_BGR2GRAY);
                matBGR.close();
                return matGrey;
            }

        }
    }

    /**
     * Resolves the stretch bounds of the ROI, as {@code [min, upper]}.
     * <p>
     * {@code min} is the actual ROI minimum; {@code upper} is the {@code (100 - tolerancePercent)}
     * percentile of the ROI pixels, so bright outliers stop defining the top of the range.
     * Pixels at or below {@code min} become black and pixels at or above {@code upper} saturate,
     * which is the same behavior as the previous ROI min/max stretch.
     * <p>
     * The returned bounds always satisfy {@code upper > min}, so the caller can divide by the span.
     */
    private double[] resolveStretchBounds(Mat sumMat) {
        if (sumMat == null || sumMat.empty()) {
            return new double[]{0, 1};
        }

        double tolerance = Math.max(0, Math.min(100, tolerancePercent));

        try (Mat mask = getRoiMask(sumMat);
             DoublePointer minPtr = new DoublePointer(1);
             DoublePointer maxPtr = new DoublePointer(1)) {

            opencv_core.minMaxLoc(sumMat, minPtr, maxPtr, null, null, mask);
            double min = minPtr.get();
            double upper = maxPtr.get();

            if (Double.isNaN(min) && Double.isNaN(upper)) {
                return new double[]{0, 1};
            }
            if (Double.isNaN(min)) {
                min = upper;
            }
            if (Double.isNaN(upper)) {
                upper = min;
            }

            if (tolerance > 0) {
                upper = percentileUpperBound(sumMat, mask, min, upper, tolerance);
            }

            if (upper <= min) {
                // Degenerate ROI (a flat region): the previous implementation saturated everything
                // here too, so put the whole range at the top instead of dividing by zero.
                return new double[]{min, min + 1};
            }
            return new double[]{min, upper};
        }
    }

    /**
     * Returns the smallest value that still covers {@code 100 - tolerancePercent} percent of the
     * ROI pixels, i.e. the {@code (100 - tolerancePercent)}-th percentile.
     * <p>
     * Uses a histogram, so the cost is a single pass over the ROI with no sorting and no boxing.
     * Pixels above the returned value saturate, exactly like pixels at the old ROI max did.
     */
    private double percentileUpperBound(Mat sumMat, Mat mask, double min, double max, double tolerancePercent) {
        double span = max - min;
        if (!(span > 0)) {
            return max;
        }

        long[] histogram = new long[HISTOGRAM_BINS];
        double binWidth = span / HISTOGRAM_BINS;
        // Indexer extends AutoCloseable, so both views are released with the block.
        // The mask comes from getMaskMatFromRoi() as CV_8UC1, so its indexer is a UByteIndexer:
        // createIndexer() derives the concrete type from Mat.depth(), it does not cast to what the
        // assignment target asks for.
        try (UByteIndexer maskPixels = mask == null ? null : mask.createIndexer(false);
             FloatIndexer pixels = sumMat.createIndexer(false)) {
            long rows = sumMat.rows();
            long cols = sumMat.cols();

            for (long row = 0; row < rows; row++) {
                for (long col = 0; col < cols; col++) {
                    if (maskPixels != null && maskPixels.get(row, col) == 0) {
                        continue; // outside the ROI
                    }
                    double value = pixels.get(row, col);
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        continue; // minMaxLoc skips these too
                    }
                    int bin = (int) ((value - min) / binWidth);
                    if (bin < 0) {
                        bin = 0;
                    } else if (bin >= HISTOGRAM_BINS) {
                        bin = HISTOGRAM_BINS - 1;
                    }
                    histogram[bin]++;
                }
            }
        }

        long total = 0;
        for (long count : histogram) {
            total += count;
        }
        if (total == 0) {
            return max;
        }

        // "Tolerated" pixels are the brightest ones, so they are dropped from the top.
        long keep = total - (long) Math.ceil(total * (tolerancePercent / 100.0));
        if (keep <= 0) {
            return min; // tolerance of 100%: everything saturates
        }

        long cumulative = 0;
        for (int bin = 0; bin < HISTOGRAM_BINS; bin++) {
            cumulative += histogram[bin];
            if (cumulative >= keep) {
                return min + (bin + 1) * binWidth; // right edge of the bin holding the percentile
            }
        }
        return max;
    }

    /**
     * ROI mask as a CV_8UC1 mat. Returns {@code null} when there is no usable ROI, which makes
     * OpenCV use the whole image.
     * <p>
     * The caller owns the returned mat and must close it. It is not cached: this runs once per
     * processed image, which is negligible next to reading and projecting the video frames.
     */
    private Mat getRoiMask(Mat sumMat) {
        if (roi == null) {
            return null;
        }
        Mat built = ZFHelperMethods.getMaskMatFromRoi(sumMat.cols(), sumMat.rows(), roi);
        if (built == null || built.empty()) {
            if (built != null) {
                built.close();
            }
            IJ.log("BrightnessLUTFunction: the ROI produced an empty mask, using the whole image instead.");
            return null;
        }
        return built;
    }

    public void close() {
        if (this.lastBi != null) {
            this.lastBi.flush();
            this.lastBi = null;
        }
    }

    public BufferedImage getLastBi() {
        return lastBi;
    }
}
