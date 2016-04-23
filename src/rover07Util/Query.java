package rover07Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import common.Coord;
import common.ScanMap;
import enums.Science;

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

	private String sendAndGetReply(String command) throws IOException {
		out.println(command);
		String reply = in.readLine();
		if (reply == null || !reply.startsWith(command)) {
			flush();
			return null;
		}
		return reply;
	}
	
	public ArrayList<String> getEquipment() throws IOException {
		//System.out.println("ROVER_07 method getEquipment()");
		if (sendAndGetReply("EQUIPMENT") == null) return null;

		// start building string of json data
		String jsonEqListIn;
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
		String line = sendAndGetReply(type.getCommand());
		if (line == null) return null;
		return Parser.extractLocation(line);
	}

	public ScanMap getScan() throws IOException {
		if (sendAndGetReply("SCAN") == null) return null;

		// start building string of json data
		String jsonScanMapIn;
		StringBuilder jsonScanMap = new StringBuilder();	
		while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
			jsonScanMap.append(jsonScanMapIn);
		}

		// return parsed result
		return gson.fromJson(jsonScanMap.toString(), ScanMap.class);
	}
	
	public ArrayList<Science> getCargo() throws IOException {
		if (sendAndGetReply("CARGO") == null) return null;

		// start building string of json data
		String jsonCargoListIn;
		StringBuilder jsonCargoList = new StringBuilder();	
		while (!(jsonCargoListIn = in.readLine()).equals("CARGO_END")) {
			jsonCargoList.append(jsonCargoListIn);
		}

		// return parsed result
		return gson.fromJson(jsonCargoList.toString(), new TypeToken<ArrayList<Science>>(){}.getType());
	}
	
	public void doMove(String dir) {
		out.println("MOVE " + dir);
	}
	
	public void doGather() {
		out.println("GATHER");
	}
}
