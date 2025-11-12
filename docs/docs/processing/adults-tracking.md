# Adults Tracking

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