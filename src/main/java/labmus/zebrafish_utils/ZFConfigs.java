package labmus.zebrafish_utils;

import ij.IJ;
import ij.Macro;
import ij.Menus;
import ij.Prefs;
import ij.plugin.frame.Recorder;
import net.imagej.updater.CommandLine;
import org.scijava.util.AppUtils;

import javax.swing.*;
import java.util.Arrays;
import java.util.Hashtable;

public abstract class ZFConfigs {
    private static final String prePath = "ZF Utils>";
    public static final String ffmpegPath = prePath + "FFmpeg";
    public static final String avgPath = prePath + "Z project";
    public static final String imgCalcPath = prePath + "Image Calculator";

    private static final String minimalRequiredVersion = "1.5.10";
    private static final String components = "ffmpeg, opencv";

    // straight up from https://github.com/anotherche/imagej-ffmpeg-video
    // great project, check it out
    public static boolean checkJavaCV() {

        String javaCVInstallCommand = "Install JavaCV libraries";
        Hashtable table = Menus.getCommands();
        String javaCVInstallClassName = (String) table.get(javaCVInstallCommand);
        if (javaCVInstallClassName == null) {
            int result = JOptionPane.showConfirmDialog(null,
                    "<html><h2>JavaCV Installer not found.</h2>"
                            + "<br>Please install it from from JavaCVInstaller update site:"
                            + "<br>https://sites.imagej.net/JavaCVInstaller/"
                            + "<br>Do you whant it to be installed now for you?"
                            + "<br><i>you need to restart ImageJ after the install</i></html>",
                    "JavaCV check", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                net.imagej.updater.CommandLine updCmd = new net.imagej.updater.CommandLine(
                        AppUtils.getBaseDirectory("ij.dir", CommandLine.class, "updater"), 80);
                updCmd.addOrEditUploadSite("JavaCVInstaller", "https://sites.imagej.net/JavaCVInstaller/", null, null,
                        false);
                net.imagej.updater.CommandLine updCmd2 = new net.imagej.updater.CommandLine(
                        AppUtils.getBaseDirectory("ij.dir", CommandLine.class, "updater"), 80);
                updCmd2.update(Arrays.asList("plugins/JavaCV_Installer/JavaCV_Installer.jar"));
                IJ.run("Refresh Menus");
                table = Menus.getCommands();
                javaCVInstallClassName = (String) table.get(javaCVInstallCommand);
                if (javaCVInstallClassName == null) {
                    IJ.showMessage("JavaCV check",
                            "Failed to install JavaCV Installer plugin.\nPlease install it manually.");
                }
            }
            return false;
        }

        String installerCommand = "version=" + minimalRequiredVersion + " select_installation_option=[Install missing] "
                + "treat_selected_version_as_minimal_required " + components;

        boolean saveRecorder = Recorder.record; // save state of the macro Recorder
        Recorder.record = false; // disable the macro Recorder to avoid the JavaCV installer plugin being
        // recorded instead of this plugin
        String saveMacroOptions = Macro.getOptions();
        IJ.run("Install JavaCV libraries", installerCommand);
        if (saveMacroOptions != null)
            Macro.setOptions(saveMacroOptions);
        Recorder.record = saveRecorder; // restore the state of the macro Recorder

        String result = Prefs.get("javacv.install_result", "");
        String launcherResult = Prefs.get("javacv.install_result_launcher", "");
        if (!(result.equalsIgnoreCase("success") && launcherResult.equalsIgnoreCase("success"))) {
            if (result.indexOf("restart") > -1 || launcherResult.indexOf("restart") > -1) {
                IJ.log("Please restart ImageJ to proceed with installation of necessary JavaCV libraries.");
                return false;
            } else {
                IJ.log("JavaCV installation failed. Trying to use JavaCV as is...");
                return true;
            }
        }
        return true;
    }

}
