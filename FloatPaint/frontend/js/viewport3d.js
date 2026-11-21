/**
 * Three.js 3D Spatial Painting Viewport.
 * Renders a dark reference grid, active Z-depth plane indicator, continuous
 * Catmull-Rom TubeGeometry strokes, gesture-driven orbit controls, 3D eraser,
 * and .OBJ / .GLTF export.
 */

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { OBJExporter } from 'three/addons/exporters/OBJExporter.js';
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js';

const SPACE_WIDTH = 10.0;
const SPACE_HEIGHT = 6.4;
const SPACE_DEPTH = 6.0; // Z in [-3, +3]

export class Viewport3DRenderer {
  constructor(containerEl) {
    this.container = containerEl;
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color('#0B0E14');
    this.scene.fog = new THREE.FogExp2('#0B0E14', 0.032);

    const width = Math.max(1, containerEl.clientWidth || 960);
    const height = Math.max(1, containerEl.clientHeight || 640);

    this.camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 120);
    this.camera.position.set(0, 1.4, 10.5);

    this.renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.setSize(width, height);
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.15;
    containerEl.appendChild(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.08;
    this.controls.target.set(0, 0, 0);

    this.strokeGroup = new THREE.Group();
    this.strokeGroup.name = 'AeroBrushStrokes';
    this.scene.add(this.strokeGroup);

    // Undo history of actions: { type: 'add', entry } | { type: 'remove', entry }
    this.strokes = [];
    this.history = [];
    this.activeStroke = null;
    this._lastOrbitWrist = null;

    this._setupLights();
    this._setupEnvironment();
    this._setupCursorAndZPlane();
  }

  _setupLights() {
    this.scene.add(new THREE.AmbientLight('#b8c7e0', 0.75));

    const keyLight = new THREE.DirectionalLight('#ffffff', 1.4);
    keyLight.position.set(6, 10, 8);
    this.scene.add(keyLight);

    const cyanRim = new THREE.PointLight('#00F0FF', 1.2, 24);
    cyanRim.position.set(-6, 3, 4);
    this.scene.add(cyanRim);

    const magentaRim = new THREE.PointLight('#FF2E93', 1.0, 24);
    magentaRim.position.set(6, -2, -4);
    this.scene.add(magentaRim);
  }

  _setupEnvironment() {
    const floorGrid = new THREE.GridHelper(16, 24, 0x1e3a5f, 0x162033);
    floorGrid.position.y = -3.4;
    this.scene.add(floorGrid);

    // Subtle bounding frame for the active painting volume
    const boxGeo = new THREE.BoxGeometry(SPACE_WIDTH, SPACE_HEIGHT, SPACE_DEPTH);
    const edges = new THREE.EdgesGeometry(boxGeo);
    const frame = new THREE.LineSegments(
      edges,
      new THREE.LineBasicMaterial({ color: 0x26344d, transparent: true, opacity: 0.45 })
    );
    this.scene.add(frame);
  }

