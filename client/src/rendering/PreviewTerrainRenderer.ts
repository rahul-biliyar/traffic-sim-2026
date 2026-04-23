import * as THREE from "three";

/**
 * Renders a simple procedural terrain for the start menu preview.
 * This is replaced with the real terrain once the snapshot arrives.
 */
export class PreviewTerrainRenderer {
  private scene: THREE.Scene;
  private mesh: THREE.Mesh | null = null;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
  }

  build(): void {
    this.dispose();

    // Simple procedural terrain: 64×64 grid with Simplex-like noise
    const size = 64;
    const scale = 16; // TILE_SIZE
    const geometry = new THREE.BufferGeometry();

    const vertices: number[] = [];
    const indices: number[] = [];
    const colors: number[] = [];

    // Generate height map using simple noise function
    const heightMap: number[][] = [];
    for (let x = 0; x <= size; x++) {
      heightMap[x] = [];
      for (let z = 0; z <= size; z++) {
        heightMap[x][z] = this.noise(x * 0.1, z * 0.1) * 2;
      }
    }

    // Create vertices
    for (let x = 0; x <= size; x++) {
      for (let z = 0; z <= size; z++) {
        const vx = (x - size / 2) * scale;
        const vy = heightMap[x][z];
        const vz = (z - size / 2) * scale;
        vertices.push(vx, vy, vz);

        // Color based on height and zone (simple green/brown gradient)
        const height = heightMap[x][z];
        const r = 0.4 + height * 0.1;
        const g = 0.6 + height * 0.05;
        const b = 0.3;
        colors.push(r, g, b);
      }
    }

    // Create faces
    for (let x = 0; x < size; x++) {
      for (let z = 0; z < size; z++) {
        const a = x * (size + 1) + z;
        const b = a + 1;
        const c = a + (size + 1);
        const d = c + 1;

        indices.push(a, c, b);
        indices.push(b, c, d);
      }
    }

    geometry.setAttribute(
      "position",
      new THREE.BufferAttribute(new Float32Array(vertices), 3),
    );
    geometry.setAttribute(
      "color",
      new THREE.BufferAttribute(new Float32Array(colors), 3),
    );
    geometry.setIndex(new THREE.BufferAttribute(new Uint32Array(indices), 1));
    geometry.computeVertexNormals();

    const material = new THREE.MeshPhongMaterial({
      vertexColors: true,
      side: THREE.DoubleSide,
      wireframe: false,
    });

    this.mesh = new THREE.Mesh(geometry, material);
    this.scene.add(this.mesh);
  }

  /**
   * Simple noise function for terrain variation.
   */
  private noise(x: number, y: number): number {
    return (
      Math.sin(x * 0.5) * Math.cos(y * 0.5) +
      Math.sin(x * 0.1) * Math.cos(y * 0.2) * 0.5
    );
  }

  dispose(): void {
    if (this.mesh) {
      this.scene.remove(this.mesh);
      (this.mesh.geometry as THREE.BufferGeometry).dispose();
      if (Array.isArray(this.mesh.material)) {
        this.mesh.material.forEach((m: THREE.Material) => m.dispose());
      } else {
        this.mesh.material.dispose();
      }
      this.mesh = null;
    }
  }
}
