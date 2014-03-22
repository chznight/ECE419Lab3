import java.net.*;
import java.util.LinkedList;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MazewarServerSendThread extends Thread {
	private LinkedList<ClientInfo> ClientList;
	private BlockingQueue<MazewarPacket> QueueList;
	private boolean game_start;

	public MazewarServerSendThread(LinkedList<ClientInfo> ClientList_, BlockingQueue<MazewarPacket> QueueList_) {
		super("MazewarServerSendThread");
		ClientList=ClientList_;
		QueueList=QueueList_;
		game_start = true;
		System.out.println("Created new Thread to handle client (send)");
	}

	public synchronized void start_game () {
		game_start = true;
	}

	public synchronized void stop_game () {
		game_start = false;
	}

	public void run() {
		Socket socket=null;
		ObjectOutputStream toClient;
		MazewarPacket packetToClient;
		MazewarPacket headPacketFromQueue;

		try{

			synchronized(ClientList){
				ClientList.wait();
			}

			//System.out.println("Sending STR to...");
			synchronized(ClientList){
				for(int i=0;i<ClientList.size();i++){
					for (int j=0; j<ClientList.size(); j++) {
						
						packetToClient = new MazewarPacket();
						packetToClient.type=MazewarPacket.MAZEWAR_INITIALIZE_PLAYERS;
						packetToClient.pointx = ClientList.get(j).pointx;
						packetToClient.pointy = ClientList.get(j).pointy;
						packetToClient.name = ClientList.get(j).name;
						//packetToClient.direction = ClientList.get(j).direction;
						ClientList.get(i).toClient.writeObject(packetToClient);
						//System.out.println("...to "+ClientList.get(i).name);
					}

				}					
			}

			synchronized(ClientList){
				for(int i=0;i<ClientList.size();i++){
					packetToClient = new MazewarPacket();
					packetToClient.type=MazewarPacket.MAZEWAR_STR;
					ClientList.get(i).toClient.writeObject(packetToClient);
				}
			}


			while(game_start){ /*This is no good*/
					headPacketFromQueue=QueueList.poll();
					if(headPacketFromQueue!=null){
						packetToClient = new MazewarPacket();
						packetToClient = headPacketFromQueue;
						//System.out.println("Sending BRC by "+packetToClient.name+"...");
						synchronized(ClientList){
							for(int i=0;i<ClientList.size();i++){
								ClientList.get(i).toClient.writeObject(packetToClient);
								//System.out.println("...to "+ClientList.get(i).name);
							}				
						}	
					}	
				
			}
		}catch (IOException e) {
			e.printStackTrace();
		} catch(InterruptedException e){
			//return;
		}
	}

}
