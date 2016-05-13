package rover07Util.Communications;

import common.Coord;
import enums.Science;
import enums.Terrain;

public class Protocol {
    public static String stringify(ScienceInfo info) {
        StringBuilder builder = new StringBuilder();
        builder.append(info.getTerrain().name());
        builder.append(' ');
        builder.append(info.getScience().name());
        builder.append(' ');
        builder.append(info.getCoord().xpos);
        builder.append(' ');
        builder.append(info.getCoord().ypos);
        return builder.toString();
    }

    public static ScienceInfo parse(String line) {
        String[] params = line.split(" ");
        if (params.length < 4) return null;

        Terrain terrain = Terrain.valueOf(params[0]);
        Science science = Science.valueOf(params[1]);
        int x = Integer.parseInt(params[2]);
        int y = Integer.parseInt(params[3]);
        Coord coord = new Coord(x, y);
        return new ScienceInfo(terrain, science, coord);
    }
}
