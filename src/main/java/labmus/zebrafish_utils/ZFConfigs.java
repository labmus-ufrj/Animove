package labmus.zebrafish_utils;

import ij.IJ;
import ij.Macro;
import ij.Menus;
import ij.Prefs;
import ij.plugin.frame.Recorder;
import net.imagej.updater.CommandLine;
import org.bytedeco.javacpp.Loader;
import org.scijava.util.AppUtils;

import javax.swing.*;
import java.util.Arrays;
import java.util.Hashtable;

public abstract class ZFConfigs {
    public static final String pluginName = "ZF Utils";

    public static final String ffmpeg = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);

    private static final String prePath = "ZF Utils>";

    private static final String toolsPath = prePath + "Tools>";
    private static final String processingPath = prePath + "Processing>"; // processing / generation / some other word
    private static final String analysisPath = prePath + "Analysis>";

    public static final String ffmpegPath = toolsPath + "FFmpeg";
    public static final String avgPath = toolsPath + "Z-project";
    public static final String imgCalcPath = toolsPath + "Image Calculator";
    public static final String checkDepsPath = toolsPath + "Check Dependencies";
    public static final String helperPath = toolsPath + "Helper";

    public static final String heatmapImages = processingPath + "Generate Heatmap Images";
    public static final String heatmapVideo = processingPath + "Generate Heatmap Video";

    private static final String embryosPath = analysisPath + "Embryos Analysis>";
    public static final String roisPath = embryosPath + "Create ROIs";
    public static final String analyzeEmbryos = embryosPath + "Quantify Heatmap";
    public static final String analyzeFrequencyEmbryos = embryosPath + "Extract Frequency Data";

    public static final String scorePath = analysisPath + "Calculate Score Gradient";
    public static final String scoreSectorPath = analysisPath + "Calculate Score Sectors";

    public static final String trackingDataPath = analysisPath + "Extract Tracking Data";

}
