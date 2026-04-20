import * as THREE from "three";
import { GameStateSnapshot } from "../types";

const TILE_SIZE = 16;
const EXT = 40; // extension cells beyond each map edge

/** Zone colours — naturalistic palette with vegetation tones. */
const ZONE_COLOURS: Record<number, THREE.Color> = {
  0: new THREE.Color(0x1a5a6a), // WATER — dark riverbed/ocean floor
  1: new THREE.Color(0x6b8b6b), // MOUNTAIN — mossy grey-green
  2: new THREE.Color(0x8a9a78), // CITY — warm stone with green undertone
  3: new THREE.Color(0x6db85a), // SUBURB — bright lawn grass
  4: new THREE.Color(0x8cc050), // COUNTRYSIDE — yellow-green fields
  5: new THREE.Color(0xd4c090), // COAST/BEACH — sandy
  6: new THREE.Color(0x3d7a3d), // FOREST — rich dark green
  7: new THREE.Color(0x7a8070), // INDUSTRIAL — greenish grey concrete
  8: new THREE.Color(0x7a8878), // DOWNTOWN — blue-grey with green (urban parks)
};

const WATER_ZONE = 0;

/**
 * Low-poly terrain renderer using a unified vertex grid covering both
 * the playable map and extended borders. Every cell — map and extension —
 * uses the same vertex-averaged polygon technique for seamless visuals.
 */
export class TerrainRenderer {
  private scene: THREE.Scene;
  private meshes: THREE.Object3D[] = [];

  constructor(scene: THREE.Scene) {
    this.scene = scene;
  }

