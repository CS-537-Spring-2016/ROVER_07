package communication;

import java.util.Arrays;
import java.util.List;

import common.Coord;
import common.MapTile;
import enums.Science;

/** Used to detect stuff on the ROVER'S scanned map.
 *  */
public interface Detector {

    /** A list of all the detectable sciences: Crystal, Mineral, Organic,
     * Radioactive. */
    List<Science> DETECTABLE_SCIENCES = Arrays.asList(Science.CRYSTAL, Science.MINERAL, Science.ORGANIC,
            Science.RADIOACTIVE);

    /** @param map
     *            A scannedMap from your ROVER (i.e. scanMap.getScannedMap())
     * @param roverCoord
     *            Rover current coordinates. Used to determine the absolute,(not
     *            based on your ROVER 7x7 or 11x11 radius but based on the
     *            ENTIRE map), coordinates of the science.
     * @param sightRange
     *            Either 3 if your ROVER radius is 7x7 or 5 if your ROVER radius
     *            is 11x11. This number indicates how many tiles your ROVER can
     *            see to each Direction. Since your ROVER is in the middle of
     *            the Scanned Map, it can only see 3 or 5 tiles NORTH, EAST,
     *            WEST, OR SOUTH
     * @return A list of all the science detected in the scanned map. */
    List<Coord> detectScience(MapTile[][] map, Coord roverCoord, int sightRange);
}
