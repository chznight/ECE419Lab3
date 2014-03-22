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
				//System.out.println("I got "+packetFromClient.type+" from client");
				/* create a packet to send reply back to client */
				MazewarPacket packetToClient = new MazewarPacket();
				/* process request */
				boolean flag=false;
				/* If you want to register */
				if(packetFromClient.type == MazewarPacket.LOOKUP_REGISTER) {
					for(int i=0;i<num_of_players;i++){
						if("".equals(clientLookupTable[i].client_name)){ /*found empty slot*/
							clientLookupTable[i].client_name = packetFromClient.name; /*store name into table*/
							clientLookupTable[i].client_id = i;
							clientLookupTable[i].client_location = new ClientLocation (packetFromClient.locations[0].host, packetFromClient.locations[0].port); /*store location into table*/
							packetToClient.type = MazewarPacket.LOOKUP_REPLY;
							toClient.writeObject(packetToClient);
							flag=true;
							break;
						}
						if(clientLookupTable[i].client_name==packetFromClient.name){/*name already exists, register using new location*/ 
							clientLookupTable[i].client_location = new ClientLocation (packetFromClient.locations[0].host, packetFromClient.locations[0].port);
							client_id = i;
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
				
				/* If you want to request lookup */
				if(packetFromClient.type == MazewarPacket.LOOKUP_REQUEST) {

					System.out.println("You are in request");
					int j = 0;
					for(int i=0;i<num_of_players;i++){
						if(("".equals(clientLookupTable[i].client_name)) == false){ /*if none empty spot*/
							j++;
						}
					}
					packetToClient = new MazewarPacket();
					packetToClient.locations = new ClientLocation[j];
					packetToClient.num_locations = j;
					packetToClient.type = MazewarPacket.LOOKUP_REPLY;
					j = 0;
					for(int i=0;i<num_of_players;i++){
						if(("".equals(clientLookupTable[i].client_name)) == false){ 
							packetToClient.locations[j] = clientLookupTable[i].client_location; /*tell client the location*/
							j++;
						}
					}
					toClient.writeObject(packetToClient);
				}

				if(packetFromClient.type == MazewarPacket.LOOKUP_QUIT) {

					System.out.println("You are in quit");
					for(int i=0;i<num_of_players;i++){
						if(packetFromClient.name.equals(clientLookupTable[i].client_name)){ /*if none empty spot*/
							clientLookupTable[i].client_name = "";
							clientLookupTable.client_location = null;
						}
					}
					packetToClient = new MazewarPacket();
					packetToClient.type = MazewarPacket.LOOKUP_REPLY;
					toClient.writeObject(packetToClient);
				}
				
				/* Sending an ECHO_NULL || ECHO_BYE means quit */
				if (packetFromClient.type == MazewarPacket.MAZEWAR_BYE || packetFromClient.type == MazewarPacket.MAZEWAR_NULL) {
					//packetToClient.type = MazewarPacket.BROKER_NULL;
					//toClient.writeObject(packetToClient);
					//System.out.println ("Naming server thread exiting");
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
