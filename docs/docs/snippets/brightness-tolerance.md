## Brightness Tolerance (%)

Percentage of the ROI pixels allowed to sit above the brightness upper bound.

The brightness adjustment stretches the accumulated signal over the available range so the
result is not dominated by the darkest and brightest pixels inside the ROI. Bright pixels,
however, are often single saturated spots: a sensor hit, a reflection, or a compressed video
artifact. When the brightest pixel of the ROI is one of those, it becomes the upper bound and
every other pixel gets squeezed towards black, which is what makes the adjustment appear to
do nothing.

This parameter sets an upper bound that ignores that kind of outlier. The bound becomes the
`(100 - tolerance)`-th percentile of the ROI pixels, so `0.01` means "the 99.99th percentile":
only the 0.01% brightest pixels of the ROI are disregarded. Since the percentile is taken over
the ROI itself, the number of tolerated pixels scales with the ROI size, from a handful of
pixels in a small ROI to a few hundreds in a large one.

Pixels above the computed bound are not discarded: they saturate at the top of the range,
exactly like a regular brightness and contrast upper limit would.

`0` disables the tolerance and uses the plain ROI maximum.
