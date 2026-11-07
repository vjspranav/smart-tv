/* House view: furniture shapes and the view outside the dining balcony, matched to the user's photos
   (smart-home/docs/house-mockups/living-v2.html, spec addendum 2026-10-22). house.js loads this with the scene.
   Plan units are feet; a model fills its plan box, with its front towards `facing`. */
import * as THREE from '/vendor/three/three.module.min.js';

const TURN = { '+y': 0, '-y': Math.PI, '+x': Math.PI / 2, '-x': -Math.PI / 2 };
const COMPASS = { n: [0, -1], s: [0, 1], e: [1, 0], w: [-1, 0] };

// a small fixed random, so the shelf's games and the school's stains look the same on every phone
const seeded = seed => () => ((seed = (seed * 16807) % 2147483647) - 1) / 2147483646;

export function extraMaterials() {
  const std = (color, roughness, extra = {}) => new THREE.MeshStandardMaterial({ color, roughness, ...extra });
  return {
    navy: std(0x2c3a63, 0.95), navyCushion: std(0x34446f, 0.95),
    leather: std(0x2a2a2f, 0.36), beanbag: std(0x3a2820, 0.7),
    darkwood: std(0x4a2f20, 0.5), gold: std(0xc9a24a, 0.3, { metalness: 0.8 }), beige: std(0xd8c3a2, 0.6),
    frosted: std(0xf4f4f2, 0.4, { transparent: true, opacity: 0.6 }), granite: std(0x5d5955, 0.35),
    chrome: std(0xd0d4d8, 0.15, { metalness: 1 }), mirror: std(0xdde3e8, 0.04, { metalness: 1 }),
    lampshade: std(0xf6eee0, 0.8, { emissive: 0x332a1c }), games: std(0xffffff, 0.7),
    doorWood: std(0x9a6a3e, 0.55), mainDoor: std(0x3b2a22, 0.5),
    record: std(0x7a2e2a, 0.6), vinyl: std(0x0d0d0f, 0.28), label: std(0xd9b44a, 0.6), platter: std(0x8d9196, 0.35, { metalness: 0.6 }),
  };
}

/* One piece of furniture from its plan entry, as a group placed in the world. Pieces with an easter egg keep the
   parts that move in group.userData.parts. */
