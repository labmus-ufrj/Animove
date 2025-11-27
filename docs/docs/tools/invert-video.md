# Invert Video

Uses OpenCV and FFmpeg to invert each frame, allowing faster operation without the need of load the whole video into memory. Done as a [bitwise NOT](https://docs.opencv.org/4.x/d0/d86/tutorial_py_image_arithmetics.html) operation.

## Interface
 <img width="514" height="360" alt="image" src="https://github.com/user-attachments/assets/e4c31c08-8754-4250-ae16-22bf799f762b" />

## Input Video
Any videos supported by ffmpeg. This includes most video formats in existence. Notably, one exclusion: AVI videos saved from ImageJ with compression set to "None" (codec rawvideo) will not work.

## Output Formats
The user can choose between AVI, TIFF or MP4.

## Don't save, open in ImageJ instead
Files won't be saved to the specified Output Folder. Instead, they'll be opened in ImageJ.

## Initial and last frame
Select the frame number to start and end of the processing. Inclusive. Can be visualized as opening the video in ImageJ and looking at the slice number.
To automatically take the last frame of the video type 0 in the 'End frame' option.

The plugin shows a preview of the function, using the first 10 frames.
