package labmus.animove;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.net.URI;

@Plugin(type = Command.class, menuPath = ZFConfigs.aboutPath)
public class AboutPlugin implements Command {

    @Parameter
    private StatusService statusService;

    @Override
    public void run() {
        statusService.showStatus("Loading...");
        final String version = AppInfo.getProperty("app.version");
        final String repoUrl = AppInfo.getProperty("app.url");
        final String wikiUrl = "https://tracking-plugin.vercel.app/";
        final String paperUrl = "https://doi.org/your-paper-doi";
        final String descriptionText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus pretium molestie semper. Sed a leo eleifend, sollicitudin dui ut, tristique turpis. Integer nec varius metus, in posuere mauris. Integer suscipit ligula leo, id pellentesque nibh vulputate sit amet. Aenean condimentum, quam a faucibus consequat, felis elit fermentum lacus, non blandit tellus diam ac lacus. Quisque efficitur ullamcorper tortor, nec vestibulum nisl tincidunt eu. Integer augue leo, molestie ac felis at, viverra feugiat risus.";
        final String authorsText = "Developed by: Murilo Nespolo Spineli, Paloma de Carvalho Vieira,\n Claudia Mermelstein and Manoel Luis Costa";

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel nameLabel = new JLabel(ZFConfigs.pluginName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel versionLabel = new JLabel("Version " + version);
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        versionLabel.setForeground(Color.DARK_GRAY);
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextPane descPane = new JTextPane();
        descPane.setText(descriptionText);
        descPane.setEditable(false);
        descPane.setOpaque(false); // Transparent background
        descPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        descPane.setAlignmentX(Component.CENTER_ALIGNMENT);

        StyledDocument doc = descPane.getStyledDocument();
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_JUSTIFIED);
        doc.setParagraphAttributes(0, doc.getLength(), center, false);

        int targetWidth = 350;
        descPane.setSize(new Dimension(targetWidth, Short.MAX_VALUE));
        Dimension preferredSize = descPane.getPreferredSize();
        descPane.setPreferredSize(new Dimension(targetWidth, preferredSize.height));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.setOpaque(false);

        JButton githubButton = new JButton("GitHub");
        JButton wikiButton = new JButton("Wiki");
        JButton paperButton = new JButton("Paper");

        ActionListener openLink = e -> {
            String targetUrl = "";
            Object source = e.getSource();

            if (source == githubButton) targetUrl = repoUrl;
            else if (source == wikiButton) targetUrl = wikiUrl;
            else if (source == paperButton) targetUrl = paperUrl;

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    Desktop.getDesktop().browse(new URI(targetUrl));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        githubButton.addActionListener(openLink);
        wikiButton.addActionListener(openLink);
        paperButton.addActionListener(openLink);

        buttonPanel.add(githubButton);
        buttonPanel.add(wikiButton);
        buttonPanel.add(paperButton);

//        using HTML for multi - line centering
        String htmlFooter = "<html><div style='text-align: center;'>" + authorsText.replace("\n", "<br>") + "</div></html>";

        JLabel footerLabel = new JLabel(htmlFooter);
        footerLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        footerLabel.setForeground(Color.GRAY);
        footerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(versionLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(descPane);
        panel.add(Box.createVerticalStrut(20));
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(footerLabel);

        SwingUtilities.invokeLater(() -> {
            JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE);
            JDialog dialog = optionPane.createDialog("About " + ZFConfigs.pluginName);
            Icon infoIcon = UIManager.getIcon("OptionPane.informationIcon");
            BufferedImage iconImage = new BufferedImage(
                    infoIcon.getIconWidth(),
                    infoIcon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics g = iconImage.createGraphics();
            infoIcon.paintIcon(null, g, 0, 0);
            g.dispose();

            dialog.setIconImage(iconImage);
            dialog.setVisible(true);
            dialog.dispose();

            statusService.clearStatus();
        });
    }
}