package labmus.zebrafish_utils.utils.functions;

import ij.IJ;
import labmus.zebrafish_utils.ZFConfigs;
import labmus.zebrafish_utils.utils.SimpleRecorder;
import org.bytedeco.opencv.opencv_core.Mat;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.util.function.Function;

/**
 * this is actually a consumer. the function just passes the input mat ahead.
 */
public class SimpleRecorderFunction implements Function<Mat, Mat>, AutoCloseable {
    private final SimpleRecorder recorder;

    private final UIService uiService;
    public SimpleRecorderFunction(SimpleRecorder recorder, UIService uiService) throws Exception {
        this.recorder = recorder;
        this.uiService = uiService;
        this.recorder.start();
    }

    @Override
    public Mat apply(Mat mat) {
        try {
            this.recorder.recordMat(mat);
        } catch (Exception e) {
            IJ.log(e.getMessage());
            uiService.showDialog("An error occurred when writing the video to a file: " + e.getMessage(), ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
        }
        return mat;
    }

    @Override
    public void close() throws Exception {
        this.recorder.close();
    }

    public SimpleRecorder getRecorder() {
        return recorder;
    }
}
