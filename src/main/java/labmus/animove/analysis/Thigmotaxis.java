package labmus.animove.analysis;

import labmus.animove.ZFConfigs;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.plugin.Plugin;

import java.util.concurrent.Executors;

@SuppressWarnings({"FieldCanBeLocal"})
@Plugin(type = Command.class, menuPath = ZFConfigs.thigmotaxisPath)
public class Thigmotaxis implements Command, Interactive {
    static {
        // this runs on a Menu click
        // reduces loading time for FFmpegFrameGrabber
        Executors.newSingleThreadExecutor().submit(() -> ZFConfigs.ffmpeg);
    }

    @Override
    public void run() {

    }
}
