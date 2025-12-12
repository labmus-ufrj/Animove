package labmus.animove;

import org.bytedeco.javacpp.Loader;

public abstract class ZFConfigs {
    public static final String pluginName = AppInfo.getProperty("app.artifactId");

    public static final String ffmpeg = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
    public static final String ffprobe = Loader.load(org.bytedeco.ffmpeg.ffprobe.class);

    private static final String prePath = "Animove" + ">"; // sadly can't use pluginName here

    private static final String toolsPath = prePath + "1 - Tools>";
    private static final String processingPath = prePath + "2 - Processing>"; // processing / generation / some other word
    private static final String analysisPath = prePath + "3 - Analysis>";

    public static final String aboutPath = prePath + "About";

    public static final String ffmpegPath = toolsPath + "FFmpeg";
    public static final String avgPath = toolsPath + "Z-project";
    public static final String imgCalcPath = toolsPath + "Image Calculator";
    public static final String checkDepsPath = toolsPath + "Check Dependencies";
//    public static final String helperPath = toolsPath + "Helper";
    public static final String frameExtractorPath = toolsPath + "Frame Extractor";
    public static final String performanceTest = toolsPath + "Performance Test";
    public static final String invertPath = toolsPath + "Invert Video";

    private static final String heatmapsPath = processingPath + "Heatmaps>";
    public static final String heatmapSumImagesPath = heatmapsPath + "Sum Heatmap Images (Adults)";
    public static final String heatmapSumVideoPath = heatmapsPath + "Sum Heatmap Video (Adults)";
    public static final String heatmapBinaryImagePath = heatmapsPath + "Binary Heatmap Image (Larvae)";
    public static final String roisPath = toolsPath + "Create Sequential ROIs";

    private static final String trackingPath = processingPath + "Tracking>";
    public static final String larvaeTrackingPath = trackingPath + "Larvae Tracking";
    public static final String adultsTrackingPath = trackingPath + "Adults Tracking";

    public static final String scoreGradientPath = analysisPath + "Calculate Score Gradient";
    public static final String scoreSectorPath = analysisPath + "Calculate Score Sectors";
    public static final String analyzeFrequencyLarvaePath = analysisPath + "Extract Frequency Data";
    public static final String quantifyLarvaePath = analysisPath + "Quantify Binary Heatmap";
    public static final String thigmotaxisPath = analysisPath + "Thigmotaxis";


    public static final String trackingDataPath = analysisPath + "Extract Tracking Data";

}