export function buildModel(f, mat, P) {
  const [x1, y1, x2, y2, h1, h2] = f.box, turn = TURN[f.facing || '+y'], sideways = f.facing === '+x' || f.facing === '-x';
  const w = sideways ? y2 - y1 : x2 - x1, d = sideways ? x2 - x1 : y2 - y1, h = h2 - h1;
  const g = new THREE.Group(), [cx, cz] = P((x1 + x2) / 2, (y1 + y2) / 2);
  g.position.set(cx, h1, cz); g.rotation.y = turn;
  const main = mat[f.material] || mat.white, parts = g.userData.parts = {};
  // a box by its local extents: x across the width, y up, z from back (-d/2) to front (+d/2)
  const b = (ax, bx, ay, by, az, bz, m = main, shadow = true) => {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(bx - ax, by - ay, bz - az), m);
    mesh.position.set((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2);
    mesh.castShadow = shadow; mesh.receiveShadow = true; g.add(mesh); return mesh;
  };
  const cyl = (r1, r2, len, x, y, z, m = main, seg = 18) => {
    const mesh = new THREE.Mesh(new THREE.CylinderGeometry(r1, r2, len, seg), m);
    mesh.position.set(x, y, z); mesh.castShadow = true; g.add(mesh); return mesh;
  };
  const W = w / 2, Dp = d / 2;
  // which end of the width a compass side is: local +x points along (cos t, -sin t) in plan x, y
  const end = side => { const [px, py] = COMPASS[side]; return px * Math.cos(turn) - py * Math.sin(turn) > 0 ? 1 : -1; };
  const legs = (inset, t, top, m = main) => { for (const sx of [-1, 1]) for (const sz of [-1, 1]) b(sx * (W - inset) - t / 2, sx * (W - inset) + t / 2, 0, top, sz * (Dp - inset) - t / 2, sz * (Dp - inset) + t / 2, m); };

  ({
    'tv-unit'() {        // floating: dark body, four drawer fronts, the two end ones light with gold knobs
      b(-W, W, 0, h, -Dp, Dp - 0.04);
      for (let i = 0; i < 4; i++) {
        const a = -W + i * w / 4 + 0.03, z = i === 0 || i === 3;
        b(a, a + w / 4 - 0.06, 0.05, h - 0.05, Dp - 0.04, Dp, z ? mat.beige : mat.dark, false);
        if (z) { const k = new THREE.Mesh(new THREE.SphereGeometry(0.06, 10, 8), mat.gold); k.position.set(a + w / 8, h * 0.6, Dp + 0.03); g.add(k); }
      }
    },
    'shoe-rack'() {      // stacked see-through cubes on a white frame
      for (let row = 0; row < 3; row++) for (const s of [-1, 1]) b(s > 0 ? 0.03 : -W, s > 0 ? W : -0.03, row * h / 3 + 0.03, (row + 1) * h / 3 - 0.03, -Dp, Dp, mat.frosted);
      for (const s of [-1, 1]) b(s * W - 0.03, s * W + 0.03, 0, h, -Dp, Dp, mat.white);
    },
    desk() {
      b(-W, W, h - 0.1, h, -Dp, Dp);
      for (const s of [-1, 1]) b(s > 0 ? W - 0.08 : -W, s > 0 ? W : -W + 0.08, 0, h - 0.1, -Dp + 0.05, Dp - 0.05);
      b(-W + 0.1, W - 0.1, h * 0.35, h - 0.1, -Dp + 0.05, -Dp + 0.13);
      b(W - 1.3, W - 0.1, h - 0.45, h - 0.1, -Dp + 0.15, Dp - 0.1);     // a drawer under one end
    },
    sofa() {             // base, seat and back cushions, arms on the named sides
      const back = 0.75, seats = Math.max(1, Math.round(w / 2.7));
      const arms = (f.arms || []).map(end), aw = 0.55;
      const left = arms.includes(-1) ? -W + aw : -W, right = arms.includes(1) ? W - aw : W;
      b(-W, W, 0.15, 1.05, -Dp, Dp, mat.navy);
      b(-W, W, 1.05, h - 0.5, -Dp, -Dp + back * 0.55, mat.navy);
      for (let i = 0; i < seats; i++) {
        const a = left + i * (right - left) / seats, z = a + (right - left) / seats;
        b(a + 0.04, z - 0.04, 1.05, 1.45, -Dp + back, Dp - 0.05, mat.navyCushion);
        const c = b(a + 0.06, z - 0.06, 1.3, h + 0.05, -Dp + back * 0.45, -Dp + back + 0.05, mat.navyCushion);
        c.rotation.x = -0.12;
      }
      for (const s of arms) b(s > 0 ? W - aw : -W, s > 0 ? W : -W + aw, 0.15, 2.0, -Dp, Dp, mat.navy);
    },
    table() {
      b(-W, W, h - 0.12, h, -Dp, Dp);
      legs(0.12, 0.16, h - 0.12);
      if (f.shelf) b(-W + 0.12, W - 0.12, h * 0.28, h * 0.28 + 0.06, -Dp + 0.12, Dp - 0.12);
    },
    recliner() {         // chunky leather: base, seat, wide padded arms, a high back that reclines, a footrest that folds out
      const aw = 0.55;
      b(-W + aw, W - aw, 0.2, 1.45, -Dp + 0.6, Dp - 0.05);
      for (const s of [-1, 1]) b(s > 0 ? W - aw : -W, s > 0 ? W : -W + aw, 0.15, 2.25, -Dp + 0.2, Dp, main);
      const back = new THREE.Group(); back.position.set(0, 0.8, -Dp + 0.45); back.rotation.x = -0.1; g.add(back);
      const pad = new THREE.Mesh(new THREE.BoxGeometry(w - 0.2, h - 0.8, 0.85), main); pad.position.set(0, (h - 0.8) / 2, 0); pad.castShadow = true; back.add(pad);
      b(-W + aw + 0.05, W - aw - 0.05, 1.45, 1.75, -Dp + 0.7, Dp - 0.1);
      const foot = new THREE.Group(); foot.position.set(0, 1.25, Dp); g.add(foot);      // hangs down folded; swings up to recline
      const rest = new THREE.Mesh(new THREE.BoxGeometry(w - 2 * aw - 0.1, 1.0, 0.28), main); rest.position.set(0, -0.5, 0.14); rest.castShadow = true; foot.add(rest);
      Object.assign(parts, { back, foot });
    },
    beanbag() {
      const r = Math.min(w, d) / 2, m = new THREE.Mesh(new THREE.SphereGeometry(r, 22, 14), main);
      m.scale.set(1, h / (2 * r) * 1.15, 0.94); m.position.y = h * 0.5; m.castShadow = true; g.add(m);
      parts.bag = m;
    },
    'arc-lamp'() {       // marble foot, a pole and an arc reaching over, a white dome shade
      cyl(0.45, 0.5, 0.12, 0, 0.06, -Dp + 0.5, mat.white, 24);
      const arc = new THREE.QuadraticBezierCurve3(new THREE.Vector3(0, 0.1, -Dp + 0.5), new THREE.Vector3(0, h + 0.9, -Dp + 0.2), new THREE.Vector3(0, h - 0.4, Dp + 2.4));
      const tube = new THREE.Mesh(new THREE.TubeGeometry(arc, 24, 0.035, 6), mat.white); tube.castShadow = true; g.add(tube);
      const shade = new THREE.Mesh(new THREE.SphereGeometry(0.45, 18, 10, 0, Math.PI * 2, 0, Math.PI / 2), mat.lampshade);
      shade.material = shade.material.clone(); shade.material.side = THREE.DoubleSide; shade.position.set(0, h - 0.55, Dp + 2.4); g.add(shade);
      parts.shade = shade;
    },
    armchair() {         // wooden frame, slatted back, a cushion
      legs(0.1, 0.13, 1.4);
      b(-W + 0.05, W - 0.05, 1.25, 1.4, -Dp + 0.05, Dp - 0.05);
      b(-W + 0.12, W - 0.12, 1.4, 1.6, -Dp + 0.2, Dp - 0.1, mat.beige);
      for (const s of [-1, 1]) { b(s * (W - 0.07) - 0.07, s * (W - 0.07) + 0.07, 1.4, 2.2, -Dp + 0.05, Dp - 0.05); b(s * (W - 0.1) - 0.05, s * (W - 0.1) + 0.05, 1.4, h, -Dp + 0.02, -Dp + 0.14); }
      for (let i = 1; i < 5; i++) b(-W + i * w / 5 - 0.06, -W + i * w / 5 + 0.06, 1.6, h - 0.15, -Dp + 0.03, -Dp + 0.11);
      b(-W + 0.1, W - 0.1, h - 0.15, h, -Dp + 0.02, -Dp + 0.14);
    },
    'drawer-table'() {   // thin black frame, a stack of white drawers under one side
      b(-W, W, h - 0.08, h, -Dp, Dp); legs(0.05, 0.08, h - 0.08);
      b(-W + 0.15, -W + w * 0.45, 0.1, h - 0.2, -Dp + 0.1, Dp - 0.1, mat.white);
      for (let i = 1; i < 4; i++) b(-W + 0.15, -W + w * 0.45, 0.1 + i * (h - 0.3) / 4 - 0.01, 0.1 + i * (h - 0.3) / 4 + 0.01, Dp - 0.1, Dp - 0.08, mat.dark, false);
    },
    chair() {            // dining chair: four legs, seat, a back of three rails behind
      legs(0.1, 0.12, 1.45); b(-W + 0.05, W - 0.05, 1.45, 1.55, -Dp + 0.05, Dp - 0.05);
      for (const s of [-1, 1]) b(s * (W - 0.1) - 0.06, s * (W - 0.1) + 0.06, 1.55, h, -Dp + 0.04, -Dp + 0.16);
      for (const y of [h - 0.12, h - 0.55, h - 0.95]) b(-W + 0.1, W - 0.1, y - 0.12, y, -Dp + 0.05, -Dp + 0.15);
    },
    shelf() {            // black cubbies, two rows of four, full of board games
      const t = 0.06, rows = 2, cols = 4;
      b(-W, W, 0, t, -Dp, Dp); b(-W, W, h - t, h, -Dp, Dp); b(-W, W, 0, h, -Dp, -Dp + t);
      for (let i = 0; i <= cols; i++) b(-W + i * (w - t) / cols, -W + i * (w - t) / cols + t, 0, h, -Dp, Dp);
      b(-W, W, h / 2 - t / 2, h / 2 + t / 2, -Dp, Dp);
      const rand = seeded(7), n = rows * cols * 4, games = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), mat.games, n);
      const palette = [0xb23a48, 0x2f6690, 0xe0a458, 0x3a7d44, 0xf2efe9, 0x6c4f8a, 0xd9b44a], m4 = new THREE.Matrix4(), col = new THREE.Color();
      let k = 0;
      for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++) {
        const cw = (w - t) / cols - t, x0 = -W + t + c * (w - t) / cols, y0 = r * h / 2 + t, ch = h / 2 - 1.5 * t;
        for (let j = 0; j < 4; j++, k++) {
          const gw = cw / 4 - 0.02, gh = ch * (0.75 + rand() * 0.2);
          m4.compose(new THREE.Vector3(x0 + (j + 0.5) * cw / 4, y0 + gh / 2, -Dp + t + (d - t) * 0.45), new THREE.Quaternion(), new THREE.Vector3(gw, gh, (d - t) * 0.85));
          games.setMatrixAt(k, m4); games.setColorAt(k, col.setHex(palette[Math.floor(rand() * palette.length)]));
        }
      }
      g.add(games);
      parts.games = games;
    },
    basin() {            // wall-hung: a grey slab, a white bowl with a tap, and the round mirror above
      b(-W + 0.1, W - 0.1, 2.55, 2.65, -Dp, Dp - 0.1, mat.granite);
      cyl(0.55, 0.42, 0.42, 0, 2.86, 0.05, mat.white, 24);
      cyl(0.04, 0.04, 0.45, 0, 2.95, -Dp + 0.2, mat.chrome, 8);
      const mirror = new THREE.Mesh(new THREE.CircleGeometry(0.62, 32), mat.mirror); mirror.position.set(0, 4.6, -Dp + 0.03); g.add(mirror);
    },
    sideboard() {        // dark wood with rounded ends, a black top, four doors with gold knobs
      const r = Dp;
      b(-W + r, W - r, 0.25, h - 0.08, -Dp, Dp);
      for (const s of [-1, 1]) cyl(r, r, h - 0.33, s * (W - r), 0.25 + (h - 0.33) / 2, 0, main, 24);
      const top = new THREE.Mesh(new THREE.CapsuleGeometry(r + 0.02, w - 2 * r, 4, 16), mat.granite);
      top.rotation.z = Math.PI / 2; top.scale.set(0.08 / (r + 0.02), 1, 1); top.position.set(0, h - 0.04, 0); g.add(top);
      b(-W + r, W - r, 0, 0.25, -Dp + 0.15, Dp - 0.15, mat.dark);
      for (let i = 0; i < 4; i++) {
        const a = -W + r + i * (w - 2 * r) / 4;
        b(a + 0.02, a + 0.04, 0.35, h - 0.2, Dp - 0.01, Dp + 0.01, mat.dark, false);
        const k = new THREE.Mesh(new THREE.SphereGeometry(0.05, 10, 8), mat.gold); k.position.set(a + (w - 2 * r) / 8, h * 0.62, Dp + 0.04); g.add(k);
      }
    },
    'folding-chair'() {  // black steel: crossed legs, a plastic seat and back; folds flat (parts: rear legs, seat)
      const rear = [], front = [];
      for (const s of [-1, 1]) {
        const a = b(s * (W - 0.1) - 0.03, s * (W - 0.1) + 0.03, 0, h, -0.03, 0.03); a.rotation.x = 0.22; a.position.z = -0.1; front.push(a);
        const c = b(s * (W - 0.1) - 0.03, s * (W - 0.1) + 0.03, 0, 1.6, -0.03, 0.03); c.rotation.x = -0.35; c.position.set(c.position.x, 0.78, 0.15); rear.push(c);
      }
      const seat = new THREE.Group(); seat.position.set(0, 1.5, -Dp + 0.25); g.add(seat);     // hinged at its back edge
      const plate = new THREE.Mesh(new THREE.BoxGeometry(w - 0.2, 0.07, d - 0.35), main); plate.position.set(0, 0, (d - 0.35) / 2); plate.castShadow = true; seat.add(plate);
      const back = b(-W + 0.15, W - 0.15, h - 0.8, h - 0.05, -Dp + 0.05, -Dp + 0.11); back.rotation.x = -0.22;
      Object.assign(parts, { rear, front, seat });
    },
    'record-player'() {  // a portable suitcase player: open lid behind, platter, a record that spins, a tonearm
      b(-W, W, 0, 0.32, -Dp, Dp);
      const lid = b(-W, W, 0.32, 0.36, -Dp, Dp - 0.05); lid.geometry.translate(0, 0, (d - 0.05) / 2); lid.position.z = -Dp;
      lid.rotation.x = -1.68; lid.position.y = 0.36;
      cyl(0.5, 0.5, 0.04, -0.12, 0.34, 0.05, mat.platter, 32);
      const record = new THREE.Group(); record.position.set(-0.12, 0.37, 0.05); g.add(record);
      const disc = new THREE.Mesh(new THREE.CylinderGeometry(0.47, 0.47, 0.02, 40), mat.vinyl); record.add(disc);
      const label = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.15, 0.025, 20), mat.label); record.add(label);
      const nub = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.03, 0.12), mat.white); nub.position.set(0.3, 0.015, 0); record.add(nub);   // so you see it turn
      const arm = new THREE.Group(); arm.position.set(W - 0.12, 0.42, -Dp + 0.18); g.add(arm);
      const rod = new THREE.Mesh(new THREE.BoxGeometry(0.035, 0.035, 0.75), mat.chrome); rod.position.set(0, 0, 0.36); arm.add(rod);
      const head = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.05, 0.12), mat.dark); head.position.set(0, -0.02, 0.76); arm.add(head);
      cyl(0.05, 0.05, 0.1, W - 0.12, 0.37, -Dp + 0.18, mat.chrome, 10);
      Object.assign(parts, { record, arm });
    },
  }[f.model] || (() => b(-W, W, 0, h, -Dp, Dp)))();
  return g;
}

