import * as THREE from "three";
import { TickDelta, VehicleUpdate } from "../types";

const VEHICLE_Y = 1.2; // just above road surface at y=0.15

/** Colour per vehicle type. */
const COLOURS: Record<string, number> = {
  CAR: 0xdddddd,
  TRUCK: 0xee9933,
  BUS: 0x3399ee,
  EMERGENCY: 0xee3333,
  TAXI: 0xeeee33,
  MOTORCYCLE: 0x9944ee,
};

/** (width, height, depth) per vehicle type. */
const SIZES: Record<string, [number, number, number]> = {
  CAR: [3, 2, 5],
  TRUCK: [3.5, 3, 8],
  BUS: [3, 3.2, 10],
  EMERGENCY: [3, 2.5, 6],
  TAXI: [3, 2, 5],
  MOTORCYCLE: [1.5, 1.8, 3],
};

/**
 * Renders vehicles as 3D boxes with per-frame interpolation.
 * Each vehicle gets its own Mesh (reasonable for <3 000 vehicles).
 */
export class VehicleRenderer {
  private scene: THREE.Scene;
  private group = new THREE.Group();
  private meshes = new Map<number, THREE.Mesh>();
  private prevVehicles = new Map<number, VehicleUpdate>();

  constructor(scene: THREE.Scene) {
    this.scene = scene;
    this.scene.add(this.group);
  }

  update(prev: TickDelta, current: TickDelta, alpha: number): void {
    const prevUpdates = prev.vehicleUpdates ?? [];
    const curUpdates = current.vehicleUpdates ?? [];

    // Index previous positions for interpolation
    if (prev !== current) {
      this.prevVehicles.clear();
      for (const v of prevUpdates) this.prevVehicles.set(v.id, v);
    }

    const activeIds = new Set<number>();

    for (const v of curUpdates) {
      activeIds.add(v.id);

      const pv = this.prevVehicles.get(v.id);
      let x = v.x;
      let z = v.y; // server y → Three z
      let angle = v.angle;
      let elev = v.elevation ?? 0;

      if (pv) {
        x = pv.x + (v.x - pv.x) * alpha;
        z = pv.y + (v.y - pv.y) * alpha;
        angle = pv.angle + (v.angle - pv.angle) * alpha;
        elev =
          (pv.elevation ?? 0) +
          ((v.elevation ?? 0) - (pv.elevation ?? 0)) * alpha;
      }

      let mesh = this.meshes.get(v.id);
      if (!mesh) {
        mesh = this.createMesh(v.type);
        this.group.add(mesh);
        this.meshes.set(v.id, mesh);
      }

      mesh.position.set(x, VEHICLE_Y + elev, z);
      mesh.rotation.y = angle; // server sends correct Three.js rotation
    }

    // Remove vehicles no longer present
    for (const id of current.removedVehicleIds ?? []) activeIds.delete(id);
    for (const [id, mesh] of this.meshes) {
      if (!activeIds.has(id)) {
        this.group.remove(mesh);
        mesh.geometry.dispose();
        (mesh.material as THREE.Material).dispose();
        this.meshes.delete(id);
      }
    }
  }

  /* ── helpers ───────────────────── */

  private createMesh(type: string): THREE.Mesh {
    const [w, h, d] = SIZES[type] ?? [3, 2, 5];
    const colour = COLOURS[type] ?? 0xffffff;
    const geo = new THREE.BoxGeometry(w, h, d);
    const mat = new THREE.MeshLambertMaterial({ color: colour });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.castShadow = true;
    return mesh;
  }
}
