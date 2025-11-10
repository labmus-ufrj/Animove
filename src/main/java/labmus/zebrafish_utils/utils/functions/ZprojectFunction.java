package labmus.zebrafish_utils.utils.functions;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

/**
 * Processes video frames from an input file according to the specified operation mode
 * (e.g., finding the darkest, brightest, average, or summing frames) and writes the
 * resulting image to the output file.
 * Converts the frames to grayscale, operates on a specific frame range.
 * <p>
 * this is actually a consumer. the function just passes the input mat ahead.
 */
public class ZprojectFunction implements Function<Mat, Mat> {

    public enum OperationMode {
        MIN("Darkest (Min)"),
        MAX("Brightest (Max)"),
        AVG("Average"),
        SUM("Sum");

        private final String text;

        OperationMode(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }

        /**
         * Finds an OperationMode by its user-facing text.
         * This method is case-insensitive.
         *
         * @param text The text to search for (e.g., "Average")
         * @return The matching OperationMode (null if nothing is found)
         */
        public static OperationMode fromText(String text) {
            for (OperationMode mode : OperationMode.values()) {
                if (mode.getText().equalsIgnoreCase(text)) {
                    return mode;
                }
            }
            return null;
        }
    }

    private Mat accumulator;
    private final OperationMode mode;
    private int framesProcessedCount = 0;

    public ZprojectFunction(OperationMode mode) {
        this.mode = mode;
    }

    /**
     * you really need to close this mat. please. it leaks.
     **/
    public Mat getResultMat() throws Exception {
        if (accumulator == null) {
            throw new Exception("No frames were processed.");
        }
        Mat resultMat;
        if (mode == OperationMode.AVG) {
            // The logic is: you opened a video file, which can only be 8-bit. No need to save anything higher than that.
            Mat tempMat = new Mat();
            double scale = 1.0 / framesProcessedCount;
            accumulator.convertTo(tempMat, opencv_core.CV_32FC1, scale, 0); // scale to create avg
            resultMat = new Mat();
            opencv_core.normalize( // normalize to 8 bit
                    tempMat,
                    resultMat,
                    0,
                    Math.pow(2, 8) - 1,
                    opencv_core.NORM_MINMAX,
                    opencv_core.CV_8UC1,
                    null
            );
        } else {
            resultMat = accumulator;
        }
        if (resultMat != accumulator) {
            accumulator.close();
        }
        return resultMat;
    }

    @Override
    public Mat apply(Mat currentFrame) {
        if (accumulator == null) {
            accumulator = new Mat();
            switch (mode) {
                case AVG:
                case SUM:
                    currentFrame.convertTo(accumulator, opencv_core.CV_32FC1);
                    break;
                default: // Darkest and Brightest
                    // using convertTo() instead of clone() fixes the 180° flipping issue
                    currentFrame.convertTo(accumulator, currentFrame.type());
                    break;
            }
        } else {
            switch (mode) {
                case MIN:
                    opencv_core.min(accumulator, currentFrame, accumulator);
                    break;
                case MAX:
                    opencv_core.max(accumulator, currentFrame, accumulator);
                    break;
                case AVG:
                case SUM:
                    try (Mat tempFloatFrame = new Mat()) {
                        currentFrame.convertTo(tempFloatFrame, opencv_core.CV_32FC1);
                        opencv_core.add(accumulator, tempFloatFrame, accumulator);
                    }
                    break;
            }
        }
        framesProcessedCount++;
        return currentFrame;
    }
}
