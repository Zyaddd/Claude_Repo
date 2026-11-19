import * as THREE from 'three';

// Garment material: a physically based fabric (MeshPhysicalMaterial with sheen)
// whose colour comes from projecting the kit atlas onto the body (front/back
// panels, unwrapped sleeves), plus a live decal layer for the name & number,
// triplanar knit relief, raised print edges, and a darker hollow interior.

const VERT_DECL = /* glsl */ `
attribute float aSleeve;
attribute vec2 aAO;
varying vec3 vObjPos;
varying vec3 vObjNormal;
varying float vSleeve;
varying vec2 vAO;
`;

const FRAG_DECL = /* glsl */ `
varying vec3 vObjPos;
varying vec3 vObjNormal;
varying float vSleeve;
varying vec2 vAO;
uniform sampler2D uAtlas;
uniform vec4 uBox;          // xMin, yMax, width, height (metres)
uniform vec4 uRegFront;
uniform vec4 uRegBack;
uniform vec4 uRegSR;
uniform vec4 uRegSL;
uniform float uHasSleeves;
uniform vec3 uArmS[2];
uniform vec3 uArmD[2];
uniform vec3 uArmE1[2];
uniform vec3 uArmE2[2];
uniform vec2 uSleeveT;
uniform sampler2D uDecal;
uniform vec4 uDecalBox;     // xMin, yMax, width, height
uniform float uDecalMirror;
uniform float uDecalOn;
uniform float uDecalRough;
uniform float uDecalBump;
uniform sampler2D uFabric;
uniform float uFabricScale;
uniform float uFabricStrength;
uniform vec3 uInnerTint;
uniform sampler2D uLabel;
uniform vec4 uLabelBox;
uniform float uLabelOn;
uniform float uDebug;
float kDecalA;
float kAO;

vec2 regUV(vec4 r, vec2 st) {
  st = clamp(st, vec2(0.003), vec2(0.997));
  return vec2(r.x + st.x * r.z, r.y + st.y * r.w);
}

vec3 kPerturb(vec3 surf_pos, vec3 surf_norm, vec2 dHdxy, float faceDir) {
  vec3 vSigmaX = normalize(dFdx(surf_pos));
  vec3 vSigmaY = normalize(dFdy(surf_pos));
  vec3 vN = surf_norm;
  vec3 R1 = cross(vSigmaY, vN);
  vec3 R2 = cross(vN, vSigmaX);
  float fDet = dot(vSigmaX, R1) * faceDir;
  vec3 vGrad = sign(fDet) * (dHdxy.x * R1 + dHdxy.y * R2);
  return normalize(abs(fDet) * surf_norm - vGrad);
}
`;

