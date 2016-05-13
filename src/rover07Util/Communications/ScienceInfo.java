package rover07Util.Communications;

import common.Coord;
import enums.Science;
import enums.Terrain;

public class ScienceInfo {
    private final Terrain terrain;
    private final Science science;
    private final Coord coord;

    public ScienceInfo(Terrain terrain, Science science, Coord coord) {
        this.terrain = terrain;
        this.science = science;
        this.coord = coord;
    }

    public Terrain getTerrain() {
        return terrain;
    }

    public Science getScience() {
        return science;
    }

    public Coord getCoord() {
        return coord;
    }
}
