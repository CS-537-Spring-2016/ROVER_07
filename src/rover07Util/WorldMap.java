package rover07Util;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.Science;
import rover07Util.Pathfinding.Map;
import rover07Util.Pathfinding.MapCell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorldMap implements Map {
    private final static int[][] DELTAS = {
            { 1, 0 }, // east
            { 0, 1 }, // south
            { -1, 0 }, // west
            { 0, -1 }, // north
    };

    private final List<List<WorldMapCell>> map;
    private final int width;
    private final int height;

    public WorldMap(int width, int height) {
        this.width = width;
        this.height = height;

        this.map = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            List<WorldMapCell> row = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                row.add(new WorldMapCell(new Coord(x, y), null));
            }
            map.add(row);
        }
    }

    public WorldMapCell getCell(Coord coord) {
        return getCell(coord.xpos, coord.ypos);
    }

    public Set<WorldMapCell> updateMap(Coord center, ScanMap scan) {
        final Set<WorldMapCell> changed = new HashSet<>();
        final int size = scan.getEdgeSize();
        final int topLeftX = center.xpos - (size >> 1);
        final int topLeftY = center.ypos - (size >> 1);
        final MapTile[][] scanned = scan.getScanMap();

        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                final WorldMapCell cell = getCell(topLeftX + dx, topLeftY + dy);
                if (cell == null) continue;

                final MapTile newTile = scanned[dx][dy];
                final MapTile oldTile = cell.getTile();
                if (oldTile != null && oldTile.getTerrain() == newTile.getTerrain()) {
                    Science oldScience = oldTile.getScience();
                    if (oldScience != Science.NONE || oldScience == newTile.getScience()) {
                        cell.touch();
                        continue;
                    }
                }

                cell.setTile(newTile);
                changed.add(cell);
            }
        }

        return changed;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public WorldMapCell getCell(int x, int y) {
        if (!(0 <= x && x < getWidth())) return null;
        if (!(0 <= y && y < getHeight())) return null;
        return map.get(y).get(x);
    }

    @Override
    public List<MapCell> getNeighbors(MapCell cell) {
        final int x = cell.getX();
        final int y = cell.getY();

        List<MapCell> neighbors = new ArrayList<>();
        for (int[] n : DELTAS) {
            MapCell c = getCell(x + n[0], y + n[1]);
            if (c != null) neighbors.add(c);
        }
        return neighbors;
    }
}
