# Calculate Score Gradient

## XML File
A XML file output from Trackmate that contains spots data. Ideally, use the full XML output or the tracks XML export. Those options allow for separate tracks as outputs, while the simple spots XML will average out all the spots in the video as a single track.

## Source Video
The source video used for tracking. Ideally not the processed one, so you can set the limits better.

## Fix Missing Spots
When tracking with Trackmate, not all frames in a video will have spots. That's because your target might not be visible in those frames. It's usually not moving as well. An uneven number of frames will create an unreliable score overall. This option fixes the missing spots by using an available adjacent spot and copying its coordinates.

There's no prejudice in setting this to `True` if you have a perfect tracking, with spots in all frames.
