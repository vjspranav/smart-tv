/* House view: the flat as a live 3D model over Home. Loaded with import() the first time it's opened (home.js
   openHouse), so the remote never pays for three.js until then. Plan data: house-plan.json (feet; x east, y south);
   plain rules: house-logic.js.

   app: { snap, shown, change, openDevice, tv, logo, togglePower, setMode, buzz, toast, close } from home.js. */
import * as THREE from '/vendor/three/three.module.min.js';
import { RectAreaLightUniformsLib } from '/vendor/three/addons/lights/RectAreaLightUniformsLib.js';
import { EffectComposer } from '/vendor/three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from '/vendor/three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from '/vendor/three/addons/postprocessing/UnrealBloomPass.js';
import { ShaderPass } from '/vendor/three/addons/postprocessing/ShaderPass.js';
import { OutputPass } from '/vendor/three/addons/postprocessing/OutputPass.js';
import { OrbitControls } from '/vendor/three/addons/controls/OrbitControls.js';
import { wallPieces, reconcile, standing, isTap, loadPlace, savePlace, daylight, curtainPanels, walkGrid, findPath, slide } from '/house-logic.js';
import { buildModel, buildOutside, extraMaterials } from '/house-models.js';

const CLOSE = '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"/></svg>';
const H = 9, T = 0.45;            // wall height and thickness, feet
const EYE = 5.3, UPRIGHT = 0.55, HOLD = 450;   // walking eye height (ft), the 3D view's tilt (rad), press-and-hold (ms)

const MATERIAL = () => ({
  wall: new THREE.MeshStandardMaterial({ color: 0xece7df, roughness: 0.92 }),
  cap: new THREE.MeshStandardMaterial({ color: 0x2b3038, roughness: 0.8 }),
  glass: new THREE.MeshStandardMaterial({ color: 0x9cc7e6, roughness: 0.05, transparent: true, opacity: 0.22 }),
  rail: new THREE.MeshStandardMaterial({ color: 0xb9c3cc, roughness: 0.4, metalness: 0.4, transparent: true, opacity: 0.55 }),
  wood: new THREE.MeshStandardMaterial({ color: 0x6b4a33, roughness: 0.6 }),
  dark: new THREE.MeshStandardMaterial({ color: 0x24282f, roughness: 0.5 }),
  white: new THREE.MeshStandardMaterial({ color: 0xf3f3f1, roughness: 0.5 }),
  fabric: new THREE.MeshStandardMaterial({ color: 0x4a4f58, roughness: 0.95 }),
  curtain: new THREE.MeshStandardMaterial({ color: 0xc9b48a, roughness: 0.95 }),
  bed: new THREE.MeshStandardMaterial({ color: 0xd9d2c6, roughness: 0.95 }),
  counter: new THREE.MeshStandardMaterial({ color: 0x9fc7bd, roughness: 0.6 }),
  ceiling: new THREE.MeshStandardMaterial({ color: 0xf1ede6, roughness: 1 }),
  floor: {
    tile: new THREE.MeshStandardMaterial({ color: 0xd8ccb7, roughness: 0.28, metalness: 0.05 }),
    bed: new THREE.MeshStandardMaterial({ color: 0xcfc3ad, roughness: 0.4 }),
    bath: new THREE.MeshStandardMaterial({ color: 0x9ea6aa, roughness: 0.6 }),
    balcony: new THREE.MeshStandardMaterial({ color: 0x8f8b82, roughness: 0.8 }),
    kitchen: new THREE.MeshStandardMaterial({ color: 0xc9c0b0, roughness: 0.4 }),
    deck: new THREE.MeshStandardMaterial({ color: 0x4b3b2e, roughness: 0.7 }),
  },
});

const warmth = t => new THREE.Color().lerpColors(new THREE.Color(0xffc27a), new THREE.Color(0xdfeaff), t);   // 0 warm … 1 cool

function canvasSprite(w, h, draw, scale) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace;
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex }));
  sprite.scale.set(...scale, 1); sprite.userData.scale = scale;
  sprite.redraw = (...args) => { const g = c.getContext('2d'); g.clearRect(0, 0, w, h); draw(g, ...args); tex.needsUpdate = true; };
  return sprite;
}

