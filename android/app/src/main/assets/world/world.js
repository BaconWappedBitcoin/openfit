// OpenFit virtual trails (user ideas 2026-11-24/25): an endless first-person trail for workouts
// without a video, in one of nine real-world biomes, at a time of day and in a weather, with a small
// trail map. The path ahead rises or falls with the workout's incline and the world moves at the
// runner's speed; trees, rocks and plants are recycled as they pass. The app calls
// window.ofitWorld(speedKph, inclinePercent, running) about twice a second.
// Page parameters: seed, land, time, weather, dist (metres), units.
// The look follows the owner's wicklight art notes: solid textured crowns (not paper cards), depth
// haze, macro ground variation fading to a far colour, rooted grass, faceted noise rocks, wind sway.
// Credits: CREDITS.txt.
import * as THREE from "three";
import { mergeGeometries } from "three/addons/utils/BufferGeometryUtils.js";
import { RGBELoader } from "three/addons/loaders/RGBELoader.js";

const params = new URLSearchParams(location.search);
const seed = Number(params.get("seed") || 1);
const LAND = params.get("land") || "forest";
const TIME = params.get("time") || "day"; // morning, day, sunset, night
const WEATHER = params.get("weather") || "clear"; // clear, overcast, fog, rain, snow
const TOTAL = Math.max(400, Number(params.get("dist") || 5000)); // the workout's distance, metres
const START = Math.max(0, Number(params.get("at") || 0)); // metres already covered when the scene was (re)loaded: the map only
const MILES = params.get("units") === "mi";

// ---- a small seeded random, so each workout gets the same trail every time ----
let s = (seed * 9301 + 49297) % 233280;
const rand = () => ((s = (s * 9301 + 49297) % 233280) / 233280);
const pick = (list) => list[Math.floor(rand() * list.length)];

// ---- the biomes ----
// ground: texture set; tile: metres per tile; path/verge/field/far: colours multiplied with it
// (wicklight's palettes); trees: weighted kinds; count/near: how many and how far from the trail;
// rocks; clumps: grass kinds and how many; extras: crops, hedges, sea, peaks.
const BIOMES = {
  forest: { ground: "forest_ground_04", tile: 3.15, path: 0xf2e6d6, verge: 0xa9c27a, field: 0x9cbb72, far: 0x6c7d58, sky: "forest", fog: 0x9fb0a8,
    trees: ["oak", "oak", "bigOak", "birch", "maple", "pine", "spruce", "bush", "sapling"], count: 230, near: 5, rocks: 12, clumps: ["lush", "lush", "flower", "heather"], clumpN: 120, hills: 1 },
  pine: { ground: "forest_ground_04", tile: 3.15, path: 0xe6d8c4, verge: 0xa3b47c, field: 0x8fa070, far: 0x55664c, sky: "forest", fog: 0x8f9f98,
    trees: ["pine", "pine", "spruce", "spruce", "fir", "birch", "dead"], count: 240, near: 5, rocks: 18, clumps: ["dry", "heather", "lush"], clumpN: 100, hills: 1.6 },
  mountains: { ground: "rock", tile: 6, path: 0xd8d2c8, verge: 0xb8bfa4, field: 0xa9ad9c, far: 0x8c9296, sky: "mountain", fog: 0xc8d2e0,
    trees: ["spruce", "spruce", "fir", "dead", "bush"], count: 70, near: 9, rocks: 70, clumps: ["dry", "heather"], clumpN: 70, hills: 2.0, peaks: true, fogFar: 300 },
  autumn: { ground: "leaf_litter", tile: 3, path: 0xf0dcc0, verge: 0xd8b67a, field: 0xd2ad74, far: 0x8a6a48, sky: "clear", fog: 0xc9b79c,
    trees: ["birch", "birch", "maple", "maple", "oak", "bigOak", "bush"], count: 220, near: 5, rocks: 10, clumps: ["dry", "flower", "dry"], clumpN: 100, hills: 1.2, autumn: true },
  meadow: { ground: "grass", tile: 4, path: 0xe8dcc6, verge: 0x8aa462, field: 0x7f9c5a, far: 0x667a52, sky: "clear", fog: 0xb8c8d4,
    trees: ["oak", "bigOak", "birch", "bush"], count: 45, near: 16, rocks: 6, clumps: ["lush", "flower", "flower", "lush"], clumpN: 190, hills: 0.8 },
  fields: { ground: "grass", tile: 4, path: 0xe0d0b2, verge: 0x88a060, field: 0x7c9658, far: 0x74805a, sky: "clear", fog: 0xc2ccd4,
    trees: ["oak", "bigOak", "poplar", "poplar"], count: 30, near: 22, rocks: 0, clumps: ["lush", "flower"], clumpN: 90, hills: 0.5, crops: true, hedges: true },
  moor: { ground: "dry_heather", tile: 3, path: 0xd8c8b0, verge: 0xc0a8a0, field: 0xb89c94, far: 0x6a5a62, sky: "clear", fog: 0xa8a4a8,
    trees: ["pine", "dead", "bush"], count: 35, near: 14, rocks: 40, clumps: ["heather", "heather", "dry"], clumpN: 170, hills: 1.6 },
  desert: { ground: "sand", tile: 15, path: 0xf2e2c8, verge: 0xeed8b8, field: 0xf0d6ae, far: 0xd8b48a, sky: "desert", fog: 0xe6d4bc,
    trees: ["cactus", "cactus", "cactus", "dead", "shrub"], count: 90, near: 6, rocks: 45, clumps: ["dry"], clumpN: 60, hills: 1.4, fogFar: 300 },
  coast: { ground: "sand", tile: 15, path: 0xf6ead6, verge: 0xe8dcc0, field: 0xd8d0a8, far: 0xbfb68e, sky: "beach", fog: 0xc8d8e4,
    trees: ["pine", "bush", "shrub", "bush"], count: 60, near: 7, rocks: 30, clumps: ["dry", "lush"], clumpN: 80, hills: 0.7, sea: true, fogFar: 300 },
};
// The old town: a paved street between terraces, built in code below (townRows).
BIOMES.town = { ground: "rock", tile: 3, path: 0xcfc6b8, verge: 0xb4ada2, field: 0xaaa398, far: 0x8c8882, sky: "clear", fog: 0xbcc4cc,
  trees: ["oak"], count: 0, near: 4.5, rocks: 0, clumps: [], clumpN: 0, hills: 0.1, town: true };
const B = BIOMES[LAND] || BIOMES.forest;
const night = TIME === "night";
const wet = WEATHER === "rain";
const snowy = WEATHER === "snow";
const grey = WEATHER === "overcast" || WEATHER === "fog" || wet || snowy;

// ---- renderer, scene, camera ----
// The scene is drawn to an HDR texture first and graded in one pass (below); needs float targets.
// Developer overrides (?fx=sh0,pr1,ms2,sm1024,post0,ldr): effect settings to try on a device.
const FX = (params.get("fx") || "").split(",");
const fxNum = (k, d) => { for (const t of FX) if (t.startsWith(k)) { const v = parseFloat(t.slice(k.length)); if (!Number.isNaN(v)) return v; } return d; };
const probeGl = document.createElement("canvas").getContext("webgl2");
const USE_POST = !FX.includes("post0") && !!(probeGl && (probeGl.getExtension("EXT_color_buffer_float") || probeGl.getExtension("EXT_color_buffer_half_float")));
const renderer = new THREE.WebGLRenderer({ antialias: !USE_POST });
const PIXEL_RATIO = Math.min(window.devicePixelRatio, fxNum("pr", 1.25));
renderer.setPixelRatio(PIXEL_RATIO);
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
document.body.appendChild(renderer.domElement);

// Light by time of day (wicklight's sun, dusk and moon colours).
const LOOK = {
  morning: { exposure: 0.95, sun: 0xffe0b8, sunI: 1.15, sunPos: [38, 20, -85] },
  day: { exposure: 1.05, sun: 0xfff2cc, sunI: 1.25, sunPos: [-40, 75, -35] },
  sunset: { exposure: 0.85, sun: 0xff9e59, sunI: 1.3, sunPos: [-35, 14, -90] },
  night: { exposure: 0.55, sun: 0x738cd9, sunI: 0.3, sunPos: [30, 60, -40] },
}[TIME] || { exposure: 1.05, sun: 0xfff2cc, sunI: 1.25, sunPos: [-40, 75, -35] };
renderer.toneMappingExposure = LOOK.exposure;
const sunDir = new THREE.Vector3(...LOOK.sunPos).normalize();

// Depth haze: from ~12 m to ~115 m in a forest, further in open country; colour per biome.
const fogColour = new THREE.Color(
  night ? 0x121722 : snowy ? 0xd8dde2 : WEATHER === "fog" ? 0xb9bfc2 : grey ? 0xa9b0b4 : TIME === "sunset" ? 0xc9a58e : B.fog,
);
const scene = new THREE.Scene();
const far = B.fogFar || 230;
const [fogNear, fogFar] = WEATHER === "fog" ? [6, 70] : wet ? [20, 150] : snowy ? [18, 130] : WEATHER === "overcast" ? [25, far * 0.8] : night ? [15, 130] : [30, far];
scene.fog = new THREE.Fog(fogColour, fogNear, fogFar);
scene.background = fogColour.clone();

const camera = new THREE.PerspectiveCamera(62, window.innerWidth / window.innerHeight, 0.1, 900);
const EYE = 1.65;
camera.position.set(0, EYE, 0);

// The sky: a real photo panorama (Poly Haven, CC0) and its HDR for the light. Grey weather uses
// the overcast sky; night is drawn here (stars and a moon).
const skyName = grey ? "overcast" : TIME === "sunset" ? "sunset" : TIME === "morning" && !["desert", "beach", "mountain"].includes(B.sky) ? "clear" : B.sky;
const pmrem = new THREE.PMREMGenerator(renderer);
new RGBELoader().load("sky/" + (night ? "overcast" : skyName) + ".hdr", (hdr) => {
  scene.environment = pmrem.fromEquirectangular(hdr).texture;
  hdr.dispose();
});
// Inland landscapes get a drawn sky: gradient, a sun glow that follows the time of day, and clouds
// that drift. (The old photo sky for these was a dark storm cloud on a clear day.)
const PROC_SKY = !night && !["forest", "coast", "desert", "mountains"].includes(LAND);
const skyUniforms = { uTime: { value: 0 } };
function buildProcSky() {
  const cloudy = WEATHER === "overcast" || WEATHER === "fog" || wet || snowy;
  const pal = cloudy
    ? { zenith: 0x7e8794, horizon: 0xb4bcc4, sun: 0xdfe3e8, cloud: 0.92, sunK: 0.25 }
    : TIME === "sunset"
      ? { zenith: 0x2a3d78, horizon: 0xf29a5a, sun: 0xff9a50, cloud: 0.5, sunK: 1.0 }
      : TIME === "morning"
        ? { zenith: 0x4a7cc0, horizon: 0xf6cfa8, sun: 0xffcf98, cloud: 0.45, sunK: 0.9 }
        : { zenith: 0x2f6cc2, horizon: 0xc2dcf2, sun: 0xfff2d0, cloud: 0.5, sunK: 0.8 };
  Object.assign(skyUniforms, {
    zenith: { value: new THREE.Color(pal.zenith) }, horizon: { value: new THREE.Color(pal.horizon) }, sunCol: { value: new THREE.Color(pal.sun) },
    sunDir: { value: sunDir.clone() }, cloud: { value: pal.cloud }, sunK: { value: pal.sunK }, warm: { value: TIME === "sunset" ? 1 : TIME === "morning" ? 0.5 : 0 },
  });
  const dome = new THREE.Mesh(
    new THREE.SphereGeometry(800, 32, 16),
    new THREE.ShaderMaterial({
      side: THREE.BackSide, depthWrite: false, fog: false, uniforms: skyUniforms,
      vertexShader: "varying vec3 p; void main(){ p = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",
      fragmentShader: `
        uniform vec3 zenith, horizon, sunCol, sunDir; uniform float cloud, sunK, warm, uTime; varying vec3 p;
        float hash(vec2 q){ return fract(sin(dot(q, vec2(127.1, 311.7))) * 43758.5453); }
        float noise(vec2 q){ vec2 i = floor(q), f = fract(q); f = f * f * (3.0 - 2.0 * f);
          return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y); }
        float fbm(vec2 q){ float v = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { v += a * noise(q); q = q * 2.03 + 11.7; a *= 0.5; } return v; }
        void main() {
          vec3 d = normalize(p);
          float h = clamp(d.y, 0.0, 1.0);
          vec3 col = mix(horizon, zenith, pow(h, 0.45));
          float sd = max(dot(d, sunDir), 0.0);
          col += sunCol * (pow(sd, 6.0) * 0.22 * sunK + pow(sd, 60.0) * 0.5 * sunK + pow(sd, 700.0) * 6.0 * sunK);
          if (d.y > 0.0) {
            vec2 uv = d.xz / (d.y + 0.2) * 0.8 + vec2(uTime * 0.006, uTime * 0.002);
            float f = fbm(uv * 1.5);
            float c = smoothstep(1.0 - cloud, 1.0 - cloud + 0.3, f) * smoothstep(0.0, 0.18, d.y);
            // Lit from the sun's side; the undersides darker; a warm edge when the sun is low.
            float lit = 0.72 + 0.28 * smoothstep(0.3, 0.9, fbm(uv * 1.5 + sunDir.xz * 0.35));
            vec3 cc = mix(vec3(1.0), sunCol * 0.9 + 0.2, warm * 0.8) * lit;
            col = mix(col, cc, c * 0.92);
          }
          gl_FragColor = vec4(col, 1.0);
        }`,
    }),
  );
  dome.userData.fx = true;
  scene.add(dome);
}
if (night) buildNightSky();
else if (PROC_SKY) buildProcSky();
else if (WEATHER !== "fog") {
  new THREE.TextureLoader().load("sky/" + skyName + ".jpg", (t) => {
    t.mapping = THREE.EquirectangularReflectionMapping;
    t.colorSpace = THREE.SRGBColorSpace;
    scene.background = t;
  });
}

