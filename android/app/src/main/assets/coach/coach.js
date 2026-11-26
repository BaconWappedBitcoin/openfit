// The virtual coach: a Quaternius character (CC0) running ahead of the runner, seen from behind,
// with Quaternius UAL1 loops (CC0). OpenFit calls window.ofitCoach(kph, running) about twice a
// second; the coach walks, jogs or sprints to match, and stands when the workout is paused.
import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";

const params = new URLSearchParams(location.search);
const who = params.get("who") === "male" ? "male" : "female";
// Outfit: race (singlet, bib; the default), classic (T-shirt and shorts), tank or tights (long sleeves and leggings).
// The race kit is the coach's outfit (user's pick from the mockups, 2026-11-24); the others stay for previews.
const style = ["classic", "tank", "tights", "race"].includes(params.get("style")) ? params.get("style") : "race";
const suit = new THREE.Color("#" + (params.get("color") || "b39ddb").replace(/[^0-9a-fA-F]/g, "").slice(0, 6));
// Colours from the coach designer (0xRRGGBB as hex); bottom falls back to the outfit's own.
const colourParam = (name) => {
  const v = (params.get(name) || "").replace(/[^0-9a-fA-F]/g, "").slice(0, 6);
  return v.length === 6 ? new THREE.Color("#" + v) : null;
};
const bottomColour = colourParam("bottom");
const skinColour = colourParam("skin") || new THREE.Color(0xc68c6a);
const hairParam = colourParam("hair") || new THREE.Color(0x2b2018);

const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setClearColor(0x000000, 0);
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 50);
// Behind and a touch to the left: the coach sits just left of the middle of the screen, so seen from
// here it runs straight ahead into the video, where the path goes.
camera.position.set(-0.35, 1.6, -3.6);
camera.lookAt(0.05, 0.95, 0.6);

scene.add(new THREE.HemisphereLight(0xffffff, 0x445566, 1.6));
// A rim light from ahead, so the figure stands out against any video.
const rim = new THREE.DirectionalLight(0xffffff, 2.5);
rim.position.set(-1.5, 2.5, 4);
scene.add(rim);
const sun = new THREE.DirectionalLight(0xffffff, 2.2);
sun.position.set(2, 4, -3);
scene.add(sun);

// A soft shadow under the feet, so the coach stands on the path rather than floating.
const shadowCanvas = document.createElement("canvas");
shadowCanvas.width = shadowCanvas.height = 128;
const g = shadowCanvas.getContext("2d");
const grad = g.createRadialGradient(64, 64, 4, 64, 64, 64);
grad.addColorStop(0, "rgba(0,0,0,0.55)");
grad.addColorStop(1, "rgba(0,0,0,0)");
g.fillStyle = grad;
g.fillRect(0, 0, 128, 128);
const shadow = new THREE.Mesh(
  new THREE.PlaneGeometry(1.1, 1.1),
  new THREE.MeshBasicMaterial({ map: new THREE.CanvasTexture(shadowCanvas), transparent: true, depthWrite: false }),
);
shadow.rotation.x = -Math.PI / 2;
shadow.position.y = 0.01;
scene.add(shadow);

function resize() {
  const w = window.innerWidth, h = window.innerHeight;
  renderer.setSize(w, h, false);
  camera.aspect = w / Math.max(h, 1);
  camera.updateProjectionMatrix();
}
window.addEventListener("resize", resize);
resize();

let baseY = 0;
let mixer = null, actions = {}, current = null, coach = null, feet = [], groundShift = 0, lowest = [];
let want = { kph: 0, running: false, pace: 1 };

