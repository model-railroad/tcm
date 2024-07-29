# TCM - Train Cam Monitor

## What is it

"TCM" (aka the "Train Cam Monitor") is a simple Android application that
connects to two RTSP cameras and displays their video feed on-screen.

The application is tailored to view specific train tracks on the Randall Museum
Model Railroad. In our case, the goal is to display the mainline that is on the
other side of the mountain, which operators typically cannot see when they are
located at the main operating yard. In this case, a tablet is mounted to the fascia
of the layout where the operators are and that allows them to view the track on the
other side of the layout.

The same system should be useful for example to view hidden staging yards.

There are a number of Android application that already allow one to view multiple
cameras RTSP feeds, and so there's no novelty in that.

The following features are unique to TCM:

- It's designed to work with no user interaction. In our case, our operators are
  not techies and we can't expect them to boot a tablet and run a specific app.
  Instead, the TCM application is designed to automatically start when the tablet
  boots.
- A wake lock is used to keep the screen from turning off when the videos are shown.
- A WiFi lock is used to prevent the WiFi from disconnecting during use.
- A specific WiFi SSID can be designed and the software makes the tablet connect to that automatically.
- The application disconnects from the video cameras automatically when the tablet power is off.
  In our case, the museum's layout is only powered from 9am to 5pm. When the layout's power is off,
  the tablet automatically stops processing video and enters a sleep mode (to preserve battery)
  and the video connection automatically restarts when power is turned back on the next day.
  This is designed so that layout operations and museum staff have no need to bother turning on
  the tablet during normal business days.
- The video feeds can be rotated, zoomed, and panned. These are recorded in settings.
- The application can detect motion on specific parts of the video, in order to highlight it
  to the operators and call their attention when specific part of the track shows train movement.


## Implementation Notes

The app is designed to run on a 10-inch tablet in landscape mode.
It should work on other devices, however no effort is being made (so far) in optimizing
the screen layout for different form factors.

To achieve the desired "unattended" behavior, the tablet is typically configured as
follows in the Android system settings:

- Display > Screen Timeout : 15 seconds
- Display > Screen Saver : None
- Security > Screen Lock : None
- Developer Options > Keep Screen On When Charging

The tablet is to be treated as an "appliances" -- it should only contain this application,
and should be considered unsecured. This can be achieved two ways:

- Do not have a specific account on it. E.g. add an account to install the TCM app via Google Play
  then __remove__ the account (Android Settings > Accounts).
- Or alternatively, use a dedicated throw-away gmail account just for this purpose and then
  make sure that the Google Play account has a PIN to install paid apps (GP does not support
  a PIN to install free apps, unfortunately).

Note that this is not a locked Kiosk mode: Anyone with access to the tablet can change anything.
Publicly visible devices at the Randall Museum Model Railroad are consequently placed behind a
locked glass.


### Start at Boot / Home Launcher

On Android API 21 up to 28 (Android 5/L TO 9/P), the implementation of the "Start at Boot" feature
uses the typical Android pattern:

- A "Boot Receiver" is declared in the app's AndroidManifest to respond to `BOOT_COMPLETED`.
- This requires the `RECEIVE_BOOT_COMPLETED` permission.
- The Boot Receiver class checks the app's preferences. If the preference to start
  at boot is activated, the MainActivity is started using an intent.

This mechanism however is no longer possible starting with API 29 (Android 10/Q).
Instead, on these devices, the application can be set as the default home launcher via the
preferences. This is done using the `RoleManager` which only allows the application to set
itself as the home launcher app by presenting a chooser dialog to the user.
There is no way to use the same `RoleManager` to "reset" the home launcher to the default
tablet app. Instead, when the preference is unchecked in TCM, the Android Settings "Default App"
screen is shown, which allows the user to change the launcher app.

As indicated in the previous section, even when the app is set as a home launcher, the user
can still "escape" the app by accessing the settings from the drop-down notification bar.
This is not a locked Kiosk mode: Anyone with access to the tablet can change anything.


### Foreground/Background Behavior