function buildNightSky() {
  const dome = new THREE.Mesh(
    new THREE.SphereGeometry(800, 32, 16),
    new THREE.ShaderMaterial({
      side: THREE.BackSide, depthWrite: false, fog: false, toneMapped: false,
      uniforms: { top: { value: new THREE.Color(0x02040b) }, bottom: { value: new THREE.Color(0x1a2438) } },
      vertexShader: "varying vec3 p; void main(){ p = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }",
      fragmentShader: "uniform vec3 top; uniform vec3 bottom; varying vec3 p; void main(){ float h = clamp(p.y*2.2+0.1,0.0,1.0); gl_FragColor = vec4(mix(bottom, top, h), 1.0); }",
    }),
  );
  scene.add(dome);
  if (grey) return;
  const n = 2200, pts = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) {
    const a = rand() * Math.PI * 2, y = 0.08 + rand() * 0.92, r = Math.sqrt(1 - y * y);
    pts.set([Math.cos(a) * r * 750, y * 750, Math.sin(a) * r * 750], i * 3);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.BufferAttribute(pts, 3));
  scene.add(new THREE.Points(g, new THREE.PointsMaterial({ color: 0xdfe8ff, size: 1.6, sizeAttenuation: false, fog: false, toneMapped: false })));
  const moon = new THREE.Mesh(new THREE.CircleGeometry(14, 32), new THREE.MeshBasicMaterial({ color: 0xf4f1e3, fog: false, toneMapped: false }));
  moon.position.set(200, 360, -620);
  moon.lookAt(0, 0, 0);
  scene.add(moon);
}

scene.add(new THREE.HemisphereLight(night ? 0x5a6a90 : 0xdcecff, night ? 0x0d1018 : 0x4a5a32, night ? 0.5 : grey ? 0.8 : 0.68));
const sun = new THREE.DirectionalLight(LOOK.sun, grey ? LOOK.sunI * 0.4 : LOOK.sunI);
sun.position.set(...LOOK.sunPos);
scene.add(sun);

// ---- shadows: the sun's shadow box follows the runner; none at night (moonlight shows none) ----
let shadowsOn = !night && !FX.includes("sh0");
if (shadowsOn) {
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = FX.includes("soft") ? THREE.PCFSoftShadowMap : THREE.PCFShadowMap;
  sun.castShadow = true;
  sun.shadow.mapSize.set(fxNum("sm", 2048), fxNum("sm", 2048));
  const sc = sun.shadow.camera;
  sc.left = -42; sc.right = 42; sc.top = 42; sc.bottom = -42; sc.near = 1; sc.far = 300;
  sun.shadow.bias = -0.0004; sun.shadow.normalBias = 0.5;
  scene.add(sun.target);
}
function updateSun() { // the shadow box sits 22 m ahead of the runner
  sun.target.position.set(0, 0, -22);
  sun.position.copy(sun.target.position).addScaledVector(sunDir, 130);
}

// ---- grade: tone mapping, a little more colour, cool shadows and warm lights, soft vignette ----
const GRADE = night
  ? { sat: 0.95, con: 1.05, vig: 0.42, shadow: [0.92, 0.98, 1.1], high: [0.92, 0.98, 1.08] }
  : TIME === "sunset"
    ? { sat: 1.16, con: 1.07, vig: 0.3, shadow: [0.92, 0.96, 1.1], high: [1.12, 0.98, 0.84] }
    : grey
      ? { sat: 0.98, con: 1.05, vig: 0.3, shadow: [0.95, 1.0, 1.05], high: [1.0, 1.0, 1.0] }
      : { sat: 1.12, con: 1.06, vig: 0.26, shadow: [0.94, 1.0, 1.08], high: [1.05, 1.0, 0.92] };
const postMat = new THREE.ShaderMaterial({
  depthTest: false, depthWrite: false,
  uniforms: {
    tDiffuse: { value: null }, uRes: { value: new THREE.Vector2(1280, 800) }, uAA: { value: FX.includes("noaa") ? 0 : 1 }, uTime: { value: 0 }, uSat: { value: GRADE.sat }, uCon: { value: GRADE.con }, uVig: { value: GRADE.vig },
    uShadow: { value: new THREE.Vector3(...GRADE.shadow) }, uHigh: { value: new THREE.Vector3(...GRADE.high) },
  },
  vertexShader: "varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }",
  fragmentShader: `
    uniform sampler2D tDiffuse; uniform vec2 uRes; uniform float uAA, uTime, uSat, uCon, uVig; uniform vec3 uShadow, uHigh; varying vec2 vUv;
    float lum3(vec3 c) { c = clamp(c, 0.0, 1.0); return dot(c, vec3(0.299, 0.587, 0.114)); }
    void main() {
      vec3 rgbM = texture2D(tDiffuse, vUv).rgb;
      if (uAA > 0.5) {
        // FXAA (edge-aware blur along the edge), in place of multisampling, which this GPU cannot afford.
        vec2 px = 1.0 / uRes;
        vec3 nw = texture2D(tDiffuse, vUv + vec2(-1.0, -1.0) * px).rgb, ne = texture2D(tDiffuse, vUv + vec2(1.0, -1.0) * px).rgb;
        vec3 sw = texture2D(tDiffuse, vUv + vec2(-1.0, 1.0) * px).rgb, se = texture2D(tDiffuse, vUv + vec2(1.0, 1.0) * px).rgb;
        float lM = lum3(rgbM), lNW = lum3(nw), lNE = lum3(ne), lSW = lum3(sw), lSE = lum3(se);
        float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE))), lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
        if (lMax - lMin > max(0.04, lMax * 0.125)) {
          vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), (lNW + lSW) - (lNE + lSE));
          float reduce = max((lNW + lNE + lSW + lSE) * 0.03125, 0.0078125);
          dir = clamp(dir / (min(abs(dir.x), abs(dir.y)) + reduce), -8.0, 8.0) * px;
          vec3 a = 0.5 * (texture2D(tDiffuse, vUv + dir * (1.0 / 3.0 - 0.5)).rgb + texture2D(tDiffuse, vUv + dir * (2.0 / 3.0 - 0.5)).rgb);
          vec3 b = a * 0.5 + 0.25 * (texture2D(tDiffuse, vUv + dir * -0.5).rgb + texture2D(tDiffuse, vUv + dir * 0.5).rgb);
          float lB = lum3(b);
          rgbM = (lB < lMin || lB > lMax) ? a : b;
        }
      }
      gl_FragColor = vec4(rgbM, 1.0);
      #if defined( TONE_MAPPING )
        gl_FragColor.rgb = toneMapping( gl_FragColor.rgb );
      #endif
      vec3 col = clamp(gl_FragColor.rgb, 0.0, 1.0);
      float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
      col = mix(vec3(l), col, uSat);
      col *= mix(uShadow, uHigh, smoothstep(0.08, 0.75, l));
      gl_FragColor = linearToOutputTexel(vec4(col, 1.0));
      vec3 d = (gl_FragColor.rgb - 0.5) * uCon + 0.5;
      d *= 1.0 - uVig * smoothstep(0.35, 1.05, length(vUv - 0.5) * 1.35);
      d += (fract(sin(dot(vUv * 640.0 + uTime, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) * 0.012;
      gl_FragColor = vec4(d, 1.0);
    }`,
});
const postScene = new THREE.Scene();
const postCam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
postScene.add(new THREE.Mesh(new THREE.PlaneGeometry(2, 2), postMat));
let rt = null;
function makeTarget(samples) {
  const size = renderer.getDrawingBufferSize(new THREE.Vector2());
  if (rt) rt.dispose();
  rt = new THREE.WebGLRenderTarget(Math.max(2, size.x), Math.max(2, size.y), { type: FX.includes("ldr") ? THREE.UnsignedByteType : THREE.HalfFloatType, samples });
  postMat.uniforms.tDiffuse.value = rt.texture;
  postMat.uniforms.uRes.value.set(rt.width, rt.height);
}
if (USE_POST) makeTarget(fxNum("ms", 0));
function renderFrame(now) {
  if (!USE_POST) { renderer.render(scene, camera); return; }
  renderer.setRenderTarget(rt);
  renderer.render(scene, camera);
  renderer.setRenderTarget(null);
  postMat.uniforms.uTime.value = (now / 1000) % 100;
  renderer.render(postScene, postCam);
}
if (night) {
  const torch = new THREE.PointLight(0xfff2d8, LAND === "town" ? 5 : 18, 26, 1.6) // the town has street lamps;
  torch.position.set(0, 2.2, -3);
  scene.add(torch);
}

// ---- wind: one clock for every swaying material ----
const wind = { value: 0 };
function sway(material, strength, height) {
  material.onBeforeCompile = (shader) => {
    shader.uniforms.uTime = wind;
    shader.vertexShader = "uniform float uTime;\n" + shader.vertexShader.replace(
      "#include <begin_vertex>",
      `#include <begin_vertex>
      #ifdef USE_INSTANCING
        vec3 swayAt = instanceMatrix[3].xyz;
      #else
        vec3 swayAt = modelMatrix[3].xyz;
      #endif
      float swayH = clamp(position.y / ${height.toFixed(1)}, 0.0, 1.0);
      float swayP = dot(floor(swayAt.xz * 0.5), vec2(0.7, 1.3));
      transformed.x += sin(uTime * 1.6 + swayP) * ${strength.toFixed(3)} * swayH * swayH;
      transformed.z += sin(uTime * 1.33 + swayP * 1.1) * ${(strength * 0.7).toFixed(3)} * swayH * swayH;`,
    );
  };
  return material;
}

