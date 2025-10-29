package labmus.zebrafish_utils.tools;

import labmus.zebrafish_utils.ZFConfigs;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = ZFConfigs.checkDepsPath)
public class CheckDependencies implements Command{

    @Override
    public void run() {
        ZFConfigs.checkJavaCV();
    }
}
