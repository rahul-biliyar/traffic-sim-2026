package com.trafficgame.game.mapgen;

import com.trafficgame.engine.util.Vec2;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.Intersection;
import com.trafficgame.game.model.RoadNetwork;
import com.trafficgame.game.model.RoadSegment;

import java.util.*;

/**
 * Unified-grid road planner.  All roads are axis-aligned (horizontal or
 * vertical), guaranteeing clean 90-degree intersections with no diagonal
 * overlaps.
 *
 * <ol>
 *   <li>Find the buildable area</li>
 *   <li>Lay major arterials across the whole map (evenly spaced)</li>
 *   <li>Fill local roads within each arterial block (density from district)</li>
 *   <li>Mark intersection cells</li>
 *   <li>Build graph by walking between intersections</li>
 *   <li>Prune dead-ends</li>
 * </ol>
 */
public final class RoadPlanner {

    private final Random rng;
    private final double tileSize;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Y-coordinates of horizontal arterial lines (for road-tier upgrades). */
    private final Set<Integer> arterialRows = new HashSet<>();
    /** X-coordinates of vertical arterial lines. */
    private final Set<Integer> arterialCols = new HashSet<>();

    /** Tunnel entry node IDs — must not be pruned. */
    private final Set<String> tunnelNodeIds = new HashSet<>();

    /** Grid positions explicitly placed as tunnel entries by addTunnelEntries. */
    private final Set<Long> tunnelCellPositions = new HashSet<>();

    /** Corner node IDs (2-way 90° bends) — must not be pruned. */
    private final Set<String> cornerNodeIds = new HashSet<>();

    /** Grid positions of corner intersections (2 perpendicular neighbors). */
    private final Set<Long> cornerCellPositions = new HashSet<>();

    public RoadPlanner(int seed) {
        this.rng = new Random(seed);
        this.tileSize = GameConfig.TILE_SIZE;
    }

    public RoadNetwork plan(CellType[][] grid, int gridW, int gridH,
                            int[][] districtGrid,
                            Map<Integer, DistrictTemplate> districtMap,
                            double[][] heightMap) {

        RoadNetwork network = new RoadNetwork();

        // ── Find buildable bounds ────────────────────────────
        int bMinX = gridW, bMinY = gridH, bMaxX = 0, bMaxY = 0;
        for (int x = 0; x < gridW; x++)
            for (int y = 0; y < gridH; y++)
                if (grid[x][y] == CellType.EMPTY) {
                    bMinX = Math.min(bMinX, x);
                    bMinY = Math.min(bMinY, y);
                    bMaxX = Math.max(bMaxX, x);
                    bMaxY = Math.max(bMaxY, y);
                }
        if (bMinX > bMaxX) return network;

        // ── Phase 1: Major arterials ─────────────────────────
        int spH = Math.max(10, (bMaxY - bMinY) / 4);
        int spV = Math.max(10, (bMaxX - bMinX) / 4);

        List<Integer> artH = new ArrayList<>();
        List<Integer> artV = new ArrayList<>();

        for (int y = bMinY + spH; y < bMaxY - 3; y += spH) artH.add(y);
        for (int x = bMinX + spV; x < bMaxX - 3; x += spV) artV.add(x);

        arterialRows.addAll(artH);
        arterialCols.addAll(artV);

        // Lay arterials — only bridge narrow water gaps (≤ 4 cells)
        for (int y : artH)
            layArterialLine(grid, bMinX, bMaxX, y, gridW, gridH, true);
        for (int x : artV)
            layArterialLine(grid, bMinY, bMaxY, x, gridW, gridH, false);

        // ── Phase 2: Local roads within each arterial block ──
        List<Integer> hBounds = new ArrayList<>();
        hBounds.add(bMinY);
        hBounds.addAll(artH);
        hBounds.add(bMaxY);

        List<Integer> vBounds = new ArrayList<>();
        vBounds.add(bMinX);
        vBounds.addAll(artV);
        vBounds.add(bMaxX);

        for (int hi = 0; hi < hBounds.size() - 1; hi++) {
            for (int vi = 0; vi < vBounds.size() - 1; vi++) {
                int by0 = hBounds.get(hi), by1 = hBounds.get(hi + 1);
                int bx0 = vBounds.get(vi), bx1 = vBounds.get(vi + 1);
                int dom = dominantDistrict(districtGrid, bx0, by0, bx1, by1, gridW, gridH);
                DistrictTemplate tmpl = districtMap.getOrDefault(dom, DistrictTemplate.RESIDENTIAL);
                int sp = tmpl.getRoadSpacing();

                // Local horizontal roads (skip if too close to an arterial)
                for (int y = by0 + sp; y < by1; y += sp) {
                    boolean tooClose = false;
                    for (int ay : artH) {
                        if (Math.abs(y - ay) < 3) { tooClose = true; break; }
                    }
                    if (tooClose) continue;
                    for (int x = bx0; x <= bx1; x++)
                        layCell(grid, x, y, gridW, gridH, false);
                }

                // Local vertical roads (skip if too close to an arterial)
                for (int x = bx0 + sp; x < bx1; x += sp) {
                    boolean tooClose = false;
                    for (int ax : artV) {
                        if (Math.abs(x - ax) < 3) { tooClose = true; break; }
                    }
                    if (tooClose) continue;
                    for (int y = by0; y <= by1; y++)
                        layCell(grid, x, y, gridW, gridH, false);
                }
            }
        }

        // ── Phase 2b: Connect farm district to arterial grid ──
        // Farm has wide road spacing (20) which can leave it isolated.
        // Force connector roads from farm area to nearest arterials.
        connectFarmToArterials(grid, gridW, gridH, districtGrid, artH, artV, bMinX, bMaxX, bMinY, bMaxY);

        // ── Phase 3: Mark intersections ──────────────────────
        markIntersections(grid, gridW, gridH);

        // ── Phase 4: Add tunnel entry points at mountain border ──
        // (Must run BEFORE buildGraph so tunnel nodes get proper graph edges)
        addTunnelEntries(grid, gridW, gridH, heightMap, network, districtGrid, districtMap);

        // ── Phase 5: Build graph ─────────────────────────────
        buildGraph(grid, gridW, gridH, districtGrid, districtMap, network, heightMap);

        // ── Phase 5b: Assign STOP/YIELD types at road hierarchy transitions ──
        assignIntersectionControlTypes(network);

        // ── Phase 6: Prune dead-ends (but NOT tunnel nodes) ──
        pruneDeadEnds(network);

        return network;
    }

