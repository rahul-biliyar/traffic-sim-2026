package com.trafficgame.game.mapgen;

import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.BuildingData;
import com.trafficgame.game.model.RoadNetwork;

import java.util.*;

public final class BuildingPlacer {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final Random rng;
    private final double tileSize;

    public BuildingPlacer(int seed) {
        this.rng = new Random(seed);
        this.tileSize = GameConfig.TILE_SIZE;
    }

    public List<BuildingData> place(CellType[][] grid, int gridW, int gridH,
                                    int[][] districtGrid,
                                    Map<Integer, DistrictTemplate> districtMap,
                                    RoadNetwork network) {

        List<BuildingData> buildings = new ArrayList<>();
        int idx = 0;

        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                if (grid[x][y] != CellType.EMPTY) continue;

                int dn = districtGrid[x][y];
                if (dn == 0) continue;
                DistrictTemplate tmpl = districtMap.get(dn);
                if (tmpl == null) continue;

                if (!isAdjacentToRoad(grid, gridW, gridH, x, y)) continue;
                if (rng.nextDouble() > tmpl.getBuildingChance()) continue;

                // Footprint varies by building style
                int fw = 1, fh = 1;
                switch (tmpl.getBuildingStyle()) {
                    case TOWER:
                        if (rng.nextDouble() < 0.25) fw = 2;
                        else if (rng.nextDouble() < 0.25) fh = 2;
                        break;
                    case OFFICE:
                        if (rng.nextDouble() < 0.35) fw = 2;
                        break;
                    case WAREHOUSE:
                    case FARM:
                        fw = 1 + rng.nextInt(2);
                        fh = 1 + rng.nextInt(2);
                        break;
                    case SHOP:
                        if (rng.nextDouble() < 0.25) fh = 2;
                        break;
                    default:
                        break;
                }

                if (!canPlaceFootprint(grid, gridW, gridH, districtGrid, x, y, fw, fh, dn))
                    continue;
                // Ensure at least one cell of the footprint is adjacent to a road
                if (!footprintHasRoadAccess(grid, gridW, gridH, x, y, fw, fh))
                    continue;

                for (int bx = x; bx < x + fw; bx++)
                    for (int by = y; by < y + fh; by++)
                        grid[bx][by] = CellType.BUILDING;

                // World dimensions strictly from footprint (70-82% fill for sidewalk gap)
                double fill = 0.70 + rng.nextDouble() * 0.12;
                double worldW = fw * tileSize * fill;
                double worldD = fh * tileSize * fill;
                double worldH = tmpl.getMinBuildingH() + rng.nextDouble()
                        * (tmpl.getMaxBuildingH() - tmpl.getMinBuildingH());

                // Correct center: no half-tile offset bug
                double cx = (x + fw / 2.0) * tileSize;
                double cz = (y + fh / 2.0) * tileSize;

                buildings.add(new BuildingData(
                        "b_" + (idx++), cx, cz, worldW, worldD, worldH,
                        tmpl.getBuildingStyle(), dn, rng.nextInt(6)));
            }
        }
        return buildings;
    }

    public List<BuildingData> placeTrees(CellType[][] grid, int gridW, int gridH,
                                         int[][] districtGrid,
                                         Map<Integer, DistrictTemplate> districtMap) {
        List<BuildingData> trees = new ArrayList<>();
        int idx = 0;

        // ── Dense forest on FOREST cells (70%+ coverage, multiple trees per cell) ──
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                if (grid[x][y] != CellType.FOREST) continue;

                // 2-3 trees per forest cell for dense canopy
                int treesPerCell = 2 + (rng.nextDouble() < 0.4 ? 1 : 0);
                for (int t = 0; t < treesPerCell; t++) {
                    if (rng.nextDouble() > 0.75) continue;
                    double cx = x * tileSize + rng.nextDouble() * tileSize;
                    double cz = y * tileSize + rng.nextDouble() * tileSize;
                    double scale = 0.9 + rng.nextDouble() * 1.8;
                    int treeType = rng.nextDouble() < 0.65 ? -2 : -1; // 65% pine, 35% deciduous
                    trees.add(new BuildingData(
                            "t_" + (idx++), cx, cz, scale, scale, scale * 2,
                            DistrictTemplate.BuildingStyle.FARM, 0, treeType));
                }
            }
        }

        // ── Organized crop fields in FARMLAND (district 3) ──
        // Place crops in rectangular plots aligned to grid blocks
        boolean[][] cropPlaced = new boolean[gridW][gridH];
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                if (grid[x][y] != CellType.EMPTY) continue;
                if (districtGrid[x][y] != 3) continue;
                if (cropPlaced[x][y]) continue;
                if (isAdjacentToRoad(grid, gridW, gridH, x, y)) continue;

                // Try to place a rectangular crop plot (3-6 cells wide/tall)
                int plotW = 2 + rng.nextInt(4);
                int plotH = 2 + rng.nextInt(4);
                // Check if plot fits
                boolean fits = true;
                for (int px = x; px < x + plotW && fits; px++) {
                    for (int py = y; py < y + plotH && fits; py++) {
                        if (px >= gridW || py >= gridH) { fits = false; break; }
                        if (grid[px][py] != CellType.EMPTY) { fits = false; break; }
                        if (districtGrid[px][py] != 3) { fits = false; break; }
                        if (cropPlaced[px][py]) { fits = false; break; }
                        if (isAdjacentToRoad(grid, gridW, gridH, px, py)) { fits = false; break; }
                    }
                }
                if (!fits) continue;
                if (rng.nextDouble() > 0.6) continue;

                // Fill the plot with aligned crop rows
                int cropType = rng.nextInt(3); // 0=wheat, 1=corn rows, 2=mixed
                for (int px = x; px < x + plotW; px++) {
                    for (int py = y; py < y + plotH; py++) {
                        cropPlaced[px][py] = true;
                        double cx = px * tileSize + tileSize / 2;
                        double cz = py * tileSize + tileSize / 2;
                        double h = cropType == 1 ? 1.5 + rng.nextDouble() * 0.5 : 0.8 + rng.nextDouble() * 0.6;
                        trees.add(new BuildingData(
                                "t_" + (idx++), cx, cz, tileSize * 0.92, tileSize * 0.92, h,
                                DistrictTemplate.BuildingStyle.FARM, 3,
                                cropType == 1 ? -4 : -3)); // -3=wheat, -4=corn
                    }
                }
                // Add fence posts at plot corners
                double fx0 = x * tileSize, fz0 = y * tileSize;
                double fx1 = (x + plotW) * tileSize, fz1 = (y + plotH) * tileSize;
                for (double[] corner : new double[][]{{fx0,fz0},{fx1,fz0},{fx0,fz1},{fx1,fz1}}) {
                    trees.add(new BuildingData(
                            "t_" + (idx++), corner[0], corner[1], 0.4, 0.4, 3,
                            DistrictTemplate.BuildingStyle.FARM, 3, -5)); // -5=fence post
                }
            }
        }

        // Scattered oaks in remaining farmland
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                if (grid[x][y] != CellType.EMPTY) continue;
                if (districtGrid[x][y] != 3) continue;
                if (cropPlaced[x][y]) continue;
                if (rng.nextDouble() > 0.06) continue;
                double cx = x * tileSize + tileSize / 2 + (rng.nextDouble() - 0.5) * tileSize * 0.3;
                double cz = y * tileSize + tileSize / 2 + (rng.nextDouble() - 0.5) * tileSize * 0.3;
                double scale = 1.0 + rng.nextDouble() * 1.2;
                trees.add(new BuildingData(
                        "t_" + (idx++), cx, cz, scale, scale, scale * 2,
                        DistrictTemplate.BuildingStyle.FARM, 3, -1));
            }
        }

        // ── Vegetation in all district types for naturalism ──
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                if (grid[x][y] != CellType.EMPTY) continue;
                int dn = districtGrid[x][y];
                if (dn == 0 || dn == 3) continue; // skip unassigned and farmland (handled above)
                DistrictTemplate tmpl = districtMap.get(dn);
                if (tmpl == null) continue;

                double chance;
                int treeType;
                switch (dn) {
                    case 1 -> { chance = 0.08; treeType = -1; }  // Town center: some street trees
                    case 2 -> { chance = 0.10; treeType = -1; }  // Residential: yard trees
                    case 4 -> { chance = 0.05; treeType = -1; }  // Commercial: planted trees
                    case 5 -> { chance = 0.04; treeType = -2; }  // Industrial: sparse evergreens
                    case 6 -> { chance = 0.20; treeType = -2; }  // Highway corridor: pine rows
                    case 7 -> { chance = 0.04; treeType = -1; }  // Downtown: city street trees
                    default -> { chance = 0.05; treeType = rng.nextBoolean() ? -1 : -2; }
                }

                if (rng.nextDouble() > chance) continue;
                double cx = x * tileSize + tileSize / 2 + (rng.nextDouble() - 0.5) * tileSize * 0.3;
                double cz = y * tileSize + tileSize / 2 + (rng.nextDouble() - 0.5) * tileSize * 0.3;
                double scale = 0.6 + rng.nextDouble() * 1.2;
                trees.add(new BuildingData(
                        "t_" + (idx++), cx, cz, scale, scale, scale * 2,
                        DistrictTemplate.BuildingStyle.FARM, dn, treeType));
            }
        }

        // PARK cells are beach/sand — no trees on sand

        return trees;
    }

    private boolean isAdjacentToRoad(CellType[][] grid, int gw, int gh, int x, int y) {
        // Check immediate 4-neighbors (1-cell distance) for direct road access
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && nx < gw && ny >= 0 && ny < gh) {
                CellType c = grid[nx][ny];
                if (c == CellType.ROAD || c == CellType.INTERSECTION) return true;
            }
        }
        return false;
    }

    /** Check if any cell on the footprint perimeter is adjacent to a road. */
    private boolean footprintHasRoadAccess(CellType[][] grid, int gw, int gh,
                                           int sx, int sy, int fw, int fh) {
        // Check all perimeter cells of the footprint
        for (int bx = sx; bx < sx + fw; bx++) {
            for (int by = sy; by < sy + fh; by++) {
                // Only check perimeter cells
                if (bx > sx && bx < sx + fw - 1 && by > sy && by < sy + fh - 1) continue;
                if (isAdjacentToRoad(grid, gw, gh, bx, by)) return true;
            }
        }
        return false;
    }

    private boolean canPlaceFootprint(CellType[][] grid, int gw, int gh,
                                       int[][] districtGrid,
                                       int sx, int sy, int fw, int fh, int dn) {
        for (int bx = sx; bx < sx + fw; bx++) {
            for (int by = sy; by < sy + fh; by++) {
                if (bx < 0 || bx >= gw || by < 0 || by >= gh) return false;
                if (grid[bx][by] != CellType.EMPTY) return false;
                if (districtGrid[bx][by] != dn) return false;
            }
        }
        return true;
    }

    /**
     * BFS to find the largest connected road cluster. Returns a boolean grid
     * where true means this ROAD/INTERSECTION cell is part of the main network.
     */
    private boolean[][] computeRoadConnected(CellType[][] grid, int gw, int gh) {
        boolean[][] visited = new boolean[gw][gh];
        boolean[][] bestCluster = null;
        int bestSize = 0;

        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                CellType c = grid[x][y];
                if ((c != CellType.ROAD && c != CellType.INTERSECTION) || visited[x][y]) continue;

                // BFS from this unvisited road cell
                boolean[][] cluster = new boolean[gw][gh];
                Queue<int[]> queue = new LinkedList<>();
                queue.add(new int[]{x, y});
                visited[x][y] = true;
                cluster[x][y] = true;
                int size = 0;

                while (!queue.isEmpty()) {
                    int[] pos = queue.poll();
                    size++;
                    for (int[] d : DIRS) {
                        int nx = pos[0] + d[0], ny = pos[1] + d[1];
                        if (nx >= 0 && nx < gw && ny >= 0 && ny < gh && !visited[nx][ny]) {
                            CellType nc = grid[nx][ny];
                            if (nc == CellType.ROAD || nc == CellType.INTERSECTION) {
                                visited[nx][ny] = true;
                                cluster[nx][ny] = true;
                                queue.add(new int[]{nx, ny});
                            }
                        }
                    }
                }

                if (size > bestSize) {
                    bestSize = size;
                    bestCluster = cluster;
                }
            }
        }

        return bestCluster != null ? bestCluster : new boolean[gw][gh];
    }

    /**
     * Check if any adjacent ROAD/INTERSECTION cell is part of the connected road network.
     */
    private boolean isAdjacentToConnectedRoad(CellType[][] grid, int gw, int gh,
                                               int x, int y, boolean[][] roadConnected) {
        for (int[] d : DIRS) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && nx < gw && ny >= 0 && ny < gh) {
                CellType c = grid[nx][ny];
                if ((c == CellType.ROAD || c == CellType.INTERSECTION) && roadConnected[nx][ny])
                    return true;
            }
        }
        return false;
    }
}