  _setupCursorAndZPlane() {
    // Active Z-depth indicator plane
    const planeGeo = new THREE.PlaneGeometry(SPACE_WIDTH, SPACE_HEIGHT);
    const planeMat = new THREE.MeshBasicMaterial({
      color: 0x00f0ff,
      transparent: true,
      opacity: 0.045,
      side: THREE.DoubleSide,
      depthWrite: false,
    });
    this.zPlaneMesh = new THREE.Mesh(planeGeo, planeMat);

    const zPlaneBorder = new THREE.LineSegments(
      new THREE.EdgesGeometry(planeGeo),
      new THREE.LineBasicMaterial({ color: 0x00f0ff, transparent: true, opacity: 0.38 })
    );
    this.zPlaneMesh.add(zPlaneBorder);
    this.scene.add(this.zPlaneMesh);

    // 3D cursor sphere
    this.cursorMesh = new THREE.Mesh(
      new THREE.SphereGeometry(0.11, 20, 20),
      new THREE.MeshBasicMaterial({ color: 0x00f0ff })
    );
    this.scene.add(this.cursorMesh);

    // Vertical drop stem to floor grid for spatial depth clarity
    const stemGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(0, 0, 0),
      new THREE.Vector3(0, -3.4, 0),
    ]);
    this.cursorStem = new THREE.Line(
      stemGeo,
      new THREE.LineBasicMaterial({ color: 0x00f0ff, transparent: true, opacity: 0.35 })
    );
    this.scene.add(this.cursorStem);
  }

  resize() {
    const width = Math.max(1, this.container.clientWidth);
    const height = Math.max(1, this.container.clientHeight);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }

  /**
   * Convert normalized [0..1, 0..1, -1..1] cursor into 3D world coordinates.
   */
  normalizedToWorld(nx, ny, nz) {
    const x = (nx - 0.5) * SPACE_WIDTH;
    const y = (0.5 - ny) * SPACE_HEIGHT;
    const z = Math.max(-1, Math.min(1, nz)) * (SPACE_DEPTH * 0.5);
    return new THREE.Vector3(x, y, z);
  }

  updateCursor(nx, ny, nz, { state, brushRadius, brushColor }) {
    const pos = this.normalizedToWorld(nx, ny, nz);
    this.zPlaneMesh.position.z = pos.z;

    const radiusScale = Math.max(0.04, (brushRadius / 30) * 0.24);
    const isErase = state === 'ERASE';
    const scale = isErase ? radiusScale * 2.6 : radiusScale;

    this.cursorMesh.position.copy(pos);
    this.cursorMesh.scale.setScalar(scale / 0.11);

    const stateColor =
      state === 'DRAW'
        ? brushColor
        : state === 'ERASE'
          ? '#FF6B35'
          : state === 'PINCH_SCALE'
            ? '#FFB020'
            : state === 'ORBIT_3D'
              ? '#9D6CFF'
              : '#00F0FF';
    this.cursorMesh.material.color.set(stateColor);

    const stemAttr = this.cursorStem.geometry.attributes.position;
    stemAttr.setXYZ(0, pos.x, pos.y, pos.z);
    stemAttr.setXYZ(1, pos.x, -3.4, pos.z);
    stemAttr.needsUpdate = true;

    return pos;
  }

  beginStroke(worldPos, { color, radius }) {
    const tubeRadius = Math.max(0.02, (radius / 30) * 0.18);
    const material = new THREE.MeshStandardMaterial({
      color: new THREE.Color(color),
      emissive: new THREE.Color(color),
      emissiveIntensity: 0.42,
      roughness: 0.25,
      metalness: 0.15,
    });

    const mesh = new THREE.Mesh(new THREE.SphereGeometry(tubeRadius, 12, 12), material);
    mesh.position.copy(worldPos);
    this.strokeGroup.add(mesh);

    this.activeStroke = {
      points: [worldPos.clone()],
      color,
      tubeRadius,
      material,
      mesh,
      isSingleDot: true,
    };
  }

  appendPoint(worldPos) {
    if (!this.activeStroke) return;
    const pts = this.activeStroke.points;
    const last = pts[pts.length - 1];
    if (last.distanceTo(worldPos) < 0.05) return;

    pts.push(worldPos.clone());
    this._rebuildStrokeMesh(this.activeStroke);
  }

  endStroke() {
    if (!this.activeStroke) return;
    const entry = this.activeStroke;
    this.activeStroke = null;
    this.strokes.push(entry);
    this.history.push({ type: 'add', entry });
  }

  _rebuildStrokeMesh(stroke) {
    if (stroke.points.length < 2) return;
    const curve = new THREE.CatmullRomCurve3(stroke.points, false, 'catmullrom', 0.45);
    const tubularSegments = Math.max(12, stroke.points.length * 6);
    const nextGeo = new THREE.TubeGeometry(curve, tubularSegments, stroke.tubeRadius, 10, false);

    stroke.mesh.geometry.dispose();
    stroke.mesh.geometry = nextGeo;
    stroke.mesh.position.set(0, 0, 0);
    stroke.isSingleDot = false;
  }

  eraseAt(worldPos, brushRadius) {
    const eraseRadius = Math.max(0.32, (brushRadius / 30) * 0.72);
    const remaining = [];
    for (const entry of this.strokes) {
      const hit = entry.points.some((p) => p.distanceTo(worldPos) <= eraseRadius);
      if (hit) {
        this.strokeGroup.remove(entry.mesh);
        this.history.push({ type: 'remove', entry });
      } else {
        remaining.push(entry);
      }
    }
    this.strokes = remaining;
  }

  /**
   * Orbit the 3D camera around controls.target using wrist (0) delta movement.
   */
  orbitByWrist(wristLandmark) {
    if (!wristLandmark || wristLandmark.length < 2) {
      this._lastOrbitWrist = null;
      return;
    }
    const wx = wristLandmark[0];
    const wy = wristLandmark[1];
    if (!this._lastOrbitWrist) {
      this._lastOrbitWrist = [wx, wy];
      return;
    }

    const dx = wx - this._lastOrbitWrist[0];
    const dy = wy - this._lastOrbitWrist[1];
    this._lastOrbitWrist = [wx, wy];

    const offset = this.camera.position.clone().sub(this.controls.target);
    const spherical = new THREE.Spherical().setFromVector3(offset);
    spherical.theta -= dx * 4.5;
    spherical.phi = Math.max(0.18, Math.min(Math.PI - 0.18, spherical.phi - dy * 3.5));
    offset.setFromSpherical(spherical);
    this.camera.position.copy(this.controls.target).add(offset);
    this.camera.lookAt(this.controls.target);
  }

  resetOrbitTracking() {
    this._lastOrbitWrist = null;
  }

  undo() {
    if (this.activeStroke) {
      this.strokeGroup.remove(this.activeStroke.mesh);
      this.activeStroke.mesh.geometry.dispose();
      this.activeStroke.material.dispose();
      this.activeStroke = null;
      return true;
    }
    const action = this.history.pop();
    if (!action) return false;

    if (action.type === 'add') {
      const idx = this.strokes.indexOf(action.entry);
      if (idx !== -1) this.strokes.splice(idx, 1);
      this.strokeGroup.remove(action.entry.mesh);
    } else if (action.type === 'remove') {
      this.strokes.push(action.entry);
      this.strokeGroup.add(action.entry.mesh);
    }
    return true;
  }

  clear() {
    if (this.activeStroke) {
      this.strokeGroup.remove(this.activeStroke.mesh);
      this.activeStroke.mesh.geometry.dispose();
      this.activeStroke.material.dispose();
      this.activeStroke = null;
    }
    for (const entry of this.strokes) {
      this.strokeGroup.remove(entry.mesh);
      entry.mesh.geometry.dispose();
      entry.material.dispose();
    }
    this.strokes = [];
    this.history = [];
  }

  render() {
    this.controls.update();
    this.renderer.render(this.scene, this.camera);
  }

  exportOBJ() {
    const exporter = new OBJExporter();
    return exporter.parse(this.strokeGroup);
  }

  exportGLTF() {
    const exporter = new GLTFExporter();
    return new Promise((resolve, reject) => {
      exporter.parse(
        this.strokeGroup,
        (result) => resolve(JSON.stringify(result, null, 2)),
        (err) => reject(err),
        { binary: false }
      );
    });
  }
}
