package rover07Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import common.Coord;
import common.ScanMap;

public class Query {
	public enum LocType {
		HERE("LOC"),
		START("START_LOC"),
		TARGET("TARGET_LOC");
		
		private String command;
		
		LocType(String command) {
			this.command = command;
		}
		
		public String getCommand() {
			return command;
		}
	}
	
	private BufferedReader in;
	private PrintWriter out;
	private Gson gson;
	
	public Query(BufferedReader in, PrintWriter out, Gson gson) {
		this.in = in;
		this.out = out;
		this.gson = gson;
	}
	
	private void flush() throws IOException {
		while (in.ready()) in.readLine();
	}
	
	public ArrayList<String> getEquipment() throws IOException {
		//System.out.println("ROVER_07 method getEquipment()");
		out.println("EQUIPMENT");

		String jsonEqListIn = in.readLine(); // get first reply
		if (jsonEqListIn == null || !jsonEqListIn.startsWith("EQUIPMENT")) {
			// if no match, bail
			flush();
			return null;
		}

		// start building string of json data
		StringBuilder jsonEqList = new StringBuilder();
		while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {
			jsonEqList.append(jsonEqListIn);
		}

		// return parsed result
		return gson.fromJson(jsonEqList.toString(), new TypeToken<ArrayList<String>>(){}.getType());
	}
	
	public Coord getLoc() throws IOException {
		return getLoc(LocType.HERE);
	}
	
	public Coord getLoc(LocType type) throws IOException {
		out.println(type.getCommand());
		
		String line = in.readLine();
        if (line == null || !line.startsWith(type.getCommand())) {
        	flush();
        	return null;
        }
        
		return Parser.extractLocation(line);
	}

	public ScanMap getScan() throws IOException {
		out.println("SCAN");

		String jsonScanMapIn = in.readLine(); // get first reply
		if (jsonScanMapIn == null || !jsonScanMapIn.startsWith("SCAN")){
			// if no match, bail
			flush();
			return null;
		}

		// start building string of json data
		StringBuilder jsonScanMap = new StringBuilder();	
		while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
			jsonScanMap.append(jsonScanMapIn);
		}

		// return parsed result
		return gson.fromJson(jsonScanMap.toString(), ScanMap.class);
	}
	
	public void doMove(String dir) {
		out.println("MOVE " + dir);
	}
}