// Gait from speed, with a margin either side of each change so the coach does not flick between
// walking and jogging at a speed right on the line.
const PACE_BOOST = 1.1;
let gait = "Idle_Loop";
function pick(kph, running) {
  if (!running || kph < 0.5) gait = "Idle_Loop";
  else if (gait === "Idle_Loop") gait = kph < 6.3 ? "Walk_Loop" : kph < 10.5 ? "Jog_Fwd_Loop" : "Sprint_Loop";
  else if (gait === "Walk_Loop" && kph >= 6.6) gait = "Jog_Fwd_Loop";
  else if (gait === "Jog_Fwd_Loop" && kph < 6.0) gait = "Walk_Loop";
  else if (gait === "Jog_Fwd_Loop" && kph >= 10.8) gait = "Sprint_Loop";
  else if (gait === "Sprint_Loop" && kph < 10.2) gait = "Jog_Fwd_Loop";
  // Stride rate from real cadences (steps a minute): walking about 112 at 4 km/h and 130 at 6,
  // running about 174 at 8 km/h and 189 at 13 (a touch brisk, so it never looks slow on screen). Each loop is two steps, so the loop's time scale is
  // its length times the strides a second.
  if (gait === "Idle_Loop") return [gait, 1];
  const spm = gait === "Walk_Loop" ? clamp(76 + 9 * kph, 90, 140) : clamp(150 + 3 * kph, 160, 200);
  const loop = actions[gait] ? actions[gait].getClip().duration : 1;
  // Seen over a video the coach looked slow at true cadences (the scenery rushes past a figure
  // running on the spot), so it steps a tenth faster than life (user reports 2026-11-24).
  return [gait, clamp((spm * PACE_BOOST * want.pace / 120) * loop, 0.3, 4)];
}
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

// The stride speed eases towards its target every frame (see the loop below), and a change of gait
// blends over about 1.5 s with the strides kept in step, so walking turns into running gradually.
let targetScale = 1;
function apply() {
  if (!mixer) return;
  const [name, scale] = pick(want.kph, want.running);
  const next = actions[name];
  if (!next) return;
  targetScale = scale;
  if (current === next) return;
  next.reset();
  next.timeScale = current ? current.timeScale : scale;
  next.play();
  if (current) current.crossFadeTo(next, 1.5, true);
  current = next;
}

function easeStride(dt) {
  if (!current) return;
  const k = Math.min(1, dt * 1.2); // about a second to settle
  current.timeScale += (targetScale - current.timeScale) * k;
}

// pace (Coach pace) scales only how fast the legs move; walk, jog or sprint follows the speed itself
// (user 2026-11-24: a low pace had the coach walking at 7.5 mph).
window.ofitCoach = function (kph, running, pace) {
  want = { kph: Number(kph) || 0, running: !!running, pace: clamp(Number(pace) || 1, 0.3, 3) };
  apply();
};

/**
 * Dress the coach as a runner. The base body only has underwear, so each vertex is placed by where
 * it is on the posed body (t: 0 at the feet, 1 at the top of the head; side: how far out along the
 * arms). The skin, hair and face are coloured on the body. The clothes are separate garments: copies
 * of the body's shirt, shorts and shoe areas pushed out a little along the surface and skinned to the
 * same bones, so they have their own thickness and edges and move with the body instead of looking
 * sprayed on (user request 2026-11-24), with panels, trim and stripes in the theme colour.
 */
