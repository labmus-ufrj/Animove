# Embryos Tracking

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