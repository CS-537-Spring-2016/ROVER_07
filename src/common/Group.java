package common;

/**
 * store essential information for each group
 *
 */
public class Group {

	public String ip;
	public int port;
	public String group;

	public Group(String group, String ip, int port) {
		this.group = group;
		this.ip = ip;
		this.port = port;
	}
}
