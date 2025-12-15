# Thigmotaxis

Analyzes the percentage of distance and time spent by the larvae in the inner and outer regions of the plate, defined by the user.

--8<-- "excel-functions.md"

## Interface
![Interface image](img/gui-thigmotaxis.png){ width="400em" }

## XML File
!!! warning "Tracking Required"
    Note that this function **depends on the previous use of Trackmate** to gather data about movement distance, so files without them will not get those results.

Two possible files can be used here:

1. Trackmate's whole save file
2. Output of trackmate's `Export tracks to XML file` export action.

--8<-- "source-video-analysis.md"

## Area Percentage
Specifies the percentage of the ROI used to calculate the Thigmotaxis. Note this is a percentage of **area**, not of dimensions.

## Fix Missing Spots
When tracking with Trackmate, not all frames in a video will have spots. That's because your target might not be visible in those frames. It's usually not moving as well. An uneven number of frames will create an unreliable score overall. This option fixes the missing spots by using an available adjacent spot and copying its coordinates.

There's no prejudice in setting this to `True` if you have a perfect tracking, with spots in all frames.

If you have only spots and no tracks, this feature won't work in this mode. It is not significant to mean out the positions of scores on each frame.

## Display Plots
* **Stacked Bar Chart**: Simple chart to represent time and distance relative percentages.
  ![Stacked Bar Chart image for time](img/output-thigmotaxis-1.png){ width="400em" }
  ![Stacked Bar Chart image for distance](img/output-thigmotaxis-1.png){ width="400em" }