package labmus.zebrafish_utils.tools;

import ij.IJ;
import ij.ImagePlus;
import io.scif.media.imageioimpl.plugins.tiff.TIFFImageWriterSpi;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.bytedeco.opencv.global.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.opencv.global.opencv_core.copyMakeBorder;

/**
 * SimpleRecorder is a utility class to handle video recording using FFmpegFrameRecorder.
 * A streamlined version that enhances the usability of the original class in the plugin context.
 * All output is without audio, and all video is high quality.
 * Only codecs that are useful in this context are supported: libx264 and mjpeg.
 * <p>
 * also supports TIFF lossless export
 */
public class SimpleRecorder implements AutoCloseable {

    public enum Format {
        MP4, AVI, TIFF;
    }

//    public enum Quality {
//        HIGH, LOSSLESS
//    }

    private FFmpegFrameRecorder recorder;

    private ImageOutputStream ios;
    private ImageWriter writer;
    private ImageWriteParam params;
    private Java2DFrameConverter biConverter;

    private final File outputFile;

    private final int proposedWidth;
    private final int proposedHeight;
    private final double frameRate;

    private Format format;

    // only if its mp4 AND has odd resolution
    private boolean refitNeeded;


    public SimpleRecorder(File outputFile, int imageWidth, int imageHeight, double frameRate) {
        this.outputFile = outputFile;
        this.proposedWidth = imageWidth;
        this.proposedHeight = imageHeight;
        this.frameRate = frameRate;
    }

    public SimpleRecorder(File outputFile, FFmpegFrameGrabber grabber) {
        this(outputFile, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getFrameRate());
    }

    public void start() throws Exception {
        String[] a = outputFile.getName().split("\\.");
        String extension = a[a.length - 1];
        if (!extension.equalsIgnoreCase("mp4") && !extension.equalsIgnoreCase("avi")
                && !extension.equalsIgnoreCase("tif") && !extension.equalsIgnoreCase("tiff")) {
            throw new Exception("Invalid file extension. Expected .mp4, .avi, .tif or .tiff");
        }

        if (extension.equalsIgnoreCase("mp4")) {
            this.format = Format.MP4;
        } else if (extension.equalsIgnoreCase("avi")) {
            this.format = Format.AVI;
        } else if (extension.equalsIgnoreCase("tif") || extension.equalsIgnoreCase("tiff")) {
            this.format = Format.TIFF;
        }

        switch (this.format) {
            case MP4:
                this.recorder = new FFmpegFrameRecorder(outputFile, 0, 0, 0);
                // mp4 only supports even numbers as resolution. to avoid cropping out a row of pixels, we will be adding one.
                // mp4 should only be used to create exports, not during processing steps.
                // chroma subsampling and this row can mess with everything
                recorder.setFormat("mp4");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                recorder.setVideoOption("crf", "15"); // visually lossless (0-51)
                if ((this.proposedHeight % 2 != 0) || (this.proposedWidth % 2 != 0)) {
                    this.refitNeeded = true;
                }
                recorder.setImageHeight((this.proposedHeight % 2 == 0) ? this.proposedHeight : this.proposedHeight + 1);
                recorder.setImageWidth((this.proposedWidth % 2 == 0) ? this.proposedWidth : this.proposedWidth + 1);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                setupFFmpegRecorder();
                break;
            case AVI:
                this.recorder = new FFmpegFrameRecorder(outputFile, 0, 0, 0);
                recorder.setFormat("avi");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
                recorder.setVideoQuality(2); // visually lossless (2-31) this is the q or qscale:v parameter
                recorder.setImageWidth(this.proposedWidth);
                recorder.setImageHeight(this.proposedHeight);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUVJ420P);
                // you can use other formats like rgb or AV_PIX_FMT_GRAY16
                // but they may not be compatible with the codec, and will not be compatible with imageJ.
                // if you are looking for a lossless alternative, use tiff.
                setupFFmpegRecorder();
                break;
            case TIFF:
                IIORegistry.getDefaultInstance().registerServiceProvider(new TIFFImageWriterSpi());
                this.writer = ImageIO.getImageWritersByFormatName("TIFF").next();
                this.ios = ImageIO.createImageOutputStream(outputFile);
                this.writer.setOutput(this.ios);
                this.biConverter = new Java2DFrameConverter();

                this.params = writer.getDefaultWriteParam();
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                // LZW does good, fast, lossless compression.
                params.setCompressionType("LZW");
                writer.prepareWriteSequence(null);
                break;
            default:
        }

    }

    private void setupFFmpegRecorder() throws FFmpegFrameRecorder.Exception {
        recorder.setFrameRate(this.frameRate);
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_NONE);
        recorder.setSampleRate(0);
        recorder.setAudioBitrate(0);
        recorder.setAudioChannels(0);
        this.recorder.start();
    }

    public void recordMat(Mat frameMat) throws Exception {
        try (OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat()) {
            recordMat(frameMat, converter);
        }
    }

    public void recordMat(Mat frameMat, OpenCVFrameConverter.ToMat matConverter) throws Exception {
        switch (this.format) {
            case AVI:
            case MP4:
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
                    this.recorder.record(matConverter.convert(frameToRecord));
                    frameToRecord.close();
                } else {
                    this.recorder.record(matConverter.convert(frameMat));
                }
                break;
            case TIFF:
                BufferedImage bi = biConverter.convert(matConverter.convert(frameMat));
                if (bi != null) {
                    writer.writeToSequence(new IIOImage(bi, null, null), this.params);
                } else {
                    throw new Exception("Error writing frame");
                }
                break;
            default:
        }

    }

    public ImagePlus openResultinIJ(){

        switch (this.format) {
            case AVI:
                ImagePlus imagePlus = new ImagePlus(outputFile.getAbsolutePath());
                imagePlus.show();
                return imagePlus;
            case TIFF:
                return IJ.openVirtual(outputFile.getAbsolutePath());
            default:
        }
        return null;
    }

    @Override
    public void close() throws Exception {
        switch (this.format) {
            case MP4:
            case AVI:
                this.recorder.flush();
                this.recorder.close();
                break;
            case TIFF:
                this.ios.flush();
                this.writer.endWriteSequence();
                this.writer.dispose();
                this.ios.close();
                this.biConverter.close();
                break;
            default:
        }
    }
}
