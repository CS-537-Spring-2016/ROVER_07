package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.RoverName;
import enums.Science;
import enums.Terrain;

import rover07Util.*;
import rover07Util.Communications.ScienceInfo;
import rover07Util.Communications.ServerThread;
import rover07Util.Pathfinding.DStarLite;
import rover07Util.Pathfinding.MapCell;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_07 {
	// connection settings
	final String ROVER_NAME = "ROVER_07";
	final String SERVER_ADDRESS;
	final int PORT_ADDRESS = 9537;

	// IO
	BufferedReader in;
	PrintWriter out;
	Query q;
	RoverComms comms;

	// rover vars
    int sleepTime = 200; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	Gson gson;
	ScanMap scanMap;
	WorldMap worldMap;

	/**
	 * Constructors
	 */
	public ROVER_07() {
		System.out.println("ROVER_07 rover object constructed");
		SERVER_ADDRESS = "localhost";
	}

	public ROVER_07(String serverAddress) {
		System.out.println("ROVER_07 rover object constructed");
		SERVER_ADDRESS = serverAddress;
	}

	/**
	 * Connects to the server then enters the processing loop.
	 */
	private void run() throws IOException {
		gson = new GsonBuilder().setPrettyPrinting().create();

		// Make connection and initialize streams
		// TODO - need to close this socket
		Socket socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		out = new PrintWriter(socket.getOutputStream(), true);
		
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
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
		worldMap = new WorldMap(targetLoc.xpos + 10, targetLoc.ypos + 10);

        // build GoalPicker
        final GoalPicker goalPicker = new GoalPicker();
        goal = targetLoc; // initial goal
		
		while (true) {
			if (comms != null) {
				Set<ScienceInfo> commsData = comms.getScience();
				if (commsData != null) {
                    for (ScienceInfo info : commsData) {
                        System.out.println("received data from other rover: " +
                                info.getTerrain() + " " + info.getScience() + " " + info.getCoord());

                        goalPicker.addCell(worldMap.getCell(info.getCoord()));
                    }
				}
			}

			// **** do a LOC ****
			currentLoc = q.getLoc();
			System.out.println("ROVER_07 current location: " + currentLoc);



			// ***** do a SCAN *****
			//System.out.println("ROVER_07 sending SCAN request");
			scanMap = q.getScan();
			scanMap.debugPrintMap();



            boolean replan = false;

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
			
			
			
			// ***** do a GATHER *****
			q.doGather();



			// ***** move *****
            Coord bestGoal = goalPicker.getClosestScience(currentLoc);
            if (bestGoal == null) bestGoal = targetLoc;
            if (!bestGoal.equals(goal)) {
                System.out.println("closet science @ " + goal);
                goal = bestGoal;
                pf = null;
            }

			if (pf == null) {
				pf = new DStarLite(worldMap, worldMap.getCell(currentLoc), worldMap.getCell(goal));
				replan = true;
			}

			if (replan) {
				pf.solve();
				path = pf.getPath();

				/*
				System.out.println("--- PATH ---");
				for (MapCell cell : path) {
					System.out.println(new Coord(cell.getX(), cell.getY()));
				}
				*/
			}

			if (!path.isEmpty()) {
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
					}
				} else {
					System.err.println("Nowhere left to go?");
				}
			}

			// repeat
            Thread.sleep(sleepTime);
			System.out.println("ROVER_07 ------------ bottom process control --------------");
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
            client = new ROVER_07();
        }

		client.run();
	}
}
