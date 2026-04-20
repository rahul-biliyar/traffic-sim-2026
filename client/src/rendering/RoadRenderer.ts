import * as THREE from "three";
import {
  GameStateSnapshot,
  IntersectionData,
  RoadSegmentData,
  SignalUpdate,
} from "../types";

const ROAD_Y = 0.35; // comfortably above flat terrain (elev=0)
const ROAD_THICKNESS = 0.3;

/** Road surface colour by type. */
const ROAD_COLOURS: Record<string, number> = {
  HIGHWAY: 0x3a3a4a,
  ARTERIAL: 0x505050,
  COLLECTOR: 0x606060,
  LOCAL: 0x707070,
  PATH: 0x887766,
};

/** Dim colour for inactive signal lights. */
const LIGHT_DIM = 0x222222;
const LIGHT_RED = 0xff0000;
const LIGHT_YELLOW = 0xffaa00;
const LIGHT_GREEN = 0x00ff66;

/**
 * Renders the road network as solid 3D strips with markings, intersection pads,
 * and traffic lights whose colours update each tick.
 */
export class RoadRenderer {
  private scene: THREE.Scene;
  private group = new THREE.Group();
  private terrain: number[][] | null = null;

  /** Map of intersection ID → [red, yellow, green] light meshes. */
  private signalLights = new Map<string, THREE.Mesh[]>();

  constructor(scene: THREE.Scene) {
    this.scene = scene;
    this.scene.add(this.group);
  }

  build(snapshot: GameStateSnapshot): void {
    this.clear();
    this.terrain = snapshot.terrain;

    const interMap = new Map<string, IntersectionData>();
    for (const i of snapshot.intersections) interMap.set(i.id, i);

    // Build map of intersection → connected roads for adaptive shapes
    const interRoads = new Map<string, RoadSegmentData[]>();
    for (const road of snapshot.roads) {
      if (!interRoads.has(road.fromId)) interRoads.set(road.fromId, []);
      if (!interRoads.has(road.toId)) interRoads.set(road.toId, []);
      interRoads.get(road.fromId)!.push(road);
      interRoads.get(road.toId)!.push(road);
    }

    // Pre-compute intersection pad sizes so road segments can be trimmed
    const padSizes = new Map<string, number>();
    for (const inter of snapshot.intersections) {
      const connectedRoads = interRoads.get(inter.id) ?? [];
      padSizes.set(
        inter.id,
        this.computePadSize(connectedRoads, inter, interMap),
      );
    }

    // Avoid drawing both directions of bi-directional roads
    const drawn = new Set<string>();

    for (const road of snapshot.roads) {
      const pairKey = [road.fromId, road.toId].sort().join("|");
      if (drawn.has(pairKey)) continue;
      drawn.add(pairKey);

      const from = interMap.get(road.fromId);
      const to = interMap.get(road.toId);
      if (!from || !to) continue;

      const fromPad = padSizes.get(road.fromId) ?? 0;
      const toPad = padSizes.get(road.toId) ?? 0;
      this.addRoadSegment(from, to, road, fromPad, toPad);
    }

    for (const inter of snapshot.intersections) {
      const connectedRoads = interRoads.get(inter.id) ?? [];
      this.addIntersection(inter, connectedRoads, interMap);
    }
  }

  private computePadSize(
    connectedRoads: RoadSegmentData[],
    inter: IntersectionData,
    interMap: Map<string, IntersectionData>,
  ): number {
    if (inter.type === "TUNNEL") return 0;
    // Count unique neighbors: 2-way nodes are bends, not junctions — no pad
    const uniqueNeighbors = new Set<string>();
    for (const road of connectedRoads) {
      const otherId = road.fromId === inter.id ? road.toId : road.fromId;
      uniqueNeighbors.add(otherId);
    }
    if (uniqueNeighbors.size <= 2 && inter.type !== "SIGNAL") return 0;

    const laneWidth = 3.7;
    let maxWidth = 8;
    for (const road of connectedRoads) {
      let w: number;
      switch (road.roadType) {
        case "HIGHWAY":
          w = road.lanes * 2 * laneWidth + 3 + 4;
          break;
        case "ARTERIAL":
          w = road.lanes * 2 * laneWidth + 1.5 + 2;
          break;
        case "COLLECTOR":
          w = road.lanes * 2 * laneWidth + 1 + 1;
          break;
        case "LOCAL":
          w = road.lanes * 2 * laneWidth;
          break;
        default:
          w = laneWidth * 1.5;
      }
      if (w > maxWidth) maxWidth = w;
    }
    return maxWidth * 0.7 + 2;
  }

