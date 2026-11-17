# Offline shader harness (optional)

Re-implements the renderer's pass structure in WebGL2 so the GLSL can be compiled and
eyeballed on a desktop, and tuned without a phone.

    npm i playwright && npx playwright install chromium
    # put a few test photos next to harness.js and list them in `images` (or IMGS=a.jpg,b.jpg)
    node harness.js out all 0        # all styles, live quality  (arg 4 = 1 for photo quality)
    python3 sheet.py out             # contact sheets (needs Pillow + same image names)
    python3 genpresets.py            # regenerate PresetData.kt from presets.json

`presets.json` is the single source of truth for all built-in presets and Railway palettes;
`genpresets.py` turns it into `filters/PresetData.kt`.