function dress(model, height) {
  const skin = skinColour, hair = hairParam, eyes = new THREE.Color(0x111111);
  const white = new THREE.Color(0xf4f4f4), charcoal = new THREE.Color(0x24272e), sock = new THREE.Color(0xeeeeee);
  const bodyMaterial = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.55, metalness: 0.0 });
  const fabric = new THREE.MeshPhysicalMaterial({
    vertexColors: true, roughness: 0.82, metalness: 0.0, sheen: 0.6, sheenRoughness: 0.45, sheenColor: new THREE.Color(0xffffff),
  });
  // The shorts: matte, no sheen, so they read the same as the body under them at the creases.
  const shortsFabric = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.85, metalness: 0.0 });
  const rubber = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.45, metalness: 0.0 });
  const v = new THREE.Vector3();
  model.updateMatrixWorld(true);
  // Where the head is front to back, to tell the back of the head (hair) from the face. The coach
  // faces +z, into the video; the camera sees the back of the head.
  let headZ = 0, headCount = 0;
  headBack = Infinity;
  model.traverse((o) => {
    if (!o.isSkinnedMesh) return;
    const pos = o.geometry.attributes.position;
    for (let i = 0; i < pos.count; i++) {
      v.fromBufferAttribute(pos, i);
      o.applyBoneTransform(i, v);
      v.applyMatrix4(o.matrixWorld);
      if (v.y / height > 0.9) { headZ += v.z; headCount++; headBack = Math.min(headBack, v.z); }
    }
  });
  headZ = headCount ? headZ / headCount : 0;
  hairColour = hair;
  const hairLow = who === "female" ? 0.855 : 0.905;

  const garments = [];
  model.traverse((o) => {
    if (!o.isSkinnedMesh) return;
    const name = ((o.name || "") + " " + ((o.material && o.material.name) || "")).toLowerCase();
    const pos = o.geometry.attributes.position;
    const colours = new Float32Array(pos.count * 3);
    // What each vertex is part of: 0 skin, 1 shirt, 2 shorts, 3 shoe; and the garment's colour.
    const part = new Uint8Array(pos.count);
    const cloth = new Float32Array(pos.count * 3);
    const isBody = !name.includes("eye") && !name.includes("brow") && !name.includes("face");
    const posed = new Float32Array(pos.count * 3); // where each vertex is on the standing coach
    for (let i = 0; i < pos.count; i++) {
      v.fromBufferAttribute(pos, i);
      o.applyBoneTransform(i, v);
      v.applyMatrix4(o.matrixWorld);
      posed[i * 3] = v.x; posed[i * 3 + 1] = v.y; posed[i * 3 + 2] = v.z;
      const t = v.y / height;
      const side = Math.abs(v.x) / height;
      const back = v.z < headZ;
      let c = null, p = 0, g = null;
      if (name.includes("eye") || name.includes("brow")) c = eyes;
      // Hair: part 4, a layer of its own with a strand texture (see addHair).
      else if (t > 0.955) { p = 4; g = hair; }
      // Back of the head only, behind the ears, so the ears and face keep the skin colour.
      else if (t > hairLow && side < 0.06 && (v.z - headZ) / height < -0.012) { p = 4; g = hair; }
      else if (t > 0.93 && side < 0.06 && (v.z - headZ) / height < 0.0) { p = 4; g = hair; }
      else if (t > 0.86 || (t > 0.8 && side < 0.055)) c = skin;      // head and neck (shoulders stay in the shirt)
      else if (side > 0.12 && t > 0.70) {                             // arms
        const sleeveEnd = style === "tights" ? 0.40 : style === "tank" || style === "race" ? 0 : 0.2;
        if (side < sleeveEnd) { p = 1; g = side > sleeveEnd - 0.015 ? white : suit; } // sleeve, white cuff
        else c = skin;
      } else if (side > 0.12) c = skin;                               // hands
      else if ((style === "tank" || style === "race") && side > 0.1 && t > 0.74) c = skin; // bare shoulders
      else if (t > 0.585) {                                           // top
        p = 1;
        // The race kit's singlet is plain white: its number bib is a separate patch (addBib), as
        // painted bands and bibs blurred into blobs on this body's large triangles.
        if (style === "race") g = white;
        else if (t > 0.815 && side < 0.035) g = white;              // collar
        else if (back && t > 0.738 && t < 0.75 && side < 0.07) g = white; // reflective stripe across the back
        else g = suit;
      } else if (t > (style === "tights" ? 0.09 : 0.44)) {            // shorts, or full-length tights
        p = 2;
        if (t > 0.575) g = style === "race" ? white : suit;           // waistband
        else if (bottomColour) g = bottomColour;                      // the designer's choice
        else if (style === "race") g = suit;                          // race shorts in the top colour
        else g = charcoal;
      } else if (t > 0.075) c = skin;                                 // legs
      else if (t > 0.055) c = sock;                                   // sock cuff
      else { p = 3; g = t < 0.014 ? suit : white; }                  // shoe: white upper, coloured sole
      if (!isBody) p = 0;
      const col = g || c;
      colours[i * 3] = col.r; colours[i * 3 + 1] = col.g; colours[i * 3 + 2] = col.b;
      part[i] = p;
      if (g) { cloth[i * 3] = g.r; cloth[i * 3 + 1] = g.g; cloth[i * 3 + 2] = g.b; }
    }
    o.geometry.setAttribute("color", new THREE.BufferAttribute(colours, 3));
    o.material = bodyMaterial;
    if (isBody) garments.push({ o, part, cloth, posed });
  });
  // The garments, made after the walk over the model so the new meshes are not walked too.
  for (const { o, part, cloth, posed } of garments) {
    o.geometry.computeBoundingBox();
    const gs = o.geometry.boundingBox.getSize(new THREE.Vector3());
    const perMetre = Math.max(gs.x, gs.y, gs.z) / height; // geometry units in a metre
    addGarment(o, part, cloth, 1, 0.013 * perMetre, fabric);   // shirt
    addGarment(o, part, cloth, 2, 0.013 * perMetre, shortsFabric); // shorts, a little looser
    addGarment(o, part, cloth, 3, 0.005 * perMetre, rubber);   // shoes
    if (style === "race") addBib(o, posed, height, headZ, 0.017 * perMetre);
    addHair(o, part, posed, height, headZ, hairLow, 0.004 * perMetre);
  }
}

