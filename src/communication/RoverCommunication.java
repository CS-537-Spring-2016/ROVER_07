package communication;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.Coord;
import common.MapTile;

/** Used to send and or receive locations of sciences to the team.
 *  */
public class RoverCommunication implements Runnable, Detector, Sender {

    private Map<Group, DataOutputStream> groupOutputMap;
    private List<Coord> discoveredSciences;
    private List<Group> groupList;
    private Group group;
    private Receiver receiver;

    public RoverCommunication(Group group, List<Group> groups) throws IOException {
        groupOutputMap = new HashMap<Group, DataOutputStream>();
        discoveredSciences = new ArrayList<Coord>();
        this.group = group;
        groupList = removeSelfFromGroups(groups);
        receiver = new RoverReceiver();
    }

    public void startServer() throws IOException {
        receiver.startServer(new ServerSocket(group.getPort()));
    }

    public Receiver getReceiver() {
        return receiver;
    }

    public List<Coord> getShareScience() {
        return receiver.getSharedCoords();
    }

    /** Scan the map for science. Update rover science list. Share the science
     * to all the ROVERS. Display result on console. Also display the list of
     * connected ROVER and all the SCIENCE shared to you that are not filetered
     * 
     * @param map
     *            Result of scanMap.getScanMap(). Use to check for science
     * @param currentLoc
     *            ROVER current location. Use to calculate the science absolute
     *            location
     * @param sightRange
     *            Either 3, if your radius is 7x7, or 5, if your radius is 11x11
     * @throws IOException */
    public void detectAndShare(MapTile[][] map, Coord currentLoc, int sightRange)
            throws IOException {
        List<Coord> detectedSciences = detectScience(map, currentLoc, sightRange);
        List<Coord> newSciences = updateDiscoveries(detectedSciences);
        for (Coord c : newSciences) {
            shareScience(convertToList(groupOutputMap.values()), c);
        }
        displayAllDiscoveries();
        displayRoversImConnectedTo();
        displayNumRoversConnectedToMe();
        displayShareScience();
    }

    private List<DataOutputStream> convertToList(Collection<DataOutputStream> values) {
        List<DataOutputStream> output_streams = new ArrayList<DataOutputStream>();
        for (DataOutputStream dos : values) {
            output_streams.add(dos);
        }
        return output_streams;
    }

    public void displayNumRoversConnectedToMe() {
        System.out.println(
                group.getName() + " ROVERS-CONNECTED-TO-ME: " + receiver.getRoversConnectedToMe());
    }

    @Override
    public List<Coord> detectScience(MapTile[][] map, Coord rover_coord, int sightRange) {
        List<Coord> science_coords = new ArrayList<Coord>();

        /* iterate through every MapTile Object in the 2D Array. If the MapTile
         * contains science, calculate and save the coordinates of the tiles. */
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {

                MapTile mapTile = map[x][y];

                if (Detector.DETECTABLE_SCIENCES.contains(mapTile.getScience())) {
                    int tileX = rover_coord.xpos + (x - sightRange);
                    int tileY = rover_coord.ypos + (y - sightRange);
                    Coord coord = new Coord(mapTile.getTerrain(), mapTile.getScience(), tileX,
                            tileY);
                    science_coords.add(coord);
                }
            }
        }
        return science_coords;
    }

    public void displayAllDiscoveries() {
        System.out.println(group.getName() + " SCIENCE-DISCOVERED-BY-ME: "
                + toProtocolString(discoveredSciences));
        System.out.println(group.getName() + " TOTAL-NUMBER-OF-SCIENCE-DISCOVERED-BY-ME: "
                + discoveredSciences.size());
    }

    public void displayRoversImConnectedTo() {
        System.out.println(group.getName() + " CONNECTIONS: " + groupOutputMap.keySet());
        System.out.println(
                group.getName() + " ROVERS-IM-CONNECTED-TO: " + groupOutputMap.keySet().size());
    }

    public void displayShareScience() {
        System.out.println(
                group.getName() + " SCIENCES-SHARED-TO-ME: " + toProtocolString(getShareScience()));
        System.out.println(
                group.getName() + " TOTAL-SCIENCE-SHARED-TO-ME: " + getShareScience().size());
    }

    private List<Group> removeSelfFromGroups(List<Group> groups) {
        List<Group> groupsWithoutMe = new ArrayList<Group>();
        for (Group g : groups) {
            if (!g.getName().equals(group.getName())) {
                groupsWithoutMe.add(g);

            }
        }
        return groupsWithoutMe;
    }

    @Override
    public void run() {

        /* Will try to connect to all the ROVERs on a separate Thread. Add them
         * to a list if connection is successful. */
        for (Group group : groupList) {

            new Thread(() -> {
                final int MAX_ATTEMPTS = 60;
                final int TIME_WAIT_TILL_NEXT_ATTEMPT = 1000; // milliseconds
                int attempts = 0;
                Socket socket = null;

                do {
                    try {
                        socket = new Socket(group.getIp(), group.getPort());
                        groupOutputMap.put(group, new DataOutputStream(socket.getOutputStream()));
                        System.out.println(group.getName() + " CONNECTED TO " + group);
                    } catch (Exception e) {
                        try {
                            Thread.sleep(TIME_WAIT_TILL_NEXT_ATTEMPT);
                            attempts++;
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                } while (socket == null && attempts <= MAX_ATTEMPTS);
            }).start();
        }
    }

    /** @param coords
     *            Coord with Science
     * @return A list of Coord.toProtocol(). For example (SOIL CRYSTAL 5 3, ROCK
     *         MINERAL 52 13) */
    private String toProtocolString(List<Coord> coords) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = coords.size() - 1; i >= 0; i--) {
            sb.append(coords.get(i).toProtocol() + " ");
        }
        sb.append("]");
        return sb.toString();
    }

    /** @param detectedSciences
     *            The science that your ROVER found on its scanned map
     * @return A list of Coordinates that are new. Will compare the
     *         detected_sciences list with the ALL the science the ROVER has
     *         discovered. The result , what this method is returning, is the
     *         difference between detected_sciences and all the sciences
     *         discovered so far by the ROVER */
    public List<Coord> updateDiscoveries(List<Coord> detectedSciences) {
        List<Coord> new_sciences = new ArrayList<Coord>();
        for (Coord c : detectedSciences) {
            if (!discoveredSciences.contains(c)) {
                discoveredSciences.add(c);
                new_sciences.add(c);
            }
        }
        return new_sciences;
    }

    @Override
    public void shareScience(List<DataOutputStream> outputStreams, Coord coord) throws IOException {
        for (DataOutputStream dos : outputStreams) {
            dos.writeBytes(coord.toProtocol() + "\n");
            dos.flush();
        }
    }
}
