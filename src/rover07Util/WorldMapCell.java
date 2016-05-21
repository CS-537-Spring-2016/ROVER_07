package rover07Util;

import common.Coord;
import common.MapTile;
import rover07Util.Pathfinding.MapCell;

public class WorldMapCell implements MapCell {
    private final Coord pos;

    private MapTile tile;
    private long lastUpdate;
    private int cost;
    private boolean blocked;

    public WorldMapCell(Coord pos, MapTile tile) {
        this.pos = pos;
        this.tile = tile;
        lastUpdate = 0;
        cost = 1;
        blocked = false;
    }

    public Coord getCoord() {
        return pos;
    }

    public MapTile getTile() {
        return tile;
    }

    public void setTile(MapTile tile) {
        this.tile = tile;
        touch();
    }

    public void touch() {
        lastUpdate = System.currentTimeMillis();
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    @Override
    public int getX() {
        return getCoord().xpos;
    }

    @Override
    public int getY() {
        return getCoord().ypos;
    }

    @Override
    public int getCost() {
        return cost;
    }

    @Override
    public boolean isBlocked() {
        return blocked;
    }
}