/** One garment: the triangles of [which] part, pushed out along the surface by [offset]. */
function addGarment(body, part, cloth, which, offset, material) {
  const src = body.geometry;
  if (!src.index || !src.attributes.normal) return;
  const index = src.index.array;
  const kept = [];
  for (let i = 0; i < index.length; i += 3) {
    const a = index[i], b = index[i + 1], c = index[i + 2];
    if (part[a] === which && part[b] === which && part[c] === which) kept.push(a, b, c);
  }
  if (kept.length === 0) return;
  const geo = new THREE.BufferGeometry();
  const pos = src.attributes.position, nrm = src.attributes.normal;
  const out = new Float32Array(pos.count * 3);
  for (let i = 0; i < pos.count; i++) {
    out[i * 3] = pos.getX(i) + nrm.getX(i) * offset;
    out[i * 3 + 1] = pos.getY(i) + nrm.getY(i) * offset;
    out[i * 3 + 2] = pos.getZ(i) + nrm.getZ(i) * offset;
  }
  geo.setAttribute("position", new THREE.BufferAttribute(out, 3));
  geo.setAttribute("normal", nrm);
  geo.setAttribute("color", new THREE.BufferAttribute(cloth, 3));
  geo.setAttribute("skinIndex", src.attributes.skinIndex);
  geo.setAttribute("skinWeight", src.attributes.skinWeight);
  geo.setIndex(kept);
  const mesh = new THREE.SkinnedMesh(geo, material);
  mesh.userData.garment = true;
  mesh.frustumCulled = false;
  mesh.position.copy(body.position);
  mesh.quaternion.copy(body.quaternion);
  mesh.scale.copy(body.scale);
  body.parent.add(mesh);
  mesh.bind(body.skeleton, body.bindMatrix);
  console.log("garment " + which + ": " + kept.length / 3 + " triangles");
}

let hairColour = new THREE.Color(0x2b2018);

/**
 * Hair that reads as hair, not clay (user 2026-11-25). The owner's wicklight answer for its trees:
 * a painted texture with real detail on its own layer, matte, never one flat colour. So: many fine
 * strands in lighter and darker shades of the hair colour, running from the hairline to the crown,
 * with a soft sheen band where light catches hair, on a thin layer over the scalp.
 */
function hairTexture(colour) {
  const c = document.createElement("canvas");
  c.width = 256; c.height = 256;
  const g = c.getContext("2d");
  const hex = (k) => "#" + colour.clone().multiplyScalar(k).getHexString();
  g.fillStyle = hex(0.7);
  g.fillRect(0, 0, 256, 256);
  let r = 7;
  const rnd = () => ((r = (r * 9301 + 49297) % 233280) / 233280);
  for (let i = 0; i < 900; i++) {
    const x = rnd() * 256, w = 0.6 + rnd() * 1.6, k = 0.45 + rnd() * 1.1, sway = (rnd() - 0.5) * 10;
    g.strokeStyle = hex(k);
    g.globalAlpha = 0.35 + rnd() * 0.5;
    g.lineWidth = w;
    g.beginPath();
    g.moveTo(x, 256);
    g.bezierCurveTo(x + sway, 170, x - sway, 90, x + sway * 0.5, 0);
    g.stroke();
  }
  // The sheen: a soft light band a third of the way down from the crown.
  const band = g.createLinearGradient(0, 40, 0, 140);
  band.addColorStop(0, "rgba(255,255,255,0)");
  band.addColorStop(0.5, "rgba(255,240,220,0.16)");
  band.addColorStop(1, "rgba(255,255,255,0)");
  g.globalAlpha = 1;
  g.fillStyle = band;
  g.fillRect(0, 0, 256, 256);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.anisotropy = 4;
  return t;
}
// Matte, like wicklight's foliage (no shine), and white so the texture carries the colour.
const hairMaterial = () => new THREE.MeshStandardMaterial({ map: hairTexture(hairColour), roughness: 0.85, metalness: 0 });