/* What you see from the dining balcony, built once, the first time you walk. Plan y is negative to the north (the
   balcony's wall is y = 0); `ground` is how far the street is below this floor. Returns the group, a night setter
   for the windows' glow, what a tap can land on (the tree, the school), the birds, and the school's windows. */
export function buildOutside(P, ground) {
  const g = new THREE.Group(), std = (color, roughness = 0.95, extra = {}) => new THREE.MeshStandardMaterial({ color, roughness, ...extra });
  const at = (x, y, h) => { const [wx, wz] = P(x, y); return new THREE.Vector3(wx, h, wz); };
  const block = (x1, y1, x2, y2, hBottom, hTop, m) => {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(x2 - x1, hTop - hBottom, y2 - y1), m);
    mesh.position.copy(at((x1 + x2) / 2, (y1 + y2) / 2, (hBottom + hTop) / 2)); g.add(mesh); return mesh;
  };
  const canvasTex = (w, h, draw) => {
    const c = document.createElement('canvas'); c.width = w; c.height = h; draw(c.getContext('2d'), w, h);
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
  };
  const rand = seeded(42);

  // the ground: a wide plain, the bare mud lot in front with rubble, our own building under the flat
  const plain = new THREE.Mesh(new THREE.PlaneGeometry(900, 900), std(0x2b2f27)); plain.rotation.x = -Math.PI / 2; plain.position.copy(at(22, -100, ground - 0.05)); g.add(plain);
  const mud = new THREE.Mesh(new THREE.PlaneGeometry(150, 58), std(0x5e4e3d)); mud.rotation.x = -Math.PI / 2; mud.position.copy(at(25, -30, ground)); g.add(mud);
  const rubble = new THREE.InstancedMesh(new THREE.DodecahedronGeometry(1, 0), std(0x8a8378, 0.9), 40), m4 = new THREE.Matrix4(), q = new THREE.Quaternion();
  for (let i = 0; i < 40; i++) {
    const s = 0.3 + rand() * 0.9;
    m4.compose(at(-10 + rand() * 70, -12 - rand() * 42, ground + s * 0.3), q.setFromEuler(new THREE.Euler(rand() * 3, rand() * 3, 0)), new THREE.Vector3(s, s * 0.6, s));
    rubble.setMatrixAt(i, m4);
  }
  g.add(rubble);
  block(0, 0, 45, 41, ground, -0.3, std(0xbdb6aa, 0.9));
  // a corrugated metal roof just below the balcony
  const ribs = canvasTex(64, 8, (c, w, h) => { const grd = c.createLinearGradient(0, 0, w, 0); grd.addColorStop(0, '#5d6266'); grd.addColorStop(0.5, '#8b9094'); grd.addColorStop(1, '#5d6266'); c.fillStyle = grd; c.fillRect(0, 0, w, h); });
  ribs.wrapS = ribs.wrapT = THREE.RepeatWrapping; ribs.repeat.set(30, 1);
  const roof = new THREE.Mesh(new THREE.PlaneGeometry(32, 10), std(0xffffff, 0.6, { map: ribs, metalness: 0.4 }));
  roof.rotation.x = -Math.PI / 2 + 0.08; roof.position.copy(at(24, -5.5, ground + 9)); g.add(roof);
  // the compound wall, then the school: four storeys of weathered grey concrete
  block(-40, -58.6, 95, -58, ground, ground + 7, std(0x77736c));
  const SW = 112, SH = 48, windows = [];
  const cols = [0.07, 0.13, 0.33, 0.42, 0.55, 0.64, 0.79, 0.89], stair = 0.23;
  for (let f = 0; f < 4; f++) for (const c of cols) windows.push([c, 0.16 + f * 0.21]);
  for (let f = 0; f < 4; f++) windows.push([stair, 0.27 + f * 0.21]);              // the stairwell, half a floor up
  const facade = canvasTex(1024, 440, (c, w, h) => {
    c.fillStyle = '#8f8a84'; c.fillRect(0, 0, w, h);
    for (let i = 0; i < 60; i++) {                     // rain stains and patches
      c.fillStyle = `rgba(${rand() < 0.5 ? '60,58,55' : '190,185,176'},${0.05 + rand() * 0.1})`;
      c.beginPath(); c.ellipse(rand() * w, rand() * h, 20 + rand() * 90, 8 + rand() * 40, rand(), 0, Math.PI * 2); c.fill();
    }
    c.fillStyle = '#6d6862'; c.fillRect(0, h * 0.94, w, h * 0.06);
    for (const [fx, fy] of windows) {
      const x = fx * w, y = h - fy * h - 34;
      c.fillStyle = '#5a5650'; c.fillRect(x - 3, y - 6, 54, 4);        // the little sunshade over each window
      c.fillStyle = '#d9cfbd'; c.fillRect(x, y, 48, 40);
      c.fillStyle = '#b8a888'; for (let s = 4; s < 40; s += 4) c.fillRect(x + 3, y + s, 42, 1.5);
      c.strokeStyle = '#eceae4'; c.lineWidth = 2; c.strokeRect(x, y, 48, 40);
    }
  });
  const lights = windows.map(() => ({ on: rand() < 0.85, warm: rand() < 0.8 }));
  const paintGlow = (c, w, h) => {
    c.fillStyle = '#000'; c.fillRect(0, 0, w, h);
    windows.forEach(([fx, fy], i) => { if (lights[i].on) { c.fillStyle = lights[i].warm ? '#ffd28a' : '#fff2d6'; c.fillRect(fx * w + 2, h - fy * h - 32, 44, 36); } });
  };
  const glow = canvasTex(1024, 440, paintGlow);
  const concrete = std(0x8a857e);
  const school = std(0xffffff, 0.95, { map: facade, emissive: 0xffffff, emissiveMap: glow, emissiveIntensity: 0 });
  const front = block(-30, -100, -30 + SW, -62, ground, ground + SH, [concrete, concrete, concrete, concrete, school, concrete]);
  const hits = [{ mesh: front, id: 'school' }];
  for (const x of [8, 26]) block(x, -62.6, x + 2.2, -62, ground + 7.5, ground + 9, std(0xe8e8e6, 0.5));        // AC units
  // trees: one big leafy tree near on the left, darker ones on the right
  const leaves = std(0x2f4a2a, 0.9, { flatShading: true }), dark = std(0x1f2d1d, 0.9, { flatShading: true });
  const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.7, 1.1, 22, 8), std(0x4a3b2c)); trunk.position.copy(at(4, -10, ground + 11)); g.add(trunk);
  const crown = (x, y, h, r, m) => { const s = new THREE.Mesh(new THREE.IcosahedronGeometry(r, 1), m); s.position.copy(at(x, y, h)); s.scale.y = 0.8; g.add(s); return s; };
  for (const [x, y, h, r] of [[4, -10, 4, 9], [9, -6, 8, 6.5], [-2, -12, 9, 7], [6, -15, 13, 7], [11, -11, 1, 5.5], [1, -5, 0, 5]]) hits.push({ mesh: crown(x, y, ground + 21 + h, r, leaves), id: 'tree' });
  for (const [x, y, h, r] of [[58, -45, 6, 9], [70, -55, 10, 11], [52, -62, 14, 8], [80, -40, 4, 10]]) crown(x, y, ground + 21 + h, r, dark);
  // an electric pole with sagging wires across the lot
  const poleAt = [48, -52], poleTop = ground + 32;
  const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.4, 32, 8), std(0x55524d)); pole.position.copy(at(...poleAt, ground + 16)); g.add(pole);
  block(poleAt[0] - 3, poleAt[1] - 0.2, poleAt[0] + 3, poleAt[1] + 0.2, poleTop - 1.5, poleTop - 1.1, std(0x3a3835));
  const wireMat = new THREE.LineBasicMaterial({ color: 0x1b1b1b });
  for (const [i, [ex, ey, eh]] of [[-40, -48, ground + 26], [-40, -55, ground + 24], [-38, -50, ground + 28], [48, 30, ground + 27]].entries()) {
    const a = at(poleAt[0] + (i - 1.5) * 1.5, poleAt[1], poleTop - 1.2), b = at(ex, ey, eh), pts = [];
    for (let k = 0; k <= 24; k++) { const t = k / 24; pts.push(a.clone().lerp(b, t).add(new THREE.Vector3(0, -Math.sin(Math.PI * t) * 4, 0))); }
    g.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), wireMat));
  }
  // a tall apartment tower far off to the right, its windows lit at night
  const towerGlow = canvasTex(128, 512, (c, w, h) => {
    c.fillStyle = '#000'; c.fillRect(0, 0, w, h);
    for (let y = 6; y < h; y += 10) for (let x = 4; x < w; x += 12) if (rand() < 0.45) { c.fillStyle = rand() < 0.7 ? '#fff4dc' : '#ffd79a'; c.fillRect(x, y, 7, 5); }
  });
  const tower = std(0x9da3ab, 0.8, { emissive: 0xffffff, emissiveMap: towerGlow, emissiveIntensity: 0 });
  block(100, -300, 150, -250, ground, ground + 240, tower);

  // a few birds that live in the big tree: hidden until it's tapped, then they flap up and away over the lot
  const wing = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(-0.5, 0, 0), new THREE.Vector3(0, 0, 0.18), new THREE.Vector3(0, 0, -0.18),
    new THREE.Vector3(0.5, 0, 0), new THREE.Vector3(0, 0, -0.18), new THREE.Vector3(0, 0, 0.18)]);
  wing.computeVertexNormals();
  const birdMat = new THREE.MeshBasicMaterial({ color: 0x15171a, side: THREE.DoubleSide });
  const flock = Array.from({ length: 7 }, () => { const m = new THREE.Mesh(wing, birdMat); m.visible = false; g.add(m); return { m, v: new THREE.Vector3(), phase: rand() * 6 }; });
  let flying = 0;
  const birds = {
    start() {
      flying = 4;
      for (const b of flock) {
        b.m.position.copy(at(3 + rand() * 8, -12 + rand() * 8, ground + 28 + rand() * 6)); b.m.visible = true;
        b.v.set(4 + rand() * 6, 5 + rand() * 4, -8 - rand() * 8);
      }
    },
    step(dt) {                                  // true while they're still in the air
      if (flying <= 0) return false;
      flying -= dt;
      for (const b of flock) {
        b.m.position.addScaledVector(b.v, dt); b.phase += dt * 14;
        b.m.scale.set(1.6, 1, 1.6 * (0.35 + 0.65 * Math.abs(Math.sin(b.phase))));   // the wings beat
        b.m.rotation.y = Math.atan2(b.v.x, b.v.z);
        if (flying <= 0) b.m.visible = false;
      }
      return flying > 0;
    },
    get flying() { return flying > 0; },
  };
  // the school's windows: a tap on a lit one at night switches it off, again switches it back on
  const windowAt = uv => {
    const cx = uv.x * 1024, cy = (1 - uv.y) * 440;
    return windows.findIndex(([fx, fy]) => cx >= fx * 1024 - 4 && cx <= fx * 1024 + 52 && cy >= 440 - fy * 440 - 38 && cy <= 440 - fy * 440 + 10);
  };
  const schoolWindows = {
    get lit() { return lights.filter(l => l.on).length; },
    toggle(uv) {
      const i = windowAt(uv);
      if (i < 0) return false;
      lights[i].on = !lights[i].on; paintGlow(glow.image.getContext('2d'), 1024, 440); glow.needsUpdate = true;
      return true;
    },
    centre(n) {                                 // the n-th lit window's middle, in the world
      const i = lights.map((l, k) => l.on ? k : -1).filter(k => k >= 0)[n];
      if (i == null) return null;
      const [fx, fy] = windows[i];
      return at(-30 + (fx + 24 / 1024) * SW, -61.9, ground + (fy + 14 / 440) * SH);
    },
  };

  return {
    group: g, hits, birds, windows: schoolWindows,
    night(k) { school.emissiveIntensity = 1.1 * k; tower.emissiveIntensity = 1.3 * k; },
  };
}
