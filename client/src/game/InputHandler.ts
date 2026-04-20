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
  private isPlacing = false;
  private roadStart = new THREE.Vector3();

  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);

  // Keyboard state
  private keys: Record<string, boolean> = {};
  private panSpeed = 8;
  private rotateSpeed = 0.015;

  constructor(
    renderer: THREE.WebGLRenderer,
    camera: THREE.OrthographicCamera,
    _scene: THREE.Scene,
    controls: OrbitControls,
    sendCommand: CommandSender,
  ) {
    this.renderer = renderer;
    this.camera = camera;
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
      });
    });
  }

  /* ── keyboard ──────────────────────── */

  private setupKeyboard(): void {
    document.addEventListener("keydown", (e) => {
      this.keys[e.code] = true;

      // Tool shortcuts 1-4
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
        case "4":
          this.setTool("demolish");
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

    canvas.addEventListener("pointerdown", (e) => {
      if (e.button !== 0) return; // left-click only

      const world = this.getWorldPosition(e);
      if (!world) return;

      switch (this.activeTool) {
        case "road":
          this.roadStart.copy(world);
          this.isPlacing = true;
          break;
        case "signal":
          this.sendCommand({
            type: "place_signal",
            targetId: `player_${Math.round(world.x)}_${Math.round(world.z)}`,
          });
          break;
        case "demolish":
          this.sendCommand({
            type: "demolish",
            x: world.x,
            y: world.z, // Three.js z → server y
          });
          break;
      }
    });

    canvas.addEventListener("pointerup", (e) => {
      if (this.activeTool === "road" && this.isPlacing) {
        const world = this.getWorldPosition(e);
        if (world) {
          this.sendCommand({
            type: "place_road",
            x: this.roadStart.x,
            y: this.roadStart.z,
            x2: world.x,
            y2: world.z,
            data: "LOCAL",
          });
        }
        this.isPlacing = false;
      }
    });
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
  }
}