const FRAG_COLOR = /* glsl */ `
vec3 kp = vObjPos;
vec3 kn = normalize(vObjNormal);
float ksx = (kp.x - uBox.x) / uBox.z;
float ksy = (uBox.y - kp.y) / uBox.w;
vec3 kcF = texture2D(uAtlas, regUV(uRegFront, vec2(ksx, ksy))).rgb;
vec3 kcB = texture2D(uAtlas, regUV(uRegBack, vec2(1.0 - ksx, ksy))).rgb;
float kwF = smoothstep(-0.16, 0.16, kn.z);
vec3 kcol = mix(kcB, kcF, kwF);
float ksl = uHasSleeves * vSleeve;
if (uHasSleeves > 0.5) {
  bool right = kp.x >= 0.0;
  vec3 S = right ? uArmS[0] : uArmS[1];
  vec3 D = right ? uArmD[0] : uArmD[1];
  vec3 E1 = right ? uArmE1[0] : uArmE1[1];
  vec3 E2 = right ? uArmE2[0] : uArmE2[1];
  vec3 r = kp - S;
  float t = dot(r, D);
  vec3 rr = r - t * D;
  float ang = atan(dot(rr, E2), dot(rr, E1));
  vec2 st = vec2(ang / 6.2831853 + 0.5, (t - uSleeveT.x) / uSleeveT.y);
  vec3 cSR = texture2D(uAtlas, regUV(uRegSR, st)).rgb;
  vec3 cSL = texture2D(uAtlas, regUV(uRegSL, st)).rgb;
  vec3 cS = right ? cSR : cSL;
  kcol = mix(kcol, cS, ksl);
  // stitched set-in seam
  kcol *= 1.0 - 0.22 * (4.0 * ksl * (1.0 - ksl));
}
kDecalA = 0.0;
vec2 kdst = vec2((kp.x - uDecalBox.x) / uDecalBox.z, (uDecalBox.y - kp.y) / uDecalBox.w);
if (uDecalMirror > 0.5) kdst.x = 1.0 - kdst.x;
vec4 kdc = texture2D(uDecal, vec2(clamp(kdst.x, 0.0, 1.0), 1.0 - clamp(kdst.y, 0.0, 1.0)));
float kin = step(0.0, kdst.x) * step(kdst.x, 1.0) * step(0.0, kdst.y) * step(kdst.y, 1.0);
float kfacing = uDecalMirror > 0.5 ? (1.0 - kwF) : kwF;
float kda = uDecalOn * kdc.a * kin * smoothstep(0.35, 0.65, kfacing) * (1.0 - ksl) * (gl_FrontFacing ? 1.0 : 0.0);
kcol = mix(kcol, kdc.rgb, kda);
kDecalA = kda;
kAO = gl_FrontFacing ? vAO.x : vAO.y;
if (!gl_FrontFacing) {
  float lum = dot(kcol, vec3(0.299, 0.587, 0.114));
  kcol = mix(kcol, vec3(lum), 0.3) * uInnerTint;
  if (uLabelOn > 0.5 && kn.z < -0.2) {
    vec2 lst = vec2((kp.x - uLabelBox.x) / uLabelBox.z, (uLabelBox.y - kp.y) / uLabelBox.w);
    if (lst.x > 0.0 && lst.x < 1.0 && lst.y > 0.0 && lst.y < 1.0) {
      vec4 lc = texture2D(uLabel, vec2(lst.x, 1.0 - lst.y));
      kcol = mix(kcol, lc.rgb, lc.a * 0.9);
    }
  }
}
diffuseColor.rgb *= kcol * kAO;
if (uDebug > 0.5 && uDebug < 1.5) diffuseColor.rgb = vec3(vSleeve);
if (uDebug > 1.5 && uDebug < 2.5) diffuseColor.rgb = vec3(kAO);
if (uDebug > 2.5) diffuseColor.rgb = vec3(0.7);
`;

const FRAG_NORMAL = /* glsl */ `
{
  vec3 an = abs(normalize(vObjNormal));
  an = an * an * an * an;
  an /= (an.x + an.y + an.z);
  vec3 q = vObjPos * uFabricScale;
  float hx = texture2D(uFabric, q.zy).r;
  float hy = texture2D(uFabric, q.xz).r;
  float hz = texture2D(uFabric, q.xy).r;
  float kh = (hx * an.x + hy * an.y + hz * an.z) * uFabricStrength * (1.0 - kDecalA);
  kh += kDecalA * uDecalBump;
  vec2 dHdxy = vec2(dFdx(kh), dFdy(kh));
  normal = kPerturb(-vViewPosition, normal, dHdxy, faceDirection);
}
`;

