package rover07Util;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import common.Coord;

public final class Parser {
	private static Pattern locationRegex = Pattern.compile("(?:.*?)LOC\\s+(\\d+)\\s+(\\d+)");
	
	public static Coord extractLocation(String str) {
		Matcher matcher = locationRegex.matcher(str);
		if (!matcher.matches()) return null;
		MatchResult res = matcher.toMatchResult();
		System.out.println("loc = '" + res.group(1) + "' '" + res.group(2) + "'");
		return new Coord(Integer.parseInt(res.group(1)), Integer.parseInt(res.group(2)));
	}
}
