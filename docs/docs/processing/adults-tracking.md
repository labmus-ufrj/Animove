# Adults Tracking

Pre-process the input video to make it suitable for Trackmate analysis.
These steps include image inversion, as Trackmate algorithm recognizes white spots in a dark background.
It uses Top-Hat processing to subtract the background and adjusts brightness and contrast.

A TrackMate tutorial can be found [here](https://imagej.net/plugins/trackmate/tutorials/getting-started).

!!! Warning "FPS reduction"
    We found 10 fps to be suitable to track adult fish movement. It's possible to change the framerate using [this plugin's FFmpeg tool](../tools/ffmpeg.md)


## Interface
![Interface image](img/gui-adults-tracking.png){ width="400em" }

--8<-- "input-video.md"

--8<-- "output-file.md"

--8<-- "output-formats.md"

--8<-- "start-end-frame.md"

--8<-- "preview.md"