// ---- the ground: a wide strip that travels with the runner ----
const WIDTH = 240, LENGTH = 340, SEG_X = 80, SEG_Z = 116;
const groundGeo = new THREE.PlaneGeometry(WIDTH, LENGTH, SEG_X, SEG_Z);
groundGeo.rotateX(-Math.PI / 2);
groundGeo.translate(0, 0, -LENGTH / 2 + 20);
const colors = new Float32Array(groundGeo.attributes.position.count * 3);
groundGeo.setAttribute("color", new THREE.BufferAttribute(colors, 3));
// Per vertex: how far the ground's tint replaces the texture's own colour (0 multiply, 1 use only
// its light and shade), the across-trail position for crop rows, and how strong the rows are.
// A plain multiply left a sandy path or ripe wheat green on the grass texture.
const gInfo = new Float32Array(groundGeo.attributes.position.count * 3);
groundGeo.setAttribute("gInfo", new THREE.BufferAttribute(gInfo, 3));
const texLoader = new THREE.TextureLoader();
function groundMap(file, srgb) {
  const t = texLoader.load("ground/" + file);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.repeat.set(WIDTH / B.tile, LENGTH / B.tile);
  t.anisotropy = 4;
  if (srgb) t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
const groundMaps = [groundMap(B.ground + "_baseColor.jpg", true), groundMap(B.ground + "_normal.jpg", false)];
const GROUND_LIFT = { autumn: 1.9, moor: 1.8, pine: 1.95, forest: 1.5, meadow: 1.15, fields: 1.15, desert: 0.95, coast: 1.0, mountains: 1.1, town: 1.2 };
const groundMat = new THREE.MeshStandardMaterial({
  color: new THREE.Color().setScalar(GROUND_LIFT[LAND] || 1.3),
  vertexColors: true, map: groundMaps[0], normalMap: groundMaps[1],
  normalScale: new THREE.Vector2(0.55, 0.55), roughness: wet ? 0.5 : 0.9, metalness: 0,
  envMapIntensity: 0.55, // the sky light washed grass out to neon
});
groundMat.onBeforeCompile = (shader) => {
  shader.vertexShader = "attribute vec3 gInfo;\nvarying vec3 vGInfo;\n" +
    shader.vertexShader.replace("#include <begin_vertex>", "#include <begin_vertex>\n vGInfo = gInfo;");
  shader.fragmentShader = "varying vec3 vGInfo;\n" + shader.fragmentShader.replace("#include <map_fragment>", `
    #ifdef USE_MAP
      vec4 sampledDiffuseColor = texture2D( map, vMapUv );
      float lum = dot(sampledDiffuseColor.rgb, vec3(0.299, 0.587, 0.114));
      sampledDiffuseColor.rgb = mix(sampledDiffuseColor.rgb, vec3(lum * 1.35), vGInfo.x);
      sampledDiffuseColor.rgb *= mix(1.0, 0.7 + 0.3 * sin(vGInfo.y * 5.0), vGInfo.z);
      diffuseColor *= sampledDiffuseColor;
    #endif`);
};
const ground = new THREE.Mesh(groundGeo, groundMat);
scene.add(ground);
const baseX = Float32Array.from({ length: groundGeo.attributes.position.count }, (_, i) => groundGeo.attributes.position.getX(i));
const baseZ = Float32Array.from({ length: groundGeo.attributes.position.count }, (_, i) => groundGeo.attributes.position.getZ(i));

const PATH = 1.6;
const wetK = wet ? 0.7 : 1;
const col = (hex) => new THREE.Color(hex).multiplyScalar(wetK);
const cPath = snowy ? new THREE.Color(0xe8ebef) : col(B.path);
const cVerge = snowy ? new THREE.Color(0xf4f6f9) : col(B.verge);
const cField = snowy ? new THREE.Color(0xfafbfd) : col(B.field);
const cFar = snowy ? new THREE.Color(0xe6eaef) : col(B.far).lerp(fogColour, 0.15);
const tmp = new THREE.Color();
const cropGold = col(0xd8b860), cropGreen = col(0x8fae58);

// Two scales of gentle noise for big colour patches (wicklight: fbm 0.045 and 0.11).
const macro = (x, z) => 0.62 * (0.5 + 0.5 * Math.sin(x * 0.045 + z * 0.031 + seed) * Math.cos(z * 0.045 - x * 0.02)) +
  0.38 * (0.5 + 0.5 * Math.sin(x * 0.11 - z * 0.09 + seed * 2.1));

// Rolling hills away from the trail; the coast falls away to the sea on the right.
function hills(x, wz) {
  const ax = Math.abs(x);
  const away = Math.min(1, Math.max(0, (ax - PATH - 2) / 25));
  let h = B.hills * away * (2.2 * Math.sin(wz * 0.021 + x * 0.05 + seed) + 1.4 * Math.sin(wz * 0.047 - x * 0.08) + 3.5 * away * Math.sin(x * 0.03 + wz * 0.01));
  if (B.sea && x > 9) h = Math.max(-2.6, -(x - 9) * 0.22) + Math.min(0, h) * 0.2;
  return h;
}
const windPath = (wz) => 4.0 * Math.sin(wz * 0.012 + seed) + 1.5 * Math.sin(wz * 0.031);

// ---- state from the app ----
let targetSpeed = 0, speed = 0;
let targetGrade = 0, grade = 0;
let running = false;
let travelled = 0;
let bob = 0;
window.ofitWorld = function (kph, inclinePercent, run) {
  targetSpeed = Math.max(0, Number(kph) || 0) / 3.6;
  targetGrade = Math.max(-0.15, Math.min(0.4, (Number(inclinePercent) || 0) / 100));
  running = !!run;
};

function updateGround() {
  const pos = groundGeo.attributes.position;
  const wzNow = travelled;
  for (let i = 0; i < pos.count; i++) {
    const x = baseX[i], z = baseZ[i];
    const ahead = -z;
    const wz = wzNow + ahead;
    const dx = x - (windPath(wz) - windPath(wzNow));
    pos.setY(i, ahead * grade + hills(dx, wz));
    const ax = Math.abs(dx);
    let c = ax < PATH ? cPath : ax < PATH + 1.4 ? cVerge : cField;
    tmp.copy(c);
    if (ax >= PATH) tmp.multiplyScalar(0.82 + 0.3 * macro(x, wz)); // big colour patches
    // Far ground reads as grass, not grey mist (wicklight: blend to a far colour 45-120 m).
    const d = Math.hypot(x, ahead);
    tmp.lerp(cFar, Math.min(1, Math.max(0, (d - 45) / 75)));
    // Farm fields: crop rows beyond the hedges, ripe wheat on the left, green crops on the right.
    if (B.crops && ax > 5.2 && !snowy) {
      tmp.copy(dx < 0 ? cropGold : cropGreen).multiplyScalar(0.9 + 0.2 * macro(x, wz)); // rows: in the shader
      tmp.lerp(cFar, Math.min(1, Math.max(0, (d - 60) / 90)));
    }
    if (B.sea && dx > 14) tmp.lerp(new THREE.Color(0xc8b994), Math.min(1, (dx - 14) / 6)); // wet sand by the water
    colors[i * 3] = tmp.r; colors[i * 3 + 1] = tmp.g; colors[i * 3 + 2] = tmp.b;
    const crop = B.crops && ax > 5.2 && !snowy ? 1 - Math.min(1, Math.max(0, (d - 60) / 90)) : 0;
    gInfo[i * 3] = ax < PATH ? 1 : ax < PATH + 1.4 ? 0.5 : crop * 0.9;
    gInfo[i * 3 + 1] = dx;
    gInfo[i * 3 + 2] = crop;
  }
  pos.needsUpdate = true;
  groundGeo.attributes.color.needsUpdate = true;
  groundGeo.attributes.gInfo.needsUpdate = true;
  groundGeo.computeVertexNormals();
}

// ---- trees, built once per kind (three variants each), from the owner's wicklight textures ----
const tex = (file, srgb = true) => {
  const t = texLoader.load("flora/" + file);
  if (srgb) t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  return t;
};
const barkTex = tex("bark.png"); barkTex.wrapS = barkTex.wrapT = THREE.RepeatWrapping; barkTex.repeat.set(1, 2);
const birchBarkTex = tex("birch_bark.png"); birchBarkTex.wrapS = birchBarkTex.wrapT = THREE.RepeatWrapping; birchBarkTex.repeat.set(1, 2);
const leafTex = { oak: tex("oak_foliage_leaf.png"), birch: tex("birch_foliage_leaf.png"), pine: tex("pine_foliage_leaf.png") };
// wicklight's mapping: the sheet wraps twice round a lobe and once from bottom to top.
for (const t of Object.values(leafTex)) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(2, 1); }

// The leaf sheets are already painted their colour, so wicklight tints them white: a tint on top
// is what made crowns dark and splotchy. Only a small per-tree variation here; autumn and snow
// recolour (lightened, so the sheet's own shading stays).
function leafColour(base, autumnHex) {
  const c = new THREE.Color(0xffffff);
  if (B.autumn && autumnHex) c.set(autumnHex).lerp(new THREE.Color(0xffffff), 0.5);
  if (snowy) c.lerp(new THREE.Color(0xeef2f6), 0.5);
  c.offsetHSL((rand() - 0.5) * 0.03, 0, (rand() - 0.5) * 0.08);
  return c;
}
// wicklight's foliage shader: lambert wrap lighting, no specular, both sides, alpha scissor 0.5.
// Lambert (no specular or environment glints) plus a little self-light from the sheet itself
// stands in for the wrap, so the shaded side reads as deep leaves, not black patches.
const leafMaterial = (map, colour, height) => sway(new THREE.MeshLambertMaterial({
  map, color: colour, alphaTest: 0.5, side: THREE.DoubleSide,
  emissive: new THREE.Color(colour).multiplyScalar(night ? 0.04 : 0.22), emissiveMap: map,
}), 0.06 * height / 8, height);

// A trunk: 8 sides, 3 segments, tapering to 55 %, a little bowed and uneven.
function trunk(h, r, birch) {
  const g = new THREE.CylinderGeometry(r * 0.55, r, h, 8, 3);
  const p = g.attributes.position;
  const bowX = (rand() - 0.5) * 0.36, bowZ = (rand() - 0.5) * 0.36;
  for (let i = 0; i < p.count; i++) {
    const y = p.getY(i) / h + 0.5;
    const k = 1 + (rand() - 0.5) * 0.12;
    p.setXYZ(i, p.getX(i) * k + bowX * Math.sin(y * Math.PI), p.getY(i), p.getZ(i) * k + bowZ * Math.sin(y * Math.PI));
  }
  g.translate(0, h / 2, 0);
  g.computeVertexNormals();
  return new THREE.Mesh(g, new THREE.MeshStandardMaterial({ map: birch ? birchBarkTex : barkTex, color: snowy ? 0xdfe3e8 : 0xffffff, roughness: 1 }));
}
// A jittered spheroid lobe for a broadleaf crown.
function lobe(r, squash, x, y, z) {
  const g = new THREE.SphereGeometry(r, 9, 5);
  const p = g.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const k = 1 + (rand() - 0.5) * 0.2;
    p.setXYZ(i, p.getX(i) * k, p.getY(i) * k * squash, p.getZ(i) * k);
  }
  g.translate(x, y, z);
  return g;
}
// A faceted conifer crown: a cone of bands, each a little wider or narrower.
function cone(h, r, base) {
  const g = new THREE.ConeGeometry(r, h, 9, 6, true);
  const p = g.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const t = p.getY(i) / h + 0.5;
    const k = (0.88 + rand() * 0.26) * (t > 0.98 ? 1 : 1);
    const a = (rand() - 0.5) * 0.18;
    const x = p.getX(i) * k, z = p.getZ(i) * k;
    p.setXYZ(i, x * Math.cos(a) - z * Math.sin(a), p.getY(i), x * Math.sin(a) + z * Math.cos(a));
  }
  g.translate(0, base + h / 2, 0);
  return g;
}

