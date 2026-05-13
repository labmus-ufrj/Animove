# Plot Spots

Extract spots position data from a given Trackmate file. Outputs a scatter plot and a results table.

[//]: # (TODO: finish this)
{% include "excel-functions.md" %}

## Interface
![Interface image](img/gui-spots-plotting.png){ width="400em" }

## XML File
Two possible files can be used here:

1. Trackmate's whole save file
    * Ideal option overall.
    * Mandatory if you only have spots and no tracks. In this case, spots will be considered as-is, without fixing missing spots.
    * **If there are any tracks, all spots outside them will get filtered out.**
2. Output of trackmate's `Export tracks to XML file` export action.
    * Only spots in tracks get saved in this file, so only them will be considered.

{% include "source-video-analysis.md" %}

## Output
* **Scatter Chart**: Simple chart to represent spot positions.
  ![Scatter Chart image](img/output-spots-plotting.png){ width="400em" }

[//]: # (TODO: this)
[How to reproduce this?](../guide/reproducibility.md#plot-spots){ .md-button }