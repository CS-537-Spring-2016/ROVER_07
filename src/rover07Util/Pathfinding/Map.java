package rover07Util.Pathfinding;

import java.util.List;

public interface Map {
    int getWidth();
    int getHeight();
    MapCell getCell(int x, int y);
    List<MapCell> getNeighbors(MapCell cell);
}
