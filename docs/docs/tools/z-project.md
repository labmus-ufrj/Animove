# Z-Project

## Input Video
Any videos supported by ffmpeg. This includes most video formats in existence. Notably, one exclusion: AVI videos saved from ImageJ with compression set to "None" (codec rawvideo) will not work.

## Output Image
Use `.tiff` format to uphold 32bit quality for certain outputs. `.png` and other formats may be used, at your own risk.

## Processing Mode
The operations available mimic ImageJ's built-in **Image → Stacks → Z project** operations:

* Darkest (Min)
* Brightest (Max)
* Average
    * Always saved as 32-bit
* Sum
    * Always saved as 32-bit

## Convert to Grayscale
If activated, will merge all image channels to a single one (usually this is the desirable output).
If not, the resulting image will have separate channels, which may or may not be interpreted as RGB by your software.

The conversion is [handled by OpenCV](https://docs.opencv.org/4.12.0/de/d25/imgproc_color_conversions.html), using the proportions: 0.114 * B + 0.587 * G + 0.229 * R

## Invert before operation
Invert each frame before performing the operation.

## Initial Frame
One-indexed frame number. Inclusive. Can be visualized as opening the video in ImageJ and looking at the slice number.

## End Frame
One-indexed frame number. Inclusive. Can be visualized as opening the video in ImageJ and looking at the slice number.

## Open processed image
Choose whether to open the processed image in ImageJ automatically after saving it as the specified Output Image.