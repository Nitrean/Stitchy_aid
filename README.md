# Stitchy Aid

A free, open-source Android app that helps people cross stitch. It is made for
tablets (and works on foldables), and designed first for stitchers over 60:
big text, big buttons, high contrast, and it is always easy to go back.

## What it does so far (step 1)

- **One Import button.** Pick any file; the app works out what it is.
  Open Cross Stitch (`.oxs`) charts and Stitchy Aid projects open today.
  PDF, Pattern Maker (`.xsd`), PCStitch (`.pat`) and pictures are recognised and
  will be read in later steps.
- **Home screen** with one big "Continue stitching" button for the pattern you
  were last working on.
- **Chart viewer**: pinch to zoom, drag to move, row and column numbers, thick
  lines every 10 stitches, centre mark. Too-small stitches zoom in when tapped,
  instead of marking the wrong one.
- **Mark stitches** by tapping them. Unlimited **Undo**. Progress is saved
  automatically after every change.
- **Find your place**: "Welcome back" reminder of the colour you were on, and
  the last stitches you marked have an orange border.
- **Colours list** with what is left of each colour; tap one to show only that
  colour on the chart.
- **Lock** the chart so a resting hand can't move it. The screen stays on while
  stitching.
- **Never lost**: a labelled Back button and a Home button on every screen,
  confirmations in plain words, and a Bin that keeps removed patterns for 30 days.

The full feature plan is in the project spec; the remaining steps add PDF and
other formats, thread and shopping lists, photo-to-pattern conversion, art
tools, an editor and export.

## Building

- `core/` is plain Kotlin (chart model, file formats, colours, progress) and
  builds anywhere with a JDK: `./gradlew :core:test`
- `app/` is the Android app (Kotlin + Jetpack Compose). It needs the Android
  SDK: `./gradlew :app:assembleDebug`

Every pull request is built on GitHub Actions, and the installable debug APK is
attached to the run as `stitchy-aid-debug-apk`.

## Licence

Apache License 2.0, see [LICENSE](LICENSE). Third-party data is listed in
[NOTICE](NOTICE).
