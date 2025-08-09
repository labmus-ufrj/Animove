package zebrafish_utils.config;

import ij.IJ;
import ij.Macro;
import ij.Menus;
import ij.Prefs;
import ij.plugin.frame.Recorder;
import net.imagej.updater.CommandLine;
import org.scijava.util.AppUtils;

import javax.swing.*;
import java.util.*;
import java.util.Locale;

public abstract class ZFConfigs {
    private static final String prePath = "ZF Utils>";
    public static final String ffmpegPath = prePath + "FFmpeg";
    public static final String avgPath = prePath + "Z project";
    public static final String imgCalcPath = prePath + "Image Calculator";

    private static final String minimalRequiredVersion = "1.5.10";
    private static final String components = "ffmpeg, opencv";

    private static final HashMap<FFmpegMessageTypes, String> FFMessages = new HashMap<>();

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

    /*
        Will set up the language hashmaps here
     */
    static {
        boolean isPortuguese = Locale.getDefault().getLanguage()
                .equals(new Locale("pt", "BR").getLanguage());

        if (isPortuguese) {
            FFMessages.put(FFmpegMessageTypes.INPUT_FILE_LABEL, "Arquivo de Entrada");
            FFMessages.put(FFmpegMessageTypes.PREVIEW_FRAME_BUTTON_LABEL, "Abrir um frame e buscar dados");
            FFMessages.put(FFmpegMessageTypes.OUTPUT_FILE_LABEL, "Arquivo de Saída");
            FFMessages.put(FFmpegMessageTypes.OUTPUT_CODEC_LABEL, "Codec de Saída");
            FFMessages.put(FFmpegMessageTypes.GRAYSCALE_LABEL, "Convert to grayscale");
            FFMessages.put(FFmpegMessageTypes.QUALITY_LABEL, "Qualidade (1=Baixa, 10=Alta)");
            FFMessages.put(FFmpegMessageTypes.START_FRAME_LABEL, "Frame Inicial");
            FFMessages.put(FFmpegMessageTypes.END_FRAME_LABEL, "Frame Final (0 para ir até o fim)");
            FFMessages.put(FFmpegMessageTypes.OUTPUT_FPS_LABEL, "FPS de Saída");
            FFMessages.put(FFmpegMessageTypes.HORIZONTAL_FLIP_LABEL, "Flip Horizontal");
            FFMessages.put(FFmpegMessageTypes.VERTICAL_FLIP_LABEL, "Flip Vertical");
            FFMessages.put(FFmpegMessageTypes.ROTATION_LABEL, "Rotação");
            FFMessages.put(FFmpegMessageTypes.REMOVE_AUDIO_LABEL, "Remover Áudio");
            FFMessages.put(FFmpegMessageTypes.USE_ROI_CROP_LABEL, "Cortar usando a ROI ativa");
            FFMessages.put(FFmpegMessageTypes.MULTI_CROP_LABEL, "Multi-Crop (um vídeo por ROI)");
            FFMessages.put(FFmpegMessageTypes.POSVIEW_BUTTON_LABEL, "Posview");
            FFMessages.put(FFmpegMessageTypes.PROCESS_BUTTON_LABEL, "Processar");
            FFMessages.put(FFmpegMessageTypes.MULTI_CROP_ERROR_TITLE, "Erro no Multi-Crop");
            FFMessages.put(FFmpegMessageTypes.MISSING_FILES_DIALOG_TITLE, "Arquivos Faltando");
            FFMessages.put(FFmpegMessageTypes.OVERWRITING_DIALOG_TITLE, "Sobrescrevendo");
            FFMessages.put(FFmpegMessageTypes.ROI_ERROR_DIALOG_TITLE, "Erro de ROI");
            FFMessages.put(FFmpegMessageTypes.PROCESS_COMPLETE_DIALOG_TITLE, "Processo Concluído");
            FFMessages.put(FFmpegMessageTypes.UNEXPECTED_ERROR_DIALOG_TITLE, "Erro Inesperado");
            FFMessages.put(FFmpegMessageTypes.PREVIEW_ERROR_DIALOG_TITLE, "Erro");
            FFMessages.put(FFmpegMessageTypes.POSVIEW_IN_MULTICROP_ERROR_MSG, "Não é possível criar um Posview no modo Multi-Crop.");
            FFMessages.put(FFmpegMessageTypes.MISSING_FILES_ERROR_MSG, "Selecione um arquivo de entrada e um de saída.");
            FFMessages.put(FFmpegMessageTypes.OVERWRITING_ERROR_MSG, "O arquivo de saída já existe. Por favor delete-o ou escolha um nome diferente.");
            FFMessages.put(FFmpegMessageTypes.MULTICROP_REQUIRES_ROIS_ERROR_MSG, "O modo Multi-Crop requer ROIs no ROI Manager.");
            FFMessages.put(FFmpegMessageTypes.ROI_CROP_REQUIRES_SELECTION_ERROR_MSG, "A opção 'Cortar usando a ROI ativa' foi marcada, mas nenhuma ROI está selecionada.");
            FFMessages.put(FFmpegMessageTypes.CONVERSION_SUCCESS_MSG, "Conversão de vídeo finalizada com sucesso!");
            FFMessages.put(FFmpegMessageTypes.UNEXPECTED_ERROR_MSG, "Ocorreu um erro: ");
            FFMessages.put(FFmpegMessageTypes.PREVIEW_FILE_ERROR_MSG, "Não foi possível abrir um preview do arquivo (pode não ser um vídeo válido): ");
            FFMessages.put(FFmpegMessageTypes.MULTICROP_STATUS, "Processando Multi-Crop: ");
            FFMessages.put(FFmpegMessageTypes.PROCESSING_STATUS, "Processando ");
            FFMessages.put(FFmpegMessageTypes.POSVIEW_IMAGE_TITLE, "Imagem Posview");
            FFMessages.put(FFmpegMessageTypes.PREVIEW_IMAGE_TITLE, "Imagem Preview");
            FFMessages.put(FFmpegMessageTypes.POSVIEW_WITH_FILENAME_TITLE, "Posview - ");


        } else {
            // Default enum values are in English
            // just passing them over
            for (FFmpegMessageTypes type : FFmpegMessageTypes.values()) {
                FFMessages.put(type, type.getText());
            }
        }

    }

    // switches between different enums for different messages
    public static String getMessage(FFmpegMessageTypes type) {
        return FFMessages.get(type);
    }

}
