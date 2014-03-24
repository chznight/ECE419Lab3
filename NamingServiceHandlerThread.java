import java.net.*;
import java.io.*;
import java.util.HashMap;

public class NamingServiceHandlerThread extends Thread {
	private Socket socket = null;

	private LookupTable[] clientLookupTable;
	private int num_of_players;

	public NamingServiceHandlerThread(Socket socket, LookupTable[] clientLookupTable_, int num_of_players_) {
		super("NamingServiceHandlerThread");
		this.socket = socket;
		clientLookupTable = clientLookupTable_;
		num_of_players = num_of_players_;
		System.out.println("Naming Service: Created new Thread to handle client");
	}

	public void run() {

		boolean gotByePacket = false;
		
		try {
			/* stream to read from client */
			ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
			toClient.flush();
			ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
			MazewarPacket packetFromClient;
			
			while (( packetFromClient = (MazewarPacket) fromClient.readObject()) != null) {
				MazewarPacket packetToClient = new MazewarPacket();
				//boolean flag=false;
				
				/*If name already exists, shouldn't we prompt that client to enter new name?*/
				if(packetFromClient.type == MazewarPacket.LOOKUP_REGISTER) {
					System.out.println("Received REGISTER");
					boolean flag=false;
					for(int i=0;i<num_of_players;i++){
						if("".equals(clientLookupTable[i].client_name)){ /*found empty slot*/
							clientLookupTable[i].client_name = packetFromClient.name; 
							clientLookupTable[i].client_id = i;
							clientLookupTable[i].client_location = new ClientLocation (packetFromClient.locations[0].client_host, packetFromClient.locations[0].client_port); /*store location into table*/
							packetToClient.type = MazewarPacket.LOOKUP_REPLY;
							packetToClient.client_id = i;
							toClient.writeObject(packetToClient);
							flag=true;
							break;
						}
						if(clientLookupTable[i].client_name==packetFromClient.name){/*name already exists, register using new location*/ 
							clientLookupTable[i].client_location = new ClientLocation (packetFromClient.locations[0].client_host, packetFromClient.locations[0].client_port);
							clientLookupTable[i].client_id = i;
							packetToClient.type = MazewarPacket.LOOKUP_REPLY;
							toClient.writeObject(packetToClient);
							flag=true;
							break;											
						}
					}

					if(flag==true){
						continue;
					}else{
						//exceeded num_of_players, did not register
						continue;												
					}
				}
				
				if(packetFromClient.type == MazewarPacket.LOOKUP_REQUEST) {
					System.out.println("Received REQUEST");
					int j = 0;
					for(int i=0;i<num_of_players;i++){
						if("".equals(clientLookupTable[i].client_name)) /*reaches empty spot*/
							break;
						j++;
					}
					packetToClient = new MazewarPacket();
					packetToClient.locations = new ClientLocation[j];
					packetToClient.num_locations = j;
					packetToClient.type = MazewarPacket.LOOKUP_REPLY;
					j = 0; //Reset j
					for(int i=0;i<num_of_players;i++){
						if("".equals(clientLookupTable[i].client_name))
							break;
						packetToClient.locations[j++] = clientLookupTable[i].client_location; /*fill in locations*/
					}
					toClient.writeObject(packetToClient);
					continue;
				}

				/*What is this for again?*/
				if(packetFromClient.type == MazewarPacket.LOOKUP_UNREGISTER) {
					System.out.println("Received UNREGISTER");
					System.out.println("Unregistering client " + packetFromClient.name);
					for(int i=0;i<num_of_players;i++){
						if(clientLookupTable[i].client_name.equals(packetFromClient.name)){ 
							clientLookupTable[i].client_name = ""; 
							clientLookupTable[i].client_location = null;
							break;
						}
					}
					break;
				}
				
				if (packetFromClient.type == MazewarPacket.MAZEWAR_BYE || packetFromClient.type == MazewarPacket.MAZEWAR_NULL) {
					System.out.println("Received BYE/NULL");
					gotByePacket = true;
					break;
				}
				
				/* if code comes here, there is an error in the packet */
				System.err.println("ERROR: Unknown ECHO_* packet!!");
				System.exit(-1);
			}
			
			/* cleanup when client exits */
			fromClient.close();
			toClient.close();
			socket.close();

		} catch (IOException e) {
			if(!gotByePacket)
				e.printStackTrace();
		} catch (ClassNotFoundException e) {
			if(!gotByePacket)
				e.printStackTrace();
		}
	}

}
