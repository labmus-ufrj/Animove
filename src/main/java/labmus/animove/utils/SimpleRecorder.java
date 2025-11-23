package labmus.animove.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.AVI_Reader;
import io.scif.config.SCIFIOConfig;
import io.scif.img.ImageRegion;
import io.scif.img.Range;
import io.scif.media.imageioimpl.plugins.tiff.TIFFImageWriterSpi;
import io.scif.services.DatasetIOService;
import labmus.animove.ZFConfigs;
import net.imagej.Dataset;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.opencv.global.opencv_core.BORDER_CONSTANT;

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

    private FFmpegFrameRecorder recorder;

    private ImageOutputStream ios;
    private ImageWriter writer;
    private ImageWriteParam params;
    private Java2DFrameConverter biConverter; // todo: don't we need a new one every frame??

    private final File outputFile;

    private final int proposedWidth;
    private final int proposedHeight;
    private final double frameRate;

    private Format format;

    // only if its mp4 AND has an odd resolution
    private boolean refitNeeded;

    private boolean isClosed = false;

    private final Scalar blackScalar = new Scalar(0, 0, 0, 0);

    public SimpleRecorder(File outputFile, Mat mat, double frameRate) {
        this.outputFile = outputFile;
        this.proposedWidth = mat.arrayWidth();
        this.proposedHeight = mat.arrayHeight();
        this.frameRate = frameRate;
    }

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
        String extension = outputFile.getName().substring(outputFile.getName().lastIndexOf(".") + 1);
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
                this.recorder.setCloseOutputStream(true);
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
//                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
//                recorder.setVideoOption("color_range", "pc");
                // you can use other formats like rgb or AV_PIX_FMT_GRAY8
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

    /**
     * If the output is set to MP4, a row or column will be added to make the number even. It's a codec requirement.
     * If the output is set to AVI or MP4, frames will be normalized to 8bit. It's a codec limitation.
     * If the output is set to TIFF, frames will be normalized to 16bit. 32bit stack is not viewable.
     *
     * @param frameMat     Mat to be recorded
     * @param matConverter One per frame keeps it safe
     * @throws Exception If anything goes wrong, read the message
     */
    public void recordMat(Mat frameMat, OpenCVFrameConverter.ToMat matConverter) throws Exception {
        Mat tempFrame;
        if (frameMat.elemSize1() == 1) { // if it's already 8-bit
            tempFrame = frameMat;
        } else {
            tempFrame = new Mat();
            int pixFmt;
            if (this.format == Format.TIFF) {
                pixFmt = frameMat.channels() == 1 ? opencv_core.CV_16UC1 : opencv_core.CV_16UC3;
            } else {
                pixFmt = frameMat.channels() == 1 ? opencv_core.CV_8UC1 : opencv_core.CV_8UC3;
            }
            opencv_core.normalize(
                    frameMat,
                    tempFrame,
                    0,
                    Math.pow(2, this.format == Format.TIFF ? 16 : 8) - 1,
                    opencv_core.NORM_MINMAX,
                    pixFmt,
                    null
            );
        }

        switch (this.format) {
            case AVI:
            case MP4:
                if (this.refitNeeded) {
                    // add padding
                    try (Mat frameToRecord = new Mat()) {
                        opencv_core.copyMakeBorder(tempFrame,
                                frameToRecord,
                                0,
                                this.recorder.getImageHeight() - this.proposedHeight,
                                0,
                                this.recorder.getImageWidth() - this.proposedWidth,
                                BORDER_CONSTANT,
                                blackScalar); // black
                        try (Frame frame = matConverter.convert(frameToRecord)) {
                            this.recorder.record(frame);
                        }
                    }
                } else {
                    try (Frame frame = matConverter.convert(tempFrame)) {
                        this.recorder.record(frame);
                    }
                }

                break;
            case TIFF:
                try (Frame frame = matConverter.convert(tempFrame)) {
                    BufferedImage bi = biConverter.getBufferedImage(frame);
                    if (bi != null) {
                        writer.writeToSequence(new IIOImage(bi, null, null), this.params);
                    } else {
                        throw new Exception("Error writing frame");
                    }
                }
                break;
            default:
        }
        if (tempFrame != frameMat) {
            tempFrame.close();
        }
    }

    /**
     * Can open AVI and TIFF as virtual stacks
     *
     * @param uiService        get it from @Parameter
     * @param datasetIOService get it from @Parameter
     * @throws Exception mainly IOException
     */
    public void openResultinIJ(UIService uiService, DatasetIOService datasetIOService, boolean openAllChannels) throws Exception {
        this.close();
        switch (this.format) {
            case MP4:
                uiService.showDialog("Can't open MP4 files in ImageJ.", ZFConfigs.pluginName, DialogPrompt.MessageType.WARNING_MESSAGE);
                break;
            case TIFF:
                SCIFIOConfig config = new SCIFIOConfig();
                config.enableBufferedReading(true); // this is the virtual stack setting
                config.imgOpenerSetImgModes(SCIFIOConfig.ImgMode.CELL);
                if (!openAllChannels) {
                    // tiff will have 3 channels all with the same data. only need to open one.
                    Map<AxisType, Range> regionMap = new HashMap<>();
                    regionMap.put(Axes.CHANNEL, new Range(0L));
                    config.imgOpenerSetRegion(new ImageRegion(regionMap));

                } else {
                    config.imgOpenerSetImgModes(SCIFIOConfig.ImgMode.CELL, SCIFIOConfig.ImgMode.PLANAR);
                }
                Dataset dataset = datasetIOService.open(outputFile.getAbsolutePath(), config);
                uiService.show(dataset);
                break;
            case AVI:
                String opt = "virtual";
                if (!openAllChannels) {
                    opt += "convert";
                }
                AVI_Reader.open(outputFile.getAbsolutePath(), opt).show();
                // For some reason, datasetIOService.open() would throw an EOFException
                // when reading some AVI files. This seemingly random behavior made me use the legacy option instead
                break;
        }

    }

    @Override
    public void close() throws Exception {
        if (this.isClosed) {
            return;
        }
        blackScalar.close();
        switch (this.format) {
            case MP4:
            case AVI:
                this.recorder.flush();
                this.recorder.stop(); // I know this does the same thing. but testing showed it is necessary to do this explicitly
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
        this.isClosed = true;
    }
}
