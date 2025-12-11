# Adults Tracking

Pre-process the input video to make it suitable for Trackmate analysis. The steps include image inversion, as Trackmate algorithm recognizes white spots in a dark background. It uses Top-Hat processing to subtract the background and adjusts brightness and contrast. The plugin also makess a substack with a 3-frame interval and opens the TrackMate plugin. 
The TrackMate tutorial can be found [here](https://imagej.net/plugins/trackmate/tutorials/getting-started).

--8<-- "input-video.md"

--8<-- "output-file.md"

--8<-- "output-formats.md"

--8<-- "start-end-frame.md"

--8<-- "preview.md"


<!--
run("Invert", "stack");
perform a Morphological Top-Hat () subtract background

ajusteThreshold(threshold);
ajusteMinMaxAuto(2);
run("Apply LUT");

run("Grays");
run("Median...", "radius=3 stack");

run("Z Project...", "projection=[Average Intensity]");
avgID = getImageID();

imageCalculator("Subtract create stack", sliceID, avgID);
cleanBgID = getImageID();

run("Make Substack...", "slices=1-"+nSlices+"-3"); // we wont always have 1k frames here
substackID = getImageID();
rename(ogName + " - " + interval);

ajusteMinMaxAuto(2);
run("Apply LUT");

selectImage(sliceID);
close();
selectImage(avgID);
close();
selectImage(cleanBgID);
close();

selectImage(substackID);
run("HiLo");
roiManager("select", 0);
run("TrackMate");
-->
