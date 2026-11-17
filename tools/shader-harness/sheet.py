import sys, json, glob, os
from PIL import Image, ImageDraw
presets = json.load(open('presets.json'))
outdir = sys.argv[1]
styles = sys.argv[2:] or ['railway','cel','graphic','idleon']
imgs = ['building','lena','fruits']
TW = 260
for st in styles:
    names = [p['name'] for p in presets[st]['presets']]
    cols = 1 + len(names)
    rows = len(imgs)
    cells = []
    sheet = None
    for r, im in enumerate(imgs):
        src = Image.open(f'{im}.jpg').convert('RGB')
        th = int(src.height * TW / src.width) if src.width>=src.height else TW
        tw = TW if src.width>=src.height else int(src.width*TW/src.height)
        row_imgs = [src.resize((tw, th))]
        for n in names:
            f = f'{outdir}/{st}__{n}__{im}.png'
            row_imgs.append(Image.open(f).convert('RGB').resize((tw, th)))
        if sheet is None:
            sheet = Image.new('RGB', (cols*TW, rows*TW+0), (20,20,20))
        for c, ii in enumerate(row_imgs):
            sheet.paste(ii, (c*TW, r*TW))
    d = ImageDraw.Draw(sheet)
    for c, n in enumerate(['ORIGINAL']+names):
        d.text((c*TW+6, 4), n, fill=(255,255,0))
    sheet.save(f'{outdir}/sheet_{st}.png')
    print(st, sheet.size)