const KINDS = {
  // Real sizes (an oak is 12-20 m): from a runner's eye at 1.65 m, smaller trees read as shrubs.
  oak: () => broadleaf(11, 0.4, 3.6, 0.8, leafTex.oak, 0x8cb860, 0xd8a040, false),
  bigOak: () => broadleaf(15, 0.6, 5.0, 0.75, leafTex.oak, 0x7ea85a, 0xc88a38, false),
  maple: () => broadleaf(12, 0.36, 3.6, 0.9, leafTex.oak, 0x86b45c, 0xd0582c, false),
  birch: () => broadleaf(13, 0.22, 2.4, 1.1, leafTex.birch, 0xa8c868, 0xe8c84a, true),
  poplar: () => column(18, 0.3, leafTex.birch, 0x8cb45a),
  sapling: () => broadleaf(3.5, 0.08, 1.2, 0.9, leafTex.oak, 0x9cc468, 0xe0a848, false),
  bush: () => shrub(1.1, leafTex.oak, 0x7ea456),
  shrub: () => shrub(0.7, leafTex.pine, 0x8a9a5e),
  pine: () => conifer(16, 0.35, 2.8, 0.5, leafTex.pine, 0x4e8050),
  spruce: () => conifer(18, 0.4, 3.0, 0.2, leafTex.pine, 0x3a6a46),
  fir: () => conifer(12, 0.34, 3.0, 0.15, leafTex.pine, 0x4a7a4a),
  dead: () => deadTree(),
  cactus: () => cactus(),
};
function broadleaf(h, r, cr, squash, map, colour, autumn, birch) {
  // Autumn: a green sheet cannot be tinted orange (it turned muddy), so every broadleaf wears the
  // golden birch sheet, warmed toward its own autumn colour by leafColour.
  if (B.autumn) map = leafTex.birch;
  const g = new THREE.Group();
  const th = h * 0.7;
  g.add(trunk(th, r, birch));
  // wicklight's crowns: a core lobe and one to three side lobes (birch two, slimmer), each a
  // jittered spheroid wearing the whole leaf sheet. Many small overlapping lobes looked splotchy.
  const lobes = [];
  const cy = h - cr * 1.1;
  lobes.push(lobe(cr, squash, 0, cy, 0));
  const n = birch ? 1 : 2 + (h > 13 ? 1 : 0);
  const a0 = rand() * Math.PI * 2;
  for (let i = 0; i < n; i++) {
    const a = a0 + (i / n) * Math.PI * 2 + (rand() - 0.5) * 0.8, off = cr * (0.5 + rand() * 0.4);
    lobes.push(lobe(cr * (0.62 + rand() * 0.18), squash, Math.cos(a) * off, cy + (rand() - 0.35) * cr * 0.6, Math.sin(a) * off));
  }
  g.add(new THREE.Mesh(mergeGeometries(lobes), leafMaterial(map, leafColour(colour, autumn), h)));
  // The dark heart (see coreMaterial): one lobe just inside the main one.
  g.add(new THREE.Mesh(lobe(cr * 0.82, squash, 0, cy, 0), coreMaterial(B.autumn ? 0x5a3a18 : birch ? 0x3a4a22 : 0x26401e, h)));
  return g;
}
function column(h, r, map, colour) {
  const g = new THREE.Group();
  g.add(trunk(h * 0.4, r, false));
  const lobes = [lobe(2.0, 3.4, 0, h * 0.55, 0), lobe(1.5, 2.6, 0.4, h * 0.78, 0.3), lobe(1.3, 2.4, -0.4, h * 0.4, -0.2)];
  g.add(new THREE.Mesh(mergeGeometries(lobes), leafMaterial(map, leafColour(colour, 0xe0c040), h)));
  return g;
}
function shrub(r, map, colour) {
  const g = new THREE.Group();
  const lobes = [];
  for (let i = 0; i < 3; i++) lobes.push(lobe(r * (0.7 + rand() * 0.4), 0.75, (rand() - 0.5) * r, r * 0.55, (rand() - 0.5) * r));
  g.add(new THREE.Mesh(mergeGeometries(lobes), leafMaterial(map, leafColour(colour, 0xb87838), 2)));
  return g;
}
// A solid, dark heart inside every crown, so the gaps in the carved leaf sheet show deeper
// foliage, not sky straight through (user 2026-11-25: "too thin, like a cheap fake Christmas
// tree"). Dark, matte, no texture: it is only ever seen through the leaves.
function coreMaterial(hex, height) {
  const c = new THREE.Color(hex);
  if (snowy) c.lerp(new THREE.Color(0xc8d0d8), 0.35);
  return sway(new THREE.MeshLambertMaterial({ color: c }), 0.06 * height / 8, height);
}
// Conifers as real spruces and pines grow: tiers of drooping branch skirts, overlapping, each
// narrower than the one below, round a solid dark core.
function conifer(h, r, cr, start, map, colour) {
  const g = new THREE.Group();
  g.add(trunk(h * 0.8, r, false));
  const base = h * start, H = h - base + 0.4;
  const width = cr * (0.9 + rand() * 0.3);
  const tiers = [];
  const n = 5 + Math.floor(rand() * 2);
  for (let i = 0; i < n; i++) {
    const t = i / n;
    tiers.push(cone(H * 0.36, width * (1 - t * 0.78) * (0.92 + rand() * 0.16), base + H * t * 0.72));
  }
  g.add(new THREE.Mesh(mergeGeometries(tiers), leafMaterial(map, leafColour(colour, null), h)));
  const core = new THREE.ConeGeometry(width * 0.62, H * 0.95, 8, 1);
  core.translate(0, base + H * 0.95 / 2, 0);
  g.add(new THREE.Mesh(core, coreMaterial(0x1c3322, h)));
  return g;
}
function deadTree() {
  const g = new THREE.Group();
  const h = 4 + rand() * 3;
  g.add(trunk(h, 0.2, false));
  const parts = [];
  for (let i = 0; i < 4; i++) {
    const b = new THREE.CylinderGeometry(0.03, 0.07, 1.4 + rand(), 5);
    b.translate(0, 0.7, 0);
    b.rotateZ((rand() < 0.5 ? -1 : 1) * (0.5 + rand() * 0.6));
    b.rotateY(rand() * Math.PI * 2);
    b.translate(0, h * (0.5 + rand() * 0.4), 0);
    parts.push(b);
  }
  g.add(new THREE.Mesh(mergeGeometries(parts), new THREE.MeshStandardMaterial({ map: barkTex, color: 0xb8a898, roughness: 1 })));
  return g;
}
// A saguaro: a ribbed column with one to three upturned arms.
const cactusTex = (() => {
  const c = document.createElement("canvas"); c.width = 64; c.height = 8;
  const x = c.getContext("2d");
  for (let i = 0; i < 64; i++) { const v = 0.75 + 0.25 * Math.cos(i / 64 * Math.PI * 2 * 12); x.fillStyle = `rgb(${Math.round(98 * v)},${Math.round(128 * v)},${Math.round(70 * v)})`; x.fillRect(i, 0, 1, 8); }
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
})();
function cactus() {
  const h = 2.5 + rand() * 2.5;
  const parts = [];
  const body = new THREE.CapsuleGeometry(0.28, h, 4, 12); body.translate(0, h / 2 + 0.28, 0); parts.push(body);
  const arms = 1 + Math.floor(rand() * 3);
  for (let i = 0; i < arms; i++) {
    const side = i % 2 ? -1 : 1, y = h * (0.4 + rand() * 0.3), len = 0.7 + rand() * 1.2;
    const across = new THREE.CapsuleGeometry(0.18, 0.5, 3, 10); across.rotateZ(Math.PI / 2); across.translate(side * 0.45, y, 0);
    const upArm = new THREE.CapsuleGeometry(0.18, len, 3, 10); upArm.translate(side * 0.75, y + len / 2, 0);
    const rot = rand() * Math.PI * 2;
    across.rotateY(rot); upArm.rotateY(rot);
    parts.push(across, upArm);
  }
  const g = new THREE.Group();
  g.add(new THREE.Mesh(mergeGeometries(parts.map((p) => p.toNonIndexed())), new THREE.MeshStandardMaterial({ map: cactusTex, color: snowy ? 0xdde4e0 : 0xffffff, roughness: 0.8 })));
  return g;
}

const props = [];
function place(p, wz) {
  const side = rand() < 0.5 ? -1 : 1;
  p.x = side * (B.near + Math.pow(rand(), 1.2) * 55);
  if (B.sea && p.x > 8) p.x = -p.x; // nothing grows in the sea
  p.wz = wz;
  p.obj.rotation.y = rand() * Math.PI * 2;
  p.obj.scale.setScalar(0.75 + rand() * 0.55);
}
(function buildTrees() {
  const variants = {};
  const kinds = [...new Set(B.trees)];
  for (const k of kinds) variants[k] = [KINDS[k](), KINDS[k](), KINDS[k]()];
  for (let i = 0; i < B.count; i++) {
    const obj = pick(variants[pick(B.trees)]).clone();
    scene.add(obj);
    const p = { obj, x: 0, wz: 0 };
    place(p, rand() * 300 - 15);
    props.push(p);
  }
  // Hedges along the field lanes.
  if (B.hedges) {
    const hedge = [KINDS.bush(), KINDS.bush(), KINDS.bush()];
    for (let i = 0; i < 70; i++) {
      const obj = pick(hedge).clone();
      scene.add(obj);
      const p = { obj, x: 0, wz: 0, hedge: true };
      p.x = (i % 2 ? -1 : 1) * (4.2 + rand() * 0.6); p.wz = rand() * 300 - 15;
      obj.scale.setScalar(0.9 + rand() * 0.4);
      props.push(p);
    }
  }
})();

// ---- the old town: terraces both sides of a paved street, street trees and lamps ----
// Each house is a box with a painted front (storeys of windows, a door, the house colour), a hipped
// roof, and lit windows at night. Houses run on continuously along each side: one that is passed
// is moved to the far end of its row.
const town = [];
if (B.town) {
  const COLOURS = ["#9b4a3a", "#b86a4e", "#e8dcc4", "#f2efe8", "#c9b48a", "#8d9a86", "#7d8fa0", "#d8c0a8"];
  const ROOFS = [0x4a3a36, 0x5a4a44, 0x3c3f44, 0x6a3a2e];
  function facade(w, h, storeys, cols, colour, lit) {
    const c = document.createElement("canvas"); c.width = 256; c.height = Math.round(256 * h / w);
    const g = c.getContext("2d"), W = c.width, H = c.height;
    g.fillStyle = lit ? "#000" : colour; g.fillRect(0, 0, W, H);
    if (!lit) { // brick or render texture: faint courses
      g.globalAlpha = 0.08; g.fillStyle = "#000";
      for (let y = 0; y < H; y += 6) g.fillRect(0, y, W, 1);
      g.globalAlpha = 1;
    }
    const fh = H / storeys;
    for (let s = 0; s < storeys; s++) for (let k = 0; k < cols; k++) {
      const cw = W / cols, x = k * cw + cw * 0.25, y = H - (s + 1) * fh + fh * 0.22, ww = cw * 0.5, wh = fh * 0.55;
      const door = s === 0 && k === Math.floor(cols / 2);
      if (lit) {
        if (!door && rand() < 0.45) { g.fillStyle = rand() < 0.5 ? "#ffd89a" : "#ffe9c0"; g.fillRect(x, y, ww, wh); }
        continue;
      }
      if (door) { g.fillStyle = "#3a2a22"; g.fillRect(x + ww * 0.1, H - fh * 0.78, ww * 0.8, fh * 0.78); continue; }
      g.fillStyle = "#f4f2ee"; g.fillRect(x - 3, y - 3, ww + 6, wh + 6); // frame
      g.fillStyle = "#3d4a56"; g.fillRect(x, y, ww, wh);
      g.fillStyle = "rgba(255,255,255,0.18)"; g.fillRect(x, y, ww * 0.45, wh); // glass sheen
      g.fillStyle = "#f4f2ee"; g.fillRect(x + ww / 2 - 1, y, 2, wh); g.fillRect(x, y + wh / 2 - 1, ww, 2);
    }
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4;
    return t;
  }
  function house() {
    const storeys = 2 + Math.floor(rand() * 2), w = 5.5 + rand() * 3.5, h = storeys * 3.1 + 0.6, d = 8;
    const cols = w > 7.5 ? 3 : 2, colour = pick(COLOURS);
    const front = new THREE.MeshStandardMaterial({ map: facade(w, h, storeys, cols, colour, false), roughness: 0.9,
      emissive: 0xffffff, emissiveMap: facade(w, h, storeys, cols, colour, true), emissiveIntensity: night ? 1.1 : TIME === "sunset" ? 0.25 : 0 });
    const wall = new THREE.MeshStandardMaterial({ color: colour, roughness: 0.95 });
    const body = new THREE.Mesh(new THREE.BoxGeometry(d, h, w), [front, front, wall, wall, wall, wall]);
    body.position.y = h / 2;
    const roofH = 2.2 + rand() * 1.6;
    const roof = new THREE.Mesh(new THREE.ConeGeometry(Math.SQRT1_2, 1, 4, 1), new THREE.MeshStandardMaterial({ color: snowy ? 0xe8ecf0 : pick(ROOFS), roughness: 0.85, flatShading: true }));
    roof.rotation.y = Math.PI / 4;
    roof.scale.set(d + 0.6, roofH, w + 0.2);
    roof.position.y = h + roofH / 2;
    const g = new THREE.Group(); g.add(body, roof);
    g.userData.w = w;
    return g;
  }
  const variants = Array.from({ length: 10 }, house);
  // Two rows; the street side of each house faces the trail.
  for (const side of [-1, 1]) {
    let wz = -20;
    for (let i = 0; i < 44; i++) {
      const obj = pick(variants).clone();
      const w = obj.userData.w;
      scene.add(obj);
      town.push({ obj, side, x: side * (12.6 + rand() * 0.6), wz: wz + w / 2, w, row: side }); // fronts at ~8.6 m: room for the pavement trees
      wz += w + (rand() < 0.15 ? 2 + rand() * 4 : 0.05); // now and then a gap (an alley)
    }
  }
  // Street trees in the pavement and lamp posts, every so often on both sides.
  const trees = [KINDS.oak(), KINDS.maple(), KINDS.birch()];
  const lampMat = new THREE.MeshStandardMaterial({ color: 0x2c2f33, roughness: 0.6, metalness: 0.4 });
  const glass = new THREE.MeshStandardMaterial({ color: 0xfff1d0, emissive: 0xffd590, emissiveIntensity: night ? 2.2 : 0.1 });
  function lamp() {
    const g = new THREE.Group();
    const post = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.09, 4.2, 8), lampMat); post.position.y = 2.1;
    const head = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.28, 0.45, 6), lampMat); head.position.y = 4.35;
    const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.16, 10, 8), glass); bulb.position.y = 4.12;
    g.add(post, head, bulb);
    return g;
  }
  for (let i = 0; i < 26; i++) {
    const side = i % 2 ? -1 : 1;
    const tree = pick(trees).clone(); tree.scale.setScalar(0.42 + rand() * 0.14); tree.rotation.y = rand() * 6;
    scene.add(tree);
    town.push({ obj: tree, x: side * (4.6 + rand() * 0.3), wz: i * 13 + rand() * 4, span: 26 * 13 });
    const l = lamp(); scene.add(l);
    town.push({ obj: l, x: -side * 4.4, wz: i * 13 + 6.5, span: 26 * 13 });
  }
}
function updateTown() {
  if (!town.length) return;
  const rowEnd = { "-1": -Infinity, "1": -Infinity };
  for (const t of town) if (t.row) rowEnd[t.row] = Math.max(rowEnd[t.row], t.wz + t.w / 2);
  for (const t of town) {
    if (t.row && t.wz + t.w / 2 < travelled - 15) { t.wz = rowEnd[t.row] + 0.05 + t.w / 2; rowEnd[t.row] = t.wz + t.w / 2; }
    if (t.span && t.wz < travelled - 15) t.wz += t.span;
    const ahead = t.wz - travelled;
    t.obj.position.set(windPath(t.wz) - windPath(travelled) + t.x, ahead * grade, -ahead);
    t.obj.visible = ahead < 320;
  }
}