export async function mount(root, app) {
  root.innerHTML = `<div class="house-bar"><button type="button" class="house-btn icon" data-house="close" aria-label="Close house view">${CLOSE}</button>
    <span class="grow"></span><button type="button" class="house-btn" data-house="flat" aria-label="Flat plan">2D</button>
    <button type="button" class="house-btn" data-house="walk" aria-pressed="false">Walk</button></div><div class="house-unplaced" aria-live="polite"></div>
    <div class="house-stick" hidden aria-label="Walk: drag to move" role="application"><span></span></div>`;
  const plan = await (await fetch('/house-plan.json', { cache: 'no-cache' })).json();
  const [W, D] = plan.size;
  const P = (x, y) => [x - W / 2, y - D / 2];                 // plan feet -> world x, z

  const renderer = new THREE.WebGLRenderer({ antialias: false });     // the composer's own target does the smoothing
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.autoUpdate = false;       // shadows are redrawn only when something casts a different one (shadows())
  root.prepend(renderer.domElement);
  RectAreaLightUniformsLib.init();

  const scene = new THREE.Scene(), camera = new THREE.PerspectiveCamera(32, 1, 0.3, 400);
  scene.background = new THREE.Color(0x0d1015);
  camera.position.set(2, 84, 40); camera.lookAt(0, 0, 1);
  const composer = new EffectComposer(renderer, new THREE.WebGLRenderTarget(1, 1, { type: THREE.HalfFloatType, samples: 4 }));
  composer.addPass(new RenderPass(scene, camera));
  // Some phone GPUs make the odd infinite or not-a-number pixel mid-animation; the glow's blur would smear it over
  // the whole picture (a black screen until the animation ends). Clean the picture before the glow sees it.
  composer.addPass(new ShaderPass({
    uniforms: { tDiffuse: { value: null } },
    vertexShader: 'varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }',
    fragmentShader: `uniform sampler2D tDiffuse; varying vec2 vUv;
      void main() { vec4 c = texture2D(tDiffuse, vUv);
        bool bad = any(isnan(c)) || any(notEqual(c, c));
        gl_FragColor = bad ? vec4(0.0, 0.0, 0.0, 1.0) : clamp(c, 0.0, 16.0); }`,
  }));
  const bloom = new UnrealBloomPass(new THREE.Vector2(256, 256), 0.55, 0.5, 0.82);
  const fullSize = bloom.setSize.bind(bloom);
  bloom.setSize = (w, h) => fullSize(w / 2, h / 2);     // a soft glow needs no detail: a quarter of the pixels
  composer.addPass(bloom);
  composer.addPass(new OutputPass());

  const hemi = new THREE.HemisphereLight(0x4a5a7c, 0x101420, 0.6);
  const sun = new THREE.DirectionalLight(0xfff1dc, 0);
  sun.position.set(-25, 60, 30);       // from the south, as in India sun.castShadow = true; sun.shadow.mapSize.set(2048, 2048);
  Object.assign(sun.shadow.camera, { left: -30, right: 30, top: 30, bottom: -30, far: 160 });
  scene.add(hemi, sun);

  const mat = { ...MATERIAL(), ...extraMaterials() };
  const structure = new THREE.Group(); scene.add(structure);   // everything standing up from the floor
  const box = (x1, y1, x2, y2, h1, h2, m, parent = structure) => {
    const [a, c] = P(x1, y1), [b, d] = P(x2, y2);
    const g = new THREE.Mesh(new THREE.BoxGeometry(Math.abs(b - a), h2 - h1, Math.abs(d - c)), m);
    g.position.set((a + b) / 2, (h1 + h2) / 2, (c + d) / 2);
    g.castShadow = g.receiveShadow = true;
    parent.add(g);
    return g;
  };

  // ---- floors, ceiling, room labels -------------------------------------------------------------------------
  const floors = [], ceiling = new THREE.Group(), labels = [];
  ceiling.visible = false; scene.add(ceiling);
  const outline = r => r.poly || [[r.rect[0], r.rect[1]], [r.rect[0] + r.rect[2], r.rect[1]], [r.rect[0] + r.rect[2], r.rect[1] + r.rect[3]], [r.rect[0], r.rect[1] + r.rect[3]]];
  for (const r of plan.rooms) {
    const pts = outline(r);
    const shape = new THREE.Shape(pts.map(([x, y]) => { const [wx, wz] = P(x, y); return new THREE.Vector2(wx, -wz); }));
    const floor = new THREE.Mesh(new THREE.ExtrudeGeometry(shape, { depth: 0.25, bevelEnabled: false }), mat.floor[r.floor]);
    floor.rotation.x = -Math.PI / 2; floor.position.y = -0.25; floor.receiveShadow = true;
    floor.userData.room = r.id;
    scene.add(floor); floors.push(floor);
    if (r.floor !== 'balcony') {             // faces down: only seen from inside, when walking
      const top = new THREE.Mesh(new THREE.ShapeGeometry(new THREE.Shape(pts.map(([x, y]) => { const [wx, wz] = P(x, y); return new THREE.Vector2(wx, wz); }))), mat.ceiling);
      top.rotation.x = Math.PI / 2; top.position.y = H; ceiling.add(top);
    }
    if (r.name) {
      const [cx, cy] = r.label || [r.rect[0] + r.rect[2] / 2, r.rect[1] + r.rect[3] / 2];
      const el = document.createElement('div'); el.className = 'house-label'; el.textContent = r.name; root.append(el);
      labels.push({ el, at: new THREE.Vector3(P(cx, cy)[0], 0.2, P(cx, cy)[1]) });
    }
  }
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(400, 400), new THREE.MeshStandardMaterial({ color: 0x0d1015, roughness: 1 }));
  ground.rotation.x = -Math.PI / 2; ground.position.y = -0.3; ground.receiveShadow = true; scene.add(ground);

  // ---- walls, cupboards, counters, furniture ----------------------------------------------------------------
  const walls = new THREE.Group(); structure.add(walls);
  for (const w of plan.walls) {
    for (const piece of wallPieces(w, H)) {
      const m = { glass: mat.glass, rail: mat.rail, granite: mat.granite }[piece.kind] || mat.wall;
      const [x1, y1, x2, y2] = w.axis === 'h' ? [piece.from, w.at - T / 2, piece.to, w.at + T / 2] : [w.at - T / 2, piece.from, w.at + T / 2, piece.to];
      box(x1, y1, x2, y2, piece.y1, piece.y2, m, walls);
      if (m === mat.wall && piece.y2 === H) box(x1 - 0.01, y1 - 0.01, x2 + 0.01, y2 + 0.01, H, H + 0.06, mat.cap, walls).castShadow = false;   // the cut edge
    }
  }
  const shadowOnly = new THREE.MeshBasicMaterial({ colorWrite: false, depthWrite: false });
  for (const w of plan.walls) for (const piece of wallPieces(w, H)) if (piece.kind === 'wall' && piece.y1 === 0) {
    const [x1, y1, x2, y2] = w.axis === 'h' ? [piece.from, w.at - T / 2, piece.to, w.at + T / 2] : [w.at - T / 2, piece.from, w.at + T / 2, piece.to];
    box(x1, y1, x2, y2, 0, piece.y2, shadowOnly, scene).receiveShadow = false;
  }
  for (const c of plan.cupboards) { const [x, y, w, h] = c.rect; box(x + 0.05, y + 0.05, x + w - 0.05, y + h - 0.05, 0, 7.6, mat.wood); }
  for (const [x, y, w, h] of plan.counters) box(x, y, x + w, y + h, 0, 3, mat.counter);
  const eggModels = [];                       // [egg name, model]: their taps are wired up once taps exist (Easter eggs)
  for (const f of plan.furniture) {
    if (!f.model) { box(...f.box, mat[f.material] || mat.white); continue; }
    const model = buildModel(f, mat, P); structure.add(model);
    if (f.egg) eggModels.push([f.egg, model]);
  }
  for (const w of plan.walls) if (w.parapet) {          // the white grill over the east end of the balcony wall
    const x1 = w.to - 7.2, x2 = w.to - 0.4;
    for (let h = 3.75; h < 7.7; h += 0.5) box(x1, w.at + 0.12, x2, w.at + 0.18, h, h + 0.05, mat.white, walls).castShadow = false;
    for (const x of [x1, (x1 + x2) / 2, x2 - 0.06]) box(x, w.at + 0.1, x + 0.06, w.at + 0.2, 3.3, 7.7, mat.white, walls).castShadow = false;
  }

  // ---- devices ----------------------------------------------------------------------------------------------
  const hits = [];                          // meshes a tap can land on; each carries userData.id
  const hit = (mesh, id) => { mesh.userData.id = id; hits.push(mesh); return mesh; };
  const onWall = (wall, height) => {        // a point on the room side of the wall, the way into the room, the span
    const mid = (wall.from + wall.to) / 2, off = 0.3 * wall.face;
    const [x, z] = wall.axis === 'h' ? P(mid, wall.at + off) : P(wall.at + off, mid);
    const into = wall.axis === 'h' ? new THREE.Vector3(0, 0, wall.face) : new THREE.Vector3(wall.face, 0, 0);
    return { pos: new THREE.Vector3(x, height, z), into, len: wall.to - wall.from, alongX: wall.axis === 'h' };
  };
  const warning = () => canvasSprite(64, 64, g => {
    g.fillStyle = '#ffb547'; g.beginPath(); g.arc(32, 32, 26, 0, Math.PI * 2); g.fill();
    g.fillStyle = '#1a1206'; g.font = '800 40px system-ui'; g.textAlign = 'center'; g.fillText('!', 32, 46);
  }, [1.1, 1.1]);
  const views = new Map(), spinners = [];
  const INVISIBLE = new THREE.MeshBasicMaterial({ transparent: true, opacity: 0, depthWrite: false });
  // Light sources sit outside the group that flattens for 2D: squashing a light's parent bends the way it points
  // (a tubelight would shine into the room behind its wall), and they should keep their real height anyway.
  const rig = new THREE.Group(); scene.add(rig);
  let near = 1;                             // light strength: 1 from above, lower when walking (lights are close)
  let up = 1;                               // how upright the house stands: 1 in 3D, 0 as a flat plan
  let shadowsDue = true, shadowsAt = 0;     // shadows(): something casts a different shadow; redrawn at most every 120 ms
  const shadows = () => { shadowsDue = true; };
  let frame = 0, closed = false, lost = false, dirty = true, lastDraw = 0, appliedUp = 1, frames = 0, slow = { n: 0, ms: 0 };

  const build = {
    tube(place) {
      const f = onWall(place.wall, 7.4), g = new THREE.Group(); structure.add(g);
      const tube = new THREE.Mesh(new THREE.BoxGeometry(f.alongX ? f.len : 0.18, 0.16, f.alongX ? 0.18 : f.len), new THREE.MeshStandardMaterial({ color: 0xf5f5f5, roughness: 0.3 }));
      tube.position.copy(f.pos); g.add(tube);
      // a 4 ft tube is thin from across the room: a slightly bigger target, kept close to the wall so it doesn't
      // cover the TV under the living room's tubelight
      const reach = hit(new THREE.Mesh(new THREE.BoxGeometry(f.alongX ? f.len + 0.4 : 0.6, 0.7, f.alongX ? 0.6 : f.len + 0.4), INVISIBLE), place.id);
      reach.position.copy(f.pos).addScaledVector(f.into, 0.1); g.add(reach);
      const area = new THREE.RectAreaLight(0xffffff, 0, f.len, 0.5);
      area.position.copy(f.pos).addScaledVector(f.into, 0.12);
      area.lookAt(area.position.clone().add(f.into).add(new THREE.Vector3(0, -0.55, 0)));
      const glow = new THREE.PointLight(0xffffff, 0, 26, 2); glow.position.copy(f.pos).addScaledVector(f.into, 1.2);
      rig.add(area, glow);
      const mark = warning(); mark.position.copy(f.pos).add(new THREE.Vector3(0, 0.9, 0));
      g.add(mark);
      return { apply(s, online) {
        const on = online && !!s.power, level = (s.brightness ?? 100) / 100, col = warmth((s.color_temp ?? 50) / 100);
        tube.material.emissive = col; tube.material.emissiveIntensity = on ? 4 * level * Math.max(near, 0.5) : 0;
        area.color = col; area.intensity = on ? 38 * level * near : 0;
        glow.color = col; glow.intensity = on ? 120 * level * near * up : 0;
        mark.visible = !online;
        return on;
      } };
    },
    lamp(place) {
      const [x, z] = P(...place.at), g = new THREE.Group(); structure.add(g);
      const pole = hit(new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 5, 12), mat.wood), place.id); pole.position.set(x, 2.5, z);
      const shade = hit(new THREE.Mesh(new THREE.CylinderGeometry(0.75, 0.95, 1.1, 24, 1, true),
        new THREE.MeshStandardMaterial({ color: 0xf1e6cf, roughness: 0.9, emissive: 0xffb766, emissiveIntensity: 0, side: THREE.DoubleSide })), place.id);
      shade.position.set(x, 5.3, z);
      const base = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.55, 0.1, 24), mat.dark); base.position.set(x, 0.05, z);
      const light = new THREE.PointLight(0xffb766, 0, 22, 2); light.position.set(x, 5.1, z); light.castShadow = true; rig.add(light);
      const mark = warning(); mark.position.set(x, 6.6, z);
      g.add(pole, shade, base, mark);
      return { apply(s, online) {
        const on = online && !!s.power, level = (s.brightness ?? 100) / 100;
        const col = s.color ? new THREE.Color(s.color) : warmth((s.color_temp ?? 30) / 100);
        shade.material.emissive = col; shade.material.emissiveIntensity = on ? 1.4 * level : 0;
        light.color = col; light.intensity = on ? 70 * level * near : 0;
        mark.visible = !online;
        return on;
      } };
    },
    ac(place) {
      const f = onWall(place.wall, 7.6), g = new THREE.Group(); structure.add(g);
      const body = hit(new THREE.Mesh(new THREE.BoxGeometry(f.alongX ? f.len : 0.8, 0.95, f.alongX ? 0.8 : f.len), mat.white), place.id);
      body.position.copy(f.pos).addScaledVector(f.into, 0.2);
      const led = new THREE.Mesh(new THREE.BoxGeometry(f.alongX ? f.len * 0.6 : 0.05, 0.06, f.alongX ? 0.05 : f.len * 0.6),
        new THREE.MeshStandardMaterial({ color: 0x111111, emissive: 0x59b4ff, emissiveIntensity: 0 }));
      led.position.copy(body.position).addScaledVector(f.into, 0.42).add(new THREE.Vector3(0, -0.25, 0));
      const tag = canvasSprite(128, 64, (gc, text) => {
        gc.fillStyle = 'rgba(12,16,22,.85)'; gc.beginPath(); gc.roundRect(4, 8, 120, 48, 24); gc.fill();
        gc.fillStyle = '#9fd3ff'; gc.font = '700 30px system-ui'; gc.textAlign = 'center'; gc.fillText(text, 64, 43);
      }, [3, 1.5]);
      tag.position.copy(body.position).add(new THREE.Vector3(0, 1.6, 0));
      const mark = warning(); mark.position.copy(body.position).add(new THREE.Vector3(0, 1.1, 0));
      g.add(body, led, tag, mark);
      let shownTemp = null;
      return { apply(s, online) {
        const on = online && !!s.power;
        led.material.emissiveIntensity = on ? 3 : 0;
        const text = Number.isFinite(s.target_temp) ? `${Math.round(s.target_temp * 2) / 2}°` : 'On';
        if (on && text !== shownTemp) { tag.redraw(text); shownTemp = text; }
        tag.visible = on; mark.visible = !online;
        return on;
      } };
    },
    fan(place) {
      const [x, z] = P(...place.at), g = new THREE.Group(); g.position.set(x, 8.1, z); structure.add(g);
      g.add(new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.35, 0.3, 20), mat.dark));
      const blades = new THREE.Group(); g.add(blades);
      for (let i = 0; i < 3; i++) {
        const arm = new THREE.Group(), blade = new THREE.Mesh(new THREE.BoxGeometry(2.1, 0.04, 0.38), mat.wood);
        blade.position.set(1.25, 0, 0); arm.rotation.y = i * Math.PI * 2 / 3; arm.add(blade); blades.add(arm);
      }
      g.add(hit(new THREE.Mesh(new THREE.CylinderGeometry(2.4, 2.4, 0.6, 20), INVISIBLE), place.id));
      const mark = warning(); mark.position.set(0, 0.9, 0); g.add(mark);
      const spin = { blades, speed: 0, id: place.id }; spinners.push(spin);
      return { apply(s, online) {
        const on = online && !!s.power;
        spin.speed = on ? 2 + (s.speed || 3) * 1.6 : 0;
        mark.visible = !online;
        return on;
      } };
    },
    tv(place) {
      const f = onWall(place.wall, 3.75), g = new THREE.Group(); structure.add(g);
      const frame = hit(new THREE.Mesh(new THREE.BoxGeometry(f.alongX ? f.len : 0.15, 2.65, f.alongX ? 0.15 : f.len), mat.dark), place.id);
      frame.position.copy(f.pos).addScaledVector(f.into, 0.5);
      const c = document.createElement('canvas'); c.width = 512; c.height = 288;
      const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace;
      const screen = hit(new THREE.Mesh(new THREE.PlaneGeometry(f.len - 0.15, 2.5),
        new THREE.MeshStandardMaterial({ map: tex, emissiveMap: tex, emissive: 0xffffff, emissiveIntensity: 0, roughness: 0.2 })), place.id);
      screen.position.copy(frame.position).addScaledVector(f.into, 0.08); screen.lookAt(screen.position.clone().add(f.into));
      const spill = new THREE.RectAreaLight(0x8f7bd6, 0, f.len, 2.4);
      spill.position.copy(screen.position).addScaledVector(f.into, 0.05); spill.lookAt(spill.position.clone().add(f.into)); rig.add(spill);
      g.add(frame, screen);
      const images = new Map();               // url -> loaded image (cover art and app logos)
      let drawn = null;
      const image = url => {
        if (images.has(url)) return images.get(url);
        const img = new Image(); images.set(url, null);
        img.onload = () => { images.set(url, img); drawn = null; app.redraw?.(); };
        img.src = url;
        return null;
      };
      let tune = null, tuneDrawn = 0;           // the record player's song, shown while it plays (an easter egg)
      const drawTune = now => {
        const gc = c.getContext('2d'), t = (now - tune.t0) / 1000, k = Math.min(1, t / tune.length);
        const bg = gc.createLinearGradient(0, 0, 512, 288); bg.addColorStop(0, '#2a1446'); bg.addColorStop(1, '#0f1a3a');
        gc.fillStyle = bg; gc.fillRect(0, 0, 512, 288);
        gc.fillStyle = '#f4e9ff'; gc.font = '800 34px system-ui'; gc.textAlign = 'left'; gc.fillText('Never Gonna Give You Up', 36, 84, 440);
        gc.fillStyle = '#c8b6ff'; gc.font = '600 24px system-ui'; gc.fillText('Rick Astley', 36, 120);
        for (let i = 0; i < 18; i++) {           // the equalizer
          const h = 16 + 52 * Math.abs(Math.sin(t * (3.1 + (i % 5) * 0.7) + i * 1.7)) * (0.55 + 0.45 * Math.abs(Math.sin(t * 1.3 + i)));
          gc.fillStyle = `hsl(${270 + i * 4}, 85%, ${62 + (i % 3) * 6}%)`; gc.fillRect(36 + i * 24, 226 - h, 16, h);
        }
        gc.fillStyle = 'rgba(255,255,255,0.18)'; gc.fillRect(36, 246, 440, 6);
        gc.fillStyle = '#f4e9ff'; gc.fillRect(36, 246, 440 * k, 6);
        const mmss = v => `${Math.floor(v / 60)}:${String(Math.floor(v % 60)).padStart(2, '0')}`;
        gc.font = '600 16px system-ui'; gc.fillStyle = '#c8b6ff'; gc.fillText(mmss(t), 36, 274); gc.textAlign = 'right'; gc.fillText(mmss(tune.length), 476, 274);
        tex.needsUpdate = true;
      };
      return { song(s) { tune = s; drawn = null; root.dataset.tvShows = s ? 'song' : ''; },
        tick(now) { if (tune && now - tuneDrawn > 100) { tuneDrawn = now; drawTune(now); } },
        apply(tv) {
        const on = !!tv.connected && tv.power !== 'off';
        if (tune) {                              // the song has the screen; the summary still tells the real TV
          screen.material.emissiveIntensity = 0.9 * Math.max(near, 0.6); spill.intensity = 6 * near; drawn = null;
          return on;
        }
        const art = on && tv.media?.art_id ? `/api/art?id=${encodeURIComponent(tv.media.art_id)}` : null;
        const logo = on && !art ? app.logo?.(tv.package) : null;
        const key = JSON.stringify([on, art, logo, tv.app_name, tv.media?.title, art && !!images.get(art), logo && !!images.get(logo)]);
        if (key !== drawn) {
          drawn = key;
          const gc = c.getContext('2d'), pic = art ? image(art) : logo ? image(logo) : null;
          gc.fillStyle = on ? '#14161f' : '#050506'; gc.fillRect(0, 0, 512, 288);
          if (on && pic && art) {                // cover the screen with the show's art
            const k = Math.max(512 / pic.naturalWidth, 288 / pic.naturalHeight), w = pic.naturalWidth * k, h = pic.naturalHeight * k;
            gc.drawImage(pic, (512 - w) / 2, (288 - h) / 2, w, h);
          } else if (on && pic) {                // the app's logo, centred
            const k = Math.min(300 / pic.naturalWidth, 140 / pic.naturalHeight);
            gc.drawImage(pic, 256 - pic.naturalWidth * k / 2, 144 - pic.naturalHeight * k / 2, pic.naturalWidth * k, pic.naturalHeight * k);
          } else if (on) {
            gc.fillStyle = '#e9e6df'; gc.font = '700 40px system-ui'; gc.textAlign = 'center';
            gc.fillText(tv.media?.title || tv.app_name || 'TV', 256, 160, 470);
          }
          tex.needsUpdate = true;
        }
        screen.material.emissiveIntensity = on ? 0.9 * Math.max(near, 0.6) : 0;
        spill.intensity = on ? 6 * near : 0;
        return on;
      } };
    },
  };
  for (const place of plan.devices) views.set(place.id, { place, ...build[place.kind](place) });

  // ---- the balcony's sliding door and curtains, and every door: tap to open or close -------------------------
  // Closed ones stop a glide like a wall (blockers). Room doors start open, the main door shut.
  const blockers = [...walls.children];
  const cw = plan.curtains.wall, side = plan.curtains.side, glassTop = 7;
  const pane = (x1, x2, z) => {               // a white-framed glass pane, slid by moving its group along x
    const g = new THREE.Group(); structure.add(g);
    const zz = cw.at + z;
    blockers.push(box(x1, zz - 0.03, x2, zz + 0.03, 0, glassTop, mat.glass, g));
    for (const [a, b] of [[x1, x1 + 0.12], [x2 - 0.12, x2]]) box(a, zz - 0.06, b, zz + 0.06, 0, glassTop, mat.white, g);
    for (const [a, b] of [[0, 0.15], [glassTop - 0.12, glassTop]]) box(x1, zz - 0.06, x2, zz + 0.06, a, b, mat.white, g);
    return g;
  };
  const mid = (cw.from + cw.to) / 2;
  pane(cw.from, mid, -0.1); const slider = pane(mid, cw.to, 0.1);
  const fabric = mat.curtain.clone(); fabric.side = THREE.DoubleSide;
  const sheerMat = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.9, transparent: true, opacity: 0.45, side: THREE.DoubleSide, depthWrite: false });
  const COLS = 48, ROWS = 4, TOP = 7.85, FOLDS = 7;
  const pleated = m => {                      // a grid we bend into folds every time the curtains move
    const geo = new THREE.BufferGeometry(), idx = [];
    geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array((COLS + 1) * (ROWS + 1) * 3), 3));
    for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) { const a = r * (COLS + 1) + c, b = a + COLS + 1; idx.push(a, b, a + 1, b, b + 1, a + 1); }
    geo.setIndex(idx);
    const mesh = hit(new THREE.Mesh(geo, m), 'curtains'); mesh.castShadow = m === fabric; structure.add(mesh); blockers.push(mesh);
    return mesh;
  };
  const curtain = { k: 0, target: 0, sway: 0, meshes: [[pleated(fabric), 0.55, 1], [pleated(fabric), 0.55, 1], [pleated(sheerMat), 0.28, 0.6], [pleated(sheerMat), 0.28, 0.6]] };
  function hang(t) {
    const spans = curtainPanels(cw.from, cw.to, curtain.k);
    curtain.meshes.forEach(([mesh, off, deep], i) => {
      const span = spans[i % 2], pos = mesh.geometry.attributes.position, depth = (0.28 + 0.2 * curtain.k) * deep;
      for (let r = 0; r <= ROWS; r++) for (let c = 0; c <= COLS; c++) {
        const u = c / COLS, v = r / ROWS, x = span.from + u * (span.to - span.from);
        const swing = curtain.sway * v * Math.sin(t * 1.3 + u * 4 + i);              // the hem moves, the top doesn't
        const z = cw.at + side * (off + depth * (0.5 + 0.5 * Math.sin(u * FOLDS * Math.PI * 2)) + swing);
        const [wx, wz] = P(x, z);
        pos.setXYZ(r * (COLS + 1) + c, wx, TOP - v * (TOP - 0.1), wz);
      }
      pos.needsUpdate = true; mesh.geometry.computeVertexNormals(); mesh.geometry.computeBoundingSphere(); mesh.geometry.computeBoundingBox();
    });
    slider.position.x = -(cw.to - mid) * curtain.k;
  }
  const rod = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, cw.to - cw.from + 0.9, 10), mat.dark);
  rod.rotation.z = Math.PI / 2; { const [x, z] = P(mid, cw.at + side * 0.5); rod.position.set(x, TOP + 0.08, z); } structure.add(rod);
  hang(0);
  root.dataset.curtains = 'closed'; root.dataset.outside = 'off';
  const diningFan = spinners.find(s => s.id === 'dining-fan');

  const doors = [], MAIN = plan.walls.find(w => w.axis === 'h' && w.at === D && w.features?.length);   // the outer wall with a door
  for (const w of plan.walls) for (const f of w.features || []) if (f.kind === 'door') {
    const id = `door:${w.axis}${w.at}:${f.from}`, open = w !== MAIN, len = f.to - f.from - 0.1;
    const pivot = new THREE.Group(), [hx, hz] = w.axis === 'h' ? P(f.from + 0.05, w.at) : P(w.at, f.from + 0.05);
    pivot.position.set(hx, 0, hz); structure.add(pivot);
    const leaf = new THREE.Mesh(new THREE.BoxGeometry(w.axis === 'h' ? len : 0.14, 6.9, w.axis === 'h' ? 0.14 : len), w === MAIN ? mat.mainDoor : mat.doorWood);
    leaf.position.set(w.axis === 'h' ? len / 2 : 0, 3.45, w.axis === 'h' ? 0 : len / 2);
    leaf.castShadow = leaf.receiveShadow = true; pivot.add(hit(leaf, id)); blockers.push(leaf);
    const knob = new THREE.Mesh(new THREE.BoxGeometry(w.axis === 'h' ? 0.35 : 0.3, 0.06, w.axis === 'h' ? 0.3 : 0.35), mat.chrome);
    knob.position.set(w.axis === 'h' ? len - 0.35 : 0, 3.3, w.axis === 'h' ? 0 : len - 0.35); pivot.add(knob);
    const door = { id, pivot, swing: (w.axis === 'h' ? -1 : 1) * f.swing * Math.PI / 2 * 0.95, k: open ? 1 : 0, target: open ? 1 : 0 };
    pivot.rotation.y = door.k * door.swing; doors.push(door);
  }
  const showDoors = () => { root.dataset.doors = JSON.stringify(Object.fromEntries(doors.map(d => [d.id, d.target ? 'open' : 'closed']))); };
  showDoors();
  function toggleOpening(id) {
    app.buzz();
    if (id === 'curtains') { curtain.target = curtain.target ? 0 : 1; root.dataset.curtains = curtain.target ? 'open' : 'closed'; }
    else { const d = doors.find(x => x.id === id); d.target = d.target ? 0 : 1; showDoors(); }
    regrid();
    if (still.matches) step();
    requestRender();
  }
  const ease = k => k < 0.5 ? 2 * k * k : 1 - (-2 * k + 2) ** 2 / 2;
  const toward = (k, target, by) => target > k ? Math.min(target, k + by) : Math.max(target, k - by);
  let lastStep = 0;
  // ---- easter eggs: furniture that moves, a record player, game night, outside life. None touch a device -------
  const eggs = {}, egg = {}, tweens = [];      // eggs: what the check reads; egg: each one's parts
  for (const [name, model] of eggModels) { egg[name] = model.userData.parts; model.traverse(o => { if (o.isMesh) hit(o, `egg:${name}`); }); }
  const showEggs = () => { root.dataset.eggs = JSON.stringify({ ...eggs, birds: outside?.birds.flying ? 'flying' : 'still', windows: outside?.windows.lit ?? null }); };
  const animate = (set, from, to, ms, delay = 0) => { tweens.push({ set, from, to, t0: performance.now() + delay, ms: still.matches ? 0 : ms }); requestRender(); };
  structure.updateMatrixWorld(true);
  const lampGlow = new THREE.PointLight(0xffd9a0, 0, 16, 2);       // always there (off), so switching it never rebuilds shaders
  egg['arc-lamp'].shade.getWorldPosition(lampGlow.position); lampGlow.position.y -= 0.6; rig.add(lampGlow);
  const record = { speed: 0, blades: egg.record.record, id: 'record' }; spinners.push(record);
  let audio = null, song = null, playing = null;
  async function playRecord() {
    audio ||= new (window.AudioContext || window.webkitAudioContext)();
    audio.resume();                            // inside the tap, so the phone lets it make sound
    if (playing) { playing.onended = null; playing.stop(); return stopRecord(); }
    eggs.record = 'loading'; showEggs();
    animate(v => { egg.record.arm.rotation.y = v; }, egg.record.arm.rotation.y, -0.42, 700);
    try {
      song ||= await audio.decodeAudioData(await (await fetch('/easter/record.mp3')).arrayBuffer());
      if (closed || eggs.record !== 'loading') return;
      playing = audio.createBufferSource(); playing.buffer = song; playing.connect(audio.destination); playing.start();
      playing.onended = () => stopRecord();
      record.speed = 3.5; eggs.record = 'playing'; showEggs();
      views.get('tv')?.song({ t0: performance.now(), length: song.duration }); drawnKey = ''; update(); requestRender();
    } catch { app.toast("The record player won't play", true); stopRecord(); }
  }
  function stopRecord() {
    playing = null; record.speed = 0; eggs.record = 'stopped'; showEggs();
    views.get('tv')?.song(null); drawnKey = ''; update();
    animate(v => { egg.record.arm.rotation.y = v; }, egg.record.arm.rotation.y, 0, 700);
  }
  const shelfBase = new THREE.Matrix4(), shelfMoved = new THREE.Matrix4();     // scratch
  function playEgg(name, where) {
    app.buzz();
    if (name === 'record') return playRecord();
    if (name === 'beanbag') {                  // squish, then slowly puff back up
      const bag = egg.beanbag.bag, y = bag.userData.y ??= bag.scale.y, x = bag.userData.x ??= bag.scale.x;
      animate(k => { bag.scale.y = y * (1 - 0.35 * k); bag.scale.x = x * (1 + 0.12 * k); }, 0, 1, 160);
      animate(k => { bag.scale.y = y * (1 - 0.35 * k); bag.scale.x = x * (1 + 0.12 * k); }, 1, 0, 1300, 180);
      eggs.beanbag = (eggs.beanbag || 0) + 1;
    } else if (name === 'recliner') {
      const back = egg.recliner.back, foot = egg.recliner.foot, out = eggs.recliner !== 'reclined';
      animate(v => { back.rotation.x = v; }, back.rotation.x, out ? -0.5 : -0.1, 800);
      animate(v => { foot.rotation.x = v; }, foot.rotation.x, out ? -1.45 : 0, 800);
      eggs.recliner = out ? 'reclined' : 'upright';
    } else if (name === 'folding-chair') {
      const c = egg['folding-chair'], fold = eggs['folding-chair'] !== 'folded';
      for (const l of c.front) animate(v => { l.rotation.x = v; }, l.rotation.x, fold ? 0.03 : 0.22, 600);
      for (const l of c.rear) animate(v => { l.rotation.x = v; }, l.rotation.x, fold ? -0.03 : -0.35, 600);
      animate(v => { c.seat.rotation.x = v; }, c.seat.rotation.x, fold ? -1.45 : 0, 600);
      eggs['folding-chair'] = fold ? 'folded' : 'open';
    } else if (name === 'arc-lamp') {
      const on = eggs['arc-lamp'] !== 'on';
      egg['arc-lamp'].shade.material.emissive.setHex(0xffd9a0); egg['arc-lamp'].shade.material.emissiveIntensity = on ? 1.3 : 0.2;
      lampGlow.intensity = on ? 28 : 0; eggs['arc-lamp'] = on ? 'on' : 'off'; requestRender();
    } else if (name === 'shelf') {             // a game slides out of its cubby and back; a toast names it
      const games = egg.shelf.games, i = Math.floor(Math.random() * games.count), list = plan.games || [];
      games.getMatrixAt(i, shelfBase);
      const base = shelfBase.clone();
      const slide = k => { games.setMatrixAt(i, shelfMoved.makeTranslation(0, 0, 0.55 * k).multiply(base)); games.instanceMatrix.needsUpdate = true; };
      animate(slide, 0, 1, 350); animate(slide, 1, 0, 500, 3200);
      const pick = list.length ? list[Math.floor(Math.random() * list.length)] : '';
      eggs.shelf = pick;
      app.toast(pick ? `Game night: ${pick}` : 'Game night! Pick one from the shelf');
    } else if (name === 'tree') {
      if (mode !== 'walk' || outside.birds.flying) return;
      outside.birds.start(); requestRender();
    } else if (name === 'school') {
      if (mode !== 'walk' || !where?.uv || !outside.windows.toggle(where.uv)) return;
      requestRender();
    }
    showEggs();
  }

  function animateOpenings() {               // from step(): true while a door or the curtains are still moving
    const now = performance.now(), dt = Math.min(0.1, (now - (lastStep || now)) / 1000); lastStep = now;
    let busy = false;
    for (const d of doors) if (d.k !== d.target) {
      d.k = still.matches ? d.target : toward(d.k, d.target, dt / 0.8);
      d.pivot.rotation.y = ease(d.k) * d.swing; busy = true; shadows();
    }
    const sway = !still.matches && diningFan?.speed ? 0.06 : 0;
    if (curtain.k !== curtain.target || sway || curtain.sway) {
      if (curtain.k !== curtain.target) { curtain.k = still.matches ? curtain.target : toward(curtain.k, curtain.target, dt / 1.3); busy = true; }
      curtain.sway = sway; hang(now / 1000); shadows();
    }
    for (const t of [...tweens]) {            // easter eggs' little moves
      if (now < t.t0) { busy = true; continue; }
      const k = t.ms ? Math.min(1, (now - t.t0) / t.ms) : 1;
      t.set(t.from + (t.to - t.from) * ease(k)); busy = true; shadows();
      if (k === 1) tweens.splice(tweens.indexOf(t), 1);
    }
    if (outside?.birds.flying) { if (outside.birds.step(dt)) busy = true; else showEggs(); }
    return busy;
  }

  // ---- following the house ----------------------------------------------------------------------------------
  const note = root.querySelector('.house-unplaced');
  let drawnKey = '';
  function update() {
    const snap = app.snap(), tv = app.tv() || {}, summary = {}, states = [!!tv.connected, tv.power, tv.package, tv.app_name, tv.media?.title, tv.media?.art_id, up, near];
    const { placed, unplaced } = reconcile(plan.devices, snap?.devices || []);
    for (const { place, device } of placed) {
      const view = views.get(place.id);
      if (place.kind === 'tv') { summary.tv = { on: view.apply(tv), online: !!tv.connected, placed: true }; continue; }
      const online = !!device?.online, shown = device ? app.shown(device) : {};
      summary[place.id] = { on: view.apply(shown, online), online, placed: true };
      view.device = device; states.push(shown, online);
    }
    const rooms = new Map((snap?.rooms || []).map(r => [r.id, r.name]));
    note.textContent = snap && unplaced.length ? `Not on the plan: ${unplaced.map(d => `${d.name} (${rooms.get(d.room) || d.room})`).join(', ')}` : '';
    root.dataset.devices = JSON.stringify(summary);
    const key = JSON.stringify(states);           // the poll comes every 2 s: draw only when something it shows changed
    if (key !== drawnKey) { drawnKey = key; shadows(); requestRender(); }
  }
  app.redraw = () => { drawnKey = ''; update(); };      // a picture finished loading: always draw

  // ---- camera ----------------------------------------------------------------------------------------------
  const canvas = renderer.domElement;
  const resize = () => {
    const w = root.clientWidth, h = Math.max(1, root.clientHeight);
    renderer.setSize(w, h, false);
    // smooth edges (4x MSAA) up to phone-sized pixel counts; a big screen's pixels are small enough without it
    const samples = w * h * renderer.getPixelRatio() ** 2 <= 2.0e6 ? 4 : 0;
    for (const t of [composer.renderTarget1, composer.renderTarget2]) if (t.samples !== samples) { t.samples = samples; t.dispose(); }
    composer.setSize(w, h);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  };
  const sizer = new ResizeObserver(resize); sizer.observe(root); resize();
  const controls = new OrbitControls(camera, canvas);
  Object.assign(controls, { enableDamping: true, minDistance: 14, maxDistance: 230, minPolarAngle: 0, maxPolarAngle: 1.18, screenSpacePanning: false });
  const world = (x, y, h = 0) => { const [wx, wz] = P(x, y); return new THREE.Vector3(wx, h, wz); };
  const toPlan = p => [p.x + W / 2, p.z + D / 2];
  function overviewAt() {                 // the whole flat in the middle of the screen, as big as it fits
    const cam = camera.clone(), dir = new THREE.Vector3().setFromSphericalCoords(1, UPRIGHT, 0), target = world(W / 2, D / 2);
    const corners = [[0, 0], [W, 0], [0, D], [W, D]].flatMap(([x, y]) => [world(x, y), world(x, y, 9)]);
    const box = dist => {
      cam.position.copy(target).addScaledVector(dir, dist); cam.lookAt(target); cam.updateMatrixWorld();
      const q = corners.map(c => c.clone().project(cam)), xs = q.map(v => v.x), ys = q.map(v => v.y);
      return { w: Math.max(...xs) - Math.min(...xs), h: Math.max(...ys) - Math.min(...ys), cx: (Math.max(...xs) + Math.min(...xs)) / 2, cy: (Math.max(...ys) + Math.min(...ys)) / 2 };
    };
    let dist = 80;
    for (let round = 0; round < 4; round++) {
      let lo = 10, hi = controls.maxDistance;
      for (let i = 0; i < 24; i++) { const mid = (lo + hi) / 2, b = box(mid); if (b.w <= 1.9 && b.h <= 1.9) hi = mid; else lo = mid; }
      dist = hi;
      const b = box(dist), half = dist * Math.tan(THREE.MathUtils.degToRad(cam.fov / 2));
      target.x += b.cx * half * cam.aspect; target.z -= b.cy * half / Math.cos(UPRIGHT);   // nudge the picture into the middle
    }
    return { target, pos: target.clone().addScaledVector(dir, dist) };
  }
  function setAngles(phi, theta) {        // OrbitControls clamps to its limits, so pin both limits for one update
    Object.assign(controls, { minPolarAngle: phi, maxPolarAngle: phi, minAzimuthAngle: theta, maxAzimuthAngle: theta });
    controls.update();
    Object.assign(controls, { minPolarAngle: 0, maxPolarAngle: 1.18, minAzimuthAngle: -Infinity, maxAzimuthAngle: Infinity });
  }

  // ---- views: overview (3D or flat, by tilt) and walking -----------------------------------------------------
  let mode = 'overview', tween = null, tilt = null, overviewCam = null, yaw = 0, pitch = 0;
  const still = matchMedia('(prefers-reduced-motion: reduce)');
  const lookQuat = (y, p) => new THREE.Quaternion().setFromEuler(new THREE.Euler(p, y, 0, 'YXZ'));
  const flatBtn = root.querySelector('[data-house="flat"]'), walkBtn = root.querySelector('[data-house="walk"]');
  function flyTo(pos, quat, fov, ms, done) {
    tween = { from: camera.position.clone(), fq: camera.quaternion.clone(), ff: camera.fov, to: pos, tq: quat, tf: fov, t0: performance.now(), ms: still.matches ? 0 : ms, done };
    requestRender();
  }
  // ---- time of day: night, dusk and daylight follow the clock (house-logic daylight) --------------------------
  const night = { sky: new THREE.Color(0xa9b8dc), ground: new THREE.Color(0x4a4f60), bg: new THREE.Color(0x0d1015) };   // moonlight: rooms stay readable
  const day = { sky: new THREE.Color(0xdfe8f5), ground: new THREE.Color(0x8a8070), bg: new THREE.Color(0x1c2330) };
  const skyAt = { night: new THREE.Color(0x0a0f1c), dusk: new THREE.Color(0x34507a), day: new THREE.Color(0x9fc4ea) };
  let sunUp = 0;
  function sky() {
    shadows();
    sunUp = daylight(new Date());
    hemi.color.lerpColors(night.sky, day.sky, sunUp); hemi.groundColor.lerpColors(night.ground, day.ground, sunUp);
    hemi.intensity = (mode === 'walk' ? 2.4 : 2.1) - (mode === 'walk' ? 1.3 : 1.5) * sunUp;   // night: a soft fill to see by; day: the sun does it
    sun.intensity = 2.2 * sunUp;
    if (mode === 'walk') {                    // out of the windows: night, the blue of dusk, day
      if (sunUp < 0.5) scene.background.lerpColors(skyAt.night, skyAt.dusk, sunUp * 2); else scene.background.lerpColors(skyAt.dusk, skyAt.day, sunUp * 2 - 1);
    } else scene.background.lerpColors(night.bg, day.bg, sunUp);
    outside?.night(1 - sunUp);
    renderer.toneMappingExposure = 1.05 - 0.1 * sunUp;
    near = (mode === 'walk' ? 0.3 : 1) * (1 - 0.45 * sunUp);        // lamps look dimmer in daylight
    root.dataset.daylight = String(Math.round(sunUp * 100) / 100);
    update(); requestRender();
  }
  let outside = null;
  function walking(on) {
    mode = on ? 'walk' : 'overview';
    if (on && !outside) {
      outside = buildOutside(P, plan.outside?.ground ?? -21); scene.add(outside.group);
      for (const { mesh, id } of outside.hits) hit(mesh, `egg:${id}`);
      showEggs();
    }
    if (outside) outside.group.visible = on;
    ground.visible = !on; root.dataset.outside = on ? 'on' : 'off';
    stickEl.hidden = !on; if (!on) { walkPath = null; spin = null; showRing(null); }
    bloom.strength = on ? 0.25 : 0.55; sky();
    walkBtn.textContent = on ? 'Overview' : 'Walk'; walkBtn.setAttribute('aria-pressed', String(on));
    flatBtn.disabled = on;
  }
  function enterWalk(x, y, faceYaw, instant = false) {      // plan feet
    [x, y] = findPath(grid, [x, y], [x, y])?.[0] || [x, y];   // a spot you could stand on
    if (mode === 'overview') overviewCam = { pos: camera.position.clone(), target: controls.target.clone() };
    controls.enabled = false; ceiling.visible = true; walking(true);
    const spot = world(x, y, EYE);
    yaw = faceYaw ?? (Math.hypot(spot.x, spot.z) < 2 ? Math.PI / 2 : Math.atan2(spot.x, spot.z));   // face the middle of the flat
    pitch = -0.06;
    if (instant) { camera.position.copy(spot); camera.quaternion.copy(lookQuat(yaw, pitch)); camera.fov = 62; camera.updateProjectionMatrix(); }
    else flyTo(spot, lookQuat(yaw, pitch), 62, 1500);
  }
  function leaveWalk() {
    const back = overviewCam || overviewAt();
    walking(false);
    const look = new THREE.Object3D(); look.position.copy(back.pos); look.lookAt(back.target);
    flyTo(back.pos.clone(), look.quaternion.clone(), 32, 1300, () => { ceiling.visible = false; controls.target.copy(back.target); controls.enabled = true; controls.update(); });
  }
  // ---- walking: a tap walks a real path (house-logic findPath), the joystick slides along walls ---------------
  let grid = null, walkPath = null, spin = null;
  const stick = { v: [0, 0] };
  function regrid() { grid = walkGrid(plan, { doors: Object.fromEntries(doors.map(d => [d.id, !!d.target])), curtains: !!curtain.target }); }
  regrid();
  const ring = new THREE.Mesh(new THREE.RingGeometry(0.32, 0.46, 40), new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.8, depthWrite: false }));
  ring.rotation.x = -Math.PI / 2; ring.visible = false; ring.renderOrder = 2; scene.add(ring);
  const showRing = at => { ring.visible = !!at; if (at) ring.position.set(at.x, 0.03, at.z); root.dataset.ring = at ? 'shown' : 'hidden'; requestRender(); };
  function goTo(x, y) {                        // plan feet
    const path = findPath(grid, toPlan(camera.position), [x, y]);
    if (!path) { app.toast(curtain.target || y > 7 ? "Can't get there from here" : 'Open the curtains to go out', true); showRing(null); return false; }
    const pts = path.map(([px, py]) => world(px, py, EYE));
    if (pts.length === 2) pts.splice(1, 0, pts[0].clone().lerp(pts[1], 0.5));
    const curve = new THREE.CatmullRomCurve3(pts, false, 'centripetal'), len = curve.getLength();
    if (len < 0.3) { showRing(null); return true; }
    walkPath = { curve, len, t0: performance.now(), ms: still.matches ? 0 : Math.min(5200, 380 + len * 185), turn: len > 2.5 };
    showRing(pts.at(-1).clone().setY(0)); spin = null;
    requestRender(); return true;
  }
  const angleTo = (a, b) => Math.atan2(Math.sin(b - a), Math.cos(b - a));
  let walkLast = 0;
  function walkStep() {                        // from step(): moves along a path, the joystick, a look's glide
    const now = performance.now(), dt = Math.min(0.05, (now - (walkLast || now)) / 1000); walkLast = now;
    if (mode !== 'walk' || tween) return false;
    let busy = false;
    if (Math.hypot(...stick.v) > 0.08) {
      walkPath = null; showRing(null);
      const fwd = -stick.v[1], side = stick.v[0], speed = 5 * Math.min(1, Math.hypot(...stick.v)) * dt;
      const delta = [(-Math.sin(yaw) * fwd + Math.cos(yaw) * side) * speed, (-Math.cos(yaw) * fwd - Math.sin(yaw) * side) * speed];
      const [x, y] = slide(grid, toPlan(camera.position), delta);
      camera.position.copy(world(x, y, EYE)); busy = true;
    } else if (walkPath) {
      const k = walkPath.ms ? Math.min(1, (now - walkPath.t0) / walkPath.ms) : 1, e = 0.5 - 0.5 * Math.cos(Math.PI * k);   // ease in and out
      camera.position.copy(walkPath.curve.getPointAt(e));
      if (walkPath.turn && k < 1) {              // face the way you walk, gently
        const t = walkPath.curve.getTangentAt(Math.min(1, e + 0.04));
        yaw += angleTo(yaw, Math.atan2(-t.x, -t.z)) * Math.min(1, dt * 4);
        pitch += (-0.08 - pitch) * Math.min(1, dt * 3);
        camera.quaternion.copy(lookQuat(yaw, pitch));
      }
      if (k === 1) { walkPath = null; showRing(null); }
      busy = true;
    }
    if (spin) {                                // a flicked look keeps turning, then slows to a stop
      yaw += spin.vy * dt; pitch = Math.max(-1.2, Math.min(1.2, pitch + spin.vp * dt));
      spin.vy *= Math.exp(-dt * 5); spin.vp *= Math.exp(-dt * 5);
      camera.quaternion.copy(lookQuat(yaw, pitch));
      if (Math.hypot(spin.vy, spin.vp) < 0.03) spin = null;
      busy = true;
    }
    return busy;
  }
  // the joystick, bottom left while walking
  const stickEl = root.querySelector('.house-stick'), knob = stickEl.firstElementChild;
  const stickAt = e => {
    const r = stickEl.getBoundingClientRect(), x = e.clientX - (r.left + r.width / 2), y = e.clientY - (r.top + r.height / 2);
    const max = r.width * 0.36, d = Math.hypot(x, y), k = d > max ? max / d : 1;
    stick.v = [x * k / max, y * k / max]; knob.style.transform = `translate(${x * k}px, ${y * k}px)`; requestRender();
  };
  stickEl.addEventListener('pointerdown', e => { e.preventDefault(); try { stickEl.setPointerCapture(e.pointerId); } catch {} stick.id = e.pointerId; stickAt(e); });
  stickEl.addEventListener('pointermove', e => { if (stick.id === e.pointerId) stickAt(e); });
  const stickUp = e => { if (stick.id !== e.pointerId) return; stick.id = null; stick.v = [0, 0]; knob.style.transform = ''; };
  stickEl.addEventListener('pointerup', stickUp); stickEl.addEventListener('pointercancel', stickUp);
  flatBtn.addEventListener('click', () => {
    if (mode !== 'overview' || tween) return;
    app.buzz();
    const goFlat = up >= 0.5, a = controls.getAzimuthalAngle();
    tilt = { p0: controls.getPolarAngle(), p1: goFlat ? 0.0001 : UPRIGHT, a0: a, a1: goFlat ? 0 : a, t0: performance.now(), ms: still.matches ? 0 : 1000 };
    if (still.matches) step();                 // instant: apply it now rather than on the next frame
    requestRender();
  });
  walkBtn.addEventListener('click', () => {
    if (tween) return;
    app.buzz();
    if (mode === 'walk') leaveWalk(); else enterWalk(plan.start.x, plan.start.y, plan.start.yaw);
  });

  // ---- picking: walls hide what's behind them ----------------------------------------------------------------
  const ray = new THREE.Raycaster(), ndc = new THREE.Vector2();
  function aim(x, y) {
    const r = canvas.getBoundingClientRect();
    ndc.set((x - r.left) / r.width * 2 - 1, -(y - r.top) / r.height * 2 + 1);
    ray.setFromCamera(ndc, camera);
  }
  let lastHit = null;                         // the last tap's spot, for eggs that care where (a school window)
  function deviceAt(x, y) {
    aim(x, y);
    lastHit = ray.intersectObjects([...hits, ...walls.children], false)[0] || null;
    return lastHit?.object.userData.id || null;
  }
  function floorAt(x, y) { aim(x, y); return ray.intersectObjects(floors, false)[0]?.point || null; }
  function tapDevice(id) {
    if (id === 'curtains' || id.startsWith('door:')) return toggleOpening(id);
    if (id.startsWith('egg:')) return playEgg(id.slice(4), lastHit);
    const view = views.get(id), device = view.device;
    if (view.place.kind === 'tv') return app.togglePower();
    if (!device) return app.toast('This device isn\'t set up yet', true);
    if (!device.online) return app.toast(device.error || 'Offline', true);
    app.buzz();
    app.change(id, { power: !app.shown(device).power });
  }
  function holdDevice(id) {
    const view = views.get(id);
    if (!view) return;                         // doors and curtains have nothing more to show
    app.buzz(14);
    if (view.place.kind === 'tv') { app.close(); app.setMode('tv'); return; }
    if (view.device) app.openDevice(id);
  }
  function tapAt(x, y) {
    const id = deviceAt(x, y);
    if (id) return tapDevice(id);
    const spot = floorAt(x, y);
    if (!spot) return;
    if (mode === 'walk') { const [px, py] = toPlan(spot); goTo(px, py); }
    else { const [px, py] = toPlan(spot); enterWalk(px, py); }
  }

  // ---- gestures: tap, hold, look around (walking), two-finger tilt (overview); the rest is OrbitControls ----
  const fingers = new Map();
  let press = null, look = null, pair = null;
  const spread = () => { const [a, b] = [...fingers.values()]; return { d: Math.hypot(a.x - b.x, a.y - b.y), y: (a.y + b.y) / 2, a, b }; };
  canvas.addEventListener('pointerdown', e => {
    if (e.isPrimary) { fingers.clear(); pair = null; }     // a new gesture: forget fingers whose lift we never heard
    try { canvas.setPointerCapture(e.pointerId); } catch {}  // keep the finger even when it slides off the edge
    fingers.set(e.pointerId, { x: e.clientX, y: e.clientY, y0: e.clientY });
    if (fingers.size === 1) {
      press = { x: e.clientX, y: e.clientY, t: performance.now(), fired: false };
      press.timer = setTimeout(() => { if (press && !press.moved) { press.fired = true; const id = deviceAt(press.x, press.y); if (id) holdDevice(id); } }, HOLD);
      if (mode === 'walk' && !tween) {
        look = { x: e.clientX, y: e.clientY, t: performance.now(), vy: 0, vp: 0 }; spin = null;
        if (!walkPath && !deviceAt(e.clientX, e.clientY)) showRing(floorAt(e.clientX, e.clientY));   // where a tap would take you
      }
    } else {
      if (press) { clearTimeout(press.timer); press = null; }
      look = null;
      if (fingers.size === 2 && mode === 'overview') pair = { start: spread(), tilting: false };
    }
  }, true);
  canvas.addEventListener('pointermove', e => {
    const f = fingers.get(e.pointerId);
    if (!f) return;
    f.x = e.clientX; f.y = e.clientY;
    if (press && Math.hypot(e.clientX - press.x, e.clientY - press.y) >= 8) { press.moved = true; clearTimeout(press.timer); }
    if (look && press?.moved) {
      const now = performance.now(), dy = (e.clientX - look.x) * 0.005, dp = (e.clientY - look.y) * 0.005, dt = Math.max(1, now - look.t) / 1000;
      if (!walkPath) showRing(null);
      yaw += dy; pitch = Math.max(-1.2, Math.min(1.2, pitch + dp));
      look = { x: e.clientX, y: e.clientY, t: now, vy: 0.6 * look.vy + 0.4 * dy / dt, vp: 0.6 * look.vp + 0.4 * dp / dt };
      camera.quaternion.copy(lookQuat(yaw, pitch)); requestRender();
    }
    if (pair && fingers.size === 2) {
      const now = spread(), dy1 = now.a.y - now.a.y0, dy2 = now.b.y - now.b.y0;
      if (!pair.tilting && Math.abs(dy1) > 10 && Math.abs(dy2) > 10 && Math.sign(dy1) === Math.sign(dy2)
          && Math.abs(dy1 - dy2) <= 0.3 * Math.max(Math.abs(dy1), Math.abs(dy2)) && Math.abs(now.d / pair.start.d - 1) < 0.15) {
        pair.tilting = true; pair.y = now.y; controls.enabled = false; tilt = null;      // a tilt, not a pinch
      }
      if (pair.tilting) {        // as in Google Maps: fingers down flattens towards the plan, up stands the house back up
        setAngles(Math.max(0.0001, Math.min(1.18, controls.getPolarAngle() - (now.y - pair.y) * 0.006)), controls.getAzimuthalAngle());
        pair.y = now.y; requestRender();
      }
    }
  }, true);
  const lift_ = e => {
    fingers.delete(e.pointerId);
    const dragged = !!press?.moved;
    if (press && fingers.size === 0) {
      clearTimeout(press.timer);
      if (!press.fired && !press.moved && isTap(press, { x: e.clientX, y: e.clientY, t: performance.now() }) && e.type === 'pointerup') tapAt(e.clientX, e.clientY);
      press = null;
    }
    if (fingers.size < 2 && pair) { if (pair.tilting) controls.enabled = mode === 'overview' && !tween; pair = null; }
    if (fingers.size === 0) {
      if (look && dragged && performance.now() - look.t < 80 && Math.hypot(look.vy, look.vp) > 0.4) { spin = { vy: look.vy, vp: look.vp }; requestRender(); }
      if (!walkPath) showRing(null);
      look = null;
    }
  };
  canvas.addEventListener('pointerup', lift_, true);
  canvas.addEventListener('pointercancel', lift_, true);
  canvas.addEventListener('contextmenu', e => e.preventDefault());
  canvas.addEventListener('pointermove', e => {     // a mouse shows where a click would take you
    if (e.pointerType === 'mouse' && !e.buttons && mode === 'walk' && !walkPath && !tween) showRing(deviceAt(e.clientX, e.clientY) ? null : floorAt(e.clientX, e.clientY));
  });
  canvas.addEventListener('pointerleave', () => { if (!walkPath) showRing(null); });

  // ---- remembering where you were ------------------------------------------------------------------------------
  function place() {
    const cam = mode === 'walk' ? (overviewCam || overviewAt()) : { pos: camera.position, target: controls.target };
    const [x, y] = toPlan(camera.position);
    return { v: 2, view: mode, camera: { pos: cam.pos.toArray(), target: cam.target.toArray() }, walk: mode === 'walk' ? { x, y, yaw, pitch } : null };
  }
  const saved = loadPlace(localStorage);
  if (saved) {
    overviewCam = saved.v === 2 ? { pos: new THREE.Vector3(...saved.camera.pos), target: new THREE.Vector3(...saved.camera.target) } : overviewAt();
    camera.position.copy(overviewCam.pos); controls.target.copy(overviewCam.target); controls.update();
    if (saved.view === 'walk') { enterWalk(saved.walk.x, saved.walk.y, saved.walk.yaw, true); pitch = saved.walk.pitch; camera.quaternion.copy(lookQuat(yaw, pitch)); }
  } else {
    const start = overviewAt();
    camera.position.copy(start.pos); controls.target.copy(start.target); controls.update();
  }
  const remember = () => { if (document.hidden) savePlace(localStorage, place()); };
  document.addEventListener('visibilitychange', remember);

  // ---- drawing: only when something changed; spinning fans alone get about 30 fps -------------------------
  const v = new THREE.Vector3(), clock = new THREE.Clock(), sprites = [];
  structure.traverse(o => { if (o.isSprite) sprites.push(o); });
  function requestRender() { dirty = true; if (!frame && !closed && !lost && !document.hidden) frame = requestAnimationFrame(draw); }
  const spinning = () => !still.matches && spinners.some(s => s.speed);
  controls.addEventListener('change', requestRender);
  function step() {                            // camera moves for this frame; true while something is still moving
    let moving = animateOpenings() | walkStep();
    if (tween) {
      const k = Math.min(1, (performance.now() - tween.t0) / tween.ms), e = k < 0.5 ? 2 * k * k : 1 - (-2 * k + 2) ** 2 / 2;
      camera.position.lerpVectors(tween.from, tween.to, e);
      camera.quaternion.slerpQuaternions(tween.fq, tween.tq, e);
      camera.fov = tween.ff + (tween.tf - tween.ff) * e; camera.updateProjectionMatrix();
      if (k === 1) { const done = tween.done; tween = null; done?.(); }
      moving = true;
    } else if (mode === 'overview') {
      if (tilt) {
        const k = tilt.ms ? Math.min(1, (performance.now() - tilt.t0) / tilt.ms) : 1, e = k < 0.5 ? 2 * k * k : 1 - (-2 * k + 2) ** 2 / 2;
        setAngles(tilt.p0 + (tilt.p1 - tilt.p0) * e, tilt.a0 + (tilt.a1 - tilt.a0) * e);
        if (k === 1) tilt = null;
        moving = true;
      } else if (controls.enabled) moving = controls.update();   // true while damping still settles
    }
    // how upright the house stands follows the camera's tilt; walking is always upright
    const polar = mode === 'walk' ? 1 : v.subVectors(camera.position, controls.target).angleTo(THREE.Object3D.DEFAULT_UP);
    up = mode === 'walk' || tween ? (mode === 'walk' ? 1 : up) : standing(polar);
    if (structure.scale.y !== 0.04 + 0.96 * up) { structure.scale.y = 0.04 + 0.96 * up; shadows(); }
    for (const s of sprites) s.scale.set(s.userData.scale[0], s.userData.scale[1] / structure.scale.y, 1);
    if (mode === 'overview') {
      const flat = up < 0.15;
      controls.mouseButtons.LEFT = flat ? THREE.MOUSE.PAN : THREE.MOUSE.ROTATE;      // flat: dragging moves the map
      controls.touches.ONE = flat ? THREE.TOUCH.PAN : THREE.TOUCH.ROTATE;
      const label = up < 0.5 ? '3D' : '2D';
      if (flatBtn.textContent !== label) { flatBtn.textContent = label; flatBtn.setAttribute('aria-label', up < 0.5 ? '3D view' : 'Flat plan'); }
    }
    root.dataset.view = mode; root.dataset.standing = up.toFixed(2);
    if (Math.abs(up - appliedUp) > 0.04 || (up === 0) !== (appliedUp === 0)) { appliedUp = up; update(); }
    return moving;
  }
  function draw(now) {
    frame = 0;
    window.__houseRaf = (window.__houseRaf || 0) + 1;
    if (closed || lost) return;
    const dt = clock.getDelta(), moving = step(), fans = spinning();
    if ((moving || dirty || fans) && !frame) frame = requestAnimationFrame(draw);   // step() may have asked already: one loop only
    if (!moving && !dirty && fans && now - lastDraw < 32) return;       // only fans: about 30 fps is plenty
    if (!moving && !dirty && !fans) return;
    dirty = false;
    if (!still.matches) for (const s of spinners) if (s.speed) s.blades.rotation.y += Math.min(dt, 0.1) * s.speed;
    const t0 = performance.now();
    // shadows cost a pass per light (six for the lamp): redraw them only when they changed, at most every 120 ms
    // while something moves, and once more when it stops
    views.get('tv')?.tick?.(t0);
    if (shadowsDue && (!moving || t0 - shadowsAt > 120)) { renderer.shadowMap.needsUpdate = true; shadowsDue = false; shadowsAt = t0; }
    composer.render();
    if (shadowsDue) requestRender();
    root.dataset.frames = String(++frames);
    const spent = performance.now() - (lastDraw || t0); lastDraw = performance.now();
    // a phone that can't keep up loses the glow, not the smoothness. Judged on frames where the camera moves (fans
    // alone run at 30 fps on purpose), leaving out pauses between gestures, over 90 such frames
    if (bloom.enabled && moving && spent < 100) {
      slow.n++; slow.ms += spent;
      if (slow.n >= 90) { if (slow.ms / slow.n > 25) bloom.enabled = false; slow = { n: 0, ms: 0 }; }
    }
    root.dataset.bloom = bloom.enabled ? 'on' : 'off';
    const w = root.clientWidth, h = root.clientHeight;
    for (const l of labels) {
      v.copy(l.at).project(camera);
      const show = mode === 'overview' && !tween && v.z < 1;
      l.el.style.display = show ? '' : 'none';
      if (show) { l.el.style.left = `${(v.x + 1) / 2 * w}px`; l.el.style.top = `${(1 - v.y) / 2 * h}px`; }
    }
  }
  bloom.enabled = !still.matches;
  sky(); requestRender();
  const clockTimer = setInterval(sky, 60000);
  const visible = () => { if (!document.hidden) requestRender(); };
  document.addEventListener('visibilitychange', visible);
  sizer.disconnect();
  const resizer = new ResizeObserver(() => { resize(); requestRender(); }); resizer.observe(root);
  canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); lost = true; cancelAnimationFrame(frame); frame = 0; });
  canvas.addEventListener('webglcontextrestored', () => {       // iOS drops the context after a while in the background
    lost = false; shadows();
    scene.traverse(o => { const m = o.material; if (m?.map) m.map.needsUpdate = true; });
    requestRender();
  });
  root.querySelector('[data-house="close"]').addEventListener('click', () => { app.buzz(); app.close(); });

  // ---- hooks for the browser checks (tools/qa/home.js) -------------------------------------------------------
  const onScreen = p => {
    const q = p.clone().project(camera), r = canvas.getBoundingClientRect();
    if (q.z > 1 || Math.abs(q.x) > 1 || Math.abs(q.y) > 1) return null;
    return { x: r.left + (q.x + 1) / 2 * r.width, y: r.top + (1 - q.y) / 2 * r.height };
  };
  root.screenOf = id => {        // a visible point on the device, like a finger would pick: null when it's hidden
    for (const target of hits.filter(m => m.userData.id === id)) {
      const b = new THREE.Box3().setFromObject(target), c = b.getCenter(new THREE.Vector3()), s = b.getSize(new THREE.Vector3()).multiplyScalar(0.3);
      for (const [dx, dy, dz] of [[0, 0, 0], [0, -1, 0], [0, 1, 0], [-1, 0, -1], [1, 0, 1]]) {
        const at = onScreen(c.clone().add(new THREE.Vector3(dx * s.x, dy * s.y, dz * s.z)));
        if (at && deviceAt(at.x, at.y) === id) return at;
      }
    }
    return null;
  };
  root.deviceAt = deviceAt;
  root.cameraAt = () => toPlan(camera.position);
  root.targetAt = () => toPlan(controls.target);
  root.screenAtPlan = (x, y) => onScreen(world(x, y));
  root.spoilPixel = () => {          // one impossibly bright surface, as some phone GPUs make mid-animation
    const m = new THREE.Mesh(new THREE.BoxGeometry(2, 2, 2), new THREE.MeshBasicMaterial({ color: new THREE.Color(Infinity, Infinity, Infinity) }));
    m.position.copy(controls.target).setY(4); scene.add(m); requestRender();
  };
  showEggs();
  root.screenOfWindow = n => { const c = outside?.windows.centre(n); return c ? onScreen(c) : null; };
  root.goTo = (x, y) => goTo(x, y);
  root.walkable = (x, y) => grid.free(x, y);
  root.lookAt = () => [yaw, pitch];
  root.walkAt = (x, y, yaw, look = -0.06) => { enterWalk(x, y, yaw, true); pitch = look; camera.quaternion.copy(lookQuat(yaw, pitch)); requestRender(); };

  return {
    update,
    close() {
      savePlace(localStorage, place());
      closed = true; cancelAnimationFrame(frame); frame = 0; controls.dispose(); clearInterval(clockTimer); resizer.disconnect();
      clearTimeout(press?.timer); press = null;
      document.removeEventListener('visibilitychange', remember); document.removeEventListener('visibilitychange', visible);
      // give the GPU memory back now, not whenever the browser gets round to it: a 1 GB phone notices
      scene.traverse(o => {
        o.geometry?.dispose();
        for (const m of [].concat(o.material || [])) { for (const k in m) if (m[k]?.isTexture) m[k].dispose(); m.dispose(); }
      });
      if (playing) { playing.onended = null; playing.stop(); }
      audio?.close();
      bloom.dispose(); composer.dispose(); renderer.dispose(); renderer.forceContextLoss();
    },
  };
}
