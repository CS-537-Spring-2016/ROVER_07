package rover07Util;

import enums.RoverName;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RoverPorts {
    private static final Map<RoverName, Integer> mapping;

    static {
        mapping = new HashMap<>();
        mapping.put(RoverName.ROVER_01, 53701);
        mapping.put(RoverName.ROVER_02, 53702);
        mapping.put(RoverName.ROVER_03, 53703);
        mapping.put(RoverName.ROVER_04, 53704);
        mapping.put(RoverName.ROVER_05, 53705);
        mapping.put(RoverName.ROVER_06, 53706);
        mapping.put(RoverName.ROVER_07, 53707);
        mapping.put(RoverName.ROVER_08, 53708);
        mapping.put(RoverName.ROVER_09, 53709);
    }

    public static Set<RoverName> getRovers() {
        return EnumSet.copyOf(mapping.keySet());
    }

    public static int getPort(RoverName rover) {
        return mapping.getOrDefault(rover, 0);
    }
}
