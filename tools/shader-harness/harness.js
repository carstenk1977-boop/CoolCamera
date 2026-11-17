// WebGL2 re-implementation of FilterRenderer's pass structure, used only to
// validate/tune the shaders. Usage: node harness.js <outdir> [style] [photoFlag]
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const SH = require('path').join(__dirname, '../../app/src/main/assets/shaders/') ;
const rd = f => fs.readFileSync(SH + f, 'utf8');
const presets = JSON.parse(fs.readFileSync('presets.json', 'utf8'));
const outDir = process.argv[2] || 'out'; fs.mkdirSync(outDir, { recursive: true });
const onlyStyle = process.argv[3] && process.argv[3] !== 'all' ? process.argv[3] : null;
const photo = process.argv[4] === '1';
const images = process.env.IMGS ? process.env.IMGS.split(',') : ['building.jpg', 'lena.jpg', 'fruits.jpg'];
const onlyPreset = process.env.PRESET || null;

const sources = {
  header: '#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n',
  vertex: rd('vertex.glsl'), common: rd('common.glsl'), camera: rd('camera.frag'),
  composite: rd('composite.frag'), smooth: rd('smooth.frag'),
  railway: rd('railway.frag'), cel: rd('cel.frag'), graphic: rd('graphic.frag'), idleon: rd('idleon.frag')
};
const jobs = [];
for (const st of ['railway', 'cel', 'graphic', 'idleon']) {
  if (onlyStyle && onlyStyle !== st) continue;
  for (const p of presets[st].presets.filter(x => !onlyPreset || x.name === onlyPreset))
    for (const img of images)
      jobs.push({ style: st, preset: p.name, palette: p.palette, params: Object.assign({}, presets[st].defaults, p.v), img });
}
const imgData = {};
for (const i of images) imgData[i] = 'data:image/jpeg;base64,' + fs.readFileSync(i).toString('base64');

