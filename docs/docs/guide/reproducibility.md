# Reproducibility

This section aims to make it possible for any user to reproduce all outputs of this plugin present in this documentation.

--8<-- "test-data.md"

---

## 2 - Processing

### Binary Heatmap Image
`larvae/larvae-long.mp4` file was inputted in this processing tool. 

The whole video was set as the interval, rendering the [output shown](../processing/heatmap-binary.md#expected-output).

Minor cropping and rotation adjustments were done.

### Sum Heatmap Images
`adults/adults-long.mp4` file was inputted in this processing tool. 

Lookup table was set to Fire. 

The start interval was chosen (1-5400). 

`roi.roi` was loaded into ROI Manager. 

Minor cropping adjustments were done, rendering the 
[output](../processing/heatmap-images.md#expected-output).


### Sum Heatmap Video
`adults/adults-long.mp4` file was inputted in this processing tool. 

Lookup table was set to Fire.

The whole video was set as the interval.

`roi.roi` was loaded into ROI Manager.

Rendering the [output](../processing/heatmap-video.md#expected-output).

---

## 3 - Analysis

### Calculate Score Gradient

Files `adults/tracking/full.xml` and `adults/tracking/adults-tracking.avi` were inputted into this tool.

Fix missing spots and Display Plots were left enabled.

Max and Min values were loaded from the XML file using the dedicated button.

Rendering the [plots shown](../analysis/score-gradient.md#display-plots)

### Calculate Score Sectors
Files `adults/tracking/full.xml` and `adults/tracking/adults-tracking.avi` were inputted into this tool.

`adults/tracking/sectors.zip` was opened in ROI Manager.

Fix missing spots and Display Plots were left enabled.

Rendering the [plot shown](../analysis/score-sectors.md#display-plots).

### Extract Tracking Frequency Data

Using this plugin's [FFmpeg tool](../tools/ffmpeg.md), 
`larvae/larvae-short.mp4` was cropped 200 frames from the start and util frame 501,
using the Well_2 ROI from the `larvae/RoiSet.zip` file.

The output was processed with [larvae tracking](../processing/larvae-tracking.md),
generating the `larvae/tracking/larvae-tracking.avi` file as its output.

The video scale was set to millimeters. 1 px equivalent to 0.042270531400966184 mm

The latter file was run through Trackmate. Its output created the `larvae/tracking/full.xml` file.

Then, `larvae/tracking/full.xml` and `larvae/tracking/larvae-tracking.avi` were inputted into this analysis tool.

Display Plots was left enabled.

Generating the [plot shown](../analysis/frequency-data.md#display-plots).

### Quantify Binary Heatmap

`larvae/larvae-long.mp4` file was inputted in the [Binary Heatmap Image](../processing/heatmap-binary.md) processing plugin.
The whole video was set as the interval.

Its output was inputted into this analysis tool.

`larvae/RoiSet.zip` was opened in ROI Manager.

Display Plots was left enabled.

Rendering the [plots shown](../analysis/quantify-heatmap.md#display-plots).


### Thigmotaxis

Files [`larvae/tracking/full.xml`](http://asdasd) and `larvae/tracking/larvae-tracking.avi` were inputted into this tool.

Area percentage was set to 50%.

Open Frame button was pressed, then the image was calibrated to millimeters. 1 px equivalent to 0.042270531400966184 mm

A ROI enclosing the whole image was created.

Fix missing spots and Display Plots were left enabled.

Creating the [plots shown](../analysis/thigmotaxis.md#display-plots).