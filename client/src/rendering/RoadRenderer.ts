import * as THREE from "three";
import {
  GameStateSnapshot,
  IntersectionData,
  RoadSegmentData,
  SignalUpdate,
} from "../types";

const ROAD_Y = 0.35;
const ROAD_THICKNESS = 0.3;
const LANE_WIDTH = 2.0;

const ROAD_COLOURS: Record<string, number> = {
  HIGHWAY: 0x3a3a4a,
  ARTERIAL: 0x505050,
  COLLECTOR: 0x606060,
  LOCAL: 0x707070,
  PATH: 0x887766,
};

const LIGHT_DIM = 0x222222;
const LIGHT_RED = 0xff0000;
const LIGHT_YELLOW = 0xffaa00;
const LIGHT_GREEN = 0x00ff66;

function computeRoadWidth(roadType: string, lanes: number): number {
  switch (roadType) {
    case "HIGHWAY":
      return lanes * 2 * LANE_WIDTH + 2;
    case "ARTERIAL":
      return lanes * 2 * LANE_WIDTH + 1;
    case "COLLECTOR":
      return lanes * 2 * LANE_WIDTH + 0.5;
    case "LOCAL":
      return lanes * 2 * LANE_WIDTH;
    case "PATH":
      return LANE_WIDTH * 1.5;
    default:
      return lanes * 2 * LANE_WIDTH;
  }
}

function computeShoulderWidth(roadType: string): number {
  switch (roadType) {
    case "HIGHWAY":
      return 1.0;
    case "ARTERIAL":
      return 0.5;
    default:
      return 0;
  }
}

function hasMedianDivider(roadType: string, lanes: number): boolean {
  return roadType === "HIGHWAY" || (roadType === "ARTERIAL" && lanes >= 2);
}

export class RoadRenderer {
  private scene: THREE.Scene;
  private group = new THREE.Group();
  private terrain: number[][] | null = null;
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

    const interRoads = new Map<string, RoadSegmentData[]>();
    for (const road of snapshot.roads) {
      if (!interRoads.has(road.fromId)) interRoads.set(road.fromId, []);
      if (!interRoads.has(road.toId)) interRoads.set(road.toId, []);
      interRoads.get(road.fromId)!.push(road);
      interRoads.get(road.toId)!.push(road);
    }

    const padSizes = new Map<string, number>();
    for (const inter of snapshot.intersections) {
      const cr = interRoads.get(inter.id) ?? [];
      padSizes.set(inter.id, this.computePadSize(cr, inter, interMap));
    }

    const drawn = new Set<string>();
    for (const road of snapshot.roads) {
      const pairKey = [road.fromId, road.toId].sort().join("|");
      if (drawn.has(pairKey)) continue;
      drawn.add(pairKey);
      const from = interMap.get(road.fromId);
      const to = interMap.get(road.toId);
      if (!from || !to) continue;
      this.addRoadSegment(
        from,
        to,
        road,
        padSizes.get(road.fromId) ?? 0,
        padSizes.get(road.toId) ?? 0,
      );
    }

