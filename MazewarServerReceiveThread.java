import java.net.*;
import java.util.LinkedList;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.Random;

public class MazewarServerReceiveThread extends Thread {
	private Socket socket = null;	
	private LinkedList<ClientInfo> ClientList;
	private BlockingQueue<MazewarPacket> QueueList;
	private int max_num_players;
	
	public MazewarServerReceiveThread(Socket socket, LinkedList<ClientInfo> ClientList_, BlockingQueue<MazewarPacket> QueueList_, int max_players) {
		super("MazewarServerReceiveThread");
		this.socket = socket;
		ClientList=ClientList_;
		QueueList=QueueList_;
		max_num_players = max_players;
		System.out.println("Created new Thread to handle client (receive)");
	}

	public void run() {

		boolean gotByePacket = false;
		
		try {
			ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
			ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
			MazewarPacket packetFromClient;
			MazewarPacket packetToClient;
	
			/*This thread keeps receiving requests from clients and put them on queue*/
			while(( packetFromClient = (MazewarPacket) fromClient.readObject()) != null) {
				if(packetFromClient.type == MazewarPacket.MAZEWAR_REG) {
					boolean alreadyRegistered=false;
					System.out.println("Received REG from "+packetFromClient.name);
					synchronized(ClientList){
						for(int i=0;i<ClientList.size();i++){
							if(ClientList.get(i).name.equals(packetFromClient.name)){
								alreadyRegistered=true;
						        	System.out.println("> "+packetFromClient.name+" failed to register");
								
								packetToClient=new MazewarPacket();			
								packetToClient.type=MazewarPacket.MAZEWAR_ERROR;
								toClient.writeObject(packetToClient);
						       	break;
							}
						}
					}
					if(!alreadyRegistered){
						ClientInfo newClient=new ClientInfo();
						newClient.name=packetFromClient.name;
						newClient.ip=socket.getInetAddress();
						newClient.port=socket.getPort();
						newClient.fromClient = fromClient;
						newClient.toClient = toClient;
						/*Adding initial coordinate*/
						//Point point;
						boolean collideWithOthers;
						Random randomGen = new Random();
						int pointx, pointy;
						do{
							collideWithOthers=false;
							pointx = randomGen.nextInt(20); //I am using the default width and height here
							pointy = randomGen.nextInt(10);
							synchronized(ClientList){
								for(int i=0;i<ClientList.size();i++){
									if(ClientList.get(i).pointx==pointx&&ClientList.get(i).pointy==pointy){
										collideWithOthers=true; 
										break;
									}
								}
							}
						} while (collideWithOthers==true);
						newClient.pointx=pointx;
						newClient.pointy=pointy;
						synchronized(ClientList){
							ClientList.add(newClient);
						}
						System.out.println("> "+packetFromClient.name+" registered");						

						packetToClient=new MazewarPacket();
						packetToClient.type=MazewarPacket.MAZEWAR_ACK;
						toClient.writeObject(packetToClient);
						System.out.println("Sent ACK to "+packetFromClient.name);

						synchronized(ClientList){
							if(ClientList.size()==max_num_players){
								ClientList.notify();
							}
						}
					}
					/*Maintain the thread*/
					//fromClient.close();
					//toClient.close();
					//socket.close();
				} else if (packetFromClient.type == MazewarPacket.MAZEWAR_REQ) {
					//System.out.println("Received REQ from "+packetFromClient.name);
					QueueList.add(packetFromClient);
					continue;
				} else if (packetFromClient.type == MazewarPacket.MAZEWAR_BYE || packetFromClient.type == MazewarPacket.MAZEWAR_NULL) {
					System.out.println("Received NULL/BYE from "+packetFromClient.name);
					packetToClient=new MazewarPacket();
					packetToClient.type=MazewarPacket.MAZEWAR_BYE;
					toClient.writeObject(packetToClient);
					gotByePacket = true;
					break;
				} else {
					System.err.println("ERROR: Unknown packet!!");
					System.exit(-1);
				}
			}
			/*Remove this client from client list*/
			for(int i=0;i<ClientList.size();i++){
				if(ClientList.get(i).ip==socket.getInetAddress()&&ClientList.get(i).port==socket.getPort()){
					System.out.println("Removed "+ClientList.get(i).name+" from client list");
					ClientList.remove(i);
					break;
				}
			}
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