/** The hair layer: the scalp's triangles pushed out a little, wearing the strand texture. */
function addHair(body, part, posed, height, headZ, hairLow, offset) {
  const src = body.geometry;
  if (!src.index || !src.attributes.normal) return;
  const index = src.index.array, kept = [];
  for (let i = 0; i < index.length; i += 3) {
    const a = index[i], b = index[i + 1], c = index[i + 2];
    if (part[a] === 4 && part[b] === 4 && part[c] === 4) kept.push(a, b, c);
  }
  if (!kept.length) return;
  const pos = src.attributes.position, nrm = src.attributes.normal;
  const out = new Float32Array(pos.count * 3), uv = new Float32Array(pos.count * 2);
  for (let i = 0; i < pos.count; i++) {
    out[i * 3] = pos.getX(i) + nrm.getX(i) * offset;
    out[i * 3 + 1] = pos.getY(i) + nrm.getY(i) * offset;
    out[i * 3 + 2] = pos.getZ(i) + nrm.getZ(i) * offset;
    // Round the head (three repeats) and from the hairline (v 0) up to the crown (v 1).
    const ang = Math.atan2(posed[i * 3], posed[i * 3 + 2] - headZ);
    uv[i * 2] = (ang / (Math.PI * 2) + 0.5) * 3;
    uv[i * 2 + 1] = Math.min(1, Math.max(0, (posed[i * 3 + 1] / height - hairLow) / (1 - hairLow)));
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.BufferAttribute(out, 3));
  geo.setAttribute("normal", nrm);
  geo.setAttribute("uv", new THREE.BufferAttribute(uv, 2));
  geo.setAttribute("skinIndex", src.attributes.skinIndex);
  geo.setAttribute("skinWeight", src.attributes.skinWeight);
  geo.setIndex(kept);
  const mesh = new THREE.SkinnedMesh(geo, hairMaterial());
  mesh.frustumCulled = false;
  mesh.position.copy(body.position);
  mesh.quaternion.copy(body.quaternion);
  mesh.scale.copy(body.scale);
  body.parent.add(mesh);
  mesh.bind(body.skeleton, body.bindMatrix);
  console.log("hair: " + kept.length / 3 + " triangles");
}

/**
 * The race kit's number bib (user 2026-11-24: the painted one looked smudged). A thin layer over the
 * back of the singlet, skinned to the same bones so it bends with the back as she runs, with a
 * printed card (kit-colour OPENFIT band and number) mapped onto it from behind. Outside the card the
 * layer is transparent, so the bib keeps clean square edges whatever the body's triangles are.
 */
