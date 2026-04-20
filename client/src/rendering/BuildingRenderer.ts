import * as THREE from "three";
import { BuildingSnapshotData, GameStateSnapshot } from "../types";

const TILE_SIZE = 16;

/** Colour palette per building style. */
const STYLE_COLORS: Record<string, number[]> = {
  SHOP: [0x7788aa, 0x8899bb, 0x667788, 0x99aabb, 0x889999, 0x6688aa],
  HOUSE: [0xccbb99, 0xbbaa88, 0xddccaa, 0xc4a882, 0xd4c4a0, 0xaa9977],
  FARM: [0x887755, 0x776644, 0x998866, 0x665533, 0xaa9966, 0x554422],
  OFFICE: [0x6688bb, 0x5577aa, 0x7799cc, 0x4466aa, 0x88aacc, 0x336699],
  WAREHOUSE: [0x888888, 0x777777, 0x999999, 0x666666, 0xaaaaaa, 0x555555],
  SERVICE: [0xaa8866, 0x997755, 0xbb9977, 0x886644, 0xccaa88, 0x775533],
  TOWER: [0x99bbdd, 0x88aacc, 0xaaccee, 0x7799bb, 0xbbddee, 0x6688aa],
};

/**
 * Server-driven building renderer using InstancedMesh.
 * Reads building data from the snapshot (placed by server's BuildingPlacer).
 */
