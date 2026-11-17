import json
p = json.load(open('presets.json'))
K = {'strength':'STRENGTH','saturation':'SATURATION','contrast':'CONTRAST','brightness':'BRIGHTNESS','shadow':'SHADOW',
     'highlight':'HIGHLIGHT','levels':'LEVELS','edge':'EDGE','thickness':'THICKNESS','smoothing':'SMOOTHING','grain':'GRAIN',
     'warmth':'WARMTH','vignette':'VIGNETTE','pixel':'PIXEL','glow':'GLOW','chunk':'CHUNK','grade':'GRADE'}
def f(v): return repr(float(v)) + 'f'
def pairs(d): return ', '.join(f'P.{K[k]} to {f(v)}' for k, v in d.items())
out = ['// GENERATED from presets.json by genpresets.py - the same table drives the offline shader test harness.',
       'package com.stylecam.filters', '', 'import com.stylecam.models.FilterParams', 'import com.stylecam.models.P', 'import com.stylecam.models.Preset', '',
       'internal object PresetData {', '']
for st in ['railway', 'cel', 'graphic', 'idleon']:
    d = p[st]
    out.append(f'    val {st.upper()}_DEFAULTS: FilterParams = FilterParams(mapOf({pairs(d["defaults"])}))')
    out.append(f'    val {st.upper()}: List<Preset> = listOf(')
    items = []
    for pr in d['presets']:
        base = f'{st.upper()}_DEFAULTS'
        if pr['v']:
            base += f'.withAll({pairs(pr["v"])})'
        base += f'.copy(palette = {pr["palette"]})'
        items.append(f'        Preset("{pr["name"]}", {base})')
    out.append(',\n'.join(items))
    out.append('    )\n')
out.append('    /** Railway poster ink palettes: 8 colours each, 0xRRGGBB. */')
out.append('    val RAILWAY_PALETTES: List<IntArray> = listOf(')
out.append(',\n'.join('        intArrayOf(' + ', '.join('0x' + h for h in pal) + ')' for pal in p['palettes']))
out.append('    )')
out.append('}')
open('../../app/src/main/java/com/stylecam/filters/PresetData.kt', 'w').write('\n'.join(out) + '\n')
pass
print('generated')