function addBib(body, posed, height, headZ, offset) {
  const src = body.geometry;
  if (!src.index || !src.attributes.normal) return;
  const W = height * 0.12, H = W * 0.75, cy = height * 0.69;
  const inArea = (i) => {
    const x = posed[i * 3], y = posed[i * 3 + 1], z = posed[i * 3 + 2];
    return z < headZ && Math.abs(x) < W * 0.9 && Math.abs(y - cy) < H * 0.9;
  };
  const index = src.index.array, kept = [];
  for (let i = 0; i < index.length; i += 3) {
    const a = index[i], b = index[i + 1], c = index[i + 2];
    if (inArea(a) || inArea(b) || inArea(c)) kept.push(a, b, c);
  }
  if (!kept.length) return;
  const pos = src.attributes.position, nrm = src.attributes.normal;
  const out = new Float32Array(pos.count * 3), uv = new Float32Array(pos.count * 2);
  for (let i = 0; i < pos.count; i++) {
    out[i * 3] = pos.getX(i) + nrm.getX(i) * offset;
    out[i * 3 + 1] = pos.getY(i) + nrm.getY(i) * offset;
    out[i * 3 + 2] = pos.getZ(i) + nrm.getZ(i) * offset;
    // Seen from behind, the coach's -x is on the right of the picture.
    uv[i * 2] = 0.5 - posed[i * 3] / W;
    uv[i * 2 + 1] = 0.5 + (posed[i * 3 + 1] - cy) / H;
  }
  const kit = bottomColour || suit;
  const canvas = document.createElement("canvas");
  canvas.width = 340; canvas.height = 260;
  const g = canvas.getContext("2d");
  // A transparent margin: outside the card, the stretched edge of the picture stays clear.
  const x0 = 10, y0 = 10, w = 320, h = 240, r = 22;
  g.fillStyle = "#ffffff";
  g.beginPath();
  g.moveTo(x0 + r, y0); g.arcTo(x0 + w, y0, x0 + w, y0 + h, r); g.arcTo(x0 + w, y0 + h, x0, y0 + h, r);
  g.arcTo(x0, y0 + h, x0, y0, r); g.arcTo(x0, y0, x0 + w, y0, r);
  g.closePath(); g.fill();
  g.strokeStyle = "#b9bcc4"; g.lineWidth = 6; g.stroke(); // a thin edge, so the card shows on the white singlet
  g.fillStyle = "#" + kit.getHexString();
  g.fillRect(x0 + 3, y0 + 22, w - 6, 34);
  g.fillStyle = "#ffffff";
  g.font = "bold 24px sans-serif"; g.textAlign = "center"; g.textBaseline = "middle";
  g.fillText("OPENFIT", x0 + w / 2, y0 + 40);
  g.fillStyle = "#" + kit.getHexString();
  g.font = "bold 130px sans-serif";
  g.fillText("42", x0 + w / 2, y0 + 150);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 4;
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.BufferAttribute(out, 3));
  geo.setAttribute("normal", nrm);
  geo.setAttribute("uv", new THREE.BufferAttribute(uv, 2));
  geo.setAttribute("skinIndex", src.attributes.skinIndex);
  geo.setAttribute("skinWeight", src.attributes.skinWeight);
  geo.setIndex(kept);
  const material = new THREE.MeshStandardMaterial({ map: texture, transparent: true, alphaTest: 0.4, roughness: 0.9 });
  const mesh = new THREE.SkinnedMesh(geo, material);
  mesh.frustumCulled = false;
  mesh.position.copy(body.position);
  mesh.quaternion.copy(body.quaternion);
  mesh.scale.copy(body.scale);
  body.parent.add(mesh);
  mesh.bind(body.skeleton, body.bindMatrix);
}

/**
 * The woman's ponytail: the base body is bald, so a tapered tail hangs from the back of the head,
 * fixed to the head bone so it moves with every stride.
 */
function addPonytail(model, height) {
  const head = model.getObjectByName("Head");
  if (!head) return;
  model.updateMatrixWorld(true);
  // The same strands as the scalp, running down the tail, and a hair tie in the kit colour.
  const material = hairMaterial();
  material.map.repeat.set(2, 1);
  const group = new THREE.Group();
  const tie = new THREE.Mesh(new THREE.TorusGeometry(height * 0.022, height * 0.008, 8, 20),
    new THREE.MeshStandardMaterial({ color: bottomColour || suit, roughness: 0.6 }));
  tie.rotation.x = Math.PI / 2;
  // A tail that tapers to a point, in a few locks a little apart (a single smooth cone with a
  // ball on the end read as clay), each with a slight bend.
  group.add(tie);
  const lock = (r, len, x, z, bend) => {
    const g = new THREE.CylinderGeometry(r, r * 0.08, len, 12, 8, true);
    const p = g.attributes.position;
    for (let i = 0; i < p.count; i++) {
      const t = 0.5 - p.getY(i) / len; // 0 at the tie, 1 at the tip
      p.setZ(i, p.getZ(i) + bend * t * t);
    }
    g.translate(x, -len / 2, z);
    g.computeVertexNormals();
    const m = new THREE.Mesh(g, material);
    m.material.side = THREE.DoubleSide;
    group.add(m);
  };
  lock(height * 0.024, height * 0.15, 0, 0, height * 0.012);
  lock(height * 0.014, height * 0.13, height * 0.008, height * 0.004, height * 0.018);
  lock(height * 0.013, height * 0.12, -height * 0.008, -height * 0.002, height * 0.006);
  // Behind the crown, tilted so the end hangs back and down.
  group.position.set(0, height * 0.925, headBack - height * 0.012);
  group.rotation.x = 0.7;
  model.parent ? model.parent.add(group) : scene.add(group);
  group.updateMatrixWorld(true);
  head.attach(group);
}
let headBack = 0;


