import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { WebSocketClient } from "../network/WebSocketClient";
import { StateBuffer } from "../network/StateBuffer";
import { TerrainRenderer } from "../rendering/TerrainRenderer";
import { RoadRenderer } from "../rendering/RoadRenderer";
import { VehicleRenderer } from "../rendering/VehicleRenderer";
import { BuildingRenderer } from "../rendering/BuildingRenderer";
import { InputHandler } from "./InputHandler";
import { HudUpdater } from "../ui/HudUpdater";
import { GameStateSnapshot, TickDelta, PlayerCommand } from "../types";

/**
 * Main game application — Three.js scene with isometric camera,
 * WebSocket networking, and 3D rendering layers.
 */
export class GameApp {
  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.OrthographicCamera;
  private controls!: OrbitControls;
  private sun!: THREE.DirectionalLight;

  private wsClient!: WebSocketClient;
  private stateBuffer = new StateBuffer();
  private inputHandler!: InputHandler;
  private hudUpdater!: HudUpdater;

  private terrainRenderer!: TerrainRenderer;
  private roadRenderer!: RoadRenderer;
  private vehicleRenderer!: VehicleRenderer;
  private buildingRenderer!: BuildingRenderer;

  private snapshot: GameStateSnapshot | null = null;
  private lastTickTime = 0;

  private mapCenterX = 0;
  private mapCenterZ = 0;
  private mapRadius = 600;

  async init(): Promise<void> {
    // Scene – warm sky gradient (not solid blue)
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x8aad5e);

    // Soft exponential fog for distant fade (muted sky tone)
    this.scene.fog = new THREE.FogExp2(0x8aad5e, 0.00015);

    // WebGL renderer
    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setSize(window.innerWidth, window.innerHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.0;
    document
      .getElementById("game-container")!
      .appendChild(this.renderer.domElement);

    // Prevent browser context-menu on right-click (used for camera pan)
    this.renderer.domElement.addEventListener("contextmenu", (e) =>
      e.preventDefault(),
    );

    // Camera (orthographic isometric)
    this.setupCamera();

    // Lighting
    this.setupLighting();

    // Orbit controls: right-click = pan, middle = rotate, scroll = zoom
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableRotate = true;
    this.controls.enableZoom = false; // we handle zoom manually below
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.1;
    this.controls.screenSpacePanning = true;
    this.controls.maxPolarAngle = Math.PI / 2.2;
    this.controls.minPolarAngle = Math.PI / 6;
    this.controls.mouseButtons = {
      LEFT: -1 as THREE.MOUSE, // disabled — InputHandler uses left click
      MIDDLE: THREE.MOUSE.ROTATE,
      RIGHT: THREE.MOUSE.PAN,
    };
    this.controls.panSpeed = 1.5;
    this.controls.rotateSpeed = 0.5;

    // Manual scroll-wheel zoom — guaranteed to work with orthographic camera
    this.renderer.domElement.addEventListener(
      "wheel",
      (e: WheelEvent) => {
        e.preventDefault();
        const zoomFactor = e.deltaY > 0 ? 0.92 : 1.08;
        this.camera.zoom = Math.max(
          1.2,
          Math.min(5.0, this.camera.zoom * zoomFactor),
        );
        this.camera.updateProjectionMatrix();
      },
      { passive: false },
    );

    // Rendering layers
    this.terrainRenderer = new TerrainRenderer(this.scene);
    this.roadRenderer = new RoadRenderer(this.scene);
    this.vehicleRenderer = new VehicleRenderer(this.scene);
    this.buildingRenderer = new BuildingRenderer(this.scene);

    // HUD
    this.hudUpdater = new HudUpdater();

    // Input (raycasting + keyboard camera)
    this.inputHandler = new InputHandler(
      this.renderer,
      this.camera,
      this.scene,
      this.controls,
      (cmd: PlayerCommand) => this.wsClient?.sendCommand(cmd),
    );

    // WebSocket connection
    const sessionId = "game_" + Date.now();
    this.wsClient = new WebSocketClient(sessionId, {
      onSnapshot: (snap) => this.onSnapshot(snap),
      onTick: (delta) => this.onTick(delta),
      onError: (err) => console.error("WS error:", err),
    });
    this.wsClient.connect();

    // Resize handler
    window.addEventListener("resize", () => this.onResize());

    // Start render loop
    this.animate();
  }

