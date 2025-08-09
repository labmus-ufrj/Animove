package zebrafish_utils.config;

import java.awt.*;

/**
 * Centralizes all user-facing strings for the FFmpegPlugin.
 * This includes UI labels, dialog messages, error messages, log entries,
 * and status updates. Using this enum makes the code cleaner and simplifies
 * future efforts for internationalization.
 */
public enum FFmpegMessageTypes {

    // --- @Parameter Labels ---
    INPUT_FILE_LABEL("Input File"),
    PREVIEW_FRAME_BUTTON_LABEL("Preview Frame and get data"),
    OUTPUT_FILE_LABEL("Output File"),
    OUTPUT_CODEC_LABEL("Output Codec"),
    GRAYSCALE_LABEL("Convert to grayscale"),
    QUALITY_LABEL("Quality (1=Lowest, 10=Highest)"),
    START_FRAME_LABEL("Start Frame"),
    END_FRAME_LABEL("End Frame (0 for last)"),
    OUTPUT_FPS_LABEL("Output FPS"),
    HORIZONTAL_FLIP_LABEL("Horizontal Flip"),
    VERTICAL_FLIP_LABEL("Vertical Flip"),
    ROTATION_LABEL("Rotation"),
    REMOVE_AUDIO_LABEL("Remove Audio"),
    USE_ROI_CROP_LABEL("Crop using active ROI"),
    MULTI_CROP_LABEL("Multi-Crop (one video per ROI)"),
    POSVIEW_BUTTON_LABEL("Posview"),
    PROCESS_BUTTON_LABEL("Process"),

    // --- Dialog Titles ---
    MULTI_CROP_ERROR_TITLE("Multi-Crop Error"),
    MISSING_FILES_DIALOG_TITLE("Missing Files"),
    OVERWRITING_DIALOG_TITLE("Overwriting File"),
    ROI_ERROR_DIALOG_TITLE("ROI Error"),
    PROCESS_COMPLETE_DIALOG_TITLE("Process Complete"),
    UNEXPECTED_ERROR_DIALOG_TITLE("Unexpected Error"),
    PREVIEW_ERROR_DIALOG_TITLE("Preview Error"),

    // --- Dialog Messages ---
    POSVIEW_IN_MULTICROP_ERROR_MSG("Cannot create posview in Multi-Crop mode."),
    MISSING_FILES_ERROR_MSG("Please select an input file and an output file."),
    OVERWRITING_ERROR_MSG("Output file already exists. Please delete it or choose a different name."),
    MULTICROP_REQUIRES_ROIS_ERROR_MSG("Multi-Crop mode requires ROIs in the ROI Manager."),
    ROI_CROP_REQUIRES_SELECTION_ERROR_MSG("'Crop using active ROI' option is checked, but no ROI is selected."),
    CONVERSION_SUCCESS_MSG("Video conversion finished successfully!"),
    UNEXPECTED_ERROR_MSG("An unexpected error occurred: "),
    PREVIEW_FILE_ERROR_MSG("Could not open a preview of the file (it may not be a valid video): "),

    // --- Status Messages ---
    MULTICROP_STATUS("Processing Multi-Crop: "),
    PROCESSING_STATUS("Processing "),

    // --- Image Titles ---
    POSVIEW_IMAGE_TITLE("Posview Image"),
    PREVIEW_IMAGE_TITLE("Preview Image"),
    POSVIEW_WITH_FILENAME_TITLE("Posview - ");


    private final String text;

    /**
     * Constructor for the enum.
     * @param text The user-facing string.
     */
    FFmpegMessageTypes(String text) {
        this.text = text;
    }

    /**
     * Gets the raw string value.
     * @return The user-facing text.
     */
    public String getText() {
        return this.text;
    }

    /**
     * Returns the raw string value.
     * @return The user-facing text.
     */
    @Override
    public String toString() {
        return this.text;
    }
}
