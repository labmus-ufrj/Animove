package labmus.zebrafish_utils.tools;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;

import java.io.File;

import static org.bytedeco.opencv.global.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.opencv.global.opencv_core.copyMakeBorder;

/**
 * SimpleRecorder is a utility class to handle video recording using FFmpegFrameRecorder.
 * A streamlined version that enhances the usability of the original class in the plugin context.
 * All output is without audio, and all video is high quality.
 * Only codecs that are useful in this context are supported: libx264 and mjpeg.
 */
public class SimpleRecorder implements AutoCloseable{

    private final FFmpegFrameRecorder recorder;
    private final File outputFile;

    private final int proposedWidth;
    private final int proposedHeight;
    private final double frameRate;
    private boolean refitNeeded;

    public enum Quality{
        HIGH, LOSSLESS
    }

    public SimpleRecorder(File outputFile, int imageWidth, int imageHeight, double frameRate) {
        this.outputFile = outputFile;
        this.proposedWidth = imageWidth;
        this.proposedHeight = imageHeight;
        this.frameRate = frameRate;
        this.recorder = new FFmpegFrameRecorder(outputFile, 0, 0, 0);
    }

    public SimpleRecorder(File outputFile, FFmpegFrameGrabber grabber) {
        this(outputFile, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getFrameRate());
    }

    public void start() throws Exception {
        String[] a = outputFile.getName().split("\\.");
        String extension = a[a.length - 1];
        if (!extension.equalsIgnoreCase("mp4") && !extension.equalsIgnoreCase("avi")) {
            throw new Exception("Invalid file extension. Expected .mp4 or .avi");
        }

        if (extension.equalsIgnoreCase("avi")) {
            recorder.setFormat("avi");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
            recorder.setVideoQuality(2); // visually lossless (2-31) this is the q or qscale:v parameter
            recorder.setImageWidth(this.proposedWidth);
            recorder.setImageHeight(this.proposedHeight);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUVJ420P);
        } else {
            // mp4 only supports even numbers as resolution. to avoid cropping a row of pixels, we will be adding one.
            // mp4 should only be used to create exports, not during processing steps.
            // chroma subsampling and this row can mess with everything
            recorder.setFormat("mp4");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setVideoOption("crf", "15"); // visually lossless (0-51)
            if ((this.proposedHeight % 2 != 0) || (this.proposedWidth % 2 != 0)){
                this.refitNeeded = true;
            }
            recorder.setImageHeight((this.proposedHeight % 2 == 0) ? this.proposedHeight : this.proposedHeight + 1);
            recorder.setImageWidth((this.proposedWidth % 2 == 0) ? this.proposedWidth : this.proposedWidth + 1);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        }

        recorder.setFrameRate(this.frameRate);

        recorder.setAudioCodec(avcodec.AV_CODEC_ID_NONE);
        recorder.setSampleRate(0);
        recorder.setAudioBitrate(0);
        recorder.setAudioChannels(0);

        this.recorder.start();
    }

    public void recordMat(Mat frameMat) throws FFmpegFrameRecorder.Exception {
        try (OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat()) {
            recordMat(frameMat, converter);
        }
    }

    public void recordMat(Mat frameMat, OpenCVFrameConverter.ToMat converter) throws FFmpegFrameRecorder.Exception {
        if (this.refitNeeded) {
            // add padding
            Mat frameToRecord = new Mat();
            copyMakeBorder(frameMat,
                    frameToRecord,
                    0,
                    this.recorder.getImageHeight() - this.proposedHeight,
                    0,
                    this.recorder.getImageWidth() - this.proposedWidth,
                    BORDER_CONSTANT,
                    new Scalar(0, 0, 0, 0)); // black
            this.recorder.record(converter.convert(frameToRecord));
            frameToRecord.close();
        } else {
            this.recorder.record(converter.convert(frameMat));
        }
    }

    @Override
    public void close() throws Exception {
        this.recorder.flush();
        this.recorder.close();
    }
}
