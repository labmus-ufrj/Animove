package labmus.animove.analysis;

import ij.ImagePlus;
import ij.measure.ResultsTable;
import labmus.animove.ZFConfigs;
import labmus.animove.ZFHelperMethods;
import labmus.animove.utils.XMLHelper;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.AbstractRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
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
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.ArrayList;

@Plugin(type = Command.class, menuPath = ZFConfigs.spotsPlotting)
public class SpotsPlotting implements Command, Interactive {

    @Parameter(label = "XML File", style = FileWidget.OPEN_STYLE, persist = false, required = false)
    private File xmlFile;

    @Parameter(label = "Fix Missing Spots", persist = false)
    private boolean fixSpots = true;

    @Parameter(label = "Invert X Axis", persist = false)
    private boolean invertX = false;

    @Parameter(label = "Invert Y Axis", persist = false)
    private boolean invertY = false;

    @Parameter(label = "Process", callback = "process")
    private Button btn5;

    @Parameter
    private LogService log;
    @Parameter
    private UIService uiService;

    @Override
    public void run() {

    }

    private void process() {
        if (xmlFile == null || !xmlFile.exists()) {
            uiService.showDialog("Invalid XML file", ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        XMLHelper.TrackingXMLData trackingXMLData;
        try {
            trackingXMLData = XMLHelper.iterateOverXML(xmlFile, null, fixSpots);

        } catch (Exception e) {
            log.error(e);
            uiService.showDialog("An error occured during processing:\n" + e.getLocalizedMessage(),
                    ZFConfigs.pluginName, DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        String name = "Positions from " + xmlFile.getName();

        ResultsTable rt = new ResultsTable();
        rt.setNaNEmptyCells(true); // prism reads 0.00 as zeros and requires manual fixing
        // NaN just gets deleted when pasting

        XYSeriesCollection dataset = new XYSeriesCollection();
        for (int i = 0; i < trackingXMLData.data.size(); i++) {
            XYSeries series = new XYSeries(trackingXMLData.data.size() == 1 ? "Series "+ (i + 1) : "Track "+ (i + 1));
            ArrayList<XMLHelper.PointData> pointDataArrayList = trackingXMLData.data.get(i);
            for (XMLHelper.PointData point : pointDataArrayList) {
                series.add(point.x, point.y);
                rt.incrementCounter();
                rt.addValue("X (" + trackingXMLData.spacialUnit + ")", point.x);
                rt.addValue("Y (" + trackingXMLData.spacialUnit + ")", point.y);
                rt.addValue(trackingXMLData.data.size() == 1 ? "Series" : "Track", i);
            }
            dataset.addSeries(series);
        }

        new ImagePlus("Plot", getPlotChart(dataset, name, trackingXMLData.spacialUnit).createBufferedImage(1600, 1200)).show();

        rt.show(name);

    }

    private JFreeChart getPlotChart(XYSeriesCollection dataset, String name, String spacialUnit) {
        JFreeChart chart = ChartFactory.createScatterPlot(
                name,
                "X Position (" + spacialUnit + ")",
                "Y Position (" + spacialUnit + ")",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        chart.setBackgroundPaint(Color.WHITE);
        chart.setPadding(new RectangleInsets(20.0, 20.0, 20.0, 20.0));

        TextTitle title = chart.getTitle();
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        title.setPaint(Color.BLACK);

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.decode("#f0f0f0"));
        plot.setOutlineVisible(false);

        plot.setDomainGridlinePaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.WHITE);

        XYItemRenderer renderer = plot.getRenderer();
        java.util.List<Color> colors = ZFHelperMethods.generateColors(dataset.getSeriesCount());

        Shape scatterShape = new Ellipse2D.Double(-8, -8, 16, 16);

        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            renderer.setSeriesPaint(i, colors.get(i));
            renderer.setSeriesShape(i, scatterShape);
        }

        if (renderer instanceof AbstractRenderer) {
            ((AbstractRenderer) renderer).setDefaultLegendShape(new Ellipse2D.Double(0, 0, 25, 25));
        }

        ValueAxis domainAxis = plot.getDomainAxis();
        domainAxis.setLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        domainAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));

        domainAxis.setAxisLineVisible(true);
        domainAxis.setAxisLinePaint(Color.BLACK);
        domainAxis.setAxisLineStroke(new BasicStroke(6.0f)); // Increase float value for a thicker line

        domainAxis.setInverted(invertX);

        ValueAxis rangeAxis = plot.getRangeAxis();
        rangeAxis.setLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        rangeAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));

        rangeAxis.setInverted(invertY);

        rangeAxis.setAxisLineVisible(true);
        rangeAxis.setAxisLinePaint(Color.BLACK);
        rangeAxis.setAxisLineStroke(new BasicStroke(6.0f)); // Increase float value for a thicker line

        LegendTitle legend = chart.getLegend();
        legend.setPosition(RectangleEdge.BOTTOM);
        legend.setItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
        legend.setFrame(BlockBorder.NONE);
        legend.setItemLabelPadding(new RectangleInsets(0, 10, 0, 40));

        return chart;
    }

}
