package rover07Util;

import common.Coord;
import common.MapTile;
import enums.Science;
import enums.Terrain;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class GoalPicker {
    private Set<WorldMapCell> possibleCells;

    public GoalPicker() {
        possibleCells = new HashSet<>();
    }

    public void addCell(WorldMapCell cell) {
        if (cell != null) possibleCells.add(cell);
    }

    public Coord getClosestScience(Coord currentLoc) {
        Coord closest = null;
        int distance = Integer.MAX_VALUE;

        Iterator<WorldMapCell> i = possibleCells.iterator();
        while (i.hasNext()) {
            WorldMapCell cell = i.next();
            MapTile tile = cell.getTile();
            if (tile == null) {
                i.remove();
                continue;
            }

            Terrain terrain = tile.getTerrain();
            Science science = tile.getScience();

            // illegal configurations
            if (terrain == null || terrain == Terrain.NONE) {
                i.remove();
                continue;
            }
            if (science == null || science == Science.NONE) {
                i.remove();
                continue;
            }

            // configurations we cannot use
            if (terrain == Terrain.ROCK || terrain == Terrain.GRAVEL) {
                i.remove();
                continue;
            }

            // calculate distance
            Coord here = cell.getCoord();
            int dist = Math.abs(here.xpos - currentLoc.xpos) + Math.abs(here.ypos - currentLoc.ypos);
            if (dist == 0) {
                i.remove();
                continue;
            }
            if (dist < distance) {
                closest = here;
                distance = dist;
            }
        }

        return closest;
    }
}