  /* ── private helpers ─────────────── */

  private addRoadSegment(
    from: IntersectionData,
    to: IntersectionData,
    road: RoadSegmentData,
    fromPad: number,
    toPad: number,
  ): void {
    const sx = from.x,
      sz = from.y; // server y → Three z
    const ex = to.x,
      ez = to.y;

    const dx = ex - sx;
    const dz = ez - sz;
    const fullLength = Math.sqrt(dx * dx + dz * dz);
    if (fullLength < 0.1) return;

    // Trim road segment so it doesn't overlap intersection pads
    const trimFrom = fromPad * 0.5;
    const trimTo = toPad * 0.5;
    const length = Math.max(1, fullLength - trimFrom - trimTo);
    const trimFrac = trimFrom / fullLength;

    // Check if this road crosses water cells → render as bridge
    const isBridge = this.isOverWater(sx, sz, ex, ez);
    const bridgeHeight = 2.5;
    const roadY = isBridge ? ROAD_Y + bridgeHeight : ROAD_Y;

    // Trimmed segment endpoints (inset from intersection centers)
    const dirX = dx / fullLength;
    const dirZ = dz / fullLength;
    const tsx = sx + dirX * trimFrom;
    const tsz = sz + dirZ * trimFrom;
    const tex = ex - dirX * (toPad * 0.5);
    const tez = ez - dirZ * (toPad * 0.5);
    const midX = (tsx + tex) / 2;
    const midZ = (tsz + tez) / 2;

    // US-standard road widths by type
    const roadType = road.roadType;
    const lanes = road.lanes;
    const laneWidth = 3.7; // US standard lane width in game units
    let width: number;
    let hasMedian = false;
    let shoulderWidth = 0;
    switch (roadType) {
      case "HIGHWAY":
        width = lanes * 2 * laneWidth + 3; // both directions + median
        hasMedian = true;
        shoulderWidth = 2;
        break;
      case "ARTERIAL":
        width = lanes * 2 * laneWidth + 1.5; // both directions + narrow median
        hasMedian = lanes >= 2;
        shoulderWidth = 1;
        break;
      case "COLLECTOR":
        width = lanes * 2 * laneWidth + 1;
        shoulderWidth = 0.5;
        break;
      case "LOCAL":
        width = lanes * 2 * laneWidth;
        break;
      case "PATH":
        width = laneWidth * 1.5; // narrow unpaved
        break;
      default:
        width = lanes * 5 + 2;
    }

    const geo = new THREE.BoxGeometry(
      width + shoulderWidth * 2,
      ROAD_THICKNESS,
      length,
    );
    const colour = isBridge ? 0x909090 : (ROAD_COLOURS[roadType] ?? 0x707070);
    const mat = new THREE.MeshLambertMaterial({ color: colour });

    const mesh = new THREE.Mesh(geo, mat);
    mesh.receiveShadow = true;
    mesh.position.set(midX, roadY, midZ);
    mesh.rotation.y = Math.atan2(dx, dz);
    this.group.add(mesh);

    // ── Bridge ramps ──
    if (isBridge) {
      const rampLen = 16;
      for (const sign of [-1, 1]) {
        const cx2 = sign === -1 ? tsx : tex;
        const cz2 = sign === -1 ? tsz : tez;
        const rampGeo = new THREE.BoxGeometry(
          width + shoulderWidth * 2,
          ROAD_THICKNESS,
          rampLen,
        );
        const rampMat = new THREE.MeshLambertMaterial({ color: 0x808080 });
        const ramp = new THREE.Mesh(rampGeo, rampMat);
        ramp.receiveShadow = true;
        ramp.position.set(
          cx2 - dirX * rampLen * 0.5 * sign,
          ROAD_Y + bridgeHeight / 2,
          cz2 - dirZ * rampLen * 0.5 * sign,
        );
        ramp.rotation.y = Math.atan2(dx, dz);
        ramp.rotation.x = sign * Math.atan2(bridgeHeight, rampLen);
        this.group.add(ramp);
      }
    }

    const nx = -dz / fullLength;
    const nz = dx / fullLength;
    const markY = roadY + ROAD_THICKNESS / 2 + 0.04;

    // ── Center line (varies by road type) ──
    if (roadType === "HIGHWAY" || roadType === "ARTERIAL") {
      // Double yellow center line (no passing)
      for (const off of [-0.3, 0.3]) {
        const cGeo = new THREE.BoxGeometry(0.2, 0.06, length * 0.92);
        const cMat = new THREE.MeshBasicMaterial({ color: 0xddaa00 });
        const cLine = new THREE.Mesh(cGeo, cMat);
        cLine.position.set(midX + nx * off, markY, midZ + nz * off);
        cLine.rotation.y = Math.atan2(dx, dz);
        this.group.add(cLine);
      }
    } else if (roadType === "PATH") {
      // No center markings for paths
    } else {
      // Dashed white center line
      const dashCount = Math.max(1, Math.floor(length / 8));
      const dashLen = (length * 0.85) / dashCount;
      for (let i = 0; i < dashCount; i++) {
        if (i % 2 === 1) continue; // every other dash
        const t = (i + 0.5) / dashCount;
        const dpx = tsx + (tex - tsx) * t;
        const dpz = tsz + (tez - tsz) * t;
        const dGeo = new THREE.BoxGeometry(0.2, 0.06, dashLen * 0.6);
        const dMat = new THREE.MeshBasicMaterial({ color: 0xeeeeee });
        const dash = new THREE.Mesh(dGeo, dMat);
        dash.position.set(dpx, markY, dpz);
        dash.rotation.y = Math.atan2(dx, dz);
        this.group.add(dash);
      }
    }

    // ── Lane divider lines (between lanes on each side) ──
    if (lanes > 1 && roadType !== "PATH") {
      for (let l = 1; l < lanes; l++) {
        const laneOff = laneWidth * l;
        for (const side of [-1, 1]) {
          const offset = (hasMedian ? 1.5 : 0.5) + laneOff;
          const lGeo = new THREE.BoxGeometry(0.15, 0.05, length * 0.85);
          const lMat = new THREE.MeshBasicMaterial({ color: 0xdddddd });
          const lLine = new THREE.Mesh(lGeo, lMat);
          lLine.position.set(
            midX + nx * offset * side,
            markY,
            midZ + nz * offset * side,
          );
          lLine.rotation.y = Math.atan2(dx, dz);
          this.group.add(lLine);
        }
      }
    }

    // ── Median divider for highways ──
    if (hasMedian && roadType === "HIGHWAY") {
      const medGeo = new THREE.BoxGeometry(1.0, 0.3, length);
      const medMat = new THREE.MeshLambertMaterial({ color: 0x555555 });
      const median = new THREE.Mesh(medGeo, medMat);
      median.position.set(midX, roadY + 0.15, midZ);
      median.rotation.y = Math.atan2(dx, dz);
      this.group.add(median);
    }

    // ── Edge lines (solid white) ──
    if (roadType !== "PATH") {
      const edgeOffset = (width + shoulderWidth * 2) / 2 - 0.3;
      for (const side of [-1, 1]) {
        const eGeo = new THREE.BoxGeometry(0.2, 0.06, length * 0.95);
        const eMat = new THREE.MeshBasicMaterial({ color: 0xeeeeee });
        const edge = new THREE.Mesh(eGeo, eMat);
        edge.position.set(
          midX + nx * edgeOffset * side,
          markY,
          midZ + nz * edgeOffset * side,
        );
        edge.rotation.y = Math.atan2(dx, dz);
        this.group.add(edge);
      }
    }

    // ── Shoulder gravel strips for highways/arterials ──
    if (shoulderWidth > 0.5 && roadType !== "PATH") {
      for (const side of [-1, 1]) {
        const sOff = (width / 2 + shoulderWidth * 0.5) * side;
        const sGeo = new THREE.BoxGeometry(
          shoulderWidth,
          ROAD_THICKNESS * 0.8,
          length,
        );
        const sMat = new THREE.MeshLambertMaterial({ color: 0x888880 });
        const shoulder = new THREE.Mesh(sGeo, sMat);
        shoulder.position.set(midX + nx * sOff, roadY - 0.02, midZ + nz * sOff);
        shoulder.rotation.y = Math.atan2(dx, dz);
        this.group.add(shoulder);
      }
    }

    // Bridge railings and support pillars
    if (isBridge) {
      this.addBridgeDetails(
        tsx,
        tsz,
        tex,
        tez,
        width + shoulderWidth * 2,
        roadY,
        length,
        dx,
        dz,
      );
    }
  }