// ---- rocks: faceted noise spheres in the rock texture (wicklight's ProcRock) ----
const rockTex = texLoader.load("ground/rock_baseColor.jpg"); rockTex.colorSpace = THREE.SRGBColorSpace; rockTex.wrapS = rockTex.wrapT = THREE.RepeatWrapping;
const rockMat = new THREE.MeshStandardMaterial({ map: rockTex, color: LAND === "desert" ? 0xd8b088 : LAND === "coast" ? 0xb8b4ac : 0xb0b4b8, roughness: 0.95, flatShading: true });
function rockGeo(squash) {
  const g = new THREE.SphereGeometry(1, 9, 7);
  const p = g.attributes.position;
  const ph = rand() * 10;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const n = 1 + 0.22 * Math.sin(x * 2.1 + ph) * Math.cos(z * 1.9) + 0.1 * Math.sin(y * 4.3 + x * 3.1) + 0.05 * Math.sin(z * 8.2 + ph);
    p.setXYZ(i, x * n, y * n * squash, z * n);
  }
  g.translate(0, 0.35 * squash, 0);
  g.computeVertexNormals();
  return g;
}
const rockShapes = [rockGeo(0.65), rockGeo(0.75), rockGeo(1.3), rockGeo(0.5)];
const rocks = [];
for (let i = 0; i < B.rocks; i++) {
  const m = new THREE.Mesh(pick(rockShapes), rockMat);
  scene.add(m);
  const r = { obj: m, x: 0, wz: 0 };
  placeRock(r, rand() * 300 - 15);
  rocks.push(r);
}
function placeRock(r, wz) {
  const side = rand() < 0.5 ? -1 : 1;
  r.x = side * (2.6 + Math.pow(rand(), 1.4) * 60);
  if (B.sea && r.x > 18) r.x = 14 + rand() * 4; // boulders along the shore
  r.wz = wz;
  r.obj.rotation.y = rand() * Math.PI * 2;
  const k = Math.abs(r.x) < 6 ? 0.25 + rand() * 0.35 : 0.5 + Math.pow(rand(), 2) * (LAND === "mountains" ? 4 : 2);
  r.obj.scale.set(k * (0.8 + rand() * 0.4), k, k * (0.8 + rand() * 0.4));
}

function updateProps() {
  for (const p of props) {
    if (p.wz < travelled - 15) {
      if (p.hedge) p.wz = travelled + 280 + rand() * 25; else place(p, travelled + 280 + rand() * 25);
    }
    const ahead = p.wz - travelled;
    p.obj.position.set(windPath(p.wz) - windPath(travelled) + p.x, ahead * grade + hills(p.x, p.wz) - 0.05, -ahead);
    p.obj.visible = ahead < 320;
  }
  for (const r of rocks) {
    if (r.wz < travelled - 15) placeRock(r, travelled + 280 + rand() * 25);
    const ahead = r.wz - travelled;
    r.obj.position.set(windPath(r.wz) - windPath(travelled) + r.x, ahead * grade + hills(r.x, r.wz) - 0.15 * r.obj.scale.y, -ahead);
    r.obj.visible = ahead < 320;
  }
}

// ---- grass, flowers and crops: three crossed cards per clump, coloured per clump, rooted ----
const clumpGeo = (() => {
  const parts = [];
  for (let i = 0; i < 3; i++) { const a = new THREE.PlaneGeometry(1.1, 0.7); a.translate(0, 0.35, 0); a.rotateY(i * Math.PI / 3); parts.push(a); }
  return mergeGeometries(parts);
})();
function clumpMaterial(file, colour) {
  const m = new THREE.MeshStandardMaterial({ map: tex(file), color: colour, alphaTest: 0.4, side: THREE.DoubleSide, roughness: 1 });
  // Darker at the root, lighter at the tips, so clumps sit in the ground.
  m.onBeforeCompile = (shader) => {
    shader.uniforms.uTime = wind;
    shader.vertexShader = "uniform float uTime;\nvarying float vH;\n" + shader.vertexShader.replace("#include <begin_vertex>",
      `#include <begin_vertex>
      vH = clamp(position.y / 0.7, 0.0, 1.0);
      vec3 at = instanceMatrix[3].xyz;
      transformed.x += sin(uTime * 1.6 + at.x * 0.16 + at.z * 0.16) * 0.14 * vH * vH;`);
    shader.fragmentShader = "varying float vH;\n" + shader.fragmentShader.replace("#include <color_fragment>",
      "#include <color_fragment>\n diffuseColor.rgb *= mix(0.58, 1.14, pow(vH, 0.6));");
  };
  return m;
}
const clumps = [];
function addClumps(file, n, xFn, colour, scale) {
  const mesh = new THREE.InstancedMesh(clumpGeo, clumpMaterial(file, colour), n);
  const items = [];
  const c = new THREE.Color();
  for (let i = 0; i < n; i++) {
    items.push({ x: xFn(), wz: rand() * 120 - 10, s: scale[0] + rand() * (scale[1] - scale[0]), r: rand() * Math.PI, xFn });
    c.setHSL(0, 0, 0.78 + rand() * 0.3);
    mesh.setColorAt(i, c);
  }
  scene.add(mesh);
  clumps.push({ mesh, items });
}
const trailSide = () => (rand() < 0.5 ? -1 : 1) * (1.8 + Math.pow(rand(), 1.6) * 16);
const perKind = Math.round(B.clumpN * (snowy ? 0.4 : 1));
for (const name of B.clumps) addClumps("clump_" + name + ".png", perKind, trailSide, snowy ? 0xdde2e8 : 0xffffff, [0.7, 1.5]);
// Fields: rows of ripe crops on both sides of the lane, beyond the hedges.
if (B.crops && !snowy) {
  addClumps("clump_dry.png", 520, () => (rand() < 0.5 ? -1 : 1) * (6 + Math.floor(rand() * 40) * 0.9), 0xf0d070, [0.9, 1.2]);
  addClumps("clump_lush.png", 260, () => (rand() < 0.5 ? -1 : 1) * (6 + Math.floor(rand() * 40) * 0.9), 0xc8d880, [0.9, 1.2]);
}
const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), up = new THREE.Vector3(0, 1, 0), at = new THREE.Vector3(), scl = new THREE.Vector3();
function updateClumps() {
  for (const c of clumps) {
    c.items.forEach((it, i) => {
      if (it.wz < travelled - 8) { it.wz = travelled + 110 + rand() * 10; it.x = it.xFn(); }
      if (B.sea && it.x > 12) it.x = -it.x;
      const ahead = it.wz - travelled;
      // Clumps grow out of the ground in the last stretch, so nothing pops in.
      const grow = Math.min(1, Math.max(0, (118 - ahead) / 8));
      at.set(windPath(it.wz) - windPath(travelled) + it.x, ahead * grade + hills(it.x, it.wz) - 0.06, -ahead);
      q.setFromAxisAngle(up, it.r);
      scl.setScalar(it.s * grow);
      c.mesh.setMatrixAt(i, m4.compose(at, q, scl));
    });
    c.mesh.instanceMatrix.needsUpdate = true;
  }
}

// ---- the sea, for the coast ----
let sea = null;
if (B.sea) {
  const g = new THREE.PlaneGeometry(900, 900, 1, 1);
  g.rotateX(-Math.PI / 2);
  sea = new THREE.Mesh(g, new THREE.MeshStandardMaterial({ color: night ? 0x0a1420 : grey ? 0x4a5a62 : 0x2a6f8a, roughness: 0.12, metalness: 0.1, transparent: true, opacity: 0.92 }));
  sea.position.set(470, -1.3, -300);
  scene.add(sea);
  // Surf: a pale band where the waves break.
  const surf = new THREE.Mesh(new THREE.PlaneGeometry(3, 900), new THREE.MeshBasicMaterial({ color: 0xf4f6f6, transparent: true, opacity: 0.55 }));
  surf.rotation.x = -Math.PI / 2;
  surf.position.set(20.5, -1.25, -300);
  scene.add(surf);
  sea.userData.surf = surf;
}

// ---- a mountain range all round the horizon, for the mountains ----
// One ring, not separate cones (they read as tents): a ridged noise profile round the compass
// gives peaks, saddles and cols; each ridge rises from its foot to a crest and falls behind it.
// Rock below, snow only on the high crests, hazed toward the sky colour, matte.
if (B.peaks) {
  const A = 360, R = 9, inner = 330, outer = 560;
  const ph = rand() * 10;
  const ridge = (a) => {
    let h = 0, amp = 1, f = 3;
    for (let o = 0; o < 4; o++) { h += amp * Math.pow(1 - Math.abs(Math.sin(a * f + ph * (o + 1))), 2); amp *= 0.5; f *= 2.3; }
    return h / 1.875; // 0..1
  };
  const pos = [], cols = [], idx = [];
  const rockC = new THREE.Color(0x8a8e98), darkC = new THREE.Color(0x585c66), snowC = new THREE.Color(0xeef2f7), c = new THREE.Color();
  for (let i = 0; i <= A; i++) {
    const a = (i / A) * Math.PI * 2;
    const top = 90 + 270 * ridge(a);
    for (let r = 0; r <= R; r++) {
      const t = r / R; // 0 near foot, 1 far foot
      const prof = Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.15)), 0.7);
      const detail = 1 + 0.08 * Math.sin(a * 61 + r * 1.7) + 0.05 * Math.sin(a * 137 + r * 3.1);
      const y = -25 + top * prof * detail;
      const rad = inner + (outer - inner) * t;
      pos.push(Math.cos(a) * rad, y, Math.sin(a) * rad);
      const hRel = (y + 25) / 360;
      const snowLine = 0.4 + 0.05 * Math.sin(a * 23 + ph);
      if (snowy || hRel > snowLine) c.copy(snowC); else c.copy(rockC).lerp(darkC, 0.5 + 0.5 * Math.sin(a * 41 + r));
      c.lerp(fogColour, WEATHER === "fog" ? 0.92 : grey ? 0.55 : 0.38);
      cols.push(c.r, c.g, c.b);
    }
  }
  for (let i = 0; i < A; i++) for (let r = 0; r < R; r++) {
    const p0 = i * (R + 1) + r, p1 = (i + 1) * (R + 1) + r;
    idx.push(p0, p1, p0 + 1, p1, p1 + 1, p0 + 1);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute("color", new THREE.Float32BufferAttribute(cols, 3));
  g.setIndex(idx);
  g.computeVertexNormals();
  // Out of the fog's reach (it would wash them out); the haze is in their colours instead.
  const range = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ vertexColors: true, fog: false, side: THREE.DoubleSide }));
  range.userData.fx = true;
  scene.add(range);
}

