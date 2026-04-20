package com.trafficgame.game.mapgen;

import com.trafficgame.engine.terrain.Tile;
import com.trafficgame.engine.terrain.TileMap;
import com.trafficgame.game.model.BuildingData;
import com.trafficgame.game.model.RoadNetwork;

import java.util.*;

/**
 * Orchestrates cell-grid based city generation with natural terrain.
 *
 * <p><b>Pipeline:</b></p>
 * <ol>
 *   <li>Generate a 2-D height map (noise-based, ocean south, mountains north)</li>
 *   <li>Classify cells from height (water, mountain/forest, empty/buildable)</li>
 *   <li>Assign Voronoi districts to buildable area</li>
 *   <li>Lay roads via {@link RoadPlanner} (unified grid)</li>
 *   <li>Place buildings via {@link BuildingPlacer}</li>
 *   <li>Generate terrain tile map with world-space elevation</li>
 * </ol>
 */
public final class CityMapGenerator {

    private final int seed;

    public CityMapGenerator(int seed) {
        this.seed = seed;
    }

    public GeneratedMap generate(int gridW, int gridH) {
        Random rng = new Random(seed);

        // 1. Height map → natural terrain
        double[][] heightMap = generateHeightMap(gridW, gridH);

        // 2. Classify cells from height
        CellType[][] grid = new CellType[gridW][gridH];
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                double h = heightMap[x][y];
                if (h < -0.05) {
                    grid[x][y] = CellType.WATER;
                } else if (h > 0.55) {
                    grid[x][y] = CellType.FOREST;
                } else {
                    grid[x][y] = CellType.EMPTY;
                }
            }
        }

        // 2b. Force forest belt in northern zone (ny 0.10–0.22)
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                double ny = (double) y / gridH;
                if (ny >= 0.08 && ny < 0.20 && grid[x][y] == CellType.EMPTY) {
                    // Taper: denser forest closer to mountains
                    double forestChance = 1.0 - (ny - 0.08) / 0.12;
                    if (rng.nextDouble() < forestChance * 0.85) {
                        grid[x][y] = CellType.FOREST;
                    }
                }
            }
        }

        // 2c. Remove isolated water (flood-fill from ocean/river to find connected water)
        removeIsolatedWater(grid, gridW, gridH);

        // 3. Mark beach/coast cells BEFORE building placement (unbuildable buffer)
        markBeachCells(grid, heightMap, gridW, gridH);

        // 4. Assign districts in buildable area
        int[][] districtGrid = new int[gridW][gridH];
        Map<Integer, DistrictTemplate> districtMap = new LinkedHashMap<>();
        assignDistricts(grid, districtGrid, districtMap, gridW, gridH, rng);

        // 4b. Convert FOREST cells in the farm district region to EMPTY so they
        // become buildable and get road/building placement. The farm (district 3)
        // is in the NE where forest belt overlaps farmland.
        clearForestInFarmDistrict(grid, districtGrid, gridW, gridH);

        // 5. Roads (unified grid) + tunnel entries at mountain border
        RoadPlanner planner = new RoadPlanner(seed + 100);
        RoadNetwork roads = planner.plan(grid, gridW, gridH, districtGrid, districtMap, heightMap);

        // 6. Buildings + trees
        BuildingPlacer placer = new BuildingPlacer(seed + 200);
        List<BuildingData> buildings = placer.place(grid, gridW, gridH, districtGrid, districtMap, roads);
        buildings.addAll(placer.placeTrees(grid, gridW, gridH, districtGrid, districtMap));

        // 7. Terrain tile map with world-space elevation
        TileMap terrain = generateTerrain(grid, gridW, gridH, districtGrid, heightMap);

        return new GeneratedMap(terrain, roads, buildings, districtGrid);
    }

    /* ═════════════════════════════════════════════════════════ */
    /*  Height map generation                                    */
    /* ═════════════════════════════════════════════════════════ */

    private double[][] generateHeightMap(int gw, int gh) {
        double[][] height = new double[gw][gh];

        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                double nx = (double) x / gw;
                double ny = (double) y / gh; // 0 = north, 1 = south

                // Base gradient: south → ocean, north → mountains
                double base;
                if (ny > 0.82) {
                    // Ocean drops sharply
                    double t = (ny - 0.82) / 0.18;
                    base = -0.06 - t * 0.55;
                } else if (ny < 0.12) {
                    // Mountains rise
                    double t = 1.0 - ny / 0.12;
                    base = 0.25 + t * 0.55;
                } else {
                    // Buildable interior: not flat — gentle rolling terrain
                    double lat = (1.0 - ny) * 0.08;
                    base = 0.04 + lat;
                }

                // Multi-octave noise for natural variation
                double n = fbm(x * 0.07, y * 0.07, seed, 4) * 0.2 - 0.1;

                // Prevent noise from pushing interior buildable cells below water threshold
                if (ny > 0.15 && ny < 0.80) {
                    double candidate = base + n;
                    if (candidate < -0.04) {
                        n = -0.04 - base;
                    }
                }

                // Hills in the interior (northwest = residential has rolling hills)
                if (ny > 0.15 && ny < 0.75) {
                    double hillNoise = fbm(x * 0.12, y * 0.12, seed + 50, 3);
                    // Stronger hills northwest (residential/farmland area)
                    double hillStrength = 0.0;
                    if (nx < 0.5 && ny < 0.5) {
                        hillStrength = (0.5 - nx) * (0.5 - ny) * 6.0;
                        hillStrength = Math.min(hillStrength, 1.0);
                    }
                    // Eastern farmland also gets rolling terrain
                    if (nx > 0.55 && ny < 0.55) {
                        hillStrength = Math.max(hillStrength, (nx - 0.55) * 2.0);
                        hillStrength = Math.min(hillStrength, 0.7);
                    }
                    base += hillNoise * 0.18 * hillStrength;
                }

                // Canyon/ravine cutting through industrial area (southwest)
                // Wider and deeper canyon that's actually visible
                // Clamp floor above -0.04 so canyon doesn't create spurious water cells
                if (nx > 0.10 && nx < 0.42 && ny > 0.50 && ny < 0.78) {
                    double canyonCenter = 0.25 + fbm(y * 0.10, 0, seed + 77, 2) * 0.06;
                    double distFromCenter = Math.abs(nx - canyonCenter);
                    if (distFromCenter < 0.06) {
                        // Canyon floor — noticeable dip but never below water threshold
                        double depth = (1.0 - distFromCenter / 0.06);
                        base -= depth * 0.15;
                        base = Math.max(base, -0.04);
                    } else if (distFromCenter < 0.12) {
                        // Canyon rim — raised edges
                        double rimT = (distFromCenter - 0.06) / 0.06;
                        base += (1.0 - rimT) * 0.08;
                    }
                }

                // Rolling ridges in the east farmland
                if (nx > 0.60 && ny > 0.20 && ny < 0.70) {
                    double ridge = Math.sin(ny * 25.0 + nx * 5.0) * 0.04;
                    base += ridge * (nx - 0.55);
                }

                height[x][y] = base + n;

                // Final safety clamp: ensure interior buildable cells never dip below
                // water threshold (-0.05). Canyon/hills can modify base AFTER the noise
                // clamp, so this final check is essential.
                if (ny > 0.15 && ny < 0.80 && height[x][y] < -0.04) {
                    height[x][y] = -0.04;
                }
            }
        }

        // Add a river through the eastern part
        addRiver(height, gw, gh);

        return height;
    }

    /**
     * Carve a continuous river from mountains (north) to ocean (south).
     * Width ~4 cells, all cells forced deep enough to classify as WATER.
     */
    private void addRiver(double[][] height, int gw, int gh) {
        Random rr = new Random(seed + 999);
        double rx = gw * 0.62;

        int startY = (int) (gh * 0.04);
        int endY = (int) (gh * 0.84);

        for (int y = startY; y < endY; y++) {
            rx += (rr.nextDouble() - 0.47) * 1.4;
            rx = Math.max(gw * 0.45, Math.min(gw * 0.80, rx));
            int cx = (int) Math.round(rx);

            // River width: 2 cells fully submerged, 1 cell bank on each side
            for (int x = cx - 2; x <= cx + 2; x++) {
                if (x >= 0 && x < gw && y >= 0 && y < gh) {
                    double d = Math.abs(x - rx);
                    if (d < 1.5) {
                        // Deep channel — always classified as water
                        height[x][y] = Math.min(height[x][y], -0.15);
                    } else {
                        // Bank — just below the water threshold
                        height[x][y] = Math.min(height[x][y], -0.06);
                    }
                }
            }
        }
    }

    /* ═════════════════════════════════════════════════════════ */
    /*  Value noise (no external libs)                           */
    /* ═════════════════════════════════════════════════════════ */

    private double fbm(double x, double y, int s, int oct) {
        double v = 0, amp = 1, freq = 1, mx = 0;
        for (int i = 0; i < oct; i++) {
            v += valueNoise(x * freq, y * freq, s + i * 31) * amp;
            mx += amp;
            amp *= 0.5;
            freq *= 2;
        }
        return v / mx;
    }

    private double valueNoise(double x, double y, int s) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double n00 = hf(ix, iy, s), n10 = hf(ix + 1, iy, s);
        double n01 = hf(ix, iy + 1, s), n11 = hf(ix + 1, iy + 1, s);
        return lerp(lerp(n00, n10, fx), lerp(n01, n11, fx), fy);
    }

    private double hf(int x, int y, int s) {
        long h = s + (long) x * 374761393L + (long) y * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (h & 0x7fffffffL) / (double) 0x7fffffffL;
    }

    private static double lerp(double a, double b, double t) { return a + t * (b - a); }

    /* ═════════════════════════════════════════════════════════ */
    /*  Remove isolated water bodies                              */
    /* ═════════════════════════════════════════════════════════ */

    /**
     * BFS from all water cells in the southern 25% of the map (ocean zone)
     * to find connected water. Any water cell NOT reachable is isolated
     * (random noise artifact) and gets reclassified as EMPTY.
     */
    private void removeIsolatedWater(CellType[][] grid, int gw, int gh) {
        boolean[][] visited = new boolean[gw][gh];
        Queue<int[]> queue = new LinkedList<>();

        // Seed 1: Water cells on the actual map edges (bottom 3 rows = ocean)
        for (int x = 0; x < gw; x++) {
            for (int y = gh - 3; y < gh; y++) {
                if (y >= 0 && grid[x][y] == CellType.WATER && !visited[x][y]) {
                    queue.add(new int[]{x, y});
                    visited[x][y] = true;
                }
            }
        }

        // Seed 2: Water cells along any map border (north, east, west edges)
        for (int y = 0; y < gh; y++) {
            for (int x : new int[]{0, 1, gw - 2, gw - 1}) {
                if (x >= 0 && x < gw && grid[x][y] == CellType.WATER && !visited[x][y]) {
                    queue.add(new int[]{x, y});
                    visited[x][y] = true;
                }
            }
        }
        for (int x = 0; x < gw; x++) {
            for (int y : new int[]{0, 1}) {
                if (grid[x][y] == CellType.WATER && !visited[x][y]) {
                    queue.add(new int[]{x, y});
                    visited[x][y] = true;
                }
            }
        }

        // Seed 3: River column area (around x ≈ 62% of map, from top to bottom)
        int riverCenterX = (int) (gw * 0.62);
        for (int y = 0; y < gh; y++) {
            for (int x = riverCenterX - 4; x <= riverCenterX + 4; x++) {
                if (x >= 0 && x < gw && grid[x][y] == CellType.WATER && !visited[x][y]) {
                    queue.add(new int[]{x, y});
                    visited[x][y] = true;
                }
            }
        }

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            for (int[] d : dirs) {
                int nx = pos[0] + d[0], ny = pos[1] + d[1];
                if (nx >= 0 && nx < gw && ny >= 0 && ny < gh
                        && !visited[nx][ny] && grid[nx][ny] == CellType.WATER) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        // Remove isolated water
        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                if (grid[x][y] == CellType.WATER && !visited[x][y]) {
                    grid[x][y] = CellType.EMPTY;
                }
            }
        }
    }

    /* ═════════════════════════════════════════════════════════ */
    /*  Beach / coast pre-classification                          */
    /* ═════════════════════════════════════════════════════════ */

    /**
     * Mark EMPTY cells near water as PARK (beach/coast, unbuildable).
     * Must be called BEFORE district assignment and building placement.
     */
    private void markBeachCells(CellType[][] grid, double[][] hm, int gw, int gh) {
        boolean[][] beach = new boolean[gw][gh];
        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                if (grid[x][y] != CellType.EMPTY) continue;

                boolean nearWater = false;
                outer:
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < gw && ny >= 0 && ny < gh
                                && grid[nx][ny] == CellType.WATER) {
                            nearWater = true;
                            break outer;
                        }
                    }
                }
                if (nearWater) beach[x][y] = true;
            }
        }
        for (int x = 0; x < gw; x++)
            for (int y = 0; y < gh; y++)
                if (beach[x][y]) grid[x][y] = CellType.PARK;
    }

    /* ═════════════════════════════════════════════════════════ */
    /*  District assignment                                      */
    /* ═════════════════════════════════════════════════════════ */

    private void assignDistricts(CellType[][] grid, int[][] dg,
                                  Map<Integer, DistrictTemplate> dm,
                                  int gw, int gh, Random rng) {
        DistrictTemplate[] templates = DistrictTemplate.values();
        for (DistrictTemplate t : templates) dm.put(t.getNumber(), t);

        // Find buildable bounding box
        int bMinX = gw, bMinY = gh, bMaxX = 0, bMaxY = 0;
        for (int x = 0; x < gw; x++)
            for (int y = 0; y < gh; y++)
                if (grid[x][y] == CellType.EMPTY) {
                    bMinX = Math.min(bMinX, x); bMinY = Math.min(bMinY, y);
                    bMaxX = Math.max(bMaxX, x); bMaxY = Math.max(bMaxY, y);
                }

        int cx = (bMinX + bMaxX) / 2;
        int cy = (bMinY + bMaxY) / 2;
        int sx = (bMaxX - bMinX) / 5;
        int sy = (bMaxY - bMinY) / 5;

        // Place district seeds in geographically sensible positions
        // Layout: Forest corridor (north) → Suburbs (NW) + Countryside (NE)
        //         → Small Town (center) → Commercial (E) → Industrial (SW)
        //         → Downtown / Modern City (S-center)
        int[][] seeds = {
            {cx,              cy},                  // 1 Town Center — center (small town)
            {cx - sx - sx/3,  cy - sy/2},           // 2 Residential — west (suburbs)
            {cx + sx + sx/3,  cy - sy},             // 3 Farmland — northeast (countryside)
            {cx + sx,         cy + sy/3},           // 4 Commercial — east-center
            {cx - sx,         cy + sy + sy/3},      // 5 Industrial — southwest (canyon area)
            {cx,              cy - sy - sy/2},      // 6 Highway Corridor — north (forest edge)
            {cx,              cy + sy + sy/2},      // 7 Downtown — south (modern city)
        };

        for (int[] s : seeds) {
            s[0] = Math.max(bMinX + 1, Math.min(bMaxX - 1, s[0]));
            s[1] = Math.max(bMinY + 1, Math.min(bMaxY - 1, s[1]));
        }

        // Voronoi: each buildable cell gets nearest district
        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                if (grid[x][y] != CellType.EMPTY) { dg[x][y] = 0; continue; }
                double best = Double.MAX_VALUE;
                int bd = 1;
                for (int i = 0; i < templates.length; i++) {
                    double dx = x - seeds[i][0], dy = y - seeds[i][1];
                    double d = dx * dx + dy * dy;
                    if (d < best) { best = d; bd = templates[i].getNumber(); }
                }
                dg[x][y] = bd;
            }
        }
    }

    /* ═════════════════════════════════════════════════════════ */
    /*  Clear forest in farm district for buildability           */
    /* ═════════════════════════════════════════════════════════ */

    /**
     * Forest cells that fall within the farm district's Voronoi region are
     * converted to EMPTY and assigned district 3. This ensures the farm area
     * has buildable terrain even where the northern forest belt overlaps.
     */
    private void clearForestInFarmDistrict(CellType[][] grid, int[][] dg, int gw, int gh) {
        // Find the farm bounding box from cells that DID get assigned district 3
        int fMinX = gw, fMinY = gh, fMaxX = 0, fMaxY = 0;
        for (int x = 0; x < gw; x++)
            for (int y = 0; y < gh; y++)
                if (dg[x][y] == 3) {
                    fMinX = Math.min(fMinX, x); fMinY = Math.min(fMinY, y);
                    fMaxX = Math.max(fMaxX, x); fMaxY = Math.max(fMaxY, y);
                }
        if (fMinX > fMaxX) return;

        // Expand slightly to catch bordering forest cells
        fMinX = Math.max(0, fMinX - 2);
        fMinY = Math.max(0, fMinY - 2);
        fMaxX = Math.min(gw - 1, fMaxX + 2);
        fMaxY = Math.min(gh - 1, fMaxY + 2);

        // Convert FOREST cells adjacent to farm district cells to EMPTY with district 3
        for (int x = fMinX; x <= fMaxX; x++) {
            for (int y = fMinY; y <= fMaxY; y++) {
                if (grid[x][y] == CellType.FOREST && dg[x][y] == 0) {
                    // Check if this cell is adjacent to or within the farm region
                    boolean nearFarm = false;
                    for (int dx = -2; dx <= 2 && !nearFarm; dx++)
                        for (int dy = -2; dy <= 2 && !nearFarm; dy++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && nx < gw && ny >= 0 && ny < gh && dg[nx][ny] == 3)
                                nearFarm = true;
                        }
                    if (nearFarm) {
                        grid[x][y] = CellType.EMPTY;
                        dg[x][y] = 3;
                    }
                }
            }
        }
    }

    /* ═════════════════════════════════════════════════════════ */
    /*  Terrain tile map with elevation                          */
    /* ═════════════════════════════════════════════════════════ */

    private TileMap generateTerrain(CellType[][] grid, int gw, int gh,
                                     int[][] dg, double[][] hm) {
        // Pre-pass: flatten elevation near roads/buildings so terrain never rises above them
        double[][] flatElev = new double[gw][gh];
        for (int x = 0; x < gw; x++)
            for (int y = 0; y < gh; y++)
                flatElev[x][y] = hm[x][y];

        // Pass 1: mark all road/intersection/building cells as needing flattening
        boolean[][] isFlat = new boolean[gw][gh];
        for (int x = 0; x < gw; x++)
            for (int y = 0; y < gh; y++) {
                CellType c = grid[x][y];
                if (c == CellType.ROAD || c == CellType.INTERSECTION || c == CellType.BUILDING) {
                    isFlat[x][y] = true;
                }
            }

        // Pass 2: flatten cells within radius 2 of any road/building
        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                if (!isFlat[x][y]) continue;
                flatElev[x][y] = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= gw || ny < 0 || ny >= gh) continue;
                        if (isFlat[nx][ny]) continue; // already road/building
                        int dist = Math.max(Math.abs(dx), Math.abs(dy));
                        if (dist <= 1) {
                            // Immediate neighbors: force to 0
                            flatElev[nx][ny] = 0;
                        } else {
                            // 2 cells away: cap very low so multiplied elevation stays below road
                            flatElev[nx][ny] = Math.min(flatElev[nx][ny], 0.01);
                        }
                    }
                }
            }
        }

        TileMap map = new TileMap(gw, gh);
        map.fill((x, y) -> {
            CellType cell = grid[x][y];
            double raw = flatElev[x][y];
            int dn = dg[x][y];

            int type;
            double elev;
            boolean pass;

            switch (cell) {
                case WATER -> {
                    type = ZoneType.WATER.ordinal();
                    elev = raw * 20;  // negative → below sea level (less extreme depth)
                    pass = false;
                }
                case FOREST -> {
                    type = ZoneType.FOREST.ordinal();
                    elev = Math.max(1, raw * 40);  // forest/mountains rise, min 1 for visibility
                    pass = false;
                }
                case ROAD, INTERSECTION -> {
                    // Check if this road was placed over original water (bridge)
                    if (hm[x][y] < -0.05) {
                        type = ZoneType.WATER.ordinal();
                        elev = -2;  // below water plane so water visible under bridge
                    } else {
                        type = districtZone(dn);
                        elev = 0;
                    }
                    pass = true;
                }
                case BUILDING -> {
                    type = districtZone(dn);
                    elev = 0;
                    pass = false;
                }
                case PARK -> {
                    // Beach/coast (pre-marked)
                    type = ZoneType.COAST.ordinal();
                    elev = Math.max(0, raw * 4);
                    pass = true;
                }
                default -> { // EMPTY, FARM
                    type = districtZone(dn);
                    // Elevation varies by zone for visual interest
                    double zoneMultiplier = switch (dn) {
                        case 3 -> 22;   // Farmland — rolling hills
                        case 6 -> 20;   // Highway corridor — gentle terrain
                        case 2 -> 14;   // Residential — moderate
                        case 1 -> 10;   // Town center — flatter
                        case 4, 5, 7 -> 6; // Commercial/Industrial/Downtown — flat urban
                        default -> 14;
                    };
                    elev = Math.max(0, raw * zoneMultiplier);
                    pass = true;
                }
            }

            return new Tile(type, elev, 0.5, pass);
        });
        return map;
    }

    private int districtZone(int dn) {
        return switch (dn) {
            case 1 -> ZoneType.CITY.ordinal();         // Town Center — central small town
            case 2 -> ZoneType.SUBURB.ordinal();       // Residential — suburban green
            case 3 -> ZoneType.COUNTRYSIDE.ordinal();  // Farmland — open countryside
            case 4 -> ZoneType.CITY.ordinal();         // Commercial — urban commercial
            case 5 -> ZoneType.INDUSTRIAL.ordinal();   // Industrial — factory zone
            case 6 -> ZoneType.FOREST.ordinal();       // Highway Corridor — forest zone
            case 7 -> ZoneType.DOWNTOWN.ordinal();     // Downtown — dense modern city
            default -> ZoneType.COUNTRYSIDE.ordinal();
        };
    }

    /* ═════════════════════════════════════════════════════════ */
    /*  Result record                                            */
    /* ═════════════════════════════════════════════════════════ */

    public record GeneratedMap(
            TileMap terrain,
            RoadNetwork roads,
            List<BuildingData> buildings,
            int[][] districtGrid
    ) {}
}