(async () => {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--use-gl=angle'] });
  const page = await browser.newPage();
  page.on('console', m => console.log('[page]', m.text()));
  await page.setContent('<canvas id=c></canvas>');
  const results = await page.evaluate(async ({ sources, jobs, imgData, palettes, photo }) => {
    const out = []; const errors = [];
    const canvas = document.getElementById('c');
    const gl = canvas.getContext('webgl2', { preserveDrawingBuffer: true, antialias: false });
    if (!gl) return { errors: ['no webgl2'], out };
    const compile = (type, src, name) => {
      const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) { errors.push(name + ': ' + gl.getShaderInfoLog(s)); return null; }
      return s;
    };
    const build = (frag, name) => {
      const vs = compile(gl.VERTEX_SHADER, sources.header + sources.vertex, name + '.vert');
      const fs_ = compile(gl.FRAGMENT_SHADER, frag, name + '.frag');
      if (!vs || !fs_) return null;
      const p = gl.createProgram(); gl.attachShader(p, vs); gl.attachShader(p, fs_); gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) { errors.push(name + ' link: ' + gl.getProgramInfoLog(p)); return null; }
      p._loc = {}; return p;
    };
    const L = (p, n) => (n in p._loc) ? p._loc[n] : (p._loc[n] = gl.getUniformLocation(p, n));
    const prog = {
      camera: build(sources.header + sources.camera, 'camera'),
      composite: build(sources.header + sources.composite, 'composite'),
      smooth: build(sources.header + sources.common + sources.smooth, 'smooth')
    };
    for (const s of ['railway', 'cel', 'graphic', 'idleon']) prog[s] = build(sources.header + sources.common + sources[s], s);
    if (Object.values(prog).some(x => !x)) return { errors, out };

    const vbo = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0); gl.enableVertexAttribArray(0);

    const tex = () => { const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE); return t; };
    const fbo = (w, h) => { const t = tex(); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      const f = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, f);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
      return { t, f, w, h }; };
    const bindTex = (u, t) => { gl.activeTexture(gl.TEXTURE0 + u); gl.bindTexture(gl.TEXTURE_2D, t); };
    const keymap = { strength: 'uStrength', saturation: 'uSaturation', contrast: 'uContrast', brightness: 'uBrightness', shadow: 'uShadow', highlight: 'uHighlight', levels: 'uLevels', edge: 'uEdge', thickness: 'uThickness', smoothing: 'uSmoothing', grain: 'uGrain', warmth: 'uWarmth', vignette: 'uVignette', pixel: 'uPixel', glow: 'uGlow', chunk: 'uChunk', grade: 'uGrade' };
    const lerp = (a, b, t) => a + (b - a) * t;
    const radius = (style, p, ph) => {
      let s = p.smoothing; if (style === 'idleon') s = Math.min(1, Math.max(0, p.smoothing * 0.6 + p.chunk * 0.6));
      return Math.round(ph ? lerp(2, 6, s) : lerp(1, 3, s));
    };
    const imgCache = {};
    const loadImg = src => imgCache[src] || (imgCache[src] = new Promise(r => { const i = new Image(); i.onload = () => r(i); i.src = src; }));
    const t0 = performance.now();
    for (const j of jobs) {
      const img = await loadImg(imgData[j.img]);
      const aspect = img.width / img.height;
      const longSide = photo ? 1400 : 640;
      let w, h; if (aspect >= 1) { w = longSide; h = Math.round(w / aspect); } else { h = longSide; w = Math.round(h * aspect); }
      canvas.width = w; canvas.height = h;
      const raw = tex(); gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
      const base = fbo(w, h), smooth = fbo(w, h), filt = fbo(w, h);
      gl.disable(gl.DEPTH_TEST);
      // pass 0
      gl.bindFramebuffer(gl.FRAMEBUFFER, base.f); gl.viewport(0, 0, w, h);
      let p = prog.camera; gl.useProgram(p); bindTex(0, raw); gl.uniform1i(L(p, 'uRaw'), 0); gl.uniform1i(L(p, 'uRot'), 0);
      gl.uniform1f(L(p, 'uMirror'), 0); gl.uniform1f(L(p, 'uViewAspect'), aspect); gl.uniform1f(L(p, 'uImgAspect'), aspect);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      const step = Math.min(Math.max(Math.min(w, h) / 540, 0.75), 8);
      const common = (pp) => { gl.uniform1i(L(pp, 'uBase'), 0); gl.uniform1i(L(pp, 'uSmooth'), 1);
        gl.uniform2f(L(pp, 'uTexel'), 1 / w, 1 / h); gl.uniform1f(L(pp, 'uStep'), step); gl.uniform1f(L(pp, 'uPhoto'), photo ? 1 : 0); gl.uniform1f(L(pp, 'uSeed'), 0.37); };
      // pass 1
      gl.bindFramebuffer(gl.FRAMEBUFFER, smooth.f); gl.viewport(0, 0, w, h);
      p = prog.smooth; gl.useProgram(p); bindTex(0, base.t); common(p); gl.uniform1i(L(p, 'uRadius'), radius(j.style, j.params, photo));
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      // pass 2
      gl.bindFramebuffer(gl.FRAMEBUFFER, filt.f); gl.viewport(0, 0, w, h);
      p = prog[j.style]; gl.useProgram(p); bindTex(0, base.t); bindTex(1, smooth.t); common(p);
      for (const k in keymap) gl.uniform1f(L(p, keymap[k]), j.params[k] === undefined ? 0 : j.params[k]);
      gl.uniform1i(L(p, 'uPalette'), j.palette); gl.uniform1i(L(p, 'uPalCount'), 8);
      const pal = palettes[Math.min(j.palette, palettes.length - 1)].flatMap(h6 => [parseInt(h6.slice(0, 2), 16) / 255, parseInt(h6.slice(2, 4), 16) / 255, parseInt(h6.slice(4, 6), 16) / 255]);
      gl.uniform3fv(L(p, 'uPal'), new Float32Array(pal));
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      // composite
      gl.bindFramebuffer(gl.FRAMEBUFFER, null); gl.viewport(0, 0, w, h);
      p = prog.composite; gl.useProgram(p); bindTex(0, base.t); bindTex(1, filt.t); gl.uniform1i(L(p, 'uOrig'), 0); gl.uniform1i(L(p, 'uFilt'), 1);
      gl.uniform1i(L(p, 'uMode'), 0); gl.uniform1f(L(p, 'uSplit'), 0.5); gl.uniform1f(L(p, 'uViewW'), w);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      out.push({ style: j.style, preset: j.preset, img: j.img, w, h, png: canvas.toDataURL('image/png') });
      [raw, base.t, smooth.t, filt.t].forEach(t => gl.deleteTexture(t));
    }
    errors.push('render ms: ' + Math.round(performance.now() - t0) + ' for ' + jobs.length + ' jobs');
    return { errors, out };
  }, { sources, jobs, imgData, palettes: presets.palettes, photo });
  console.log(results.errors.join('\n'));
  for (const r of results.out) {
    fs.writeFileSync(path.join(outDir, `${r.style}__${r.preset}__${r.img.replace('.jpg', '')}.png`), Buffer.from(r.png.split(',')[1], 'base64'));
  }
  console.log('wrote', results.out.length, 'images');
  await browser.close();
})();