// ---- rain and snow: particles in a box that travels with the runner ----
let precip = null;
if (wet || snowy) {
  const n = wet ? 1600 : 2200;
  const box = { x: 40, y: 22, z: 60 };
  const p = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) p.set([(rand() - 0.5) * box.x, rand() * box.y, -rand() * box.z + 6], i * 3);
  if (wet) {
    const segs = new Float32Array(n * 6);
    const g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.BufferAttribute(segs, 3));
    scene.add(new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: night ? 0x8090a8 : 0xc4ccd6, transparent: true, opacity: 0.55 })));
    precip = { kind: "rain", p, n, box, g, segs };
  } else {
    const g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.BufferAttribute(p, 3));
    scene.add(new THREE.Points(g, new THREE.PointsMaterial({ color: 0xffffff, size: 0.09, transparent: true, opacity: 0.9 })));
    precip = { kind: "snow", p, n, box, g };
  }
}
function updatePrecip(dt) {
  if (!precip) return;
  const { p, n, box } = precip;
  const fall = precip.kind === "rain" ? 16 : 1.3;
  for (let i = 0; i < n; i++) {
    let x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
    y -= fall * dt;
    z += speed * dt;
    if (precip.kind === "snow") x += Math.sin((y + i) * 0.7) * 0.3 * dt; else x += 1.2 * dt;
    if (y < 0 || z > 8) { y = box.y * (0.6 + rand() * 0.4); z = -rand() * box.z + 6; x = (rand() - 0.5) * box.x; }
    p[i * 3] = x; p[i * 3 + 1] = y; p[i * 3 + 2] = z;
    if (precip.kind === "rain") precip.segs.set([x, y, z, x - 0.05, y + 0.55, z], i * 6);
  }
  precip.g.attributes.position.needsUpdate = true;
}

// ---- the trail map ----
const mapCanvas = document.getElementById("map");
if (params.get("preview")) mapCanvas.style.display = "none"; // card pictures (preview/*.jpg) have no map
const mapCtx = mapCanvas.getContext("2d");
// A workout made from a GPX file passes its real route (route=x,y;x,y..., GpxRoute.outline);
// anything else gets a winding route drawn from the seed.
const realRoute = (() => {
  const pts = (params.get("route") || "").split(";").map((p) => p.split(",").map(Number)).filter((p) => p.length === 2 && p.every(Number.isFinite));
  return pts.length >= 2 ? pts : null;
})();
const route = realRoute || (() => {
  const pts = [];
  let x = 0, y = 0;
  const step = TOTAL / 240;
  for (let i = 0; i <= 240; i++) {
    pts.push([x, y]);
    const d = i * step;
    const heading = 0.9 * Math.sin(d / 420 + seed) + 0.5 * Math.sin(d / 190 + seed * 2);
    x += Math.sin(heading) * step; y -= Math.cos(heading) * step;
  }
  const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const span = Math.max(maxX - minX, maxY - minY) || 1;
  return pts.map(([px, py]) => [(px - (minX + maxX) / 2) / span, (py - (minY + maxY) / 2) / span]);
})();
function drawMap() {
  const w = mapCanvas.width, h = mapCanvas.height, pad = 16;
  const toX = (u) => w / 2 + u * (w - pad * 2), toY = (v) => h / 2 + v * (h - pad * 2) - 8;
  mapCtx.clearRect(0, 0, w, h);
  const done = Math.min(1, (travelled + START) / TOTAL);
  const cut = Math.round(done * (route.length - 1));
  const line = (from, to, colour, width) => {
    mapCtx.beginPath();
    for (let i = from; i <= to; i++) { const [u, v] = route[i]; i === from ? mapCtx.moveTo(toX(u), toY(v)) : mapCtx.lineTo(toX(u), toY(v)); }
    mapCtx.strokeStyle = colour; mapCtx.lineWidth = width; mapCtx.lineCap = "round"; mapCtx.lineJoin = "round"; mapCtx.stroke();
  };
  line(0, route.length - 1, "rgba(255,255,255,0.35)", 5);
  if (cut > 0) line(0, cut, "#d0bcff", 5);
  const dot = (i, colour, r) => { const [u, v] = route[i]; mapCtx.beginPath(); mapCtx.arc(toX(u), toY(v), r, 0, Math.PI * 2); mapCtx.fillStyle = colour; mapCtx.fill(); };
  dot(0, "#4ade80", 5);
  dot(route.length - 1, "#f87171", 5);
  dot(cut, "#ffffff", 7);
  const unit = MILES ? 1609.344 : 1000;
  mapCtx.fillStyle = "#ffffff"; mapCtx.font = "600 13px sans-serif"; mapCtx.textAlign = "center";
  mapCtx.fillText((Math.min(travelled + START, TOTAL) / unit).toFixed(2) + " / " + (TOTAL / unit).toFixed(1) + (MILES ? " mi" : " km"), w / 2, h - 8);
}

// ======================================================================================
// Polish (user 2026-11-25: "ours look pretty sad"). From how other apps and game artists do it:
// depth in layers (hazed ridgelines behind the near scene), real shadows, a colour grade, set
// dressing scattered along the path, and a little life in the air (pollen, leaves, birds, light).
// ======================================================================================

// ---- layered horizon: rings of hills (or mesas) behind the near scene, each hazier than the last ----
function hillRing({ inner, outer, top, colour, haze, flat = false, serrate = 0.02, colourFn = null, y0 = -6 }) {
  const A = 300, R = 6, ph = rand() * 10;
  const pos = [], cols = [], idx = [];
  const base = new THREE.Color(colour), c = new THREE.Color();
  const shade = night ? 0.28 : TIME === "sunset" ? 0.85 : TIME === "morning" ? 0.95 : 1;
  const sunset = new THREE.Color(0xff9a5a);
  for (let i = 0; i <= A; i++) {
    const a = (i / A) * Math.PI * 2;
    const n = 0.5 + 0.5 * (0.55 * Math.sin(a * 3 + ph) + 0.3 * Math.sin(a * 7 + ph * 2.3) + 0.15 * Math.sin(a * 17 + ph * 1.7));
    const h = top(a, n) * (1 + serrate * (Math.sin(a * 83 + ph) + 0.6 * Math.sin(a * 197 + ph * 3)));
    for (let r = 0; r <= R; r++) {
      const t = r / R;
      let prof = Math.max(0, Math.sin(Math.PI * Math.min(1, t * 1.1)));
      prof = Math.pow(prof, flat ? 0.45 : 0.8);
      const y = y0 + h * prof;
      const rad = inner + (outer - inner) * t;
      pos.push(Math.cos(a) * rad, y, Math.sin(a) * rad);
      if (colourFn) c.copy(colourFn(a, t, prof, n, y)); else c.copy(base).multiplyScalar(0.8 + 0.4 * n * (0.4 + 0.6 * prof));
      c.multiplyScalar(shade);
      if (TIME === "sunset") c.lerp(sunset, 0.12 * (0.5 + 0.5 * Math.cos(a + 0.7)));
      if (snowy) c.lerp(new THREE.Color(0xf2f5fa), 0.75 * Math.min(1, prof * 1.3));
      c.lerp(fogColour, WEATHER === "fog" ? 0.93 : haze + (grey ? 0.12 : 0));
      cols.push(c.r, c.g, c.b);
    }
  }
  for (let i = 0; i < A; i++) for (let r = 0; r < R; r++) {
    const p0 = i * (R + 1) + r, p1 = (i + 1) * (R + 1) + r;
    idx.push(p0, p1, p0 + 1, p1, p1 + 1, p0 + 1);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute("color", new THREE.Float32BufferAttribute(cols, 3));
  g.setIndex(idx);
  g.computeVertexNormals();
  const m = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ vertexColors: true, fog: false, side: THREE.DoubleSide }));
  m.userData.fx = true;
  scene.add(m);
  return m;
}
const seaSide = (a) => 1 - 0.88 * Math.min(1, Math.max(0, (Math.cos(a) + 0.2) / 0.7)); // coast: low on the sea side
const HORIZON = {
  forest: [
    { inner: 290, outer: 370, top: (a, n) => 20 + 30 * n, colour: 0x2f4c33, haze: 0.42, serrate: 0.12 },
    { inner: 420, outer: 540, top: (a, n) => 45 + 55 * n, colour: 0x4f6e62, haze: 0.66, serrate: 0.05 },
  ],
  pine: [
    { inner: 290, outer: 370, top: (a, n) => 26 + 34 * n, colour: 0x1d3a2c, haze: 0.4, serrate: 0.16 },
    { inner: 420, outer: 540, top: (a, n) => 55 + 60 * n, colour: 0x3c5c52, haze: 0.64, serrate: 0.06 },
  ],
  autumn: [
    { inner: 290, outer: 370, top: (a, n) => 20 + 30 * n, colour: 0x9a5c26, haze: 0.4, serrate: 0.12 },
    { inner: 420, outer: 540, top: (a, n) => 45 + 55 * n, colour: 0x8a7a6a, haze: 0.66, serrate: 0.05 },
  ],
  meadow: [
    { inner: 300, outer: 380, top: (a, n) => 10 + 26 * n, colour: 0x6f9250, haze: 0.38 },
    { inner: 430, outer: 560, top: (a, n) => 30 + 55 * n, colour: 0x7f9f94, haze: 0.64 },
  ],
  fields: [
    {
      inner: 300, outer: 380, top: (a, n) => 8 + 22 * n, haze: 0.36,
      // A patchwork of crops and pasture on the near hills.
      colourFn: (a, t, prof, n) => new THREE.Color(Math.sin(a * 23) + Math.sin(a * 41 + t * 4) > 0.3 ? 0xc6b062 : Math.sin(a * 17 + t * 6) > -0.2 ? 0x7f9c52 : 0x9aa85a),
    },
    { inner: 430, outer: 560, top: (a, n) => 28 + 45 * n, colour: 0x8a9c90, haze: 0.62 },
  ],
  moor: [
    { inner: 290, outer: 380, top: (a, n) => 22 + 42 * n, colour: 0x5c4c58, haze: 0.4 },
    { inner: 430, outer: 560, top: (a, n) => 45 + 60 * n, colour: 0x6c6a80, haze: 0.66 },
  ],
  desert: [
    {
      // Flat-topped mesas with bands of rock.
      inner: 300, outer: 380, flat: true, haze: 0.2, serrate: 0.012,
      // Separate mesas with gaps between, not one long wall.
      top: (a, n) => ((Math.sin(a * 6 + 1.3) + 0.7 * Math.sin(a * 11 + 0.4)) > 0.45 ? 38 + 46 * n : 6 + 8 * n),
      colourFn: (a, t, prof, n, y) => new THREE.Color(0xb85a30).lerp(new THREE.Color(0xe8a470), 0.5 + 0.5 * Math.sin(y * 0.55 + a * 3 + n * 4)).multiplyScalar(0.75 + 0.45 * prof),
    },
    { inner: 430, outer: 560, top: (a, n) => 25 + 55 * n, colour: 0xc89a78, haze: 0.62, flat: true },
  ],
  coast: [], // the beach panorama already has its cliffs
  mountains: [
    { inner: 250, outer: 340, top: (a, n) => 24 + 34 * n, colour: 0x38503a, haze: 0.4, serrate: 0.14 },
  ],
  town: [
    { inner: 320, outer: 420, top: (a, n) => 18 + 32 * n, colour: 0x6a7a68, haze: 0.55, serrate: 0.05 },
    { inner: 450, outer: 560, top: (a, n) => 40 + 50 * n, colour: 0x8a98aa, haze: 0.7 },
  ],
};
for (const layer of HORIZON[LAND] || HORIZON.forest) hillRing(layer);