export class BuildingRenderer {
  private scene: THREE.Scene;
  private group = new THREE.Group();
  private elevation: number[][] | null = null;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
    this.scene.add(this.group);
  }

  build(snapshot: GameStateSnapshot): void {
    this.clear();
    this.elevation = snapshot.elevation;

    const buildings = snapshot.buildings;
    if (!buildings || buildings.length === 0) return;

    // Separate buildings from trees (trees have colorIndex -1)
    const actualBuildings: BuildingSnapshotData[] = [];
    const trees: BuildingSnapshotData[] = [];
    for (const b of buildings) {
      if (b.colorIndex < 0) {
        trees.push(b);
      } else {
        actualBuildings.push(b);
      }
    }

    this.buildBuildingInstances(actualBuildings);
    this.buildTreeInstances(trees);
  }

  /* ── instanced buildings ─────────────────── */

  private buildBuildingInstances(buildings: BuildingSnapshotData[]): void {
    if (buildings.length === 0) return;

    // Group by style+colorIndex for batching
    const buckets = new Map<string, BuildingSnapshotData[]>();
    for (const b of buildings) {
      const key = `${b.style}_${b.colorIndex}`;
      const arr = buckets.get(key) || [];
      arr.push(b);
      buckets.set(key, arr);
    }

    const matrix = new THREE.Matrix4();
    const pos = new THREE.Vector3();
    const quat = new THREE.Quaternion();
    const scl = new THREE.Vector3();

    for (const [key, batch] of buckets) {
      const [style, ciStr] = key.split("_");
      const ci = parseInt(ciStr);
      const palette = STYLE_COLORS[style] ?? STYLE_COLORS["SHOP"];
      const color = palette[ci % palette.length];

      const geo = new THREE.BoxGeometry(1, 1, 1);
      const mat = new THREE.MeshLambertMaterial({ color });
      const mesh = new THREE.InstancedMesh(geo, mat, batch.length);
      mesh.castShadow = true;
      mesh.receiveShadow = true;

      for (let i = 0; i < batch.length; i++) {
        const b = batch[i];
        const elev = this.getElevationAt(b.x, b.z);
        // Server x → Three x, server z → Three z, height from terrain
        pos.set(b.x, elev + b.height / 2, b.z);
        scl.set(b.width, b.height, b.depth);
        matrix.compose(pos, quat, scl);
        mesh.setMatrixAt(i, matrix);
      }

      mesh.instanceMatrix.needsUpdate = true;
      this.group.add(mesh);
    }
  }

  /* ── instanced trees ─────────────────────── */

  private buildTreeInstances(trees: BuildingSnapshotData[]): void {
    if (trees.length === 0) return;

    // Separate by tree type: -1=deciduous, -2=pine, -3=wheat, -4=corn, -5=fence
    const deciduous: BuildingSnapshotData[] = [];
    const pines: BuildingSnapshotData[] = [];
    const wheat: BuildingSnapshotData[] = [];
    const corn: BuildingSnapshotData[] = [];
    const fences: BuildingSnapshotData[] = [];
    for (const t of trees) {
      if (t.colorIndex === -5) fences.push(t);
      else if (t.colorIndex === -4) corn.push(t);
      else if (t.colorIndex === -3) wheat.push(t);
      else if (t.colorIndex === -2) pines.push(t);
      else deciduous.push(t);
    }

    const matrix = new THREE.Matrix4();
    const pos = new THREE.Vector3();
    const quat = new THREE.Quaternion();
    const scl = new THREE.Vector3();

    // ── Deciduous trees (round canopy) ──
    if (deciduous.length > 0) {
      const trunkGeo = new THREE.CylinderGeometry(0.3, 0.5, 1, 5);
      const trunkMat = new THREE.MeshLambertMaterial({ color: 0x664422 });
      const trunkMesh = new THREE.InstancedMesh(
        trunkGeo,
        trunkMat,
        deciduous.length,
      );
      trunkMesh.castShadow = true;

      const canopyGeo = new THREE.SphereGeometry(1, 6, 5);
      const canopyMat = new THREE.MeshLambertMaterial({ color: 0x3d8a2d });
      const canopyMesh = new THREE.InstancedMesh(
        canopyGeo,
        canopyMat,
        deciduous.length,
      );
      canopyMesh.castShadow = true;

      for (let i = 0; i < deciduous.length; i++) {
        const t = deciduous[i];
        const scale = t.width;
        const trunkH = 3 * scale;
        const canopyR = 2.5 * scale;
        const elev = this.getElevationAt(t.x, t.z);

        pos.set(t.x, elev + trunkH / 2, t.z);
        scl.set(scale, trunkH, scale);
        matrix.compose(pos, quat, scl);
        trunkMesh.setMatrixAt(i, matrix);

        pos.set(t.x, elev + trunkH + canopyR * 0.6, t.z);
        scl.set(canopyR, canopyR * 1.2, canopyR);
        matrix.compose(pos, quat, scl);
        canopyMesh.setMatrixAt(i, matrix);
      }

      trunkMesh.instanceMatrix.needsUpdate = true;
      canopyMesh.instanceMatrix.needsUpdate = true;
      this.group.add(trunkMesh);
      this.group.add(canopyMesh);
    }

    // ── Pine trees (cone canopy, darker green) ──
    if (pines.length > 0) {
      const trunkGeo = new THREE.CylinderGeometry(0.2, 0.4, 1, 5);
      const trunkMat = new THREE.MeshLambertMaterial({ color: 0x553311 });
      const trunkMesh = new THREE.InstancedMesh(
        trunkGeo,
        trunkMat,
        pines.length,
      );
      trunkMesh.castShadow = true;

      const canopyGeo = new THREE.ConeGeometry(1, 1, 6);
      const canopyMat = new THREE.MeshLambertMaterial({ color: 0x1a5c1a });
      const canopyMesh = new THREE.InstancedMesh(
        canopyGeo,
        canopyMat,
        pines.length,
      );
      canopyMesh.castShadow = true;

      for (let i = 0; i < pines.length; i++) {
        const t = pines[i];
        const scale = t.width;
        const trunkH = 4 * scale;
        const canopyH = 6 * scale;
        const canopyR = 2.2 * scale;
        const elev = this.getElevationAt(t.x, t.z);

        pos.set(t.x, elev + trunkH / 2, t.z);
        scl.set(scale * 0.7, trunkH, scale * 0.7);
        matrix.compose(pos, quat, scl);
        trunkMesh.setMatrixAt(i, matrix);

        pos.set(t.x, elev + trunkH + canopyH / 2 - scale, t.z);
        scl.set(canopyR, canopyH, canopyR);
        matrix.compose(pos, quat, scl);
        canopyMesh.setMatrixAt(i, matrix);
      }

      trunkMesh.instanceMatrix.needsUpdate = true;
      canopyMesh.instanceMatrix.needsUpdate = true;
      this.group.add(trunkMesh);
      this.group.add(canopyMesh);
    }

    // ── Wheat fields (golden rectangular patches with texture rows) ──
    if (wheat.length > 0) {
      const wheatGeo = new THREE.BoxGeometry(1, 1, 1);
      const wheatMat = new THREE.MeshLambertMaterial({ color: 0xc4a830 });
      const wheatMesh = new THREE.InstancedMesh(
        wheatGeo,
        wheatMat,
        wheat.length,
      );
      wheatMesh.receiveShadow = true;

      for (let i = 0; i < wheat.length; i++) {
        const c = wheat[i];
        const elev = this.getElevationAt(c.x, c.z);
        pos.set(c.x, elev + c.height / 2 + 0.1, c.z);
        scl.set(c.width, c.height, c.depth);
        matrix.compose(pos, quat, scl);
        wheatMesh.setMatrixAt(i, matrix);
      }
      wheatMesh.instanceMatrix.needsUpdate = true;
      this.group.add(wheatMesh);

      // Add darker row stripes on wheat for visual rows
      const rowGeo = new THREE.BoxGeometry(1, 1, 1);
      const rowMat = new THREE.MeshLambertMaterial({ color: 0xa89020 });
      const rowMesh = new THREE.InstancedMesh(rowGeo, rowMat, wheat.length);
      rowMesh.receiveShadow = true;
      for (let i = 0; i < wheat.length; i++) {
        const c = wheat[i];
        const elev = this.getElevationAt(c.x, c.z);
        pos.set(c.x, elev + c.height / 2 + 0.15, c.z);
        scl.set(c.width * 0.4, c.height * 1.05, c.depth);
        matrix.compose(pos, quat, scl);
        rowMesh.setMatrixAt(i, matrix);
      }
      rowMesh.instanceMatrix.needsUpdate = true;
      this.group.add(rowMesh);
    }

    // ── Corn fields (taller green-yellow patches) ──
    if (corn.length > 0) {
      const cornGeo = new THREE.BoxGeometry(1, 1, 1);
      const cornMat = new THREE.MeshLambertMaterial({ color: 0x5a8a30 });
      const cornMesh = new THREE.InstancedMesh(cornGeo, cornMat, corn.length);
      cornMesh.receiveShadow = true;

      for (let i = 0; i < corn.length; i++) {
        const c = corn[i];
        const elev = this.getElevationAt(c.x, c.z);
        pos.set(c.x, elev + c.height / 2 + 0.1, c.z);
        scl.set(c.width, c.height, c.depth);
        matrix.compose(pos, quat, scl);
        cornMesh.setMatrixAt(i, matrix);
      }
      cornMesh.instanceMatrix.needsUpdate = true;
      this.group.add(cornMesh);
    }

    // ── Fence posts (thin vertical poles at crop plot corners) ──
    if (fences.length > 0) {
      const fenceGeo = new THREE.CylinderGeometry(0.15, 0.15, 1, 4);
      const fenceMat = new THREE.MeshLambertMaterial({ color: 0x8b7355 });
      const fenceMesh = new THREE.InstancedMesh(
        fenceGeo,
        fenceMat,
        fences.length,
      );
      fenceMesh.castShadow = true;

      for (let i = 0; i < fences.length; i++) {
        const f = fences[i];
        const elev = this.getElevationAt(f.x, f.z);
        pos.set(f.x, elev + f.height / 2, f.z);
        scl.set(1, f.height, 1);
        matrix.compose(pos, quat, scl);
        fenceMesh.setMatrixAt(i, matrix);
      }
      fenceMesh.instanceMatrix.needsUpdate = true;
      this.group.add(fenceMesh);
    }
  }

  /* ── elevation sampling ────────────────── */

  /** Get terrain elevation at world position (x, z) → Three.js Y offset */
  private getElevationAt(worldX: number, worldZ: number): number {
    if (!this.elevation) return 0;
    const gx = Math.floor(worldX / TILE_SIZE);
    const gz = Math.floor(worldZ / TILE_SIZE);
    const W = this.elevation.length;
    const D = this.elevation[0]?.length ?? 0;
    if (gx < 0 || gx >= W || gz < 0 || gz >= D) return 0;
    return this.elevation[gx][gz];
  }

  /* ── cleanup ─────────────────────────────── */

  private clear(): void {
    while (this.group.children.length > 0) {
      const child = this.group.children[0];
      this.group.remove(child);
      if (child instanceof THREE.InstancedMesh || child instanceof THREE.Mesh) {
        child.geometry.dispose();
        if (Array.isArray(child.material))
          child.material.forEach((m) => m.dispose());
        else (child.material as THREE.Material).dispose();
      }
    }
  }
}
