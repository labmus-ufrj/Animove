# Quick Start

Animove is displayed in the upper-right area of Fiji pannel.
ADD FIGURE
File requirements are available in each function description. 


## Testing files 

Testing video files are avaliable in the link below, including adults zebrafish in tank, plus maze or gutter and embryos in a 6 well plate.
- [Zebrafish testing files](https://drive.google.com/drive/u/1/folders/1qrz53eYvl06PCNzTaGLvaBCJtKP0Ubd8)

## Grayscale Converting Disclaimer
All RGB videos get converted to grayscale for processing. All image channels will get merged into a single one.

The conversion is [handled by OpenCV](https://docs.opencv.org/4.12.0/de/d25/imgproc_color_conversions.html), using the proportions: 0.114 * B + 0.587 * G + 0.229 * R

These are the same proportions ImageJ uses in AVI_Reader and ColorProcessor conversions.