export function createGarmentMaterial(opts) {
  const mat = new THREE.MeshPhysicalMaterial({
    color: 0xffffff,
    roughness: 0.78,
    metalness: 0,
    sheen: 0.6,
    sheenRoughness: 0.55,
    sheenColor: new THREE.Color(0.55, 0.55, 0.55),
    side: THREE.DoubleSide,
  });
  const u = {
    uAtlas: { value: opts.atlas },
    uBox: { value: new THREE.Vector4(opts.box.xMin, opts.box.yMax, opts.box.xMax - opts.box.xMin, opts.box.yMax - opts.box.yMin) },
    uRegFront: { value: new THREE.Vector4(...opts.regions.front) },
    uRegBack: { value: new THREE.Vector4(...opts.regions.back) },
    uRegSR: { value: new THREE.Vector4(...(opts.regions.sleeveR || [0, 0, 0, 0])) },
    uRegSL: { value: new THREE.Vector4(...(opts.regions.sleeveL || [0, 0, 0, 0])) },
    uHasSleeves: { value: opts.arms ? 1 : 0 },
    uArmS: { value: opts.arms ? opts.arms.map((a) => new THREE.Vector3(...a.S)) : [new THREE.Vector3(), new THREE.Vector3()] },
    uArmD: { value: opts.arms ? opts.arms.map((a) => new THREE.Vector3(...a.D)) : [new THREE.Vector3(), new THREE.Vector3()] },
    uArmE1: { value: opts.arms ? opts.arms.map((a) => new THREE.Vector3(...a.E1)) : [new THREE.Vector3(), new THREE.Vector3()] },
    uArmE2: { value: opts.arms ? opts.arms.map((a) => new THREE.Vector3(...a.E2)) : [new THREE.Vector3(), new THREE.Vector3()] },
    uSleeveT: { value: new THREE.Vector2(opts.sleeveMap?.tStart || 0, opts.sleeveMap?.tLen || 1) },
    uDecal: { value: opts.decal.texture },
    uDecalBox: { value: new THREE.Vector4(opts.decal.box.xMin, opts.decal.box.yMax, opts.decal.box.xMax - opts.decal.box.xMin, opts.decal.box.yMax - opts.decal.box.yMin) },
    uDecalMirror: { value: opts.decal.mirror ? 1 : 0 },
    uDecalOn: { value: 1 },
    uDecalRough: { value: 0.42 },
    uDecalBump: { value: 0.0018 },
    uFabric: { value: opts.fabric },
    uFabricScale: { value: 1 / 0.011 },
    uFabricStrength: { value: 0.00045 },
    uInnerTint: { value: new THREE.Color(0.82, 0.82, 0.82) },
    uLabel: { value: opts.label?.texture || opts.decal.texture },
    uLabelBox: { value: opts.label ? new THREE.Vector4(opts.label.box.xMin, opts.label.box.yMax, opts.label.box.xMax - opts.label.box.xMin, opts.label.box.yMax - opts.label.box.yMin) : new THREE.Vector4(0, 0, 1, 1) },
    uLabelOn: { value: opts.label ? 1 : 0 },
    uDebug: { value: 0 },
  };
  mat.userData.uniforms = u;
  mat.onBeforeCompile = (shader) => {
    Object.assign(shader.uniforms, u);
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\n' + VERT_DECL)
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvObjPos = position; vObjNormal = normal; vSleeve = aSleeve; vAO = aAO;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\n' + FRAG_DECL)
      .replace('#include <map_fragment>', FRAG_COLOR)
      .replace('#include <roughnessmap_fragment>', '#include <roughnessmap_fragment>\nroughnessFactor = mix(roughnessFactor, uDecalRough, kDecalA);')
      .replace('#include <normal_fragment_maps>', FRAG_NORMAL)
      .replace(
        '#include <lights_physical_fragment>',
        '#include <lights_physical_fragment>\n#ifdef USE_SHEEN\nmaterial.sheenColor *= kAO * (1.0 - kDecalA * 0.7);\n#endif\nmaterial.specularColor *= kAO;',
      );
  };
  mat.customProgramCacheKey = () => 'garment-v1';
  return mat;
}

export function createTrimMaterial(rib) {
  return new THREE.MeshPhysicalMaterial({
    color: 0xffffff,
    roughness: 0.8,
    sheen: 0.5,
    sheenRoughness: 0.6,
    sheenColor: new THREE.Color(0.5, 0.5, 0.5),
    bumpMap: rib,
    bumpScale: 0.6,
    side: THREE.DoubleSide,
  });
}
