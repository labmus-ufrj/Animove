# Image Calculator

Performs a mathematical operation on a video and an image. Similar to ImageJ's built-in one (without the need to load the video to RAM).


## Interface
![Interface image](img/gui-image-calc.png){ width="400em" }

--8<-- "input-video.md"

--8<-- "open-frame.md"

## Input Image
Image to perform the operation.
Any image format supported by [OpenCV's imread](https://docs.opencv.org/4.x/d4/da8/group__imgcodecs.html#gaffb68fce322c6e52841d7d9357b9ad2d) can be used.
!!! warning "Image and Video Mismatch"
    This image and the input video need to be in the same resolution, bit depth and channel count.
It's recommended to only use images generated from this plugin's Z-Project function. 

Notably, **32-bit images aren't supported**.

--8<-- "output-file.md"

--8<-- "output-formats.md"

--8<-- "dont-save.md"

--8<-- "invert-before.md"

## Operation
Mathematical operation to be performed on the inputs. Same effect as the built-in ImageJ ones.

--8<-- "start-end-frame.md"

--8<-- "preview.md"