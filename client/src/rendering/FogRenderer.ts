import * as THREE from "three";
import { GameStateSnapshot } from "../types";

/**
 * Renders semi-transparent fog meshes over locked districts.
 * Fog updates dynamically as districts unlock.
 */
export class FogRenderer {
  private scene: THREE.Scene;
  private fogMeshes: THREE.Mesh[] = [];
  private fogMaterial: THREE.MeshPhongMaterial;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
    this.fogMaterial = new THREE.MeshPhongMaterial({
      color: 0x999999,
      opacity: 0.25,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide,
    });
  }

  build(snapshot: GameStateSnapshot): void {
    this.dispose();

    if (!snapshot.districts || snapshot.districts.length === 0) return;

    const fogRadius = 80; // approximate fog coverage per district
    const fogHeight = 1.5; // height above ground

    for (const district of snapshot.districts) {
      if (district.unlocked) continue; // Skip unlocked districts

      const geometry = new THREE.CylinderGeometry(
        fogRadius,
        fogRadius,
        fogHeight,
        32,
      );
      const mesh = new THREE.Mesh(geometry, this.fogMaterial.clone());
      mesh.position.set(district.centerX, fogHeight / 2, district.centerY);
      mesh.castShadow = false;
      mesh.receiveShadow = false;
      this.scene.add(mesh);
      this.fogMeshes.push(mesh);
    }
  }

  dispose(): void {
    for (const mesh of this.fogMeshes) {
      this.scene.remove(mesh);
      if (mesh.geometry) mesh.geometry.dispose();
      if (mesh.material instanceof THREE.Material) {
        mesh.material.dispose();
      }
    }
    this.fogMeshes = [];
  }
}
