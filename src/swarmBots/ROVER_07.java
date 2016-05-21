package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.RoverName;
import enums.Science;
import enums.Terrain;

import rover07Util.Communications.ScienceInfo;
import rover07Util.Communications.ServerThread;
import rover07Util.Pathfinding.DStarLite;
import rover07Util.Pathfinding.MapCell;
import rover07Util.Query;
import rover07Util.RoverComms;
import rover07Util.WorldMap;
import rover07Util.WorldMapCell;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_07 {
	// connection settings
	final String ROVER_NAME = "ROVER_07";
	String SERVER_ADDRESS = "localhost";
	final int PORT_ADDRESS = 9537;

	// IO
	BufferedReader in;
	PrintWriter out;
	Query q;
	RoverComms comms;

	// rover vars
	Gson gson;
	ScanMap scanMap;
	int sleepTime;
	WorldMap worldMap;

	/**
	 * Constructors
	 */
	public ROVER_07() {
		System.out.println("ROVER_07 rover object constructed");
		SERVER_ADDRESS = "localhost";
		sleepTime = 300; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}

	public ROVER_07(String serverAddress) {
		System.out.println("ROVER_07 rover object constructed");
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
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
	}

	/**
	 * Main rover logic
	 */
	private void mainLoop() throws IOException, InterruptedException {
		boolean goingSouth = true;
		boolean goingEast = true;
		boolean primaryDirection = true; // primary = N/S, secondary = E/W
		int sideMovement = 5;
		Coord lastLoc = null;
		Coord currentLoc = null;

		ArrayList<String> equipment;
		Coord startLoc;
		Coord targetLoc;

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
		
		while (true) {
			if (comms != null) {
				Set<ScienceInfo> commsData = comms.getScience();
				if (commsData != null) {
					for (ScienceInfo info : commsData) {
						System.out.println("received data from other rover: " +
								info.getTerrain() + " " + info.getScience() + " " + info.getCoord());
					}
				}
			}

			// currently the requirements allow sensor calls to be made with no
			// simulated resource cost

			// **** do a LOC ****
			currentLoc = q.getLoc();
			System.out.println("ROVER_07 current location: " + currentLoc);
			
			if (lastLoc != null) {
				if (currentLoc.xpos != lastLoc.xpos || currentLoc.ypos != lastLoc.ypos) {
					sideMovement--; // TODO fix misleading var name
				}
			}



			// ***** do a SCAN *****
			//System.out.println("ROVER_07 sending SCAN request");
			scanMap = q.getScan();
			scanMap.debugPrintMap();

			// merge data
			// TODO check if we need to grow worldMap to accommodate these updates
			boolean replan = false;
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
					if (comms != null) {
						comms.sendScience(new ScienceInfo(tile.getTerrain(), tile.getScience(), cell.getCoord()));
					}
				}
			}

			if (replan && pf != null) {
				pf.updateStart(worldMap.getCell(currentLoc));
			}
			
			
			
			// ***** do a GATHER *****
			q.doGather();



			// ***** move *****
			if (pf == null) {
				pf = new DStarLite(worldMap, worldMap.getCell(currentLoc), worldMap.getCell(targetLoc));
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

			/*
			// pull the MapTile array out of the ScanMap object
			MapTile[][] scanMapTiles = scanMap.getScanMap();
			int centerIndex = (scanMap.getEdgeSize() - 1) / 2;
			
			
			// TODO move to util method
			MapTile tileN = scanMapTiles[centerIndex][centerIndex - 1];
			MapTile tileS = scanMapTiles[centerIndex][centerIndex + 1];
			MapTile tileE = scanMapTiles[centerIndex + 1][centerIndex];
			MapTile tileW = scanMapTiles[centerIndex - 1][centerIndex];
			
			boolean cannotMoveN = tileN.getHasRover() 
					|| tileN.getTerrain() == Terrain.ROCK
					|| tileN.getTerrain() == Terrain.NONE;
			
			boolean cannotMoveS = tileS.getHasRover() 
					|| tileS.getTerrain() == Terrain.ROCK
					|| tileS.getTerrain() == Terrain.NONE;
			
			boolean cannotMoveE = tileE.getHasRover() 
					|| tileE.getTerrain() == Terrain.ROCK
					|| tileE.getTerrain() == Terrain.NONE;
			
			boolean cannotMoveW = tileW.getHasRover() 
					|| tileW.getTerrain() == Terrain.ROCK
					|| tileW.getTerrain() == Terrain.NONE;
			
			if (!primaryDirection && sideMovement <= 0) {
				// reset after moving sideways 5 blocks
				primaryDirection = true;
				goingSouth = true;
			}
			if (primaryDirection) {
				if ((goingSouth && cannotMoveS) || (!goingSouth && cannotMoveN)) {
					if (cannotMoveE && cannotMoveW) {
						goingSouth = !cannotMoveS;
					} else {
						primaryDirection = false;
						goingEast = !cannotMoveE;
						sideMovement = 5;
					}
				}
			} else {
				if ((goingEast && cannotMoveE) || (!goingEast && cannotMoveW)) {
					if (cannotMoveN && cannotMoveS) {
						goingEast = !cannotMoveE;
					} else {
						primaryDirection = true;
						goingSouth = !cannotMoveS;
					}
				}
			}
			
			// send movement
			if (primaryDirection) {
				if (goingSouth) {
					q.doMove("S");
				} else {
					q.doMove("N");
				}
			} else {
				if (goingEast) {
					q.doMove("E");
				} else {
					q.doMove("W");
				}
			}
			*/

			// repeat
			lastLoc = currentLoc;
			Thread.sleep(sleepTime);
			System.out.println("ROVER_07 ------------ bottom process control --------------");
		}
	}

	// ################ Support Methods ###########################

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_07 client = new ROVER_07();
		client.run();
	}
}