    for (const inter of snapshot.intersections) {
      const cr = interRoads.get(inter.id) ?? [];
      this.addIntersection(inter, cr, interMap);
    }
  }

  private computePadSize(
    connectedRoads: RoadSegmentData[],
    inter: IntersectionData,
    interMap: Map<string, IntersectionData>,
  ): number {
    if (inter.type === "TUNNEL") return 0;
    const neighbors = new Set<string>();
    for (const r of connectedRoads) {
      neighbors.add(r.fromId === inter.id ? r.toId : r.fromId);
    }
    if (neighbors.size <= 1) return 0;
    if (neighbors.size === 2 && inter.type !== "SIGNAL") {
      const [id1, id2] = [...neighbors];
      const n1 = interMap.get(id1),
        n2 = interMap.get(id2);
      if (n1 && n2) {
        const dx1 = n1.x - inter.x,
          dz1 = n1.y - inter.y;
        const dx2 = n2.x - inter.x,
          dz2 = n2.y - inter.y;
        const cross = Math.abs(dx1 * dz2 - dz1 * dx2);
        if (cross < 10 && dx1 * dx2 + dz1 * dz2 < 0) return 0;
      }
      let maxW = 4;
      for (const r of connectedRoads) {
        const w =
          computeRoadWidth(r.roadType, r.lanes) +
          computeShoulderWidth(r.roadType) * 2;
        if (w > maxW) maxW = w;
      }
      return maxW * 0.55;
    }
    let maxW = 6;
    for (const r of connectedRoads) {
      const w =
        computeRoadWidth(r.roadType, r.lanes) +
        computeShoulderWidth(r.roadType) * 2;
      if (w > maxW) maxW = w;
    }
    return maxW * 0.6 + 1;
  }

  private addRoadSegment(
    from: IntersectionData,
    to: IntersectionData,
    road: RoadSegmentData,
    fromPad: number,
    toPad: number,
  ): void {
    const sx = from.x,
      sz = from.y;
    const ex = to.x,
      ez = to.y;
    const dx = ex - sx,
      dz = ez - sz;
    const fullLen = Math.sqrt(dx * dx + dz * dz);
    if (fullLen < 0.1) return;

    const trimFrom = fromPad * 0.5;
    const trimTo = toPad * 0.5;
    const length = Math.max(1, fullLen - trimFrom - trimTo);

    const dirX = dx / fullLen,
      dirZ = dz / fullLen;
    const tsx = sx + dirX * trimFrom,
      tsz = sz + dirZ * trimFrom;
    const tex = ex - dirX * trimTo,
      tez = ez - dirZ * trimTo;
    const midX = (tsx + tex) / 2,
      midZ = (tsz + tez) / 2;

    const rType = road.roadType;
    const lanes = road.lanes;
    const width = computeRoadWidth(rType, lanes);
    const sw = computeShoulderWidth(rType);
    const totalW = width + sw * 2;
    const med = hasMedianDivider(rType, lanes);

    const isBridge = this.isOverWater(sx, sz, ex, ez);
    const bridgeHeight = 2.5;

    if (isBridge) {
      this.renderBridge(
        tsx,
        tsz,
        tex,
        tez,
        dx,
        dz,
        fullLen,
        length,
        totalW,
        width,
        sw,
        rType,
        lanes,
        med,
        bridgeHeight,
      );
    } else {
      const colour = ROAD_COLOURS[rType] ?? 0x707070;
      this.renderRoadSurface(
        midX,
        midZ,
        ROAD_Y,
        totalW,
        length,
        dx,
        dz,
        colour,
      );
      this.renderMarkings(
        tsx,
        tsz,
        tex,
        tez,
        midX,
        midZ,
        ROAD_Y,
        length,
        dx,
        dz,
        fullLen,
        rType,
        lanes,
        width,
        sw,
        med,
      );
    }
  }

  private renderRoadSurface(
    mx: number,
    mz: number,
    y: number,
    w: number,
    len: number,
    dx: number,
    dz: number,
    colour: number,
  ): void {
    const geo = new THREE.BoxGeometry(w, ROAD_THICKNESS, len);
    const mat = new THREE.MeshLambertMaterial({ color: colour });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.receiveShadow = true;
    mesh.position.set(mx, y, mz);
    mesh.rotation.y = Math.atan2(dx, dz);
    this.group.add(mesh);
  }

  private renderBridge(
    tsx: number,
    tsz: number,
    tex: number,
    tez: number,
    dx: number,
    dz: number,
    fullLen: number,
    length: number,
    totalW: number,
    width: number,
    sw: number,
    rType: string,
    lanes: number,
    med: boolean,
    bridgeHeight: number,
  ): void {
    const nx = -dz / fullLen,
      nz = dx / fullLen;
    const dirX = dx / fullLen,
      dirZ = dz / fullLen;
    const deckY = ROAD_Y + bridgeHeight;
    const rampFrac = 0.18; // fraction of length used for each ramp

    // Build one continuous mesh from start to end using vertex interpolation.
    // Segments: rampFrac → flat → (1-rampFrac). Heights interpolated smoothly.
    const SEGS = 20; // subdivisions along bridge length
    const halfW = totalW / 2;
    const th = ROAD_THICKNESS;

    const positions: number[] = [];
    const indices: number[] = [];

    // Height profile: ramp up over first rampFrac, flat in middle, ramp down in last rampFrac
    function heightAt(t: number): number {
      if (t < rampFrac) return ROAD_Y + bridgeHeight * (t / rampFrac);
      if (t > 1 - rampFrac) return ROAD_Y + bridgeHeight * ((1 - t) / rampFrac);
      return deckY;
    }

    // Generate a quad strip: for each segment cross-section, 4 vertices
    //   top-left, top-right, bottom-left, bottom-right
    for (let i = 0; i <= SEGS; i++) {
      const t = i / SEGS;
      // World position along the bridge centre-line
      const cx = tsx + (tex - tsx) * t;
      const cz = tsz + (tez - tsz) * t;
      const topY = heightAt(t);
      const botY = topY - th;

      // Left edge (negative normal direction)
      positions.push(cx - nx * halfW, topY, cz - nz * halfW); // 0: top-left
      positions.push(cx + nx * halfW, topY, cz + nz * halfW); // 1: top-right
      positions.push(cx - nx * halfW, botY, cz - nz * halfW); // 2: bot-left
      positions.push(cx + nx * halfW, botY, cz + nz * halfW); // 3: bot-right
    }

    // Build faces between consecutive cross-sections
    for (let i = 0; i < SEGS; i++) {
      const b = i * 4;
      const n = b + 4;
      // Top face
      indices.push(b, b + 1, n + 1, b, n + 1, n);
      // Bottom face (reversed winding)
      indices.push(b + 2, n + 2, n + 3, b + 2, n + 3, b + 3);
      // Left side
      indices.push(b, n, n + 2, b, n + 2, b + 2);
      // Right side
      indices.push(b + 1, b + 3, n + 3, b + 1, n + 3, n + 1);
    }
    // Start cap
    indices.push(0, 2, 3, 0, 3, 1);
    // End cap
    const last = SEGS * 4;
    indices.push(last, last + 1, last + 3, last, last + 3, last + 2);

    const geo = new THREE.BufferGeometry();
    geo.setAttribute(
      "position",
      new THREE.BufferAttribute(new Float32Array(positions), 3),
    );
    geo.setIndex(indices);
    geo.computeVertexNormals();

    const colour = ROAD_COLOURS[rType] ?? 0x808080;
    const mat = new THREE.MeshLambertMaterial({ color: colour });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.receiveShadow = true;
    mesh.castShadow = true;
    this.group.add(mesh);

    // Lane markings on flat deck section
    const flatMidX = (tsx + tex) / 2;
    const flatMidZ = (tsz + tez) / 2;
    const flatLen = length * (1 - 2 * rampFrac);
    this.renderMarkings(
      tsx,
      tsz,
      tex,
      tez,
      flatMidX,
      flatMidZ,
      deckY,
      flatLen,
      dx,
      dz,
      fullLen,
      rType,
      lanes,
      width,
      sw,
      med,
    );

    // Railings — placed as thin vertical boxes riding the height profile
    for (const side of [-1, 1]) {
      const railOff = (halfW + 0.15) * side;
      const railPositions: number[] = [];
      const railIndices: number[] = [];
      for (let i = 0; i <= SEGS; i++) {
        const t = i / SEGS;
        const cx = tsx + (tex - tsx) * t;
        const cz = tsz + (tez - tsz) * t;
        const topY = heightAt(t);
        railPositions.push(
          cx + nx * railOff - dirX * 0.15,
          topY + 1.0,
          cz + nz * railOff - dirZ * 0.15,
          cx + nx * railOff + dirX * 0.15,
          topY + 1.0,
          cz + nz * railOff + dirZ * 0.15,
          cx + nx * railOff - dirX * 0.15,
          topY,
          cz + nz * railOff - dirZ * 0.15,
          cx + nx * railOff + dirX * 0.15,
          topY,
          cz + nz * railOff + dirZ * 0.15,
        );
        if (i < SEGS) {
          const rb = i * 4,
            rn = rb + 4;
          railIndices.push(rb, rn, rn + 1, rb, rn + 1, rb + 1);
          railIndices.push(rb + 2, rb + 3, rn + 3, rb + 2, rn + 3, rn + 2);
          railIndices.push(rb, rb + 2, rn + 2, rb, rn + 2, rn);
          railIndices.push(rb + 1, rn + 1, rn + 3, rb + 1, rn + 3, rb + 3);
        }
      }
      const railGeo = new THREE.BufferGeometry();
      railGeo.setAttribute(
        "position",
        new THREE.BufferAttribute(new Float32Array(railPositions), 3),
      );
      railGeo.setIndex(railIndices);
      railGeo.computeVertexNormals();
      const railMesh = new THREE.Mesh(
        railGeo,
        new THREE.MeshLambertMaterial({ color: 0x999999 }),
      );
      railMesh.castShadow = true;
      this.group.add(railMesh);
    }

    // Support pillars under the flat section
    const pillarCount = Math.max(1, Math.floor(flatLen / 30));
    for (let i = 0; i <= pillarCount; i++) {
      const t = rampFrac + (1 - 2 * rampFrac) * (i / Math.max(pillarCount, 1));
      const px = tsx + (tex - tsx) * t;
      const pz = tsz + (tez - tsz) * t;
      for (const side of [-0.35, 0.35]) {
        const pilH = deckY + 0.5;
        const pilGeo = new THREE.BoxGeometry(0.8, pilH, 0.8);
        const pil = new THREE.Mesh(
          pilGeo,
          new THREE.MeshLambertMaterial({ color: 0x888888 }),
        );
        pil.position.set(
          px + nx * totalW * side,
          pilH / 2 - 0.5,
          pz + nz * totalW * side,
        );
        pil.castShadow = true;
        this.group.add(pil);
      }
    }
  }

  private renderMarkings(
    tsx: number,
    tsz: number,
    tex: number,
    tez: number,
    midX: number,
    midZ: number,
    roadY: number,
    len: number,
    dx: number,
    dz: number,
    fullLen: number,
    rType: string,
    lanes: number,
    width: number,
    sw: number,
    med: boolean,
  ): void {
    const nx = -dz / fullLen,
      nz = dx / fullLen;
    const markY = roadY + ROAD_THICKNESS / 2 + 0.04;

    if (rType === "HIGHWAY" || rType === "ARTERIAL") {
      for (const off of [-0.12, 0.12]) {
        const cGeo = new THREE.BoxGeometry(0.1, 0.04, len * 0.92);
        const cMat = new THREE.MeshBasicMaterial({ color: 0xddaa00 });
        const cLine = new THREE.Mesh(cGeo, cMat);
        cLine.position.set(midX + nx * off, markY, midZ + nz * off);
        cLine.rotation.y = Math.atan2(dx, dz);
        this.group.add(cLine);
      }
    } else if (rType !== "PATH") {
      const dashCount = Math.max(1, Math.floor(len / 5));
      const dashLen = (len * 0.85) / dashCount;
      for (let i = 0; i < dashCount; i += 2) {
        const t = (i + 0.5) / dashCount;
        const dpx = tsx + (tex - tsx) * t,
          dpz = tsz + (tez - tsz) * t;
        const dGeo = new THREE.BoxGeometry(0.1, 0.04, dashLen * 0.55);
        const dMat = new THREE.MeshBasicMaterial({ color: 0xeeeeee });
        const dash = new THREE.Mesh(dGeo, dMat);
        dash.position.set(dpx, markY, dpz);
        dash.rotation.y = Math.atan2(dx, dz);
        this.group.add(dash);
      }
    }

    if (lanes > 1 && rType !== "PATH") {
      const medOff = med ? 0.5 : 0;
      for (let l = 1; l < lanes; l++) {
        const laneOff = LANE_WIDTH * l + medOff;
        for (const side of [-1, 1]) {
          const lGeo = new THREE.BoxGeometry(0.08, 0.04, len * 0.85);
          const lMat = new THREE.MeshBasicMaterial({ color: 0xdddddd });
          const lLine = new THREE.Mesh(lGeo, lMat);
          lLine.position.set(
            midX + nx * laneOff * side,
            markY,
            midZ + nz * laneOff * side,
          );
          lLine.rotation.y = Math.atan2(dx, dz);
          this.group.add(lLine);
        }
      }
    }

    if (med && rType === "HIGHWAY") {
      const medGeo = new THREE.BoxGeometry(0.5, 0.2, len);
      const medMat = new THREE.MeshLambertMaterial({ color: 0x555555 });
      const median = new THREE.Mesh(medGeo, medMat);
      median.position.set(midX, roadY + 0.1, midZ);
      median.rotation.y = Math.atan2(dx, dz);
      this.group.add(median);
    }

    if (rType !== "PATH") {
      const totalW = width + sw * 2;
      const edgeOff = totalW / 2 - 0.1;
      for (const side of [-1, 1]) {
        const eGeo = new THREE.BoxGeometry(0.1, 0.04, len * 0.95);
        const eMat = new THREE.MeshBasicMaterial({ color: 0xeeeeee });
        const eLine = new THREE.Mesh(eGeo, eMat);
        eLine.position.set(
          midX + nx * edgeOff * side,
          markY,
          midZ + nz * edgeOff * side,
        );
        eLine.rotation.y = Math.atan2(dx, dz);
        this.group.add(eLine);
      }
    }

    if (sw > 0) {
      for (const side of [-1, 1]) {
        const sOff = (width / 2 + sw * 0.5) * side;
        const sGeo = new THREE.BoxGeometry(sw, ROAD_THICKNESS * 0.8, len);
        const sMat = new THREE.MeshLambertMaterial({ color: 0x888880 });
        const shoulder = new THREE.Mesh(sGeo, sMat);
        shoulder.position.set(midX + nx * sOff, roadY - 0.02, midZ + nz * sOff);
        shoulder.rotation.y = Math.atan2(dx, dz);
        this.group.add(shoulder);
      }
    }
  }

  private isOverWater(sx: number, sz: number, ex: number, ez: number): boolean {
    if (!this.terrain) return false;
    const W = this.terrain.length,
      D = this.terrain[0]?.length ?? 0;
    const steps = Math.max(
      3,
      Math.floor(Math.sqrt((ex - sx) ** 2 + (ez - sz) ** 2) / 16),
    );
    let waterCount = 0;
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      const gx = Math.floor((sx + (ex - sx) * t) / 16);
      const gz = Math.floor((sz + (ez - sz) * t) / 16);
      for (const [ddx, ddz] of [
        [0, 0],
        [1, 0],
        [-1, 0],
        [0, 1],
        [0, -1],
      ] as const) {
        const cx = gx + ddx,
          cz = gz + ddz;
        if (
          cx >= 0 &&
          cx < W &&
          cz >= 0 &&
          cz < D &&
          this.terrain[cx][cz] === 0
        ) {
          waterCount++;
          break;
        }
      }
    }
    return waterCount >= 2;
  }

  private addIntersection(
    inter: IntersectionData,
    connectedRoads: RoadSegmentData[],
    interMap: Map<string, IntersectionData>,
  ): void {
    const isSignal = inter.type === "SIGNAL";
    const isTunnel = inter.type === "TUNNEL";
    const x = inter.x,
      z = inter.y;

    const neighbors = new Set<string>();
    for (const r of connectedRoads) {
      neighbors.add(r.fromId === inter.id ? r.toId : r.fromId);
    }

    if (neighbors.size <= 1 && !isTunnel && !isSignal) return;

    if (neighbors.size === 2 && !isTunnel && !isSignal) {
      const [id1, id2] = [...neighbors];
      const n1 = interMap.get(id1),
        n2 = interMap.get(id2);
      if (n1 && n2) {
        const dx1 = n1.x - x,
          dz1 = n1.y - z;
        const dx2 = n2.x - x,
          dz2 = n2.y - z;
        const cross = Math.abs(dx1 * dz2 - dz1 * dx2);
        if (cross < 10 && dx1 * dx2 + dz1 * dz2 < 0) return;

        this.renderBendCurve(x, z, n1, n2, connectedRoads);
        return;
      }
    }

    if (isTunnel) {
      this.renderTunnel(x, z);
      return;
    }

    const roadDirs: { dx: number; dz: number; width: number }[] = [];
    let maxW = 6;
    for (const r of connectedRoads) {
      const otherId = r.fromId === inter.id ? r.toId : r.fromId;
      const other = interMap.get(otherId);
      if (!other) continue;
      const ddx = other.x - x,
        ddz = other.y - z;
      const len = Math.sqrt(ddx * ddx + ddz * ddz);
      if (len < 0.1) continue;
      const w =
        computeRoadWidth(r.roadType, r.lanes) +
        computeShoulderWidth(r.roadType) * 2;
      roadDirs.push({ dx: ddx / len, dz: ddz / len, width: w });
      if (w > maxW) maxW = w;
    }

    const padSize = maxW * 0.6 + 1;
    const padGeo = new THREE.BoxGeometry(padSize, ROAD_THICKNESS, padSize);
    const padMat = new THREE.MeshLambertMaterial({ color: 0x555555 });
    const pad = new THREE.Mesh(padGeo, padMat);
    pad.position.set(x, ROAD_Y, z);
    pad.receiveShadow = true;
    this.group.add(pad);

    for (const rd of roadDirs) {
      const flareLen = padSize * 0.5;
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

    this.renderIntersectionControl(inter, x, z, padSize, roadDirs);
  }

  private renderBendCurve(
    x: number,
    z: number,
    n1: IntersectionData,
    n2: IntersectionData,
    connectedRoads: RoadSegmentData[],
  ): void {
    let maxW = 4;
    let rType = "LOCAL";
    for (const r of connectedRoads) {
      const w =
        computeRoadWidth(r.roadType, r.lanes) +
        computeShoulderWidth(r.roadType) * 2;
      if (w > maxW) {
        maxW = w;
        rType = r.roadType;
      }
    }
    const colour = ROAD_COLOURS[rType] ?? 0x707070;

    const d1x = n1.x - x,
      d1z = n1.y - z;
    const d2x = n2.x - x,
      d2z = n2.y - z;
    const l1 = Math.sqrt(d1x * d1x + d1z * d1z);
    const l2 = Math.sqrt(d2x * d2x + d2z * d2z);
    if (l1 < 1 || l2 < 1) return;

    const arcSegments = 8;
    const radius = maxW * 0.5;
    const p0x = x + (d1x / l1) * radius,
      p0z = z + (d1z / l1) * radius;
    const p2x = x + (d2x / l2) * radius,
      p2z = z + (d2z / l2) * radius;
    const p1x = x,
      p1z = z;

    for (let i = 0; i < arcSegments; i++) {
      const t0 = i / arcSegments,
        t1 = (i + 1) / arcSegments;
      const ax =
        (1 - t0) * (1 - t0) * p0x + 2 * (1 - t0) * t0 * p1x + t0 * t0 * p2x;
      const az =
        (1 - t0) * (1 - t0) * p0z + 2 * (1 - t0) * t0 * p1z + t0 * t0 * p2z;
      const bx =
        (1 - t1) * (1 - t1) * p0x + 2 * (1 - t1) * t1 * p1x + t1 * t1 * p2x;
      const bz =
        (1 - t1) * (1 - t1) * p0z + 2 * (1 - t1) * t1 * p1z + t1 * t1 * p2z;
      const segDx = bx - ax,
        segDz = bz - az;
      const segLen = Math.sqrt(segDx * segDx + segDz * segDz);
      if (segLen < 0.01) continue;

      const segGeo = new THREE.BoxGeometry(maxW, ROAD_THICKNESS, segLen + 0.3);
      const segMat = new THREE.MeshLambertMaterial({ color: colour });
      const seg = new THREE.Mesh(segGeo, segMat);
      seg.position.set((ax + bx) / 2, ROAD_Y, (az + bz) / 2);
      seg.rotation.y = Math.atan2(segDx, segDz);
      seg.receiveShadow = true;
      this.group.add(seg);
    }

    const padGeo = new THREE.BoxGeometry(
      maxW * 0.7,
      ROAD_THICKNESS,
      maxW * 0.7,
    );
    const padMat = new THREE.MeshLambertMaterial({ color: colour });
    const pad = new THREE.Mesh(padGeo, padMat);
    pad.position.set(x, ROAD_Y, z);
    pad.receiveShadow = true;
    this.group.add(pad);
  }

  private renderTunnel(x: number, z: number): void {
    const tunnelW = 12,
      tunnelH = 10,
      tunnelD = 14;
    const sW = tunnelW + 6,
      sH = tunnelH + 6,
      sD = tunnelD + 3;

    const surGeo = new THREE.BoxGeometry(sW, sH, sD);
    const surMat = new THREE.MeshLambertMaterial({ color: 0x6b6358 });
    const sur = new THREE.Mesh(surGeo, surMat);
    sur.position.set(x, sH / 2, z);
    sur.castShadow = true;
    this.group.add(sur);

    const aR = tunnelW / 2;
    const aGeo = new THREE.CylinderGeometry(
      aR,
      aR,
      tunnelD + 2,
      12,
      1,
      false,
      0,
      Math.PI,
    );
    const aMat = new THREE.MeshLambertMaterial({ color: 0x111111 });
    const arch = new THREE.Mesh(aGeo, aMat);
    arch.rotation.x = Math.PI / 2;
    arch.rotation.z = Math.PI / 2;
    arch.position.set(x, tunnelH - aR + 1, z);
    this.group.add(arch);

    const oH = tunnelH - aR;
    const oGeo = new THREE.BoxGeometry(tunnelW, oH, tunnelD + 2);
    const oMat = new THREE.MeshLambertMaterial({ color: 0x111111 });
    const open = new THREE.Mesh(oGeo, oMat);
    open.position.set(x, oH / 2, z);
    this.group.add(open);

    const rGeo = new THREE.BoxGeometry(tunnelW - 1, 0.3, tunnelD + 5);
    const rMat = new THREE.MeshLambertMaterial({ color: 0x505050 });
    const rd = new THREE.Mesh(rGeo, rMat);
    rd.position.set(x, ROAD_Y, z);
    rd.receiveShadow = true;
    this.group.add(rd);
  }

  private renderIntersectionControl(
    inter: IntersectionData,
    x: number,
    z: number,
    padSize: number,
    roadDirs: { dx: number; dz: number; width: number }[],
  ): void {
    if (inter.type === "STOP") {
      const markY = ROAD_Y + ROAD_THICKNESS / 2 + 0.04;
      for (const rd of roadDirs) {
        const perpX = -rd.dz,
          perpZ = rd.dx;
        const dist = padSize * 0.5 + 0.5;
        const sideOff = rd.width * 0.5 + 0.8;
        const sx = x - rd.dx * dist + perpX * sideOff;
        const sz = z - rd.dz * dist + perpZ * sideOff;

        const pGeo = new THREE.CylinderGeometry(0.1, 0.1, 4.5, 5);
        const pMat = new THREE.MeshLambertMaterial({ color: 0x888888 });
        const pole = new THREE.Mesh(pGeo, pMat);
        pole.position.set(sx, ROAD_Y + 2.25, sz);
        pole.castShadow = true;
        this.group.add(pole);

        const sGeo = new THREE.CylinderGeometry(0.6, 0.6, 0.15, 8);
        const sMat = new THREE.MeshLambertMaterial({ color: 0xcc0000 });
        const sign = new THREE.Mesh(sGeo, sMat);
        sign.position.set(sx, ROAD_Y + 5, sz);
        sign.rotation.x = Math.PI / 2;
        sign.castShadow = true;
        this.group.add(sign);

        const lGeo = new THREE.BoxGeometry(rd.width * 0.65, 0.05, 0.3);
        const lMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
        const line = new THREE.Mesh(lGeo, lMat);
        line.position.set(
          x - rd.dx * padSize * 0.35,
          markY,
          z - rd.dz * padSize * 0.35,
        );
        line.rotation.y = Math.atan2(rd.dx, rd.dz);
        this.group.add(line);
      }
    }

    if (inter.type === "YIELD") {
      for (const rd of roadDirs) {
        const perpX = -rd.dz,
          perpZ = rd.dx;
        const dist = padSize * 0.5 + 0.5;
        const sideOff = rd.width * 0.5 + 0.8;
        const sx = x - rd.dx * dist + perpX * sideOff;
        const sz = z - rd.dz * dist + perpZ * sideOff;

        const pGeo = new THREE.CylinderGeometry(0.1, 0.1, 4.5, 5);
        const pMat = new THREE.MeshLambertMaterial({ color: 0x888888 });
        const pole = new THREE.Mesh(pGeo, pMat);
        pole.position.set(sx, ROAD_Y + 2.25, sz);
        pole.castShadow = true;
        this.group.add(pole);

        const sGeo = new THREE.ConeGeometry(0.5, 0.9, 3);
        const sMat = new THREE.MeshLambertMaterial({ color: 0xffffff });
        const sign = new THREE.Mesh(sGeo, sMat);
        sign.rotation.z = Math.PI;
        sign.position.set(sx, ROAD_Y + 5, sz);
        sign.castShadow = true;
        this.group.add(sign);
      }
    }

    if (inter.type === "SIGNAL") {
      const sigPos: { ox: number; oz: number; ry: number }[] = [];
      for (const rd of roadDirs) {
        const perpX = -rd.dz,
          perpZ = rd.dx;
        const dist = padSize * 0.5 + 0.5;
        const sideOff = rd.width * 0.5 + 0.8;
        sigPos.push({
          ox: -rd.dx * dist + perpX * sideOff,
          oz: -rd.dz * dist + perpZ * sideOff,
          ry: Math.atan2(rd.dx, rd.dz),
        });
      }
      const poles =
        sigPos.length > 2
          ? [sigPos[0], sigPos[Math.floor(sigPos.length / 2)]]
          : sigPos.length > 0
            ? sigPos
            : [
                { ox: 3, oz: 3, ry: 0 },
                { ox: -3, oz: -3, ry: Math.PI },
              ];

      for (const sp of poles) {
        const pGeo = new THREE.CylinderGeometry(0.15, 0.2, 7, 6);
        const pMat = new THREE.MeshLambertMaterial({ color: 0x333333 });
        const pole = new THREE.Mesh(pGeo, pMat);
        pole.position.set(x + sp.ox, ROAD_Y + 3.5, z + sp.oz);
        pole.castShadow = true;
        this.group.add(pole);

        const hGeo = new THREE.BoxGeometry(0.7, 2.4, 0.7);
        const hMat = new THREE.MeshLambertMaterial({ color: 0x222222 });
        const head = new THREE.Mesh(hGeo, hMat);
        head.position.set(x + sp.ox, ROAD_Y + 8, z + sp.oz);
        head.castShadow = true;
        this.group.add(head);
      }

      const fp = poles[0];
      const lights: THREE.Mesh[] = [];
      for (let i = 0; i < 3; i++) {
        const lGeo = new THREE.SphereGeometry(0.25, 8, 6);
        const lMat = new THREE.MeshBasicMaterial({ color: LIGHT_DIM });
        const light = new THREE.Mesh(lGeo, lMat);
        light.position.set(x + fp.ox, ROAD_Y + 9 - i * 0.8, z + fp.oz + 0.4);
        this.group.add(light);
        lights.push(light);
      }
      this.signalLights.set(inter.id, lights);
      this.applySignalState(inter.id, inter.signalState);
    }
  }

  updateSignals(signals: SignalUpdate[]): void {
    for (const s of signals) this.applySignalState(s.intersectionId, s.state);
  }

  private applySignalState(id: string, state: string): void {
    const lights = this.signalLights.get(id);
    if (!lights || lights.length !== 3) return;
    const [red, yellow, green] = lights;
    const set = (m: THREE.Mesh, c: number) =>
      (m.material as THREE.MeshBasicMaterial).color.setHex(c);
    set(red, LIGHT_DIM);
    set(yellow, LIGHT_DIM);
    set(green, LIGHT_DIM);
    if (state.startsWith("RED")) set(red, LIGHT_RED);
    else if (state.startsWith("YELLOW")) set(yellow, LIGHT_YELLOW);
    else if (state.startsWith("GREEN")) set(green, LIGHT_GREEN);
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