  private setupCamera(): void {
    const aspect = window.innerWidth / window.innerHeight;
    const frustumSize = 800;
    this.camera = new THREE.OrthographicCamera(
      (-frustumSize * aspect) / 2,
      (frustumSize * aspect) / 2,
      frustumSize / 2,
      -frustumSize / 2,
      1,
      10000,
    );

    // Classic isometric: ~45° azimuth, ~35° elevation
    const d = 2000;
    this.camera.position.set(d * 0.7, d * 0.6, d * 0.7);
    this.camera.lookAt(0, 0, 0);
    this.camera.zoom = 1.2;
    this.camera.updateProjectionMatrix();
  }

  private setupLighting(): void {
    // Bright ambient fill
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.5));

    // Sun — warm directional with full-map shadow coverage
    this.sun = new THREE.DirectionalLight(0xfff5e0, 1.4);
    this.sun.position.set(800, 1000, 600);
    this.sun.castShadow = true;
    this.sun.shadow.mapSize.width = 4096;
    this.sun.shadow.mapSize.height = 4096;
    const sc = this.sun.shadow.camera;
    sc.left = -2500;
    sc.right = 2500;
    sc.top = 2500;
    sc.bottom = -2500;
    sc.near = 1;
    sc.far = 5000;
    this.scene.add(this.sun);
    this.scene.add(this.sun.target);

    // Hemisphere sky/ground bounce
    this.scene.add(new THREE.HemisphereLight(0x88bbff, 0x445522, 0.4));
  }

  /* ── network callbacks ─────────────────── */

  private onSnapshot(snapshot: GameStateSnapshot): void {
    this.snapshot = snapshot;
    document.getElementById("loading")!.style.display = "none";
    document.getElementById("hud")!.style.display = "flex";
    document.getElementById("toolbar")!.style.display = "flex";

    this.terrainRenderer.build(snapshot);
    this.roadRenderer.build(snapshot);
    this.buildingRenderer.build(snapshot);
    this.hudUpdater.updateFromSnapshot(snapshot);

    // Centre camera on map
    const cx = (snapshot.mapWidth * 16) / 2;
    const cz = (snapshot.mapHeight * 16) / 2;
    this.mapCenterX = cx;
    this.mapCenterZ = cz;
    this.mapRadius = Math.max(snapshot.mapWidth, snapshot.mapHeight) * 16 * 0.4;
    this.controls.target.set(cx, 0, cz);

    const d = 700;
    this.camera.position.set(cx + d * 0.7, d * 0.6, cz + d * 0.7);
    this.camera.updateProjectionMatrix();
    this.controls.update();

    // Point sun at map centre for proper shadows
    this.sun.position.set(cx + 500, 800, cz + 400);
    this.sun.target.position.set(cx, 0, cz);
    this.sun.target.updateMatrixWorld();
  }

  private onTick(delta: TickDelta): void {
    this.stateBuffer.push(delta);
    this.lastTickTime = performance.now();
    this.hudUpdater.updateFromTick(delta);

    // Update traffic signal light colours
    if (delta.signalUpdates?.length) {
      this.roadRenderer.updateSignals(delta.signalUpdates);
    }
  }

  /* ── render loop ───────────────────────── */

  private animate = (): void => {
    requestAnimationFrame(this.animate);

    // Keyboard-driven camera pan / rotate
    this.inputHandler.update();
    this.controls.update();

    // Clamp camera target within map bounds (+ small margin)
    const t = this.controls.target;
    const r = this.mapRadius;
    t.x = Math.max(this.mapCenterX - r, Math.min(this.mapCenterX + r, t.x));
    t.z = Math.max(this.mapCenterZ - r, Math.min(this.mapCenterZ + r, t.z));

    const pair = this.stateBuffer.getInterpolationPair();
    if (pair) {
      const alpha = Math.min(1, (performance.now() - this.lastTickTime) / 100);
      this.vehicleRenderer.update(pair[0], pair[1], alpha);
    } else {
      const cur = this.stateBuffer.getCurrent();
      if (cur) this.vehicleRenderer.update(cur, cur, 0);
    }

    this.renderer.render(this.scene, this.camera);
  };

  private onResize(): void {
    const aspect = window.innerWidth / window.innerHeight;
    const f = 800;
    this.camera.left = (-f * aspect) / 2;
    this.camera.right = (f * aspect) / 2;
    this.camera.top = f / 2;
    this.camera.bottom = -f / 2;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(window.innerWidth, window.innerHeight);
  }
}
