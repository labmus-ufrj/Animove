# Performance Test

Used to test the plugin's performance. Creates a video with the specified dimensions and length set to the product of them. 

Each video frame has one more white pixel than the last one. Pixels are filled in left to right and up to bottom.

!!! warning "Avoid Big Dimensions"
    Only use small resolutions.
    Even using a relatively low resolution such as VGA (800x600) will create a video with 480,000 frames. That's a 4,5 hour-long AVI video, at 30 fps, saved with low compression. This will use A LOT of HDD space. And, most importantly, **it won't fit in your computer's RAM** to be processed by ImageJ's built-in Z-project function.

The video is saved as a temp file; then the operation Z-Project (Max intensity) is run, both with the ImageJ's built-in tool and this plugin's implementation.

The time to complete these operations is displayed.
