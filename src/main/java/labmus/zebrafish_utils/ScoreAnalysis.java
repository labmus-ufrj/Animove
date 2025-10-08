package labmus.zebrafish_utils;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.GeneralPath;
import java.io.File;
import java.util.ArrayList;

@Plugin(type = Command.class, menuPath = ZFConfigs.scorePath)
public class ScoreAnalysis implements Command, Interactive, MouseListener, MouseMotionListener {

    @Parameter(label = "XML File", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File xmlFile;

    @Parameter(label = "Source video", style = FileWidget.OPEN_STYLE, callback = "updateOutputName", persist = false, required = false)
    private File videoFile = new File("E:\\score_auto\\novo\\cre+exe velhos_an_processed.avi");

    @Parameter(label = "Start", callback = "displayImage")
    private Button btn;

    @Parameter(label = "Change Max Score", callback = "changeMax")
    private Button btn2;

    @Parameter(label = "Change Min Score", callback = "changeMin")
    private Button btn3;

    @Parameter(label = "Process", callback = "process")
    private Button btn4;

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;

    enum STATE {
        CHANGE_MAX, CHANGE_MIN;
    }

    private ImagePlus videoFrame = null;
    private STATE state = STATE.CHANGE_MAX;
    private int max = 0;
    private int min = 0;

    @Override
    public void run() {
        if (!ZFConfigs.checkJavaCV()) {
            return; // if the user chooses to ignore this nothing will work anyway
        }
        ij.IJ.run("Console");
        IJ.setTool("rectangle"); // zoom and others get in the way
    }

    private void process() {
        try {
            if (!xmlFile.exists()) {
                throw new Exception("Input file does not exist.");
            }

            // how can i read a xml file in java kkkkkkkkkkkkkkkkk

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog(e.getLocalizedMessage(),
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            videoFrame = null;
        }
    }

    private void displayImage() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow().setAlwaysOnTop(true);
        try {
            if (!videoFile.exists()) {
                throw new Exception("Input file does not exist.");
            }
            displayFirstFrame();

            // maybe get max and min from fish positions?
            min = (int) (videoFrame.getWidth() * 0.9);
            max = (int) (videoFrame.getWidth() * 0.1);

            EventQueue.invokeLater(() -> {
                videoFrame.getCanvas().addMouseListener(this);
                videoFrame.getCanvas().addMouseMotionListener(this);
                drawOverlay();
            });
        } catch (Exception e) {
            log.error(e);
            uiService.showDialog(e.getLocalizedMessage(),
                    "Error", DialogPrompt.MessageType.ERROR_MESSAGE);
            videoFrame = null;
        }
    }

    private void displayFirstFrame() throws Exception {
        File currentOutputFile = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis() + ".png");
        currentOutputFile.deleteOnExit();

        // Build FFmpeg command
        // Start by getting the binary path from bytedeco's lib
        // yep that's a thing, and that's how this works
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add(ZFConfigs.ffmpeg);

        commandList.add("-i");
        commandList.add("\"" + videoFile.getAbsolutePath() + "\"");

        // Set number of frames to process
        commandList.add("-vframes");
        commandList.add(String.valueOf(1));

        // Add output file
        commandList.add("\"" + currentOutputFile.getAbsolutePath() + "\"");

        log.info(commandList.toString());

        // Execute FFmpeg command
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        process.waitFor();

        if (videoFrame != null && videoFrame.getWindow() != null) {
            videoFrame.close();
        }
        videoFrame = new ImagePlus(currentOutputFile.getAbsolutePath());
        videoFrame.setTitle("Video frame");
        uiService.show(videoFrame);
    }

    private void changeMax() {
        this.state = STATE.CHANGE_MAX;
    }

    private void changeMin() {
        this.state = STATE.CHANGE_MIN;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        Object src = e.getSource();
        if (src != videoFrame.getCanvas()) {
            return;
        }
        videoFrame.deleteRoi(); // delete user selection (rectangle)

        Point p = videoFrame.getCanvas().getCursorLoc();
        switch (this.state) {
            case CHANGE_MAX:
                this.max = p.x;
                break;
            case CHANGE_MIN:
                this.min = p.x;
                break;
            default:
                break;
        }

        drawOverlay();

    }

    private void drawOverlay() {
        if (videoFrame == null) {
            return;
        }
        Overlay overlay = videoFrame.getOverlay();
        if (overlay == null) {
            overlay = new Overlay();
            videoFrame.setOverlay(overlay);
        }

        // just deleting the whole thing
        for (int i = overlay.size() - 1; i >= 0; i--) {
            overlay.remove(i);
        }

        addRoi(min, Color.RED);
        addRoi(max, Color.BLUE);

        videoFrame.draw();
    }

    private void addRoi(int value, Color awtColor) {
        GeneralPath path = new GeneralPath();
        path.moveTo(value, 0f);
        path.lineTo(value, videoFrame.getHeight());

        Roi roi = new ShapeRoi(path);
        roi.setStrokeColor(awtColor);
        roi.setStroke(new BasicStroke(5));
        roi.setName(String.valueOf(value));
        videoFrame.getOverlay().add(roi);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        mouseDragged(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }
}