  /** Check if road segment midpoint is over water terrain */
  private isOverWater(sx: number, sz: number, ex: number, ez: number): boolean {
    if (!this.terrain) return false;
    const W = this.terrain.length;
    const D = this.terrain[0]?.length ?? 0;

    // Sample points along the road and check neighboring cells for water
    const steps = Math.max(
      3,
      Math.floor(Math.sqrt((ex - sx) ** 2 + (ez - sz) ** 2) / 16),
    );
    let waterAdjacentCount = 0;
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      const px = sx + (ex - sx) * t;
      const pz = sz + (ez - sz) * t;
      const gx = Math.floor(px / 16);
      const gz = Math.floor(pz / 16);
      // Check this cell and its 4 neighbors for water
      for (const [dx, dz2] of [
        [0, 0],
        [1, 0],
        [-1, 0],
        [0, 1],
        [0, -1],
      ] as const) {
        const nx = gx + dx;
        const nz = gz + dz2;
        if (nx >= 0 && nx < W && nz >= 0 && nz < D) {
          if (this.terrain[nx][nz] === 0) {
            waterAdjacentCount++;
            break;
          }
        }
      }
    }
    return waterAdjacentCount >= 2;
  }

  /** Add bridge railings and support pillars */
  private addBridgeDetails(
    sx: number,
    sz: number,
    ex: number,
    ez: number,
    width: number,
    roadY: number,
    length: number,
    dx: number,
    dz: number,
  ): void {
    const nx = -dz / Math.sqrt(dx * dx + dz * dz);
    const nz = dx / Math.sqrt(dx * dx + dz * dz);

    // Side railings
    for (const side of [-1, 1]) {
      const railOffset = (width / 2 + 0.3) * side;
      const railGeo = new THREE.BoxGeometry(0.6, 1.5, length);
      const railMat = new THREE.MeshLambertMaterial({ color: 0x888888 });
      const rail = new THREE.Mesh(railGeo, railMat);
      rail.position.set(
        (sx + ex) / 2 + nx * railOffset,
        roadY + 0.75,
        (sz + ez) / 2 + nz * railOffset,
      );
      rail.rotation.y = Math.atan2(dx, dz);
      rail.castShadow = true;
      this.group.add(rail);
    }

    // Support pillars underneath the bridge
    const pillarSpacing = 32;
    const numPillars = Math.max(1, Math.floor(length / pillarSpacing));
    for (let i = 0; i <= numPillars; i++) {
      const t = numPillars === 0 ? 0.5 : i / numPillars;
      const px = sx + dx * t;
      const pz = sz + dz * t;

      for (const side of [-0.3, 0.3]) {
        const pillarGeo = new THREE.BoxGeometry(1.5, roadY + 2, 1.5);
        const pillarMat = new THREE.MeshLambertMaterial({ color: 0x777777 });
        const pillar = new THREE.Mesh(pillarGeo, pillarMat);
        pillar.position.set(
          px + nx * width * side,
          (roadY + 2) / 2 - 2,
          pz + nz * width * side,
        );
        pillar.castShadow = true;
        this.group.add(pillar);
      }
    }
  }

  private addIntersection(
    inter: IntersectionData,
    connectedRoads: RoadSegmentData[],
    interMap: Map<string, IntersectionData>,
  ): void {
    const isSignal = inter.type === "SIGNAL";
    const isTunnel = inter.type === "TUNNEL";
    const x = inter.x;
    const z = inter.y; // server y → Three z

    // Count unique connected nodes to determine true junction degree
    const uniqueNeighbors = new Set<string>();
    for (const road of connectedRoads) {
      const otherId = road.fromId === inter.id ? road.toId : road.fromId;
      uniqueNeighbors.add(otherId);
    }
    // Skip rendering pads for 2-way nodes (bends/straight-throughs) —
    // just let the road segments connect through. Tunnels always render.
    if (uniqueNeighbors.size <= 2 && !isTunnel && !isSignal) return;

    if (isTunnel) {
      // ── Proper tunnel entrance: stone arch carved into mountainside ──
      const tunnelW = 18; // road width through tunnel
      const tunnelH = 16; // arch height
      const tunnelD = 20; // depth into mountain

      // Stone surround (the mountain face around the tunnel)
      const surroundW = tunnelW + 12;
      const surroundH = tunnelH + 10;
      const surroundD = tunnelD + 4;
      const surroundGeo = new THREE.BoxGeometry(
        surroundW,
        surroundH,
        surroundD,
      );
      const surroundMat = new THREE.MeshLambertMaterial({ color: 0x6b6358 });
      const surround = new THREE.Mesh(surroundGeo, surroundMat);
      surround.position.set(x, surroundH / 2, z);
      surround.castShadow = true;
      this.group.add(surround);

      // Arch opening — semi-circular top using cylinder segment
      const archRadius = tunnelW / 2;
      const archGeo = new THREE.CylinderGeometry(
        archRadius,
        archRadius,
        tunnelD + 2,
        16,
        1,
        false,
        0,
        Math.PI,
      );
      const archMat = new THREE.MeshLambertMaterial({ color: 0x111111 });
      const arch = new THREE.Mesh(archGeo, archMat);
      arch.rotation.x = Math.PI / 2;
      arch.rotation.z = Math.PI / 2;
      arch.position.set(x, tunnelH - archRadius + 1, z);
      this.group.add(arch);

      // Rectangular lower opening
      const openH = tunnelH - archRadius;
      const openGeo = new THREE.BoxGeometry(tunnelW, openH, tunnelD + 2);
      const openMat = new THREE.MeshLambertMaterial({ color: 0x111111 });
      const opening = new THREE.Mesh(openGeo, openMat);
      opening.position.set(x, openH / 2, z);
      this.group.add(opening);

      // Stone keystone accent at arch top
      const keystoneGeo = new THREE.BoxGeometry(4, 3, tunnelD / 2);
      const keystoneMat = new THREE.MeshLambertMaterial({ color: 0x554a3f });
      const keystone = new THREE.Mesh(keystoneGeo, keystoneMat);
      keystone.position.set(x, tunnelH + 1, z);
      keystone.castShadow = true;
      this.group.add(keystone);

      // Road surface through tunnel
      const roadGeo = new THREE.BoxGeometry(tunnelW - 2, 0.3, tunnelD + 8);
      const roadMat = new THREE.MeshLambertMaterial({ color: 0x505050 });
      const road = new THREE.Mesh(roadGeo, roadMat);
      road.position.set(x, ROAD_Y, z);
      road.receiveShadow = true;
      this.group.add(road);
      return;
    }

    // Adaptive intersection pad — compute size from connected road widths
    const laneWidth = 3.7;
    let maxWidth = 8; // minimum pad size
    const roadDirs: { dx: number; dz: number; width: number }[] = [];

    for (const road of connectedRoads) {
      // Find the other end of this road
      const otherId = road.fromId === inter.id ? road.toId : road.fromId;
      const other = interMap.get(otherId);
      if (!other) continue;

      const ox = other.x;
      const oz = other.y;
      const ddx = ox - x;
      const ddz = oz - z;
      const len = Math.sqrt(ddx * ddx + ddz * ddz);
      if (len < 0.1) continue;

      let w: number;
      switch (road.roadType) {
        case "HIGHWAY":
          w = road.lanes * 2 * laneWidth + 3 + 4;
          break;
        case "ARTERIAL":
          w = road.lanes * 2 * laneWidth + 1.5 + 2;
          break;
        case "COLLECTOR":
          w = road.lanes * 2 * laneWidth + 1 + 1;
          break;
        case "LOCAL":
          w = road.lanes * 2 * laneWidth;
          break;
        default:
          w = laneWidth * 1.5;
      }

      roadDirs.push({ dx: ddx / len, dz: ddz / len, width: w });
      if (w > maxWidth) maxWidth = w;
    }

    // Use box geometry for intersection pad to smoothly cover all road widths
    const padSize = maxWidth * 0.7 + 2;
    const padGeo = new THREE.BoxGeometry(padSize, ROAD_THICKNESS, padSize);
    const padMat = new THREE.MeshLambertMaterial({ color: 0x555555 });
    const pad = new THREE.Mesh(padGeo, padMat);
    pad.position.set(x, ROAD_Y, z);
    pad.receiveShadow = true;
    this.group.add(pad);

    // Add road-width transition flares for each connected road
    for (const rd of roadDirs) {
      const flareLen = padSize * 0.6;
      const flareGeo = new THREE.BoxGeometry(
        rd.width,
        ROAD_THICKNESS,
        flareLen,
      );
      const flareMat = new THREE.MeshLambertMaterial({ color: 0x555555 });
      const flare = new THREE.Mesh(flareGeo, flareMat);
      flare.position.set(
        x + rd.dx * flareLen * 0.5,
        ROAD_Y,
        z + rd.dz * flareLen * 0.5,
      );
      flare.rotation.y = Math.atan2(rd.dx, rd.dz);
      flare.receiveShadow = true;
      this.group.add(flare);
    }

    const isStop = inter.type === "STOP";
    const isYield = inter.type === "YIELD";

    if (isStop) {
      // ── STOP sign: red octagonal sign on a pole ──
      for (const corner of [
        [4.5, 4.5],
        [-4.5, -4.5],
      ]) {
        const poleGeo = new THREE.CylinderGeometry(0.15, 0.15, 8, 5);
        const poleMat = new THREE.MeshLambertMaterial({ color: 0x888888 });
        const pole = new THREE.Mesh(poleGeo, poleMat);
        pole.position.set(x + corner[0], ROAD_Y + 4, z + corner[1]);
        pole.castShadow = true;
        this.group.add(pole);

        // Red sign face
        const signGeo = new THREE.CylinderGeometry(1.2, 1.2, 0.3, 8);
        const signMat = new THREE.MeshLambertMaterial({ color: 0xcc0000 });
        const sign = new THREE.Mesh(signGeo, signMat);
        sign.position.set(x + corner[0], ROAD_Y + 8.5, z + corner[1]);
        sign.castShadow = true;
        this.group.add(sign);

        // White border ring
        const borderGeo = new THREE.TorusGeometry(1.2, 0.12, 4, 8);
        const borderMat = new THREE.MeshLambertMaterial({ color: 0xffffff });
        const border = new THREE.Mesh(borderGeo, borderMat);
        border.position.set(x + corner[0], ROAD_Y + 8.5, z + corner[1]);
        border.rotation.x = Math.PI / 2;
        this.group.add(border);
      }

      // White stop line on road
      const lineGeo = new THREE.BoxGeometry(10, 0.08, 0.6);
      const lineMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
      const line = new THREE.Mesh(lineGeo, lineMat);
      line.position.set(x, ROAD_Y + ROAD_THICKNESS / 2 + 0.04, z + 3);
      this.group.add(line);
    }

    if (isYield) {
      // ── YIELD sign: inverted triangle on a pole ──
      for (const corner of [[4.5, 0]]) {
        const poleGeo = new THREE.CylinderGeometry(0.15, 0.15, 7, 5);
        const poleMat = new THREE.MeshLambertMaterial({ color: 0x888888 });
        const pole = new THREE.Mesh(poleGeo, poleMat);
        pole.position.set(x + corner[0], ROAD_Y + 3.5, z + corner[1]);
        pole.castShadow = true;
        this.group.add(pole);

        // Triangle sign (use cone geometry as approximation)
        const signGeo = new THREE.ConeGeometry(1.0, 1.6, 3);
        const signMat = new THREE.MeshLambertMaterial({ color: 0xffffff });
        const sign = new THREE.Mesh(signGeo, signMat);
        sign.rotation.z = Math.PI; // inverted triangle
        sign.position.set(x + corner[0], ROAD_Y + 8, z + corner[1]);
        sign.castShadow = true;
        this.group.add(sign);

        // Red border (slightly larger cone)
        const borderGeo = new THREE.ConeGeometry(1.25, 1.9, 3);
        const borderMat = new THREE.MeshLambertMaterial({ color: 0xcc0000 });
        const bord = new THREE.Mesh(borderGeo, borderMat);
        bord.rotation.z = Math.PI;
        bord.position.set(x + corner[0], ROAD_Y + 8, z + corner[1] - 0.1);
        this.group.add(bord);
      }
    }

    if (isSignal) {
      // ── Traffic signals: two poles for NS and EW directions ──
      for (const [ox, oz, ry] of [
        [5, 5, 0],
        [-5, -5, Math.PI],
      ] as const) {
        // Pole
        const poleGeo = new THREE.CylinderGeometry(0.25, 0.35, 10, 6);
        const poleMat = new THREE.MeshLambertMaterial({ color: 0x333333 });
        const pole = new THREE.Mesh(poleGeo, poleMat);
        pole.position.set(x + ox, ROAD_Y + 5, z + oz);
        pole.castShadow = true;
        this.group.add(pole);

        // Horizontal arm
        const armGeo = new THREE.BoxGeometry(0.4, 0.4, 4);
        const armMat = new THREE.MeshLambertMaterial({ color: 0x333333 });
        const arm = new THREE.Mesh(armGeo, armMat);
        arm.position.set(x + ox, ROAD_Y + 10, z + oz);
        arm.rotation.y = ry;
        arm.castShadow = true;
        this.group.add(arm);

        // Signal head
        const headGeo = new THREE.BoxGeometry(1.2, 3.6, 1.2);
        const headMat = new THREE.MeshLambertMaterial({ color: 0x222222 });
        const head = new THREE.Mesh(headGeo, headMat);
        head.position.set(x + ox, ROAD_Y + 11.5, z + oz);
        head.castShadow = true;
        this.group.add(head);
      }

      // Shared signal lights (single set of R/Y/G controlling both poles)
      const lights: THREE.Mesh[] = [];
      for (let i = 0; i < 3; i++) {
        const lightGeo = new THREE.SphereGeometry(0.4, 8, 6);
        const lightMat = new THREE.MeshBasicMaterial({ color: LIGHT_DIM });
        const light = new THREE.Mesh(lightGeo, lightMat);
        light.position.set(x + 5, ROAD_Y + 12.8 - i * 1.2, z + 5 + 0.7);
        this.group.add(light);
        lights.push(light);
      }
      this.signalLights.set(inter.id, lights);

      // Set initial state from snapshot
      this.applySignalState(inter.id, inter.signalState);
    }
  }

  /**
   * Update traffic signal light colours from tick delta data.
   */
  updateSignals(signals: SignalUpdate[]): void {
    for (const s of signals) {
      this.applySignalState(s.intersectionId, s.state);
    }
  }

  private applySignalState(intersectionId: string, state: string): void {
    const lights = this.signalLights.get(intersectionId);
    if (!lights || lights.length !== 3) return;

    const [red, yellow, green] = lights;
    const setColor = (mesh: THREE.Mesh, color: number) => {
      (mesh.material as THREE.MeshBasicMaterial).color.setHex(color);
    };

    setColor(red, LIGHT_DIM);
    setColor(yellow, LIGHT_DIM);
    setColor(green, LIGHT_DIM);

    // Server sends GREEN_NS, GREEN_EW, YELLOW_NS, YELLOW_EW, RED_ALL_1, RED_ALL_2
    if (state.startsWith("RED")) {
      setColor(red, LIGHT_RED);
    } else if (state.startsWith("YELLOW")) {
      setColor(yellow, LIGHT_YELLOW);
    } else if (state.startsWith("GREEN")) {
      setColor(green, LIGHT_GREEN);
    }
  }

  private clear(): void {
    this.signalLights.clear();
    while (this.group.children.length > 0) {
      const child = this.group.children[0] as THREE.Mesh;
      this.group.remove(child);
      child.geometry?.dispose();
      if (child.material) {
        if (Array.isArray(child.material))
          child.material.forEach((m) => m.dispose());
        else (child.material as THREE.Material).dispose();
      }
    }
  }
}
