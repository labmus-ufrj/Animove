# Quick Start

## Grayscale Converting Disclaimer
All RGB videos get converted to grayscale for processing. All image channels will get merged into a single one.

The conversion is [handled by OpenCV](https://docs.opencv.org/4.12.0/de/d25/imgproc_color_conversions.html), using the proportions: 0.114 * B + 0.587 * G + 0.229 * R

These are the same proportions ImageJ uses in AVI_Reader and ColorProcessor conversions.


## Testing

- [7dpf test video](https://drive.google.com/file/d/165CsE9vqNr6_5uSnPh1uESeEtDFdsUrw/view?usp=drive_link)
- [Adults test video](https://drive.google.com/file/d/13x4JdhUOuyM2NI-FVnHvSVZfZYuMWC7W/view?usp=sharing)