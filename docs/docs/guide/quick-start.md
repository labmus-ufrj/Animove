# Quick Start

Animove is displayed in the upper-right area of Fiji's menu bar.

![Menu bar image](img/menu-bar.png){ width="400em" }

File requirements are available in each function description. 


## Testing files
[Testing video files](https://drive.google.com/drive/u/1/folders/1qrz53eYvl06PCNzTaGLvaBCJtKP0Ubd8) are available.
Notably:

- Adults
    - Gutter/Track (short and long versions)
    - Plus Maze
    - Tank (sideways recording)
- Embryos
    - Six-well plate (short and long versions)

These may be used to experiment on the plugin's capabilities or simply to get used to the workflows.

[//]: # (todo: include a proper license for this data)
**The data made available is property of the authors and should not be distributed in any way. Usage outside the plugin's scope is not allowed.**

## Grayscale Converting Disclaimer
All RGB videos get converted to grayscale for processing. All image channels will get merged into a single one.

The conversion is [handled by OpenCV](https://docs.opencv.org/4.12.0/de/d25/imgproc_color_conversions.html), using the proportions: 0.114 * B + 0.587 * G + 0.229 * R

These are the same proportions ImageJ uses in [AVI_Reader](https://github.com/imagej/ImageJ/blob/master/ij/plugin/AVI_Reader.java) and ColorProcessor conversions.

