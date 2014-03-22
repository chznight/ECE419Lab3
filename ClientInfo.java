import java.util.*;
import java.net.*;
import java.util.LinkedList;
import java.io.*;

public class ClientInfo implements Serializable{
	String name;
	InetAddress ip;
	int port;
	ObjectInputStream fromClient;
	ObjectOutputStream toClient;
	int pointx;
	int pointy;
	int direction;
}
