import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { PlayerCommand } from "../types";

type CommandSender = (cmd: PlayerCommand) => void;

/**
 * Handles mouse/keyboard input and converts to game commands
 * using Three.js raycasting against a ground plane.
 *
 * Camera controls (SimCity-style):
 *   WASD / Arrow keys → pan
 *   Q / E             → rotate
 *   Right-drag        → pan  (OrbitControls)
 *   Middle-drag       → rotate (OrbitControls)
 *   Scroll            → zoom
 */
export class InputHandler {
  private renderer: THREE.WebGLRenderer;
  private camera: THREE.OrthographicCamera;
  private controls: OrbitControls;
  private sendCommand: CommandSender;

  private activeTool = "select";
  private selectedRoadType = "LOCAL";
  private selectedSignalType = "STOP";
  private isPlacing = false;
  private roadStart = new THREE.Vector3();

  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);
  private scene: THREE.Scene;

  // Keyboard state
  private keys: Record<string, boolean> = {};
  private panSpeed = 8;
  private rotateSpeed = 0.015;

  constructor(
    renderer: THREE.WebGLRenderer,
    camera: THREE.OrthographicCamera,
    scene: THREE.Scene,
    controls: OrbitControls,
    sendCommand: CommandSender,
  ) {
    this.renderer = renderer;
    this.camera = camera;
    this.scene = scene;
    this.controls = controls;
    this.sendCommand = sendCommand;
    this.setupToolbar();
    this.setupCanvasInput();
    this.setupKeyboard();
  }

  /**
   * Called once per frame from the render loop.
   * Applies WASD / arrow-key panning and Q/E rotation.
   */
  update(): void {
    // Camera-relative directions on the XZ plane
    const forward = new THREE.Vector3();
    this.camera.getWorldDirection(forward);
    forward.y = 0;
    forward.normalize();

    const right = new THREE.Vector3();
    right.crossVectors(forward, new THREE.Vector3(0, 1, 0)).normalize();

    const speed = this.panSpeed / this.camera.zoom;
    const panDelta = new THREE.Vector3();

    if (this.keys["KeyW"] || this.keys["ArrowUp"])
      panDelta.add(forward.clone().multiplyScalar(speed));
    if (this.keys["KeyS"] || this.keys["ArrowDown"])
      panDelta.add(forward.clone().multiplyScalar(-speed));
    if (this.keys["KeyD"] || this.keys["ArrowRight"])
      panDelta.add(right.clone().multiplyScalar(speed));
    if (this.keys["KeyA"] || this.keys["ArrowLeft"])
      panDelta.add(right.clone().multiplyScalar(-speed));

    if (panDelta.lengthSq() > 0) {
      this.controls.target.add(panDelta);
      this.camera.position.add(panDelta);
    }

    if (this.keys["KeyQ"]) this.rotateCamera(-this.rotateSpeed);
    if (this.keys["KeyE"]) this.rotateCamera(this.rotateSpeed);
  }

  /* ── camera rotation helper ────────── */

  private rotateCamera(angle: number): void {
    const offset = this.camera.position.clone().sub(this.controls.target);
    const spherical = new THREE.Spherical().setFromVector3(offset);
    spherical.theta += angle;
    offset.setFromSpherical(spherical);
    this.camera.position.copy(this.controls.target).add(offset);
  }

  /* ── toolbar ───────────────────────── */

  private setupToolbar(): void {
    document.querySelectorAll(".tool-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        this.activeTool = (btn as HTMLElement).dataset.tool || "select";
        document
          .querySelectorAll(".tool-btn")
          .forEach((b) => b.classList.remove("active"));
        btn.classList.add("active");
        this.updateToolBars();
      });
    });

    // Road type selector
    document.querySelectorAll(".road-type-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        this.selectedRoadType = (btn as HTMLElement).dataset.road || "LOCAL";
        document
          .querySelectorAll(".road-type-btn")
          .forEach((b) => b.classList.remove("active"));
        btn.classList.add("active");
      });
    });

    // Signal/Intersection type selector
    document.querySelectorAll(".signal-type-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        this.selectedSignalType = (btn as HTMLElement).dataset.signal || "STOP";
        document
          .querySelectorAll(".signal-type-btn")
          .forEach((b) => b.classList.remove("active"));
        btn.classList.add("active");
      });
    });
  }

  private updateToolBars(): void {
    const roadBar = document.getElementById("road-type-bar");
    const signalBar = document.getElementById("signal-type-bar");
    if (roadBar) {
      roadBar.classList.toggle("visible", this.activeTool === "road");
    }
    if (signalBar) {
      signalBar.classList.toggle("visible", this.activeTool === "signal");
    }
  }

  /* ── keyboard ──────────────────────── */

  private setupKeyboard(): void {
    document.addEventListener("keydown", (e) => {
      this.keys[e.code] = true;

      // Tool shortcuts 1-3 (no demolish)
      switch (e.key) {
        case "1":
          this.setTool("select");
          break;
        case "2":
          this.setTool("road");
          break;
        case "3":
          this.setTool("signal");
          break;
      }
    });

    document.addEventListener("keyup", (e) => {
      this.keys[e.code] = false;
    });
  }

  /* ── canvas input ──────────────────── */

  private setupCanvasInput(): void {
    const canvas = this.renderer.domElement;
    let pointerDownWorld: THREE.Vector3 | null = null;

    canvas.addEventListener("pointerdown", (e) => {
      if (e.button !== 0) return; // left-click only

      const world = this.getWorldPosition(e);
      if (!world) return;
      pointerDownWorld = world.clone();

      switch (this.activeTool) {
        case "road":
          this.roadStart.copy(world);
          this.isPlacing = true;
          break;
        case "signal":
          // Single-click to place/upgrade intersection with selected control type
          this.sendCommand({
            type: "place_signal",
            targetId: `player_${Math.round(world.x)}_${Math.round(world.z)}`,
            data: this.selectedSignalType,
          });
          break;
      }
    });

    canvas.addEventListener("pointerup", (e) => {
      const world = this.getWorldPosition(e);

      if (this.activeTool === "road" && this.isPlacing) {
        if (world) {
          const dist = this.roadStart.distanceTo(world);
          if (dist < 5) {
            // Single-click: upgrade existing road near cursor
            this.sendCommand({
              type: "upgrade_road",
              x: world.x,
              y: world.z,
              data: this.selectedRoadType,
            });
            this.showToast(
              `Upgrade request sent for ${this.selectedRoadType} road`,
            );
          } else {
            // Drag: place new road
            this.sendCommand({
              type: "place_road",
              x: this.roadStart.x,
              y: this.roadStart.z,
              x2: world.x,
              y2: world.z,
              data: this.selectedRoadType,
            });
          }
        }
        this.isPlacing = false;
      } else if (this.activeTool === "select" && world && pointerDownWorld) {
        // Select tool: show road/intersection info near click point
        const moveDist = world.distanceTo(pointerDownWorld);
        if (moveDist < 5) {
          this.sendCommand({
            type: "query_location",
            x: world.x,
            y: world.z,
            data: "",
          });
          this.showSelectionMarker(world);
        }
      }

      pointerDownWorld = null;
    });
  }

  private selectionMarker: THREE.Mesh | null = null;

  private showSelectionMarker(pos: THREE.Vector3): void {
    // Remove old marker
    if (this.selectionMarker) {
      this.selectionMarker.parent?.remove(this.selectionMarker);
      this.selectionMarker = null;
    }
    // Pulsing ring at click location
    const geo = new THREE.RingGeometry(3, 4, 24);
    const mat = new THREE.MeshBasicMaterial({
      color: 0x44ccff,
      side: THREE.DoubleSide,
      transparent: true,
      opacity: 0.8,
    });
    const ring = new THREE.Mesh(geo, mat);
    ring.rotation.x = -Math.PI / 2;
    ring.position.set(pos.x, 0.5, pos.z);
    this.scene.add(ring);
    this.selectionMarker = ring;
    // Auto-remove after 2 seconds
    setTimeout(() => {
      ring.parent?.remove(ring);
      if (this.selectionMarker === ring) this.selectionMarker = null;
    }, 2000);
  }

  private showToast(message: string): void {
    let toast = document.getElementById("input-toast");
    if (!toast) {
      toast = document.createElement("div");
      toast.id = "input-toast";
      toast.style.cssText =
        "position:fixed;bottom:80px;left:50%;transform:translateX(-50%);background:rgba(0,0,0,0.7);color:#fff;padding:8px 16px;border-radius:4px;font-size:13px;pointer-events:none;z-index:500;transition:opacity 0.3s";
      document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.style.opacity = "1";
    setTimeout(() => {
      if (toast) toast.style.opacity = "0";
    }, 2000);
  }

  /* ── raycasting ────────────────────── */

  private getWorldPosition(e: PointerEvent): THREE.Vector3 | null {
    const rect = this.renderer.domElement.getBoundingClientRect();
    this.pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
    this.pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;

    this.raycaster.setFromCamera(this.pointer, this.camera);
    const hit = new THREE.Vector3();
    return this.raycaster.ray.intersectPlane(this.groundPlane, hit)
      ? hit
      : null;
  }

  private setTool(tool: string): void {
    this.activeTool = tool;
    document.querySelectorAll(".tool-btn").forEach((b) => {
      b.classList.toggle("active", (b as HTMLElement).dataset.tool === tool);
    });
    this.updateToolBars();
  }
}
