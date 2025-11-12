package labmus.zebrafish_utils;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

@Plugin(type = Command.class, menuPath = ZFConfigs.aboutPath)
public class AboutPlugin implements Command {

    @Override
    public void run() {
        final String version = AppInfo.getProperty("app.version");
        final String url = AppInfo.getProperty("app.url");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(ZFConfigs.pluginName);
        JLabel versionLabel = new JLabel("Version: " + version);

        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton githubButton = new JButton("Open GitHub Page");
        githubButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        githubButton.addActionListener(e -> {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    try {
                        desktop.browse(new URI(url));
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        panel.add(Box.createVerticalStrut(5));
        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(versionLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(githubButton);

        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    null,
                    panel,
                    "About",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
    }
}