    /* ═══════════════════════════════════════════════════════ */
    /*  Helpers                                                */
    /* ═══════════════════════════════════════════════════════ */

    private void layCell(CellType[][] grid, int x, int y, int gw, int gh, boolean canBridge) {
        if (x < 0 || x >= gw || y < 0 || y >= gh) return;
        CellType c = grid[x][y];
        if (c == CellType.EMPTY || c == CellType.ROAD || c == CellType.INTERSECTION || c == CellType.PARK) {
            grid[x][y] = CellType.ROAD;
        } else if (canBridge && c == CellType.WATER) {
            grid[x][y] = CellType.ROAD;
        }
    }

    /**
     * Ensure the farm district (3) is connected to the arterial grid.
     * Finds farm cells and lays connector roads to the nearest arterial row/col.
     * Forces through any non-water terrain (including FOREST) to guarantee connectivity.
     */
    private void connectFarmToArterials(CellType[][] grid, int gw, int gh,
                                         int[][] districtGrid,
                                         List<Integer> artH, List<Integer> artV,
                                         int bMinX, int bMaxX, int bMinY, int bMaxY) {
        // Find bounding box of farm district (3) — accept ANY cell type except WATER
        int fMinX = gw, fMinY = gh, fMaxX = 0, fMaxY = 0;
        for (int x = 0; x < gw; x++)
            for (int y = 0; y < gh; y++)
                if (districtGrid[x][y] == 3 && grid[x][y] != CellType.WATER) {
                    fMinX = Math.min(fMinX, x);
                    fMinY = Math.min(fMinY, y);
                    fMaxX = Math.max(fMaxX, x);
                    fMaxY = Math.max(fMaxY, y);
                }
        if (fMinX > fMaxX) return;

        int farmCenterX = (fMinX + fMaxX) / 2;
        int farmCenterY = (fMinY + fMaxY) / 2;

        // Find nearest horizontal arterial to farm center
        int nearestArtH = -1;
        int bestDistH = Integer.MAX_VALUE;
        for (int ay : artH) {
            int d = Math.abs(ay - farmCenterY);
            if (d < bestDistH) { bestDistH = d; nearestArtH = ay; }
        }

        // Find nearest vertical arterial to farm center
        int nearestArtV = -1;
        int bestDistV = Integer.MAX_VALUE;
        for (int ax : artV) {
            int d = Math.abs(ax - farmCenterX);
            if (d < bestDistV) { bestDistV = d; nearestArtV = ax; }
        }

        // Lay a vertical connector from farm center to nearest horizontal arterial
        if (nearestArtH >= 0) {
            int y0 = Math.min(farmCenterY, nearestArtH);
            int y1 = Math.max(farmCenterY, nearestArtH);
            for (int y = y0; y <= y1; y++)
                forceLayCell(grid, farmCenterX, y, gw, gh);
        }

        // Lay a horizontal connector from farm center to nearest vertical arterial
        if (nearestArtV >= 0) {
            int x0 = Math.min(farmCenterX, nearestArtV);
            int x1 = Math.max(farmCenterX, nearestArtV);
            for (int x = x0; x <= x1; x++)
                forceLayCell(grid, x, farmCenterY, gw, gh);
        }

        // Internal farm grid: lay a sparse road grid for building access
        // Use wide spacing to create a clean rectangular grid
        int farmSpacing = 24;
        for (int y = fMinY + farmSpacing; y < fMaxY; y += farmSpacing) {
            for (int x = fMinX; x <= fMaxX; x++) {
                if (districtGrid[x][y] == 3) forceLayCell(grid, x, y, gw, gh);
            }
        }
        for (int x = fMinX + farmSpacing; x < fMaxX; x += farmSpacing) {
            for (int y = fMinY; y <= fMaxY; y++) {
                if (districtGrid[x][y] == 3) forceLayCell(grid, x, y, gw, gh);
            }
        }
    }