  build(snapshot: GameStateSnapshot): void {
    this.dispose();

    const terrain = snapshot.terrain;
    if (!terrain || terrain.length === 0) return;

    const W = terrain.length;
    const D = terrain[0].length;
    const elev = snapshot.elevation;

    // Total grid including extensions on all sides
    const totalW = W + 2 * EXT;
    const totalD = D + 2 * EXT;

    // ── Build unified vertex grid ─────────────────────────────
    // (totalW+1) × (totalD+1) vertices. Each vertex height is the average
    // of its 4 adjacent cells — exactly like the original map technique.
    const vW = totalW + 1;
    const vD = totalD + 1;
    const vH = new Float32Array(vW * vD);

    for (let vx = 0; vx < vW; vx++) {
      for (let vz = 0; vz < vD; vz++) {
        const gvx = vx - EXT; // grid position relative to map origin
        const gvz = vz - EXT;
        let h = 0;
        let count = 0;

        for (const [dx, dz] of [
          [0, 0],
          [-1, 0],
          [0, -1],
          [-1, -1],
        ]) {
          const cx = gvx + dx;
          const cz = gvz + dz;
          if (cx >= -EXT && cx < W + EXT && cz >= -EXT && cz < D + EXT) {
            h += this.cellHeight(cx, cz, W, D, terrain, elev);
            count++;
          }
        }

        vH[vx * vD + vz] = count > 0 ? h / count : 0;
      }
    }

    // ── Build cell mesh ───────────────────────────────────────
    // 2 triangles per cell, 4 verts (flat shading = hard edges between cells).
    const positions: number[] = [];
    const colors: number[] = [];
    const indices: number[] = [];
    let vertIdx = 0;

    for (let x = 0; x < totalW; x++) {
      for (let z = 0; z < totalD; z++) {
        const gx = x - EXT;
        const gz = z - EXT;

        const cellType = this.cellType(gx, gz, W, D, terrain);
        const base = ZONE_COLOURS[cellType] ?? new THREE.Color(0x444444);
        const hash =
          (((gx + 10000) * 127 + (gz + 10000) * 311) & 0xffff) / 65536;
        const jitter = hash * 0.06 - 0.03;

        // 4 corner heights from vertex grid
        const i00 = x * vD + z;
        const i10 = (x + 1) * vD + z;
        const i01 = x * vD + (z + 1);
        const i11 = (x + 1) * vD + (z + 1);

        // World positions
        const wx0 = gx * TILE_SIZE;
        const wx1 = (gx + 1) * TILE_SIZE;
        const wz0 = gz * TILE_SIZE;
        const wz1 = (gz + 1) * TILE_SIZE;

        positions.push(wx0, vH[i00], wz0);
        positions.push(wx1, vH[i10], wz0);
        positions.push(wx1, vH[i11], wz1);
        positions.push(wx0, vH[i01], wz1);

        const cr = Math.max(0, Math.min(1, base.r + jitter));
        const cg = Math.max(0, Math.min(1, base.g + jitter * 0.8));
        const cb = Math.max(0, Math.min(1, base.b + jitter));
        colors.push(cr, cg, cb, cr, cg, cb, cr, cg, cb, cr, cg, cb);

        indices.push(vertIdx, vertIdx + 2, vertIdx + 1);
        indices.push(vertIdx, vertIdx + 3, vertIdx + 2);
        vertIdx += 4;
      }
    }

    const geo = new THREE.BufferGeometry();
    geo.setIndex(indices);
    geo.setAttribute(
      "position",
      new THREE.Float32BufferAttribute(positions, 3),
    );
    geo.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));
    geo.computeVertexNormals();

    const mat = new THREE.MeshLambertMaterial({
      vertexColors: true,
      flatShading: true,
    });

    const mesh = new THREE.Mesh(geo, mat);
    mesh.receiveShadow = true;
    this.scene.add(mesh);
    this.meshes.push(mesh);

    // ── Water surfaces ────────────────────────────────────────
    this.buildWater(W, D, terrain, elev);
  }

  /* ── Virtual terrain for map extensions ──────────────────── */

  /* ── Noise utilities for natural terrain ──────────────────── */

  /** Hash-based pseudo-random for grid positions. Returns -1..1 */
  private hash(x: number, y: number): number {
    let n = x * 73856093 + y * 19349663;
    n = (n ^ (n >> 13)) * 0x85ebca6b;
    n = n ^ (n >> 16);
    return ((n & 0x7fffffff) / 0x7fffffff) * 2.0 - 1.0;
  }

  /** Value noise with smooth interpolation. Returns -1..1 */
  private vnoise(x: number, y: number): number {
    const ix = Math.floor(x);
    const iy = Math.floor(y);
    const fx = x - ix;
    const fy = y - iy;
    // Smoothstep
    const sx = fx * fx * (3 - 2 * fx);
    const sy = fy * fy * (3 - 2 * fy);

    const n00 = this.hash(ix, iy);
    const n10 = this.hash(ix + 1, iy);
    const n01 = this.hash(ix, iy + 1);
    const n11 = this.hash(ix + 1, iy + 1);

    const nx0 = n00 + sx * (n10 - n00);
    const nx1 = n01 + sx * (n11 - n01);
    return nx0 + sy * (nx1 - nx0);
  }

  /** Fractal Brownian Motion — multi-octave value noise. Returns roughly -1..1 */
  private fbm(
    x: number,
    y: number,
    octaves: number,
    lacunarity = 2.0,
    gain = 0.5,
  ): number {
    let sum = 0;
    let amp = 1.0;
    let freq = 1.0;
    let maxAmp = 0;
    for (let i = 0; i < octaves; i++) {
      sum += amp * this.vnoise(x * freq, y * freq);
      maxAmp += amp;
      amp *= gain;
      freq *= lacunarity;
    }
    return sum / maxAmp;
  }

  /** Ridge noise — creates sharp ridgelines. Returns 0..1 */
  private ridgeNoise(x: number, y: number, octaves: number): number {
    let sum = 0;
    let amp = 1.0;
    let freq = 1.0;
    let maxAmp = 0;
    let prev = 1.0;
    for (let i = 0; i < octaves; i++) {
      let n = Math.abs(this.vnoise(x * freq, y * freq));
      n = 1.0 - n; // invert for ridges
      n = n * n; // sharpen
      n *= prev; // erosion — lower octaves carve deeper
      prev = n;
      sum += n * amp;
      maxAmp += amp;
      amp *= 0.5;
      freq *= 2.1;
    }
    return sum / maxAmp;
  }

  /**
   * Zone type for any grid coordinate (inside or outside the map).
   * Mountains wrap all three sides (N, E, W). South is ocean with cliff edges.
   */
  private cellType(
    gx: number,
    gz: number,
    W: number,
    D: number,
    terrain: number[][],
  ): number {
    if (gx >= 0 && gx < W && gz >= 0 && gz < D) return terrain[gx][gz];

    const fz = gz / D;

    // ── North extensions: mountain/forest wall ──
    if (gz < 0) {
      const dist = -gz;
      return dist < 8 ? 6 : 1; // near = forest, far = mountain
    }

    // ── South extensions: ocean ──
    if (gz >= D) return 0;

    // ── East/West extensions: mountains all the way down, cliffs at south ──
    const dist = gx < 0 ? -gx : gx - W + 1;

    // Upper 70% = mountains/forest (same as north)
    if (fz < 0.55) {
      return dist < 8 ? 6 : 1;
    }
    // 55-70% = forest foothills
    if (fz < 0.7) {
      return dist < 5 ? 6 : 1;
    }
    // 70-80% = coastal cliffs (still mountain but transitioning)
    if (fz < 0.8) {
      return 1; // mountain cliff face
    }
    // Below 80% = ocean
    return 0;
  }

  /**
   * Elevation for any grid coordinate (inside or outside the map).
   * Uses multi-octave FBM and ridge noise for natural poly-style mountains.
   */
  private cellHeight(
    gx: number,
    gz: number,
    W: number,
    D: number,
    terrain: number[][],
    elev: number[][] | undefined,
  ): number {
    // Inside map — use actual elevation
    if (gx >= 0 && gx < W && gz >= 0 && gz < D) {
      return elev?.[gx]?.[gz] ?? 0;
    }

    // Nearest edge cell for reference
    const cx = Math.max(0, Math.min(W - 1, gx));
    const cz = Math.max(0, Math.min(D - 1, gz));
    const edgeH = elev?.[cx]?.[cz] ?? 0;
    const fz = gz / D;

    // ── North: massive mountain range with peaks and ridges ──
    if (gz < 0) {
      const dist = -gz;
      const baseRise = Math.max(edgeH + 2, 6) + dist * 1.8;

      // Multi-octave ridge noise for dramatic peaks
      const ridge = this.ridgeNoise(gx * 0.06, gz * 0.06, 5);
      const broad = this.fbm(gx * 0.03 + 100, gz * 0.03, 4);
      const detail = this.fbm(gx * 0.12, gz * 0.12 + 50, 3);

      // Peaks get taller further from map edge
      const peakHeight = ridge * 25 * Math.min(1, dist / 8);
      const variation = broad * 12 + detail * 5;

      // Add valley carving between major peaks
      const valleyPattern = Math.abs(this.vnoise(gx * 0.04 + 30, gz * 0.08));
      const valley = valleyPattern < 0.3 ? (0.3 - valleyPattern) * 20 : 0;

      return baseRise + peakHeight + variation - valley;
    }

    // ── South: ocean floor sloping down ──
    if (gz >= D) {
      const dist = gz - D + 1;
      const wave = this.fbm(gx * 0.08, gz * 0.05, 3) * 2;
      return Math.min(edgeH, -1) - dist * 2.5 - wave;
    }

    // ── East/West: mountains all the way down, cliff drop at south ──
    const dist = gx < 0 ? -gx : gx - W + 1;

    // Full mountain range (upper 70%) — same quality as north
    if (fz < 0.55) {
      const baseRise = Math.max(edgeH + 2, 6) + dist * 1.8;
      const ridge = this.ridgeNoise(gx * 0.06, gz * 0.06, 5);
      const broad = this.fbm(gx * 0.03 + 200, gz * 0.03, 4);
      const detail = this.fbm(gx * 0.12 + 80, gz * 0.12, 3);
      const peakHeight = ridge * 25 * Math.min(1, dist / 8);
      const variation = broad * 12 + detail * 5;
      const valleyPattern = Math.abs(this.vnoise(gx * 0.08, gz * 0.04 + 60));
      const valley = valleyPattern < 0.3 ? (0.3 - valleyPattern) * 20 : 0;
      return baseRise + peakHeight + variation - valley;
    }

    // Forest foothills (55-70%)
    if (fz < 0.7) {
      const t = (fz - 0.55) / 0.15;
      const mtnH = (() => {
        const baseRise = Math.max(edgeH + 2, 6) + dist * 1.8;
        const ridge = this.ridgeNoise(gx * 0.06, gz * 0.06, 5);
        const broad = this.fbm(gx * 0.03, gz * 0.03, 4);
        return baseRise + ridge * 20 + broad * 8;
      })();
      const forestH =
        Math.max(edgeH, 3) +
        dist * 0.8 +
        this.fbm(gx * 0.1, gz * 0.1, 3) * 4 +
        this.fbm(gx * 0.2, gz * 0.2, 2) * 2;
      return mtnH * (1 - t) + forestH * t;
    }

    // Coastal cliff (70-80%) — steep drop to ocean
    if (fz < 0.8) {
      const t = (fz - 0.7) / 0.1;
      const cliffTop =
        Math.max(edgeH, 4) +
        dist * 1.0 +
        this.fbm(gx * 0.1 + 40, gz * 0.1, 3) * 3;
      const cliffBase = -2 - dist * 0.5;
      // Sharp cliff with some noise for jagged edge
      const cliffNoise = this.fbm(gx * 0.15, gz * 0.15, 3) * 2;
      return cliffTop * (1 - t * t) + cliffBase * (t * t) + cliffNoise;
    }

    // Ocean (below 80%)
    const t = (fz - 0.8) / 0.2;
    const wave = this.fbm(gx * 0.08, gz * 0.05, 3) * 1.5;
    return -2 - dist * 1.5 - t * 6 - wave;
  }

  /* ── Water surfaces ─────────────────────────────────────── */

  private buildWater(
    W: number,
    D: number,
    terrain: number[][],
    elev: number[][] | undefined,
  ): void {
    const mapW = W * TILE_SIZE;
    const mapD = D * TILE_SIZE;

    // Safety ground plane far below everything (catches anything below water)
    const safetyRadius = Math.max(mapW, mapD) * 4;
    const baseGeo = new THREE.CircleGeometry(safetyRadius, 64);
    baseGeo.rotateX(-Math.PI / 2);
    const baseMat = new THREE.MeshLambertMaterial({ color: 0x0e3d4a });
    const baseMesh = new THREE.Mesh(baseGeo, baseMat);
    baseMesh.position.set(mapW / 2, -15, mapD / 2);
    baseMesh.receiveShadow = true;
    this.scene.add(baseMesh);
    this.meshes.push(baseMesh);

    // Water surface — only visible where terrain dips below y=-0.5
    // Use a circular disc so edges are never visible from isometric view
    const waterRadius = Math.max(mapW, mapD) * 4;
    const waterGeo = new THREE.CircleGeometry(waterRadius, 64);
    waterGeo.rotateX(-Math.PI / 2);
    const waterMat = new THREE.MeshPhongMaterial({
      color: 0x1a7fbb,
      transparent: true,
      opacity: 0.65,
      shininess: 80,
      specular: new THREE.Color(0x336699),
      depthWrite: false,
      side: THREE.DoubleSide,
    });
    const waterMesh = new THREE.Mesh(waterGeo, waterMat);
    waterMesh.position.set(mapW / 2, -0.5, mapD / 2);
    waterMesh.renderOrder = 1;
    waterMesh.receiveShadow = true;
    this.scene.add(waterMesh);
    this.meshes.push(waterMesh);
  }

  dispose(): void {
    for (const obj of this.meshes) {
      this.scene.remove(obj);
      if (obj instanceof THREE.Mesh) {
        obj.geometry.dispose();
        if (Array.isArray(obj.material))
          obj.material.forEach((m: THREE.Material) => m.dispose());
        else (obj.material as THREE.Material).dispose();
      }
    }
    this.meshes = [];
  }
}
