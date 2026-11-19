import * as THREE from 'three';
import {
  SHIRT, SHIRT_REGIONS, SHIRT_DECAL, SHIRT_LABEL, SLEEVE_MAP, SHORTS, SHORTS_ATLAS, SHORTS_REGIONS, SHORTS_DECAL,
  ATLAS_SIZE, armFrame, regionUniform,
} from '../garment/layout.js';
import { createGarmentMaterial, createTrimMaterial } from './materials.js';
import { makeKnitTexture, makeRibTexture } from './fabric.js';
import { paintShirtAtlas, paintShortsAtlas } from '../kits/painter.js';
import { drawBackPrint, drawShortsPrint, drawLabel } from '../kits/lettering.js';

function canvasTexture(canvas, renderer, srgb = true) {
  const t = new THREE.CanvasTexture(canvas);
  t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
  t.anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy());
  t.generateMipmaps = true;
  t.minFilter = THREE.LinearMipmapLinearFilter;
  return t;
}

// The shirt + shorts on the turntable, with live kit and print textures.
export class KitModel {
  constructor(studio, geos, { decalPxPerM = 3000 } = {}) {
    this.studio = studio;
    const r = studio.renderer;
    this.decalPxPerM = decalPxPerM;
    this.canvases = {
      shirt: document.createElement('canvas'),
      shorts: document.createElement('canvas'),
      back: document.createElement('canvas'),
      shortsNum: document.createElement('canvas'),
      label: document.createElement('canvas'),
    };
    this.canvases.shirt.width = this.canvases.shirt.height = ATLAS_SIZE;
    this.canvases.shorts.width = SHORTS_ATLAS.w;
    this.canvases.shorts.height = SHORTS_ATLAS.h;
    this.canvases.back.width = this.canvases.back.height = 4;
    this.canvases.shortsNum.width = this.canvases.shortsNum.height = 4;
    this.canvases.label.width = this.canvases.label.height = 4;
    this.tex = {};
    for (const [k, c] of Object.entries(this.canvases)) this.tex[k] = canvasTexture(c, r);

    const fabric = makeKnitTexture();
    const rib = makeRibTexture();
    const arms = [armFrame(1), armFrame(-1)];

    this.shirtMat = createGarmentMaterial({
      atlas: this.tex.shirt,
      box: SHIRT.box,
      regions: {
        front: regionUniform(SHIRT_REGIONS.front, ATLAS_SIZE, ATLAS_SIZE),
        back: regionUniform(SHIRT_REGIONS.back, ATLAS_SIZE, ATLAS_SIZE),
        sleeveR: regionUniform(SHIRT_REGIONS.sleeveR, ATLAS_SIZE, ATLAS_SIZE),
        sleeveL: regionUniform(SHIRT_REGIONS.sleeveL, ATLAS_SIZE, ATLAS_SIZE),
      },
      arms,
      sleeveMap: SLEEVE_MAP,
      decal: { texture: this.tex.back, box: SHIRT_DECAL, mirror: true },
      label: { texture: this.tex.label, box: SHIRT_LABEL },
      fabric,
    });
    this.shortsMat = createGarmentMaterial({
      atlas: this.tex.shorts,
      box: SHORTS.box,
      regions: {
        front: regionUniform(SHORTS_REGIONS.front, SHORTS_ATLAS.w, SHORTS_ATLAS.h),
        back: regionUniform(SHORTS_REGIONS.back, SHORTS_ATLAS.w, SHORTS_ATLAS.h),
      },
      decal: { texture: this.tex.shortsNum, box: SHORTS_DECAL, mirror: false },
      fabric,
    });
    this.collarMat = createTrimMaterial(rib);
    this.cuffMat = createTrimMaterial(rib);
    this.waistMat = createTrimMaterial(rib);

    const add = (geo, mat) => {
      const m = new THREE.Mesh(geo, mat);
      studio.model.add(m);
      return m;
    };
    this.meshes = {
      shirt: add(geos['shirt.body'], this.shirtMat),
      hem: add(geos['shirt.hem'], this.shirtMat),
      collar: add(geos['shirt.collar'], this.collarMat),
      cuffR: add(geos['shirt.cuffR'], this.cuffMat),
      cuffL: add(geos['shirt.cuffL'], this.cuffMat),
      shorts: add(geos['shorts.body'], this.shortsMat),
      waist: add(geos['shorts.waist'], this.waistMat),
      legR: add(geos['shorts.legR'], this.shortsMat),
      legL: add(geos['shorts.legL'], this.shortsMat),
    };
    this.kit = null;
    this.patch = 'none';
  }

  setKit(kit, patch = this.patch) {
    this.kit = kit;
    this.patch = patch;
    paintShirtAtlas(this.canvases.shirt, kit, { patch });
    paintShortsAtlas(this.canvases.shorts, kit);
    this.tex.shirt.needsUpdate = true;
    this.tex.shorts.needsUpdate = true;
    this.collarMat.color.setStyle(kit.trim);
    this.cuffMat.color.setStyle(kit.cuff || kit.trim);
    this.waistMat.color.setStyle(kit.shorts.base);
    this.studio.invalidate();
  }

  setDesign(design) {
    if (!this.kit) return null;
    const info = drawBackPrint(this.canvases.back, design, this.kit, this.decalPxPerM);
    this._refresh('back');
    drawShortsPrint(this.canvases.shortsNum, design, this.kit, this.decalPxPerM * 0.7);
    this._refresh('shortsNum');
    this.studio.invalidate();
    return info;
  }

  setLabel(shopName, size) {
    drawLabel(this.canvases.label, shopName, size);
    this._refresh('label');
    this.studio.invalidate();
  }

  setShortsVisible(v) {
    for (const k of ['shorts', 'waist', 'legR', 'legL']) this.meshes[k].visible = v;
    this.studio.invalidate();
  }

  // Canvas size changes need a fresh texture object; same size just re-uploads.
  _refresh(key) {
    const t = this.tex[key];
    const c = this.canvases[key];
    if (t.image.width !== c.width || t.image.height !== c.height || t.userData.w !== c.width) {
      const nt = canvasTexture(c, this.studio.renderer);
      nt.userData.w = c.width;
      this.tex[key] = nt;
      if (key === 'back') this.shirtMat.userData.uniforms.uDecal.value = nt;
      if (key === 'shortsNum') this.shortsMat.userData.uniforms.uDecal.value = nt;
      if (key === 'label') this.shirtMat.userData.uniforms.uLabel.value = nt;
      t.dispose();
    } else {
      t.needsUpdate = true;
    }
  }
}
