import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { Turntable } from './turntable.js';

// Studio scene: fixed camera and lights, the garment turns on a turntable
// (so the lighting always looks like a product photo shoot).

export class Studio {
  constructor(container) {
    this.container = container;
    const isMobile = matchMedia('(pointer: coarse)').matches;
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: false, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, isMobile ? 2 : 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.NeutralToneMapping;
    this.renderer.toneMappingExposure = 1.0;
    this.renderer.setClearColor(0x000000, 0);
    container.appendChild(this.renderer.domElement);
    this.renderer.domElement.classList.add('studio-canvas');

    this.scene = new THREE.Scene();
    const pmrem = new THREE.PMREMGenerator(this.renderer);
    this.scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    this.scene.environmentIntensity = 0.55;

    this.camera = new THREE.PerspectiveCamera(28, 1, 0.05, 20);

    const key = new THREE.DirectionalLight(0xffffff, 1.9);
    key.position.set(1.6, 2.2, 2.6);
    const fill = new THREE.DirectionalLight(0xdfe8ff, 0.7);
    fill.position.set(-2.4, 0.6, 1.8);
    const rim = new THREE.DirectionalLight(0xffffff, 1.1);
    rim.position.set(0.3, 1.8, -2.8);
    const under = new THREE.DirectionalLight(0xffffff, 0.25);
    under.position.set(0, -2, 1);
    this.scene.add(key, fill, rim, under, new THREE.HemisphereLight(0xffffff, 0x404048, 0.35));

    this.model = new THREE.Group();
    this.scene.add(this.model);

    // Soft contact shadow below the shorts.
    const sc = document.createElement('canvas');
    sc.width = sc.height = 128;
    const sctx = sc.getContext('2d');
    const g = sctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    g.addColorStop(0, 'rgba(0,0,0,0.38)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    sctx.fillStyle = g;
    sctx.fillRect(0, 0, 128, 128);
    const shadowTex = new THREE.CanvasTexture(sc);
    this.shadow = new THREE.Mesh(
      new THREE.PlaneGeometry(0.9, 0.5),
      new THREE.MeshBasicMaterial({ map: shadowTex, transparent: true, depthWrite: false }),
    );
    this.shadow.rotation.x = -Math.PI / 2;
    this.shadow.position.y = -0.94;
    this.scene.add(this.shadow);

    this.turntable = new Turntable(this.renderer.domElement, this.model, this.camera, () => this.invalidate());
    this.needsRender = true;
    this._resize = () => this.resize();
    new ResizeObserver(this._resize).observe(container);
    this.resize();
    this._loop = this._loop.bind(this);
    requestAnimationFrame(this._loop);
  }

  invalidate() {
    this.needsRender = true;
  }

  resize() {
    const w = this.container.clientWidth, h = this.container.clientHeight;
    if (!w || !h) return;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.turntable.setAspect(w / h);
    const top = this.container.parentElement?.querySelector('.stage-top');
    const bottom = this.container.parentElement?.querySelector('.stage-bottom');
    const topPx = top ? (top.getBoundingClientRect().height + 16) * 0.5 : 0;
    const botPx = bottom ? bottom.getBoundingClientRect().height + 16 : 0;
    this.turntable.setViewport(w, h, topPx, botPx);
    this.invalidate();
  }

  _loop(t) {
    requestAnimationFrame(this._loop);
    const animating = this.turntable.update(t);
    if (animating || this.needsRender) {
      this.renderer.render(this.scene, this.camera);
      this.needsRender = false;
    }
  }

  // Render a still from a given view into a data URL (for job sheets & sharing).
  snapshot({ width = 900, height = 1100, view = 'front', background = null } = {}) {
    const prevSize = this.renderer.getSize(new THREE.Vector2());
    const prevPR = this.renderer.getPixelRatio();
    const state = this.turntable.saveState();
    this.turntable.applyView(view, true);
    this.renderer.setPixelRatio(1);
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.turntable.setAspect(width / height);
    this.turntable.setViewport(width, height, 0, 0);
    this.turntable.update(performance.now());
    this.renderer.render(this.scene, this.camera);
    let url;
    if (background) {
      const c = document.createElement('canvas');
      c.width = width; c.height = height;
      const ctx = c.getContext('2d');
      background(ctx, width, height);
      ctx.drawImage(this.renderer.domElement, 0, 0);
      url = c.toDataURL('image/png');
    } else {
      url = this.renderer.domElement.toDataURL('image/png');
    }
    this.renderer.setPixelRatio(prevPR);
    this.renderer.setSize(prevSize.x, prevSize.y, false);
    this.turntable.restoreState(state);
    this.resize();
    return url;
  }
}