    /**
     * Force-lay a road cell, converting any non-water terrain (FOREST, EMPTY, PARK, etc.)
     * to ROAD. Used for critical connector roads that must reach the farm.
     */
    private void forceLayCell(CellType[][] grid, int x, int y, int gw, int gh) {
        if (x < 0 || x >= gw || y < 0 || y >= gh) return;
        CellType c = grid[x][y];
        if (c == CellType.WATER) return; // never overwrite water without bridging
        grid[x][y] = CellType.ROAD;
    }

    /**
     * Lay road along an arterial line with smart bridging.
     * Bridges water gaps ≤ 8 cells wide (river is ~5-7 cells with banks).
     */
    private void layArterialLine(CellType[][] grid, int start, int end, int fixed,
                                  int gw, int gh, boolean horizontal) {
        int waterStart = -1;
        for (int i = start; i <= end; i++) {
            int x = horizontal ? i : fixed;
            int y = horizontal ? fixed : i;
            if (x < 0 || x >= gw || y < 0 || y >= gh) continue;

            if (grid[x][y] == CellType.WATER) {
                if (waterStart < 0) waterStart = i;
            } else {
                if (waterStart >= 0) {
                    int gap = i - waterStart;
                    if (gap <= 8) {
                        for (int j = waterStart; j < i; j++) {
                            int bx = horizontal ? j : fixed;
                            int by = horizontal ? fixed : j;
                            if (bx >= 0 && bx < gw && by >= 0 && by < gh) {
                                grid[bx][by] = CellType.ROAD;
                            }
                        }
                    }
                    waterStart = -1;
                }
                layCell(grid, x, y, gw, gh, false);
            }
        }
    }