// ---- the sun's glow near a low sun, and slanting light shafts through woodland ----
const glowTex = (() => {
  const c = document.createElement("canvas"); c.width = c.height = 128;
  const g = c.getContext("2d"), gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  gr.addColorStop(0, "rgba(255,255,255,1)"); gr.addColorStop(0.18, "rgba(255,255,255,0.55)"); gr.addColorStop(0.5, "rgba(255,255,255,0.12)"); gr.addColorStop(1, "rgba(255,255,255,0)");
  g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
  return new THREE.CanvasTexture(c);
})();
if (!night && !grey && (TIME === "sunset" || TIME === "morning")) {
  const glow = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex, color: LOOK.sun, blending: THREE.AdditiveBlending, depthWrite: false, fog: false, toneMapped: false, opacity: 0.7 }));
  glow.position.copy(sunDir).multiplyScalar(700);
  glow.scale.set(520, 520, 1);
  scene.add(glow);
}
const shafts = [];
let shaftMat = null;
if (!night && !wet && !snowy && ["forest", "pine", "autumn"].includes(LAND) && WEATHER !== "overcast") {
  const tex = (() => {
    const c = document.createElement("canvas"); c.width = 64; c.height = 256;
    const g = c.getContext("2d");
    const v = g.createLinearGradient(0, 0, 0, 256); v.addColorStop(0, "rgba(255,255,255,0.0)"); v.addColorStop(0.25, "rgba(255,255,255,0.7)"); v.addColorStop(1, "rgba(255,255,255,0.0)");
    g.fillStyle = v; g.fillRect(0, 0, 64, 256);
    g.globalCompositeOperation = "destination-in";
    const h = g.createLinearGradient(0, 0, 64, 0); h.addColorStop(0, "rgba(0,0,0,0)"); h.addColorStop(0.5, "rgba(0,0,0,1)"); h.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = h; g.fillRect(0, 0, 64, 256);
    return new THREE.CanvasTexture(c);
  })();
  const mat = shaftMat = new THREE.MeshBasicMaterial({ map: tex, color: LOOK.sun, transparent: true, opacity: WEATHER === "fog" ? 0.3 : 0.2, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide });
  for (let i = 0; i < 9; i++) {
    const g = new THREE.Group();
    for (let k = 0; k < 2; k++) {
      const p = new THREE.Mesh(new THREE.PlaneGeometry(2.5 + rand() * 4, 44), mat);
      p.rotation.y = k * Math.PI / 2 + rand();
      p.userData.fx = true;
      g.add(p);
    }
    // The shaft runs along the sun's direction, its foot on the ground.
    g.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), sunDir);
    scene.add(g);
    shafts.push({ obj: g, x: (rand() - 0.5) * 36, wz: 12 + i * 22 + rand() * 10, tw: rand() * 6 });
  }
}
function updateShafts(now) {
  if (shaftMat) shaftMat.opacity = (WEATHER === "fog" ? 0.3 : 0.2) * (0.8 + 0.2 * Math.sin(now / 1800));
  for (const s of shafts) {
    if (s.wz < travelled + 6) { s.wz = travelled + 190 + rand() * 20; s.x = (rand() - 0.5) * 36; }
    const ahead = s.wz - travelled;
    s.obj.position.set(windPath(s.wz) - windPath(travelled) + s.x, ahead * grade + hills(s.x, s.wz) + 20 * sunDir.y, -ahead);
    s.obj.visible = ahead < 200;
  }
}

// ---- set dressing: logs, stumps, fences, walls, stones, hay, barns, a lighthouse, boats ----
const woodMat = new THREE.MeshStandardMaterial({ map: barkTex, color: 0x9a8268, roughness: 1 });
const plankMat = new THREE.MeshStandardMaterial({ color: 0x9c8a74, roughness: 1 });
const stoneMat = new THREE.MeshStandardMaterial({ map: rockTex, color: 0xa4a6a4, roughness: 1, flatShading: true });
const grp = (...m) => { const g = new THREE.Group(); g.add(...m); return g; };
const BUILD = {
  log() {
    const len = 2.6 + rand() * 3, r = 0.2 + rand() * 0.18;
    const g = new THREE.CylinderGeometry(r, r * 1.08, len, 10, 4); g.rotateZ(Math.PI / 2); g.translate(0, r * 0.85, 0);
    const p = g.attributes.position; for (let i = 0; i < p.count; i++) p.setY(i, p.getY(i) + (rand() - 0.5) * 0.05);
    g.computeVertexNormals();
    return grp(new THREE.Mesh(g, woodMat));
  },
  stump() {
    const r = 0.3 + rand() * 0.22, h = 0.35 + rand() * 0.45;
    const m = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.9, r * 1.2, h, 10, 2), woodMat); m.position.y = h / 2;
    const top = new THREE.Mesh(new THREE.CircleGeometry(r * 0.9, 10), new THREE.MeshStandardMaterial({ color: 0xc8a878, roughness: 1 })); top.rotation.x = -Math.PI / 2; top.position.y = h + 0.003;
    return grp(m, top);
  },
  fence() { // 6 m of post-and-rail
    const post = (x) => { const m = new THREE.Mesh(new THREE.BoxGeometry(0.13, 1.15, 0.13), plankMat); m.position.set(x, 0.57, 0); return m; };
    const rail = (y) => { const m = new THREE.Mesh(new THREE.BoxGeometry(6.1, 0.09, 0.06), plankMat); m.position.set(0, y, 0); return m; };
    return grp(post(-3), post(0), post(3), rail(0.5), rail(0.9));
  },
  wall() { // 4 m of dry-stone wall
    const g = new THREE.BoxGeometry(4.05, 0.85, 0.5, 6, 2, 1); const p = g.attributes.position;
    for (let i = 0; i < p.count; i++) p.setXYZ(i, p.getX(i) + (rand() - 0.5) * 0.06, p.getY(i) + (rand() - 0.5) * 0.06, p.getZ(i) + (rand() - 0.5) * 0.05);
    g.computeVertexNormals(); const m = new THREE.Mesh(g, stoneMat); m.position.y = 0.42; return grp(m);
  },
  stone() { // a standing stone
    const h = 2.2 + rand() * 1.6, m = new THREE.Mesh(new THREE.BoxGeometry(0.7 + rand() * 0.3, h, 0.4 + rand() * 0.2, 2, 3, 2), stoneMat);
    const p = m.geometry.attributes.position; for (let i = 0; i < p.count; i++) p.setX(i, p.getX(i) + (rand() - 0.5) * 0.12 + p.getY(i) * 0.03);
    m.geometry.computeVertexNormals(); m.position.y = h / 2 - 0.1; m.rotation.z = (rand() - 0.5) * 0.12; return grp(m);
  },
  cairn() { // a stack of flat stones
    const g = new THREE.Group(); let y = 0, r = 0.55 + rand() * 0.2;
    for (let i = 0; i < 4; i++) { const s = new THREE.Mesh(rockShapes[i % 2], stoneMat); s.scale.set(r * 1.3, r * 0.55, r * 1.2); s.position.set((rand() - 0.5) * 0.12, y + r * 0.2, (rand() - 0.5) * 0.12); s.rotation.y = rand() * 6; g.add(s); y += r * 0.42; r *= 0.78; }
    return g;
  },
  hay() { // a round bale
    const g = new THREE.CylinderGeometry(0.75, 0.75, 1.3, 16); g.rotateZ(Math.PI / 2);
    const m = new THREE.Mesh(g, new THREE.MeshStandardMaterial({ color: 0xcfae58, roughness: 1 })); m.position.y = 0.75; return grp(m);
  },
  pillar() { // a desert rock tower
    const g = new THREE.Group(); let y = 0, r = 2.6 + rand() * 1.6;
    const mat = new THREE.MeshStandardMaterial({ map: rockTex, color: 0xc08258, roughness: 1, flatShading: true });
    for (let i = 0; i < 4; i++) { const s = new THREE.Mesh(rockShapes[(i + 2) % 4], mat); s.scale.set(r, r * (1.2 + rand() * 0.6), r); s.position.y = y + r * 0.5; s.rotation.y = rand() * 6; g.add(s); y += r * 1.1; r *= 0.82; }
    return g;
  },
  barn() {
    const w = 6.5, h = 4.6, L = 10;
    const wallC = document.createElement("canvas"); wallC.width = 128; wallC.height = 128;
    const x = wallC.getContext("2d"); x.fillStyle = "#8c2f24"; x.fillRect(0, 0, 128, 128); x.fillStyle = "rgba(0,0,0,0.25)"; for (let i = 0; i < 128; i += 8) x.fillRect(i, 0, 1.5, 128);
    const wt = new THREE.CanvasTexture(wallC); wt.colorSpace = THREE.SRGBColorSpace; wt.wrapS = wt.wrapT = THREE.RepeatWrapping; wt.repeat.set(3, 1);
    const body = new THREE.Mesh(new THREE.BoxGeometry(w, h, L), new THREE.MeshStandardMaterial({ map: wt, roughness: 0.95 })); body.position.y = h / 2;
    const sh = new THREE.Shape(); sh.moveTo(-w / 2 - 0.3, 0); sh.lineTo(w / 2 + 0.3, 0); sh.lineTo(0, 2.6); sh.closePath();
    const rg = new THREE.ExtrudeGeometry(sh, { depth: L + 0.6, bevelEnabled: false }); rg.translate(0, 0, -(L + 0.6) / 2);
    const roof = new THREE.Mesh(rg, new THREE.MeshStandardMaterial({ color: 0x4a4540, roughness: 0.9 })); roof.position.y = h;
    const door = new THREE.Mesh(new THREE.BoxGeometry(2.4, 3, 0.1), new THREE.MeshStandardMaterial({ color: 0xe8e0d0, roughness: 1 })); door.position.set(0, 1.5, L / 2 + 0.05);
    const silo = new THREE.Mesh(new THREE.CylinderGeometry(1.5, 1.5, 9, 14), new THREE.MeshStandardMaterial({ color: 0xc9c9c4, roughness: 0.7 })); silo.position.set(w / 2 + 2.4, 4.5, -2);
    const dome = new THREE.Mesh(new THREE.SphereGeometry(1.5, 14, 8, 0, Math.PI * 2, 0, Math.PI / 2), new THREE.MeshStandardMaterial({ color: 0x8a8a88, roughness: 0.6 })); dome.position.set(w / 2 + 2.4, 9, -2);
    return grp(body, roof, door, silo, dome);
  },
  lighthouse() {
    const c = document.createElement("canvas"); c.width = 16; c.height = 128; const g2 = c.getContext("2d");
    for (let i = 0; i < 4; i++) { g2.fillStyle = i % 2 ? "#f4f0ea" : "#b3262a"; g2.fillRect(0, i * 32, 16, 32); }
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
    const tower = new THREE.Mesh(new THREE.CylinderGeometry(1.4, 2.4, 22, 20), new THREE.MeshStandardMaterial({ map: t, roughness: 0.8 })); tower.position.y = 11;
    const gallery = new THREE.Mesh(new THREE.CylinderGeometry(2.1, 2.1, 0.4, 20), new THREE.MeshStandardMaterial({ color: 0x2c2f33 })); gallery.position.y = 22.2;
    const lamp = new THREE.Mesh(new THREE.CylinderGeometry(1.0, 1.0, 2, 14), new THREE.MeshStandardMaterial({ color: 0xfff1c8, emissive: 0xffd070, emissiveIntensity: night ? 2.5 : 0.25 })); lamp.position.y = 23.4;
    const cap = new THREE.Mesh(new THREE.ConeGeometry(1.4, 1.6, 14), new THREE.MeshStandardMaterial({ color: 0xb3262a })); cap.position.y = 25.2;
    return grp(tower, gallery, lamp, cap);
  },
  boat() {
    const hull = new THREE.Mesh(new THREE.BoxGeometry(5, 0.9, 1.8), new THREE.MeshStandardMaterial({ color: 0xf0eee8, roughness: 0.7 })); hull.position.y = 0.3;
    const stripe = new THREE.Mesh(new THREE.BoxGeometry(5.02, 0.18, 1.82), new THREE.MeshStandardMaterial({ color: 0x2a5a8a })); stripe.position.y = 0.55;
    const mast = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 6, 6), plankMat); mast.position.y = 3.6;
    const sh = new THREE.Shape(); sh.moveTo(0, 0); sh.lineTo(0, 5.2); sh.lineTo(2.6, 0.2); sh.closePath();
    const sail = new THREE.Mesh(new THREE.ShapeGeometry(sh), new THREE.MeshStandardMaterial({ color: 0xfaf8f2, side: THREE.DoubleSide, roughness: 0.9 })); sail.position.set(-0.8, 0.9, 0.05);
    return grp(hull, stripe, mast, sail);
  },
};
const decor = [];
// A scatter: [builder, count, x range]. Passed items come round again far ahead.
function scatter(name, count, xmin, xmax, { span = 0, scale = [0.9, 1.2] } = {}) {
  for (let i = 0; i < count; i++) {
    const obj = BUILD[name](); scene.add(obj);
    const d = { obj, scatter: true, name, xmin, xmax, scale, span, x: 0, wz: rand() * 300 - 20 };
    rollDecor(d);
    decor.push(d);
  }
}
function rollDecor(d) {
  const side = rand() < 0.5 ? -1 : 1;
  d.x = side * (d.xmin + rand() * (d.xmax - d.xmin));
  if (B.sea && d.x > 8 && d.name !== "boat") d.x = -d.x;
  if (d.name === "boat") d.x = 70 + rand() * 120;
  d.obj.rotation.y = d.name === "boat" ? (rand() - 0.5) * 0.5 : rand() * Math.PI * 2;
  d.obj.scale.setScalar(d.scale[0] + rand() * (d.scale[1] - d.scale[0]));
}
// A row: one object after another along a side of the path, end to end.
function row(name, spacing, x, count, len = spacing) {
  for (let i = 0; i < count; i++) {
    const obj = BUILD[name](); scene.add(obj);
    decor.push({ obj, row: true, name, x, wz: i * spacing + 6, spacing, count, face: x < 0 ? Math.PI / 2 : Math.PI / 2 });
    obj.rotation.y = Math.PI / 2;
  }
}
if (["forest", "pine", "autumn"].includes(LAND)) { scatter("log", 12, 4, 34); scatter("stump", 12, 3.5, 30); }
if (LAND === "meadow") { row("fence", 6, -5.6, 50); row("fence", 6, 5.6, 50); scatter("barn", 2, 45, 90, { span: 400 }); scatter("hay", 6, 9, 36); }
if (LAND === "fields") { scatter("barn", 3, 40, 80, { span: 400 }); scatter("hay", 26, 8, 44); }
if (LAND === "moor") { row("wall", 4, -5.2, 70); row("wall", 4, 5.2, 70); scatter("stone", 10, 6, 40); scatter("cairn", 6, 5, 30); }
if (LAND === "mountains") scatter("cairn", 12, 3.5, 30);
if (LAND === "desert") scatter("pillar", 7, 22, 80, { scale: [0.8, 1.7] });
if (LAND === "coast") { scatter("lighthouse", 1, 55, 70, { span: 700, scale: [1.5, 1.5] }); scatter("boat", 5, 70, 190); scatter("log", 8, 3.5, 12); }
function updateDecor(now) {
  const ends = {};
  for (const d of decor) if (d.row) ends[d.x] = Math.max(ends[d.x] ?? -Infinity, d.wz);
  for (const d of decor) {
    if (d.wz < travelled - 15) {
      if (d.row) { d.wz = ends[d.x] + d.spacing; ends[d.x] = d.wz; }
      else { d.wz = travelled + 280 + rand() * 25 + (d.span ? rand() * d.span : 0); rollDecor(d); }
    }
    const ahead = d.wz - travelled;
    const y = ahead * grade + hills(d.x, d.wz) - 0.04;
    d.obj.position.set(windPath(d.wz) - windPath(travelled) + d.x, d.name === "boat" ? -1.15 + Math.sin(now / 900 + d.wz) * 0.12 : y, -ahead);
    d.obj.visible = ahead < 320;
  }
}

