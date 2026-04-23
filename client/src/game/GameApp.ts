import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { WebSocketClient } from "../network/WebSocketClient";
import { StateBuffer } from "../network/StateBuffer";
import { PreviewTerrainRenderer } from "../rendering/PreviewTerrainRenderer";
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

  private previewTerrainRenderer!: PreviewTerrainRenderer;
  private terrainRenderer!: TerrainRenderer;
  private roadRenderer!: RoadRenderer;
  private vehicleRenderer!: VehicleRenderer;
  private buildingRenderer!: BuildingRenderer;
  private districtMarkerGroup = new THREE.Group();

  private snapshot: GameStateSnapshot | null = null;
  private lastTickTime = 0;

  private mapCenterX = 0;
  private mapCenterZ = 0;
  private mapRadius = 600;
  private started = false;
  private snapshotReceived = false; // true after first snapshot — camera is NOT re-homed on updates
  private mayorName = "";

  async init(): Promise<void> {
    // Scene – warm sky gradient (not solid blue)
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x8aad5e);

    // Very gentle fog only for far-distance fade — keep the starting district fully clear
    this.scene.fog = new THREE.FogExp2(0x7aad8a, 0.0003);

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
    this.previewTerrainRenderer = new PreviewTerrainRenderer(this.scene);
    this.previewTerrainRenderer.build(); // Show preview terrain behind start menu

    this.terrainRenderer = new TerrainRenderer(this.scene);
    this.roadRenderer = new RoadRenderer(this.scene);
    this.vehicleRenderer = new VehicleRenderer(this.scene);
    this.buildingRenderer = new BuildingRenderer(this.scene);
    this.scene.add(this.districtMarkerGroup);

    // HUD
    this.hudUpdater = new HudUpdater();
    this.hudUpdater.setSendCommand((cmd: PlayerCommand) =>
      this.wsClient?.sendCommand(cmd),
    );
    this.hudUpdater.setOnDistrictUnlock((districtId, centerX, centerY) =>
      this.cameraMoveTo(centerX, centerY),
    );

    // Input (raycasting + keyboard camera)
    this.inputHandler = new InputHandler(
      this.renderer,
      this.camera,
      this.scene,
      this.controls,
      (cmd: PlayerCommand) => this.wsClient?.sendCommand(cmd),
    );

    // Start menu button
    document.getElementById("btn-start")!.addEventListener("click", () => {
      this.startGame();
    });

    // Resize handler
    window.addEventListener("resize", () => this.onResize());

    // Start render loop
    this.animate();
  }

  private startGame(): void {
    if (this.started) return;
    this.started = true;

    // Get mayor name from input
    const mayorInput = document.getElementById(
      "mayor-name",
    ) as HTMLInputElement;
    this.mayorName = mayorInput?.value.trim() || "Mayor";

    document.getElementById("start-menu")!.style.display = "none";
    document.getElementById("loading")!.style.display = "flex";

    // WebSocket connection
    const sessionId = "game_" + Date.now();
    this.wsClient = new WebSocketClient(sessionId, {
      onSnapshot: (snap) => this.onSnapshot(snap),
      onTick: (delta) => this.onTick(delta),
      onError: (err) => console.error("WS error:", err),
    });
    this.wsClient.connect();
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

    // Position camera over the preview terrain origin (preview is centered at 0,0)
    const d = 600;
    this.camera.position.set(d * 0.7, d * 0.6, d * 0.7);
    this.camera.lookAt(0, 0, 0);
    this.camera.zoom = 2.0;
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
    document.getElementById("district-toggle")!.style.display = "flex";

    // Dispose preview terrain, build real terrain
    this.previewTerrainRenderer.dispose();
    this.terrainRenderer.build(snapshot);
    this.roadRenderer.build(snapshot);
    this.buildingRenderer.build(snapshot);
    this.hudUpdater.updateFromSnapshot(snapshot);
    this.hudUpdater.setMayorName(this.mayorName);

    // Map bounds for camera clamping
    const mapCx = (snapshot.mapWidth * 16) / 2;
    const mapCz = (snapshot.mapHeight * 16) / 2;
    this.mapCenterX = mapCx;
    this.mapCenterZ = mapCz;
    this.mapRadius = Math.max(snapshot.mapWidth, snapshot.mapHeight) * 16 * 0.4;

    // Only home camera on the FIRST snapshot — subsequent snapshots (mutation updates)
    // must NOT jump the camera back to the district center.
    if (!this.snapshotReceived) {
      this.snapshotReceived = true;

      // Pin camera to first unlocked district (or map center as fallback)
      // District centerX/centerY arrive as world coords (grid * TILE_SIZE) from MessageBuilder
      let cx = mapCx;
      let cz = mapCz;
      const firstDistrict = snapshot.districts?.find((d) => d.unlocked);
      if (
        firstDistrict &&
        firstDistrict.centerX != null &&
        firstDistrict.centerY != null
      ) {
        cx = firstDistrict.centerX;
        cz = firstDistrict.centerY;
      }

      // Teleport camera instantly — disable damping so it doesn't drift in
      const wasDamping = this.controls.enableDamping;
      this.controls.enableDamping = false;
      this.controls.target.set(cx, 0, cz);
      const d = 600;
      this.camera.position.set(cx + d * 0.7, d * 0.6, cz + d * 0.7);
      this.camera.zoom = 2.0;
      this.camera.updateProjectionMatrix();
      this.controls.update();
      this.controls.enableDamping = wasDamping;

      // District markers — highlight unlocked district + show all districts on map
      this.buildDistrictMarkers(snapshot);
    } else {
      // Mutation snapshot: rebuild district markers in case unlock state changed
      this.buildDistrictMarkers(snapshot);
    }

    // Point sun at map centre for proper shadows
    this.sun.position.set(mapCx + 500, 800, mapCz + 400);
    this.sun.target.position.set(mapCx, 0, mapCz);
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

  /** Smoothly pan camera to a world position over ~0.5s */
  private cameraMoveTo(targetX: number, targetZ: number): void {
    const startTarget = this.controls.target.clone();
    const startPos = this.camera.position.clone();
    const duration = 500; // milliseconds
    const startTime = performance.now();

    const step = (currentTime: number) => {
      const elapsed = currentTime - startTime;
      const t = Math.min(1, elapsed / duration);
      // Ease-out quadratic
      const easeT = 1 - (1 - t) * (1 - t);

      // Lerp target
      this.controls.target.lerpVectors(
        startTarget,
        new THREE.Vector3(targetX, 0, targetZ),
        easeT,
      );

      // Lerp camera position maintaining isometric angle
      const relativePos = startPos.clone().sub(startTarget);
      const targetCamPos = new THREE.Vector3(targetX, 0, targetZ).add(
        relativePos,
      );
      this.camera.position.lerpVectors(startPos, targetCamPos, easeT);

      if (t < 1) {
        requestAnimationFrame(step);
      }
    };

    requestAnimationFrame(step);
  }

  /**
   * Renders a flat ground marker for each district:
   * - Unlocked districts: bright green pulsing ring + district label pillar
   * - Locked districts: dim grey ring only
   */
  private buildDistrictMarkers(snapshot: GameStateSnapshot): void {
    // Clear existing markers
    while (this.districtMarkerGroup.children.length > 0) {
      this.districtMarkerGroup.remove(this.districtMarkerGroup.children[0]);
    }

    // One colour per district number (1-7) — matches the Voronoi district scheme
    const DISTRICT_COLORS: Record<number, number> = {
      1: 0x00ccbb, // Town Center — teal
      2: 0x66cc44, // Residential — green
      3: 0xaacc22, // Farmland — yellow-green
      4: 0xff8844, // Commercial — orange
      5: 0x6688cc, // Industrial — steel-blue
      6: 0x9966dd, // Highway Corridor — violet
      7: 0xffcc00, // Downtown — gold
    };

    for (const d of snapshot.districts ?? []) {
      if (d.centerX == null || d.centerY == null) continue;
      const cx = d.centerX;
      const cz = d.centerY;
      const color = DISTRICT_COLORS[d.number] ?? 0x888888;

      if (d.unlocked) {
        // Large semi-transparent filled disc — the district's territory
        const discGeo = new THREE.CircleGeometry(50, 52);
        const discMat = new THREE.MeshBasicMaterial({
          color,
          transparent: true,
          opacity: 0.14,
          side: THREE.DoubleSide,
          depthWrite: false,
        });
        const disc = new THREE.Mesh(discGeo, discMat);
        disc.rotation.x = -Math.PI / 2;
        disc.position.set(cx, 0.4, cz);
        this.districtMarkerGroup.add(disc);

        // Solid outer border ring — bold, coloured
        const borderGeo = new THREE.RingGeometry(48.5, 51.5, 52);
        const borderMat = new THREE.MeshBasicMaterial({
          color,
          transparent: true,
          opacity: 0.85,
          side: THREE.DoubleSide,
          depthWrite: false,
        });
        const border = new THREE.Mesh(borderGeo, borderMat);
        border.rotation.x = -Math.PI / 2;
        border.position.set(cx, 0.5, cz);
        this.districtMarkerGroup.add(border);

        // Centre dot
        const dotGeo = new THREE.CircleGeometry(5.5, 24);
        const dotMat = new THREE.MeshBasicMaterial({
          color,
          transparent: true,
          opacity: 0.9,
          side: THREE.DoubleSide,
          depthWrite: false,
        });
        const dot = new THREE.Mesh(dotGeo, dotMat);
        dot.rotation.x = -Math.PI / 2;
        dot.position.set(cx, 0.5, cz);
        this.districtMarkerGroup.add(dot);

        // Thin cylinder beacon — visible at any zoom level
        const beaconGeo = new THREE.CylinderGeometry(0.7, 0.7, 32, 8);
        const beaconMat = new THREE.MeshBasicMaterial({
          color,
          transparent: true,
          opacity: 0.65,
        });
        const beacon = new THREE.Mesh(beaconGeo, beaconMat);
        beacon.position.set(cx, 16, cz);
        this.districtMarkerGroup.add(beacon);

        // Name label sprite
        const label = this.makeDistrictLabel(d.name.replace(/_/g, " "), color);
        label.position.set(cx, 38, cz);
        this.districtMarkerGroup.add(label);
      } else {
        // Locked district — faint grey dashed ring only
        const borderGeo = new THREE.RingGeometry(48, 51, 52);
        const borderMat = new THREE.MeshBasicMaterial({
          color: 0x334455,
          transparent: true,
          opacity: 0.22,
          side: THREE.DoubleSide,
          depthWrite: false,
        });
        const border = new THREE.Mesh(borderGeo, borderMat);
        border.rotation.x = -Math.PI / 2;
        border.position.set(cx, 0.5, cz);
        this.districtMarkerGroup.add(border);

        // Grey centre dot
        const dotGeo = new THREE.CircleGeometry(3.5, 16);
        const dotMat = new THREE.MeshBasicMaterial({
          color: 0x334455,
          transparent: true,
          opacity: 0.22,
          side: THREE.DoubleSide,
          depthWrite: false,
        });
        const dot = new THREE.Mesh(dotGeo, dotMat);
        dot.rotation.x = -Math.PI / 2;
        dot.position.set(cx, 0.5, cz);
        this.districtMarkerGroup.add(dot);
      }
    }
  }

  private makeDistrictLabel(name: string, color: number): THREE.Sprite {
    const canvas = document.createElement("canvas");
    canvas.width = 256;
    canvas.height = 64;
    const ctx = canvas.getContext("2d")!;
    ctx.clearRect(0, 0, 256, 64);

    // Coloured pill background
    const r = (color >> 16) & 0xff;
    const g = (color >> 8) & 0xff;
    const b = color & 0xff;
    ctx.fillStyle = `rgba(${r}, ${g}, ${b}, 0.65)`;
    ctx.beginPath();
    (ctx as CanvasRenderingContext2D & { roundRect: Function }).roundRect(
      4,
      4,
      248,
      56,
      16,
    );
    ctx.fill();

    ctx.font = "bold 22px Arial, sans-serif";
    ctx.fillStyle = "#ffffff";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(name, 128, 32);

    const tex = new THREE.CanvasTexture(canvas);
    const mat = new THREE.SpriteMaterial({
      map: tex,
      transparent: true,
      depthWrite: false,
    });
    const sprite = new THREE.Sprite(mat);
    sprite.scale.set(42, 10.5, 1);
    return sprite;
  }
}
