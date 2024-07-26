# TCM - Train Cam Monitor


## Description



## License



## TODO

MVP:
+ Setup: Android project w/ working JavaCV JNI libs.
+ Setup: JavaCV FFmpegFrameGrabber working with target RTSP cameras.
+ App: Refactor FFmpegFrameGrabber usage into GrabberThread.
+ App: Add FpsMeasurer to display FPS performance.
+ App: Prefs Activity to enter camera URLs.
+ App: Ability to display JavaCV frame as Android Bitmap.
+ App: Structure Main Activity w/ 2 bitmap views + 2 FPS measurers, Start/Stop.
- Main: Pause grabber threads on activity pause, restore automatically.
- App: Autostart on boot.
- Main: Ability to rotate bitmaps, zoom (fill vs fit), and offset. Configure via prefs.
- Admin: Fill readme
- Admin: Deploy MVP.

Phase 2 (optional):
- Use OpenCV to detect motion.
- Highlight videos w/ detected motion.
- Limit detection to specific part of recorded frame.

~~