// ---- life in the air: pollen, falling leaves, desert dust, and birds ----
const dust = (() => {
  if (night || wet || snowy || WEATHER === "fog") return null;
  let kind = null, n = 0, col = 0xfff4cc, size = 2.2, op = 0.55;
  if (["forest", "pine", "meadow", "fields", "coast", "town"].includes(LAND)) { kind = "pollen"; n = 260; }
  if (LAND === "autumn") { kind = "leaves"; n = 140; col = 0xd8782a; size = 5; op = 0.9; }
  if (LAND === "desert") { kind = "sand"; n = 220; col = 0xe8c898; size = 1.8; op = 0.5; }
  if (!kind) return null;
  const box = { x: 36, y: 12, z: 48 };
  const p = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) p.set([(rand() - 0.5) * box.x, rand() * box.y, -rand() * box.z + 4], i * 3);
  const g = new THREE.BufferGeometry(); g.setAttribute("position", new THREE.BufferAttribute(p, 3));
  const pts = new THREE.Points(g, new THREE.PointsMaterial({ color: col, size, sizeAttenuation: false, transparent: true, opacity: op, depthWrite: false, blending: kind === "leaves" ? THREE.NormalBlending : THREE.AdditiveBlending }));
  scene.add(pts);
  return { kind, p, n, box, g };
})();
function updateDust(dt, now) {
  if (!dust) return;
  const { p, n, box, kind } = dust;
  for (let i = 0; i < n; i++) {
    let x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
    z += speed * dt; // the runner moves through them
    if (kind === "leaves") { y -= 0.8 * dt; x += Math.sin(now / 700 + i) * 0.6 * dt; z += Math.cos(now / 900 + i * 2) * 0.4 * dt; }
    else if (kind === "sand") { x -= 2.2 * dt; y += Math.sin(now / 800 + i) * 0.05 * dt; }
    else { x += Math.sin(now / 1500 + i) * 0.12 * dt; y += Math.sin(now / 1100 + i * 1.7) * 0.15 * dt; }
    if (z > 4 || y < 0 || y > box.y || x < -box.x / 2 || x > box.x / 2) { z = -rand() * box.z + 4; x = (rand() - 0.5) * box.x; y = kind === "leaves" ? box.y * (0.5 + rand() * 0.5) : rand() * box.y; }
    p[i * 3] = x; p[i * 3 + 1] = y; p[i * 3 + 2] = z;
  }
  dust.g.attributes.position.needsUpdate = true;
}
const flock = [];
if (!night && !wet && !snowy && WEATHER !== "fog") {
  const birdMat = new THREE.MeshBasicMaterial({ color: night ? 0x000000 : 0x2a2d33, side: THREE.DoubleSide });
  const wing = new THREE.BufferGeometry();
  wing.setAttribute("position", new THREE.Float32BufferAttribute([0, 0, 0, 1.1, 0.05, 0.3, 0.9, 0, -0.35], 3));
  wing.computeVertexNormals();
  const count = ["coast", "meadow", "fields", "desert", "moor", "mountains"].includes(LAND) ? 9 : 5;
  const lead = { x: -160, y: 60, z: -120, vx: 7 + rand() * 3 };
  for (let i = 0; i < count; i++) {
    const g = new THREE.Group();
    const l = new THREE.Mesh(wing, birdMat), r = new THREE.Mesh(wing, birdMat);
    r.scale.x = -1; g.add(l, r); g.userData.fx = true; l.userData.fx = r.userData.fx = true;
    g.scale.setScalar(1.6 + rand() * 0.6);
    scene.add(g);
    flock.push({ g, l, r, dx: -Math.abs(i - count / 2) * 6 - rand() * 3, dy: (rand() - 0.5) * 6, dz: (i - count / 2) * 5 + rand() * 4, ph: rand() * 6 });
  }
  flock.lead = lead;
}
function updateBirds(dt, now) {
  if (!flock.length) return;
  const L = flock.lead;
  L.x += L.vx * dt;
  if (L.x > 190) { L.x = -190 - rand() * 200; L.y = 40 + rand() * 50; L.z = -90 - rand() * 120; L.vx = 6 + rand() * 4; }
  for (const b of flock) {
    const f = Math.sin(now / 130 + b.ph) * 0.7;
    b.l.rotation.z = f; b.r.rotation.z = -f;
    b.g.position.set(L.x + b.dx, L.y + b.dy + Math.sin(now / 700 + b.ph) * 0.8, L.z + b.dz);
    b.g.rotation.y = 0.5 * Math.PI * 0 + Math.PI / 2;
  }
}

// Everything solid casts and receives shadow (the sky, sea, effects and the ground's own do not cast).
scene.traverse((o) => {
  if (!o.isMesh || o === ground || o === sea || o.userData.fx || (o.material && (o.material.transparent || o.material.isShaderMaterial))) return;
  o.castShadow = true; o.receiveShadow = true;
});
ground.receiveShadow = true;
for (const c of clumps) c.mesh.receiveShadow = true;

// ---- the loop ----
let last = performance.now();
let frames = 0;
// Frame rate, logged every 5 s (and used below to drop effects on a slow device).
let fpsFrames = 0, fpsStart = performance.now();
function fpsTick(now) {
  fpsFrames++;
  if (now - fpsStart >= 5000) {
    const fps = fpsFrames * 1000 / (now - fpsStart);
    console.log("world fps " + fps.toFixed(1));
    fpsFrames = 0; fpsStart = now;
  }
}
// A slow device (under ~42 fps over its first 4 s): no shadows, lighter target, one pixel per pixel.
let slowChecked = false, slowStart = 0, slowFrames = 0;
function checkSpeed(now) {
  if (slowChecked) return;
  if (!slowStart) { slowStart = now + 7000; return; } // let it settle: loading and shader compiling make the first seconds slow
  if (now < slowStart) return;
  slowFrames++;
  if (now - slowStart < 4000) return;
  slowChecked = true;
  const fps = slowFrames * 1000 / (now - slowStart);
  console.log("world steady fps " + fps.toFixed(1));
  if (fps < 40 && !FX.includes("nodegrade")) {
    console.log("world slow (" + fps.toFixed(1) + " fps): effects reduced");
    if (shadowsOn) { sun.castShadow = false; shadowsOn = false; }
    renderer.setPixelRatio(1);
    renderer.setSize(window.innerWidth, window.innerHeight);
    if (USE_POST) makeTarget(0);
  }
}
function tick(now) {
  fpsTick(now);
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  wind.value = now / 1000;
  speed += ((running ? targetSpeed : 0) - speed) * Math.min(1, dt * 4); // close behind the belt (and the coach)
  grade += (targetGrade - grade) * Math.min(1, dt * 0.8);
  travelled += speed * dt;
  bob += dt * (2.4 + speed * 0.55) * Math.PI;
  const amp = Math.min(0.05, speed * 0.012);
  camera.position.y = EYE + Math.sin(bob) * amp;
  camera.position.x = Math.sin(bob * 0.5) * amp * 0.6;
  camera.lookAt(camera.position.x * 0.5, EYE - 0.35 + grade * 12, -12);
  const v = (travelled / B.tile) % 1;
  for (const t of groundMaps) t.offset.y = v;
  if (sea) sea.userData.surf.material.opacity = 0.4 + 0.2 * Math.sin(now / 900);
  if (frames++ % 2 === 0) updateGround();
  if (frames % 15 === 0) drawMap();
  updateProps();
  updateTown();
  updateClumps();
  updatePrecip(dt);
  updateSun();
  skyUniforms.uTime.value = now / 1000;
  updateShafts(now);
  updateDecor(now);
  updateDust(dt, now);
  updateBirds(dt, now);
  renderFrame(now);
  checkSpeed(now);
  requestAnimationFrame(tick);
}
updateGround();
drawMap();
console.log("world ready: " + LAND + ", " + TIME + ", " + WEATHER + ", " + props.length + " trees, " + rocks.length + " rocks");
requestAnimationFrame(tick);

window.addEventListener("resize", () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
  if (USE_POST) makeTarget(fxNum("ms", 0));
});