    private int dominantDistrict(int[][] dg, int x0, int y0, int x1, int y1, int gw, int gh) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int x = Math.max(0, x0); x <= Math.min(gw - 1, x1); x++)
            for (int y = Math.max(0, y0); y <= Math.min(gh - 1, y1); y++)
                if (dg[x][y] > 0)
                    counts.merge(dg[x][y], 1, Integer::sum);
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(2);
    }

    /* ── Phase 3 ── */

    private void markIntersections(CellType[][] grid, int gw, int gh) {
        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                if (grid[x][y] != CellType.ROAD) continue;
                // Collect which directions have road neighbors
                boolean hasN = false, hasS = false, hasE = false, hasW = false;
                int nb = 0;
                for (int[] d : DIRS) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx >= 0 && nx < gw && ny >= 0 && ny < gh) {
                        CellType nc = grid[nx][ny];
                        if (nc == CellType.ROAD || nc == CellType.INTERSECTION) {
                            nb++;
                            if (d[0] == 1) hasE = true;
                            else if (d[0] == -1) hasW = true;
                            else if (d[1] == 1) hasS = true;
                            else if (d[1] == -1) hasN = true;
                        }
                    }
                }
                // Mark as intersection if 3+ road neighbors (true junction)
                if (nb >= 3) {
                    grid[x][y] = CellType.INTERSECTION;
                }
                // Also mark corners (exactly 2 neighbors at 90°) so buildGraph
                // can create nodes — walk() only moves in straight lines and
                // cannot follow bends. Client skips rendering pads for ≤2-way nodes.
                else if (nb == 2) {
                    boolean isCorner = (hasN || hasS) && (hasE || hasW);
                    if (isCorner) {
                        grid[x][y] = CellType.INTERSECTION;
                        cornerCellPositions.add(key(x, y));
                    }
                }
            }
        }
    }

    /* ── Phase 4 ── */

    private void buildGraph(CellType[][] grid, int gw, int gh,
                            int[][] dg, Map<Integer, DistrictTemplate> dm,
                            RoadNetwork net, double[][] heightMap) {

        Map<Long, String> cellNode = new HashMap<>();
        int ni = 0;

        // Create intersection nodes
        for (int x = 0; x < gw; x++)
            for (int y = 0; y < gh; y++) {
                if (grid[x][y] != CellType.INTERSECTION) continue;
                String nid = "n_" + (ni++);
                double wx = x * tileSize + tileSize / 2;
                double wy = y * tileSize + tileSize / 2;
                int dn = dg[x][y];
                DistrictTemplate t = dm.getOrDefault(dn, DistrictTemplate.TOWN_CENTER);

                Intersection.IntersectionType it;
                if (tunnelCellPositions.contains(key(x, y))) {
                    it = Intersection.IntersectionType.TUNNEL;
                    tunnelNodeIds.add(nid);
                } else if (cornerCellPositions.contains(key(x, y))) {
                    it = Intersection.IntersectionType.UNCONTROLLED;
                    cornerNodeIds.add(nid);
                } else {
                    // All intersections start at STOP — player upgrades them
                    it = Intersection.IntersectionType.STOP;
                }

                Intersection intersection = new Intersection(it);
                intersection.setDistrictNumber(dn);
                net.addNode(nid, new Vec2(wx, wy), intersection);
                cellNode.put(key(x, y), nid);
            }

        // Endpoints (road cells with <= 1 road neighbour) — skip creating
        // intersection nodes for these; they are dead-ends that will be pruned.
        // Only create endpoint nodes for tunnel entries which must not be pruned.

        // Walk edges between intersections
        Set<String> seen = new HashSet<>();
        int ei = 0;
        for (int x = 0; x < gw; x++)
            for (int y = 0; y < gh; y++) {
                if (grid[x][y] != CellType.INTERSECTION) continue;
                String fid = cellNode.get(key(x, y));
                if (fid == null) continue;

                for (int[] d : DIRS) {
                    String tid = walk(grid, gw, gh, x, y, d[0], d[1], cellNode);
                    if (tid == null || tid.equals(fid)) continue;
                    String pk = fid.compareTo(tid) < 0 ? fid + "|" + tid : tid + "|" + fid;
                    if (seen.contains(pk)) continue;
                    seen.add(pk);

                    var fn = net.getNode(fid);
                    var tn = net.getNode(tid);
                    double dist = fn.getPosition().distanceTo(tn.getPosition());

                    // All roads start at LOCAL regardless of district — the player upgrades them.
                    // District templates determine WHAT can be upgraded, not the starting state.
                    RoadSegment.RoadType rt = RoadSegment.RoadType.LOCAL;
                    int lanes = 1;
                    double speed = 40.0;

                    String f1 = "e_" + (ei++), f2 = "e_" + (ei++);
                    RoadSegment seg1 = new RoadSegment(lanes, speed, rt);
                    RoadSegment seg2 = new RoadSegment(lanes, speed, rt);

                    // Detect bridge: check if edge path crosses water cells
                    boolean isBridge = isEdgeOverWater(fn.getPosition(), tn.getPosition(), heightMap, gw, gh);
                    seg1.setBridge(isBridge);
                    seg2.setBridge(isBridge);

                    net.addEdge(f1, fid, tid, dist, seg1);
                    net.addEdge(f2, tid, fid, dist, seg2);
                }
            }
    }

    /**
     * Check if an edge between two world positions crosses cells that were originally water
     * (heightMap < -0.05). Used to mark bridge edges.
     */
    private boolean isEdgeOverWater(com.trafficgame.engine.util.Vec2 from,
                                     com.trafficgame.engine.util.Vec2 to,
                                     double[][] heightMap, int gw, int gh) {
        int steps = (int) (from.distanceTo(to) / tileSize) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double wx = from.x() + (to.x() - from.x()) * t;
            double wy = from.y() + (to.y() - from.y()) * t;
            int gx = (int) (wx / tileSize);
            int gy = (int) (wy / tileSize);
            if (gx >= 0 && gx < gw && gy >= 0 && gy < gh) {
                if (heightMap[gx][gy] < -0.05) return true;
            }
        }
        return false;
    }

    private String walk(CellType[][] grid, int gw, int gh,
                        int sx, int sy, int dx, int dy,
                        Map<Long, String> cn) {
        int cx = sx + dx, cy = sy + dy;
        while (cx >= 0 && cx < gw && cy >= 0 && cy < gh) {
            CellType c = grid[cx][cy];
            if (c == CellType.INTERSECTION) return cn.get(key(cx, cy));
            if (c != CellType.ROAD) return null;
            cx += dx;
            cy += dy;
        }
        return null;
    }

    /* ── Phase 5: Tunnel entries ── */

    /**
     * Add tunnel entry nodes at the northern mountain border where arterial
     * roads meet the mountain edge. Vehicles spawn/despawn here.
     * Punches through forest/mountain cells to ensure connectivity.
     */
    private void addTunnelEntries(CellType[][] grid, int gw, int gh,
                                   double[][] heightMap, RoadNetwork net,
                                   int[][] dg, Map<Integer, DistrictTemplate> dm) {
        // For each vertical arterial column, find the northernmost road cell
        // and extend a tunnel road northward through any forest/mountain
        int tunnelIdx = 0;
        for (int col : arterialCols) {
            int firstRoadY = -1;
            for (int y = 0; y < gh; y++) {
                if (grid[col][y] == CellType.ROAD || grid[col][y] == CellType.INTERSECTION) {
                    firstRoadY = y;
                    break;
                }
            }
            if (firstRoadY < 0) continue;

            // Place tunnel entry 3 cells north of the first road cell
            // Force-lay road cells through forest/mountain to ensure connectivity
            int ty = Math.max(0, firstRoadY - 3);
            for (int y = ty; y <= firstRoadY; y++) {
                grid[col][y] = CellType.ROAD;  // overwrite anything including FOREST
            }

            grid[col][ty] = CellType.INTERSECTION;
            tunnelCellPositions.add(key(col, ty));
            if (grid[col][firstRoadY] != CellType.INTERSECTION) {
                grid[col][firstRoadY] = CellType.INTERSECTION;
            }

            tunnelIdx++;
            if (tunnelIdx >= 3) break;
        }

        // Add tunnel entries for horizontal arterials at east/west edges
        tunnelIdx = 0;
        for (int row : arterialRows) {
            // Try west edge
            int firstRoadX = -1;
            for (int x = 0; x < gw; x++) {
                if (grid[x][row] == CellType.ROAD || grid[x][row] == CellType.INTERSECTION) {
                    firstRoadX = x;
                    break;
                }
            }
            if (firstRoadX >= 0) {
                int tx = Math.max(0, firstRoadX - 3);
                for (int x = tx; x <= firstRoadX; x++) {
                    grid[x][row] = CellType.ROAD;
                }
                grid[tx][row] = CellType.INTERSECTION;
                tunnelCellPositions.add(key(tx, row));
                if (grid[firstRoadX][row] != CellType.INTERSECTION) {
                    grid[firstRoadX][row] = CellType.INTERSECTION;
                }
                tunnelIdx++;
            }

            // Try east edge
            int lastRoadX = -1;
            for (int x = gw - 1; x >= 0; x--) {
                if (grid[x][row] == CellType.ROAD || grid[x][row] == CellType.INTERSECTION) {
                    lastRoadX = x;
                    break;
                }
            }
            if (lastRoadX >= 0 && lastRoadX < gw - 1) {
                int tx = Math.min(gw - 1, lastRoadX + 3);
                for (int x = lastRoadX; x <= tx; x++) {
                    grid[x][row] = CellType.ROAD;
                }
                grid[tx][row] = CellType.INTERSECTION;
                tunnelCellPositions.add(key(tx, row));
                if (grid[lastRoadX][row] != CellType.INTERSECTION) {
                    grid[lastRoadX][row] = CellType.INTERSECTION;
                }
                tunnelIdx++;
            }

            if (tunnelIdx >= 4) break;
        }

        // Re-mark intersections after adding tunnel connectors
        markIntersections(grid, gw, gh);
    }

    /* ── Phase 6 ── */

    private void pruneDeadEnds(RoadNetwork net) {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<String> rem = new ArrayList<>();
            for (var n : net.getAllNodes()) {
                // Never prune tunnel or corner nodes
                if (tunnelNodeIds.contains(n.getId())) continue;
                if (cornerNodeIds.contains(n.getId())) continue;
                // Each bidirectional connection = 2 edges (in + out).
                // c < 4 means 0 or 1 connections — dead ends that trap cars.
                int c = net.getOutgoingEdges(n.getId()).size()
                        + net.getIncomingEdges(n.getId()).size();
                if (c < 4) rem.add(n.getId());
            }
            for (String id : rem) {
                net.removeNode(id);
                changed = true;
            }
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /**
     * Post-pass: assign STOP or YIELD intersection types based on road hierarchy.
     * - UNCONTROLLED nodes where LOCAL meets ARTERIAL/COLLECTOR → STOP
     * - UNCONTROLLED nodes where LOCAL meets another LOCAL with 3+ roads → YIELD
     * - SIGNAL nodes are left as-is (already assigned by district signalChance)
     * - TUNNEL nodes are left as-is
     */
    private void assignIntersectionControlTypes(RoadNetwork net) {
        for (var node : net.getAllNodes()) {
            Intersection intersection = node.getData();
            if (intersection.getType() != Intersection.IntersectionType.UNCONTROLLED) continue;

            var outEdges = net.getOutgoingEdges(node.getId());
            var inEdges = net.getIncomingEdges(node.getId());

            // Collect distinct road types meeting at this intersection
            boolean hasHighway = false, hasArterial = false, hasCollector = false;
            boolean hasLocal = false, hasPath = false;
            int totalEdges = 0;

            for (var edge : outEdges) {
                RoadSegment seg = edge.getData();
                switch (seg.getRoadType()) {
                    case HIGHWAY -> hasHighway = true;
                    case ARTERIAL -> hasArterial = true;
                    case COLLECTOR -> hasCollector = true;
                    case LOCAL -> hasLocal = true;
                    case PATH -> hasPath = true;
                }
                totalEdges++;
            }
            for (var edge : inEdges) {
                RoadSegment seg = edge.getData();
                switch (seg.getRoadType()) {
                    case HIGHWAY -> hasHighway = true;
                    case ARTERIAL -> hasArterial = true;
                    case COLLECTOR -> hasCollector = true;
                    case LOCAL -> hasLocal = true;
                    case PATH -> hasPath = true;
                }
                totalEdges++;
            }

            // Local/path meeting arterial or highway → STOP sign
            if ((hasLocal || hasPath) && (hasArterial || hasHighway)) {
                intersection.setType(Intersection.IntersectionType.STOP);
            }
            // Local meeting collector → YIELD
            else if ((hasLocal || hasPath) && hasCollector) {
                intersection.setType(Intersection.IntersectionType.YIELD);
            }
            // 3+ way intersection with same type roads → YIELD
            else if (totalEdges >= 6) { // 3 directions * 2 (bidirectional)
                intersection.setType(Intersection.IntersectionType.YIELD);
            }
        }
    }
}
