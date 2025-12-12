# Quantify Heatmap

Quantify the area of a binary heatmap to analyze the animal percentage traveled area.
It counts the black pixels from a given binary image. 

--8<-- "excel-functions.md"

## Interface
![Interface image](img/gui-qnt-bin.png){ width="400em" }

## Input Binary Heatmap Image
The binary heatmap image to be processed. Ideally, generated with [Binary Heatmap Image](../processing/heatmap-binary.md).

!!! Note "Image Input"
    This plugin also accepts the current open image as input. In that case, leave this field blank and ensure you have the image opened in Fiji **before** running the plugin.


## Display plots
* **Bar Chart**: Simple chart to represent area percentage.
![Bar Chart image](img/output-qnt-bin.png){ width="400em" }

## Create sequential ROIs
Opens the [Create sequential ROIs](../tools/create-rois.md) plugin window.
