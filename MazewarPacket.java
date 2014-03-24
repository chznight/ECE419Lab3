import java.io.Serializable;

class ClientLocation implements Serializable {
	public String  client_host;
	public Integer client_port;
	
	/* constructor */
	public ClientLocation(String host, Integer port) {
		this.client_host = host;
		this.client_port = port;
	}
	
	/* printable output */
	public String toString() {
		return " HOST: " + client_host + " PORT: " + client_port; 
	}
	
}

public class MazewarPacket implements Serializable {

	public static final int MAZEWAR_NULL = 0;
	public static final int MAZEWAR_BYE  = 1;
	public static final int MAZEWAR_REG = 2;
	public static final int MAZEWAR_ACK = 3;
	public static final int MAZEWAR_STR = 15;
	public static final int MAZEWAR_REQ = 4;
	public static final int MAZEWAR_BRC = 5;

	public static final int MAZEWAR_SETDIRECTION = 6;
	public static final int MAZEWAR_SETPOINT = 7;

	public static final int MAZEWAR_FIRE = 9;
	public static final int MAZEWAR_MOVEFORWARD = 11;
	public static final int MAZEWAR_MOVEBACKWARD = 12;
	public static final int MAZEWAR_ROTATELEFT = 13;
	public static final int MAZEWAR_ROTATERIGHT = 14;
	public static final int MAZEWAR_INITIALIZE_PLAYERS = 16;



	public static final int MAZEWAR_ERROR = 123;

	public static final int ERROR_UNKNOWN	=-1;

	public static final int PLAYER_SETUP_REQ = 401;
	public static final int PLAYER_SETUP_REPLY_OK = 402;
	public static final int PLAYER_SETUP_REPLY_FAIL = 403;
	public static final int PLAYER_SETUP_FINAL = 404;

	//USE THESE FOR NAMING SERVICE!
	public static final int LOOKUP_REQUEST  = 301;
	public static final int LOOKUP_REPLY    = 302;
	public static final int LOOKUP_REGISTER = 303;
	public static final int LOOKUP_ACK    = 304;
	public static final int LOOKUP_UNREGISTER = 305;


	
	public int type = MazewarPacket.MAZEWAR_NULL;
	public int command;
	public String name;
	public int client_id;
	public String content;
	public int pointx;
	public int pointy;
	public int direction;
	public int error_code;
	public int num_locations;
	public ClientLocation locations[];

}