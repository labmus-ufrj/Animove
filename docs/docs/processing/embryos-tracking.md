# Embryos Tracking

Pre-process embryos video to make it suitable for Trackmate analysis. The steps include frames duplication and inversion, as Trackmate algorithm recognizes white spots in a dark background, and subtract the average projection of the video. The TrackMate plugin is open in the sequence. TrackMate tutorial can be found in https://imagej.net/plugins/trackmate/tutorials/getting-started

--8<-- "input-video.md"

--8<-- "output-file.md"

--8<-- "output-formats.md"

--8<-- "start-end-frame.md"

--8<-- "preview.md"



<!--
mesmo processo de criar um heatmap:

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
-->
