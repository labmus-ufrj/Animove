# Binary Heatmap Image

Generates a binary heatmap image. Processing steps:

* **Heatmap Generation** Iterates through the video frames, performing an Otsu automatic threshold to binarize the frames, then aggregates them using a minimum Z-Projection.


* **Background Reference Creation:** Performs a second iteration over the frames to calculate the Average Z-projection of the raw footage.


* **Final Composition:** Binarizes and inverts the generated average image (using the same method), then adds it mathematically to the binary heatmap to produce the final result.

## Interface
![Interface image](img/gui-binary-heatmap.png){ width="400em" }

## Expected Output

![Expected output image](img/output-binary-heatmap.png){ width="400em" }

[How to reproduce this?](../guide/reproducibility.md#binary-heatmap-image){ .md-button }

{% include "input-video.md" %}

{% include "open-frame.md" %}

{% include "output-image.md" %}

{% include "dont-save.md" %}

{% include "start-end-frame.md" %}

{% include "preview.md" %}