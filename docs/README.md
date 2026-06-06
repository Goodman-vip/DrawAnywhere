<div align="center">
  <img src="../SVG Icon/pen-new-square-original.svg" width="96"/>
</div>

<h1 align="center">DrawAnywhere</h1>

<div align="center">
  <a href="https://f-droid.org/packages/com.shezik.drawanywhere/">
    <img src="https://img.shields.io/f-droid/v/com.shezik.drawanywhere" alt="Get it on F-Droid"/>
  </a>
  &nbsp;
  <a href="https://shields.rbtlog.dev/com.shezik.drawanywhere">
    <img src="https://shields.rbtlog.dev/simple/builders/com.shezik.drawanywhere" alt="RB shield"/>
  </a>
</div>

&emsp;English | [中文](README-zh-CN.md)

DrawAnywhere is an Android application that lets you draw on top of other apps.

![](../metadata/en-US/images/featureGraphic.png)

Or download the latest APK from the [Releases Section](https://github.com/DrawAnywhere/DrawAnywhere/releases/latest).

## 🆕 What's new since v1.2

After 7 months of silence, DrawAnywhere is back. Here's what changed:

- **Infinite canvas with viewport lock** — pan and zoom freely; three‑level lock (none, zoom only, all) with toolbar and HUD icons
- **New tools** — rectangle, ellipse, laser pointer (glow + auto‑fade), pixel eraser
- **Color system** — HSV color wheel, 18 preset swatches, recent colors, hex code input
- **Multi‑finger gestures** — pinch to zoom; double‑tap reset zoom, triple‑tap reset position (two‑finger); toggle passthrough or hide canvas (three‑finger)
- **Hover preview** — filled circle shows brush size while drawing, fades out smoothly
- **Second‑launch to close** — opening the app while the service is running closes it
- **Architecture rewrite** — hybrid Native Canvas + Jetpack Compose toolbar; cleaner touch dispatch, undo/redo, and input routing
- **Tests** — 38 instrumentation tests covering touch, gestures, viewport, and persistence

## A note on AI usage
Since v2.0, AI‑generated content has been introduced. The decision was finally made when the balance between project sustainability and personal life became a bit difficult to maintain. Still, we strive to provide good stability and ease of use, while maintaining code quality. As always, please do not hesitate to submit bug reports and feature requests so we can look into them together.

## 🎨 Features

**Drawing tools**
- Freehand pen, rectangle, and ellipse
- Laser pointer with glow effect (auto‑fades after 3 seconds)
- Pixel eraser for precise control; stroke eraser via stylus buttons
- Undo / redo up to 50 operations

**Color and appearance**
- HSV color wheel, preset swatches, recent colors, and hex code input
- Stroke width and opacity sliders

**Viewport and gestures**
- Infinite canvas with pinch to zoom
- Two‑finger double‑tap: reset zoom
- Two‑finger triple‑tap: reset position
- Three‑finger double‑tap: toggle touch passthrough
- Three‑finger triple‑tap: hide canvas
- Three‑level viewport lock (none / zoom only / all) from the toolbar

**Canvas and toolbar**
- Horizontal and vertical toolbar, draggable anywhere on screen
- Toolbar auto‑dims after 3 seconds of inactivity
- Hide canvas instantly, or pass touch events through to the app below
- Finger‑drawing toggle for stylus‑only mode

**Convenience**
- Quick Settings tile to toggle the service — tap again to close
- Opening the app a second time stops the service
- Hover circle shows brush preview when using finger or stylus

## ✨ Tutorial
- **Move the toolbar** — long‑press and drag it anywhere.
- **Lock your view** — tap the lock button on the toolbar to cycle through three levels: 🔓 unlock → 🔒 lock zoom → 🔒🔍 lock all (zoom and pan). The HUD shows your current lock state.
- **Draw with precision** — use two‑finger pinch to zoom in; double‑tap with two fingers to snap back to 100%. Triple‑tap with two fingers to recenter.
- **Clear and passthrough** — turn on `Clear canvas on hide` in Settings to wipe the canvas when you hide it (passthrough is also disabled automatically). Three‑finger double‑tap toggles passthrough on the fly.
- **Quick start** — disable `Open canvas on start` if you prefer the canvas hidden until you need it.

This app is in its early stage, feel free to open an issue if you encounter problems!

## 💌 Shoutouts
to [Akshay Sharma](https://github.com/akshay2211)'s [DrawBox](https://github.com/akshay2211/DrawBox) for inspirations,<br>
[480 Design](https://www.figma.com/@480design) and [R4IN80W](https://www.figma.com/@voidrainbow)'s [Solar Icons Set](https://www.figma.com/community/file/1166831539721848736/solar-icons-set) for the astounding app icon ([CC BY 4.0](../SVG%20Icon/LICENSE.md)),<br>
and [Mauro Banze](https://stackoverflow.com/a/66958772) & [Yannick](https://stackoverflow.com/a/65760080) for their Stack Overflow answers ([CC BY-SA 4.0](../app/src/main/java/com/shezik/drawanywhere/ToolbarLifecycleOwner.kt))!

Finally, thank [you](https://play.google.com/store/apps/details?id=com.kts.draw) for making your app subscription-based! You are my original motivation![^1]

[^1]: Despite being a large factor, this is only partially true. The other reason is that tablets running OneUI 4 do not come with such functionalities. If you are using OneUI 4 like I do, be sure to check out [Wallpaper Setter](https://github.com/shezik/WallpaperSetter)!
