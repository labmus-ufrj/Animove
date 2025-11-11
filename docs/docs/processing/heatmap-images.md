# Generate Heatmap Images

<!--
info: 
esse é o fluxograma dessa macro:
selectImage(fullStackID);
run("Duplicate...", "duplicate range="+intervals[i]);
sliceID = getImageID();
selectImage(sliceID);

run("Invert", "stack");
run("Z Project...", "projection=[Average Intensity]");
avrID = getImageID();

imageCalculator("Subtract create stack", sliceID, avrID);
run("Invert", "stack");
noBgID = getImageID();
rename(intervals[i]);

selectImage(sliceID);
close();
selectImage(avrID);
close();

brilhoContraste(noBgID);


run("Z Project...", "projection=[Sum Slices]");
run("Invert");
sumID = getImageID();

close(sliceID);
selectImage(sumID);
run("16-bit");

ajusteMinMaxAuto(1);
run("Apply LUT");
-->


## Interface

![Interface image](img/gui-heatmapImages.png){ width="300em" }

## Expected Output

![Expected output image](img/output-heatmapImages.png){ width="400em" }

## Input Video
[Refer to this plugin's Z-Project tool](../tools/z-project.md#input-video)

## Output Folder
Where output images will be saved with `.tif` format.

## Convert to Grayscale
[Refer to this plugin's Z-Project tool](../tools/z-project.md#convert-to-grayscale)

## Invert before operation
Its expected that the background has LOW values and your targets have HIGH values. If your input video don't match that, activate this option.

[Refer to this plugin's Z-Project tool](../tools/z-project.md#invert-before-operation)

## Lookup Table
The LUTs available are the ImageJ's built-in ones (found at Image → Lookup Tables). Leave as "Don't change" for RGB images if Convert to Grayscale is not checked.

## Don't save, open in ImageJ instead
Files won't be saved to the specified Output Folder. Instead, they'll be opened in ImageJ.

## Intervals
The checkboxes either activate or deactivate the relative interval. Frame numbers should be presented as `initialFrame-endFrame`, both being inclusive, like ImageJ's built-in **Image → Duplicate** feature, or [this plugin's Z-Project tool](../tools/z-project.md#initial-frame).  
An empty interval field results in a skipped interval, while a malformed one (without a `-` separator) throws an error.

