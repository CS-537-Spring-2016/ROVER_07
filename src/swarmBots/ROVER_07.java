package swarmBots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.RoverName;
import enums.Science;
import enums.Terrain;
import rover07Util.Communications.ScienceInfo;
import rover07Util.*;
import rover07Util.Pathfinding.DStarLite;
import rover07Util.Pathfinding.MapCell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_07 {
    // connection settings
    private final String ROVER_NAME = "ROVER_07";
    private final String SERVER_ADDRESS;
    private final int PORT_ADDRESS = 9537;

    private Query q;
    private RoverComms comms;

    /**
     * Constructors
     */
    public ROVER_07(String serverAddress) {
        System.out.println("ROVER_07 rover object constructed");
        SERVER_ADDRESS = serverAddress;
    }

    /**
     * Connects to the server then enters the processing loop.
     */
    private void run() throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Make connection and initialize streams
        Socket socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        q = new Query(in, out, gson);

        // Set up rover communications thread
        try {
            comms = new RoverComms(RoverName.getEnum(ROVER_NAME));
        } catch (IOException e) {
            comms = null;
            System.err.println("Failed to initialize rover connection");
            e.printStackTrace();
        }

        // Process all messages from server, wait until server requests Rover ID name
        while (true) {
            String line = in.readLine();
            if (line.startsWith("SUBMITNAME")) {
                // This sets the name of this instance of a swarmBot for identifying the thread to the server
                out.println(ROVER_NAME);
                break;
            }
        }

        // Enter main loop
        try {
            mainLoop();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        try {
            socket.close();
        } catch (Exception e) {
            System.err.println("Failed to close socket");
            e.printStackTrace();
        }

        if (comms != null) comms.close();
    }

    /**
     * Main rover logic
     */
    private void mainLoop() throws IOException, InterruptedException {
        ArrayList<String> equipment;
        Coord startLoc;
        Coord targetLoc;

        Coord currentLoc;
        Coord goal;

        DStarLite pf = null;
        List<MapCell> path = null;
        Set<WorldMapCell> roverCells = new HashSet<>();

        int failures = 0;

        /**
         *  Get initial values that won't change
         */
        // get EQUIPMENT
        equipment = q.getEquipment();
        System.out.println(ROVER_NAME + " equipment list results " + equipment + "\n");

        // get START_LOC
        startLoc = q.getLoc(Query.LocType.START);
        System.out.println(ROVER_NAME + " START_LOC " + startLoc);

        // get TARGET_LOC
        targetLoc = q.getLoc(Query.LocType.TARGET);
        System.out.println(ROVER_NAME + " TARGET_LOC " + targetLoc);

        // build WorldMap
        WorldMap worldMap = new WorldMap(targetLoc.xpos + 10, targetLoc.ypos + 10);

        // build GoalPicker
        final GoalPicker goalPicker = new GoalPicker();
        goalPicker.addDefault(worldMap.getCell(targetLoc)); // target corner
        goalPicker.addDefault(worldMap.getCell(startLoc.xpos, targetLoc.ypos)); // top right corner
        goalPicker.addDefault(worldMap.getCell(targetLoc.xpos, startLoc.ypos)); // bottom left corner

        goal = targetLoc; // initial goal

        while (true) {
            if (comms != null) {
                Set<ScienceInfo> commsData = comms.getScience();
                if (commsData != null) {
                    for (ScienceInfo info : commsData) {
                        System.out.println("received data from other rover: " +
                                info.getTerrain() + " " + info.getScience() + " " + info.getCoord());

                        // get cell from world map
                        WorldMapCell cell = worldMap.getCell(info.getCoord());
                        if (cell == null) continue;

                        // update tile
                        MapTile tile = cell.getTile();
                        if (tile == null) {
                            tile = new MapTile(info.getTerrain(), info.getScience(), 0, false);
                            cell.setTile(tile);

                            // if we had to generate a new tile, we need to check if it's traversable for pf
                            if (tile.getTerrain() == Terrain.NONE || tile.getTerrain() == Terrain.ROCK) {
                                cell.setBlocked(true);
                                if (pf != null) pf.markChangedCell(cell);
                            }
                        } else {
                            tile.setSciecne(info.getScience());
                        }

                        // add to goalpicker
                        goalPicker.addCell(worldMap.getCell(info.getCoord()));
                    }
                }
            }

            // **** do a LOC ****
            currentLoc = q.getLoc();
            System.out.println("ROVER_07 current location: " + currentLoc);



            // ***** do a SCAN *****
            //System.out.println("ROVER_07 sending SCAN request");
            ScanMap scanMap = q.getScan();
            //scanMap.debugPrintMap();



            boolean replan = false;

            { // check if we need to grow world map
                int extentX = 0;
                int extentY = 0;

                final int scanSize = scanMap.getEdgeSize();
                final MapTile[][] map = scanMap.getScanMap();
                for (int x = 0; x < scanSize; x++) {
                    for (int y = 0; y < scanSize; y++) {
                        if (x <= extentX && y <= extentY) continue;
                        final MapTile tile = map[x][y];
                        if (tile.getTerrain() != Terrain.NONE) {
                            extentX = Math.max(x, extentX);
                            extentY = Math.max(y, extentY);
                        }
                    }
                }

                final int maxX = currentLoc.xpos - (scanSize >> 1) + extentX;
                final int maxY = currentLoc.ypos - (scanSize >> 1) + extentY;

                final int growX = Math.max(maxX - worldMap.getWidth(), 0);
                final int growY = Math.max(maxY - worldMap.getHeight(), 0);

                if (growX + growY > 0) {
                    worldMap.grow(growX, growY);
                    if (pf != null) pf = new DStarLite(worldMap, worldMap.getCell(currentLoc), worldMap.getCell(goal));
                    replan = true;
                }
            }

            // merge terrain/science changes
            Set<WorldMapCell> changes = worldMap.updateMap(currentLoc, scanMap);
            for (WorldMapCell cell : changes) {
                final MapTile tile = cell.getTile();
                //System.out.println("learned " + cell.getCoord() + " is " +
                //		tile.getTerrain().getTerString() + tile.getScience().getSciString());

                // check not traversable
                if (tile.getTerrain() == Terrain.NONE || tile.getTerrain() == Terrain.ROCK) {
                    cell.setBlocked(true);
                    if (pf != null) pf.markChangedCell(cell);
                    replan = true;
                }

                // communicate science
                if (tile.getScience() != Science.NONE) {
                    goalPicker.addCell(cell);

                    if (comms != null) {
                        comms.sendScience(new ScienceInfo(tile.getTerrain(), tile.getScience(), cell.getCoord()));
                    }
                }
            }

            // mark rover cells as blocked
            {
                // unblock last set
                for (WorldMapCell cell : roverCells) {
                    cell.setBlocked(false);
                    if (pf != null) pf.markChangedCell(cell);
                    replan = true;
                }
                roverCells.clear();

                // block new set
                final int scanSize = scanMap.getEdgeSize();
                final MapTile[][] scanTiles = scanMap.getScanMap();
                final int radius = scanSize >> 1;
                final int startX = currentLoc.xpos - radius;
                final int startY = currentLoc.ypos - radius;
                for (int dx = 0; dx < scanSize; dx++) {
                    for (int dy = 0; dy < scanSize; dy++) {
                        if (dx == radius && dy == radius) continue; // don't block our own spot

                        if (scanTiles[dx][dy].getHasRover()) {
                            WorldMapCell cell = worldMap.getCell(startX + dx, startY + dy);
                            if (cell == null || cell.isBlocked()) continue;

                            cell.setBlocked(true);
                            roverCells.add(cell);
                            if (pf != null) pf.markChangedCell(cell);
                            replan = true;
                        }
                    }
                }
            }



            // ***** do a GATHER *****
            q.doGather();
            { // unmark any gatherable science at this loc
                MapTile tile = worldMap.getCell(currentLoc).getTile();
                Terrain terr = tile.getTerrain();
                if (terr != Terrain.ROCK && terr != Terrain.GRAVEL) {
                    tile.setSciecne(Science.NONE);
                }
            }



            // ***** move *****
            Coord bestGoal = goalPicker.getClosestGoal(currentLoc);
            if (bestGoal == null) {
                // TODO perform clustering to find least discovered areas?
                bestGoal = targetLoc;
            }
            if (!bestGoal.equals(goal)) {
                System.out.println("best goal -> " + bestGoal);
                goal = bestGoal;
                failures = 0;
                pf = null;
            }

            // if pf is null, we need to make a new instance of D*Lite
            if (pf == null) {
                pf = new DStarLite(worldMap, worldMap.getCell(currentLoc), worldMap.getCell(goal));
                replan = true;
            }

            // if replan is true, we need to (re)calculate a path
            if (replan) {
                pf.updateStart(worldMap.getCell(currentLoc));
                pf.solve();
                path = pf.getPath();
            }

            if (path.isEmpty()) {
                ++failures;
                if (failures >= 5) {
                    goalPicker.removeGoal(worldMap.getCell(goal));
                }
            } else {
                MapCell next;

                while (true) {
                    if (path.isEmpty()) {
                        next = null;
                        break;
                    }
                    next = path.get(0);
                    if (currentLoc.xpos == next.getX() && currentLoc.ypos == next.getY()) {
                        path.remove(0);
                    } else {
                        break;
                    }
                }

                if (next != null) {
                    if (next.getX() == currentLoc.xpos + 1) {
                        q.doMove("E");
                    } else if (next.getY() == currentLoc.ypos + 1) {
                        q.doMove("S");
                    } else if (next.getX() == currentLoc.xpos - 1) {
                        q.doMove("W");
                    } else if (next.getY() == currentLoc.ypos - 1) {
                        q.doMove("N");
                    } else {
                        System.err.println("Can't find which way to move: " +
                                "(" + currentLoc.xpos + "," + currentLoc.ypos + ") -> " +
                                "(" + next.getX() + "," + next.getY() + ")");
                        pf = null;
                    }

                    failures = 0;
                } else {
                    System.err.println("Nowhere left to go?");
                }
            }

            // repeat
            Thread.sleep(200);
            //System.out.println("ROVER_07 ------------ bottom process control --------------");
        }
    }

    // ################ Support Methods ###########################

    /**
     * Runs the client
     */
    public static void main(String[] args) throws Exception {
        ROVER_07 client;

        if (args.length > 0) {
            client = new ROVER_07(args[0]);
        } else {
            client = new ROVER_07("localhost");
        }

        client.run();
    }
}
