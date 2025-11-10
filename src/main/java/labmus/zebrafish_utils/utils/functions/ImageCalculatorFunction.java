package labmus.zebrafish_utils.utils.functions;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.function.Function;

import static org.bytedeco.opencv.global.opencv_core.add;
import static org.bytedeco.opencv.global.opencv_core.subtract;

public class ImageCalculatorFunction implements Function<Mat, Mat> {

    public enum OperationMode {
        ADD("Add"),
        SUBTRACT("Subtract");

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
    
    private final OperationMode mode;
    private final Mat inputImage;

    public ImageCalculatorFunction(OperationMode mode, Mat inputImage) {
        this.mode = mode;
        this.inputImage = inputImage;
    }

    @Override
    public Mat apply(Mat grayMatFrame) {
        Mat resultFrame = new Mat();
        switch (mode) {
            case ADD:
                add(grayMatFrame, inputImage, resultFrame, null, opencv_core.CV_8UC1);
                break;
            case SUBTRACT:
                subtract(grayMatFrame, inputImage, resultFrame, null, opencv_core.CV_8UC1);
                break;
            default:
                break; // this is not happening lol
        }
        return resultFrame;
    }
}
