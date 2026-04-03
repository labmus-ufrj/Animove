# Invert Video

Uses OpenCV and FFmpeg to invert each frame, allowing faster operation without the need to load the whole video into memory. Done as a [bitwise NOT](https://docs.opencv.org/4.x/d0/d86/tutorial_py_image_arithmetics.html) operation.

## Interface
![Interface image](img/gui-invert-video.png){ width="400em" }

{% include "input-video.md" %}

{% include "open-frame.md" %}

{% include "output-image.md" %}

{% include "output-formats.md" %}

{% include "dont-save.md" %}

{% include "start-end-frame.md" %}

{% include "preview.md" %}