The [Randall Train Automation Controller](https://www.alfray.com/trains/randall/rtac.html)
software is a similar Android application that displays the state of the automation to
museum visitors and train operators. One thing this application's implementation does is
use a background service to maintain a constant connection with the main train automation computer.

We are not using this here. All behavior happens only when the main activity is in foreground.

Instead, all the behavior logic is tied to the `MainActivity`:

- When the activity starts, the power state is checked, and the video streaming and processing
  can start if and when the tablet is properly powered.
- When the activity is active and the tablet is on battery power, video streaming and processing
  pauses, and allow the tablet to sleep to save on battery.
- When the activity is paused, all processing is also stopped, including power state monitoring.



### Wake/Wifi Lock and Power Saving Behavior

The goal is to have the tablet (almost) always turned on. The layout at the Randall Museum
Model Railroad is turned on by the museum staff at 9am and turned off at 5pm. The tablet is
powered by a switched outlet connected to the layout's main power. Consequently the tablet
will only be charging during that time, and thus video processing should only occur when
the tablet is powered.

To monitor the charging state, Android provides the `ACTION_BATTERY_CHANGED` intent and
the `BatteryManager.isCharging()` function (only API 23 and above).
We do not need any AndroidManifest broadcast to monitor the charging state outside of the
application's lifecycle.
This can be periodically checked using a `PeriodicWorkRequest` and the `WorkManager`.

The behavior should be:

- When the main activity starts, start a PeriodicWorkRequest to check the power state.
- When the main activity is paused, stop the PeriodicWorkRequest.
- When power is on:
  - Acquire a wake lock, to prevent the display from sleeping.
  - Attempt to connect to the desired WiFi network, if specified (only on API < 29).
  - Acquire a WiFi lock, to keep the WiFi active.
  - Start video streaming.
- When power is off, or the main activity is paused:
  - Stop video streaming.
  - Release the WiFi lock. WiFi should disconnect/enter sleep mode as controlled by Android.
    (we do not specifically disconnected from the wifi network).
  - Release the wake lock, to ensure the screen can dim and enter sleep mode.

Starting with Android 10 (API 29), it is no longer possible to use the WiFiManager
feature to force enable the wifi and automatically select an SSID to connect to.



## License

__TCM__ is licensed under the __GNU GPL v3 license__.

    Copyright (C) 2023-2024 alf.labs gmail com,

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

The full GPL license is available in the file "LICENSE-gpl-3.0.txt".


## TODO

MVP:
+ Setup: Android project w/ working JavaCV JNI libs.
+ Setup: JavaCV FFmpegFrameGrabber working with target RTSP cameras.
+ App: Refactor FFmpegFrameGrabber usage into GrabberThread.
+ App: Add FpsMeasurer to display FPS performance.
+ App: Prefs Activity to enter camera URLs.
+ App: Ability to display JavaCV frame as Android Bitmap.
+ App: Structure Main Activity w/ 2 bitmap views + 2 FPS measurers, Start/Stop.
+ App: Configure number of cameras (1 or 2) via prefs.
+ Main: Pause grabber threads on activity pause, restore automatically.
+ Main: Ability to rotate bitmaps, zoom (manual), and offset. Configure via prefs.
+ App: Autostart on boot.
+ App: Replace Home Launcher.
+ App: Hide nav bar, overlap translucent status bar
+ Main: Restructure video streaming to be controlled by the activity lifecycle (starts/pause).
+ App: Monitor power state. Use it to control video streaming.
+ App: Add support for Wake Lock and WiFi lock.
+ App: Handle camera lost + reconnection
+ App: Debug display preference.
+ Admin: Fill readme
- Admin: Deploy MVP.

Phase 2 (optional):
- Force activity display orientation (via prefs).
- Gamma correction on input images (via OpenCV LUT f.ex.)
- Reintroduce preference to fill/fit in image view.
- Use OpenCV to detect motion.
- Highlight videos w/ detected motion.
- Limit detection to specific part of recorded frame.

~~