const loader = new GLTFLoader();
Promise.all([loader.loadAsync(who + ".gltf"), loader.loadAsync("anims.glb")]).then(([character, anims]) => {
  const model = character.scene;
  model.traverse((o) => { if (o.isMesh) o.frustumCulled = false; });
  // Whatever units the model came in, make it 1.8 m tall with its feet on the ground.
  // Measure the posed body (skinned meshes), not the raw mesh data.
  model.updateMatrixWorld(true);
  const box = new THREE.Box3();
  model.traverse((o) => {
    if (o.isSkinnedMesh) {
      o.skeleton.update();
      o.computeBoundingBox();
      box.union(o.boundingBox.clone().applyMatrix4(o.matrixWorld));
    } else if (o.isMesh) {
      box.expandByObject(o);
    }
  });
  const size = box.getSize(new THREE.Vector3());
  console.log("coach model size " + size.x.toFixed(2) + " x " + size.y.toFixed(2) + " x " + size.z.toFixed(2));
  const tall = Math.max(size.y, size.z, 0.001);
  model.scale.multiplyScalar(1.8 / tall);
  model.updateMatrixWorld(true);
  const fitted = new THREE.Box3();
  model.traverse((o) => {
    if (o.isSkinnedMesh) { o.computeBoundingBox(); fitted.union(o.boundingBox.clone().applyMatrix4(o.matrixWorld)); }
  });
  const centre = fitted.getCenter(new THREE.Vector3());
  model.position.x -= centre.x;
  model.position.z -= centre.z;
  model.position.y -= fitted.min.y;
  dress(model, fitted.max.y - fitted.min.y);
  if (who === "female") addPonytail(model, fitted.max.y - fitted.min.y);
  scene.add(model);
  coach = model;
  baseY = model.position.y;
  // The toes (ball bones, or the feet), to keep them on the ground while running.
  feet = ["ball_l", "ball_r"].map((n) => model.getObjectByName(n)).filter(Boolean);
  if (feet.length < 2) feet = ["foot_l", "foot_r"].map((n) => model.getObjectByName(n)).filter(Boolean);
  mixer = new THREE.AnimationMixer(model);
  for (const clip of anims.animations) {
    // The root bone carries the travel and turn of the original capture: without it the coach runs
    // on the spot, facing the way the model faces (into the video).
    clip.tracks = clip.tracks.filter((t) => !t.name.startsWith("root."));
    actions[clip.name] = mixer.clipAction(clip);
  }
  console.log("coach ready: " + who + ", clips " + Object.keys(actions).join(","));
  apply();
}).catch((e) => console.log("coach load failed: " + e));

// The poses carry the hips (and so the feet) higher than the rest pose did, so the coach would
// float. Track the lowest toe over the last stride and move the body down so it touches the ground.
const tmp = new THREE.Vector3();
function keepFeetDown(dt) {
  if (!coach || feet.length === 0) return;
  coach.updateMatrixWorld(true);
  let low = Infinity;
  for (const f of feet) low = Math.min(low, f.getWorldPosition(tmp).y);
  lowest.push(low - groundShift); // where the toe would be with no correction
  if (lowest.length > 90) lowest.shift(); // about 1.5 s: at least one full stride
  const strideLow = Math.min(...lowest);
  // Ease towards it, so a change of gait does not make the coach jump.
  const target = 0.02 - strideLow;
  groundShift += (target - groundShift) * Math.min(1, dt * 4);
  coach.position.y = baseY + groundShift;
}

const clock = new THREE.Clock();
renderer.setAnimationLoop(() => {
  const dt = Math.min(clock.getDelta(), 0.1);
  if (mixer) {
    easeStride(dt);
    mixer.update(dt);
    keepFeetDown(dt);
  }
  renderer.render(scene, camera);
});
