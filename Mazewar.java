/*
Copyright (C) 2004 Geoffrey Alan Washburn
   
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
   
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
   
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
*/
  
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.BorderFactory;
import java.io.Serializable;
import java.util.*; 
import java.net.*;
import java.io.*;
import java.lang.Runnable;
import java.util.concurrent.*;
import java.util.Random;


/**
 * The entry point and glue code for the game.  It also contains some helpful
 * global utility methods.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame implements Runnable {


        private Socket mazewarSocket = null;
        private Socket lookupSocket = null;
        public Socket [] outSockets;
        //public Socket [] inSockets;
        public ServerSocket serverSocket = null;

        private ObjectOutputStream out;
        private ObjectInputStream in;
        private ObjectOutputStream [] out_to_clients;
        //private static ObjectInputStream [] in_from_clients;

        public BlockingQueue<MazewarPacket> clientCommandQueue;
        public BlockingQueue<MazewarPacket> clientBroadcastQueue;
        public BlockingQueue<MazewarPacket> tokenQueue;
        public BlockingQueue<MazewarPacket> clientCommandQueueOutOfOrder;

        private String gui_player_name;
        public  int gui_player_id;
        private static String hostname;
        private static int port;    //port for naming service
        private static int myPort; //port for this client
        private static int num_players;
        private LamportClock lamport_clock;
        private static boolean listening = true;

        private Thread receive_thread;
        //private boolean receive_thread_run = false;

        /**
         * The default width of the {@link Maze}.
         */
        private final int mazeWidth = 20;

        /**
         * The default height of the {@link Maze}.
         */
        private final int mazeHeight = 10;

        /**
         * The default random seed for the {@link Maze}.
         * All implementations of the same protocol must use 
         * the same seed value, or your mazes will be different.
         */
        private final int mazeSeed = 42;

        /**
         * The {@link Maze} that the game uses.
         */
        private Maze maze = null;

        /**
         * The {@link GUIClient} for the game.
         */
        private GUIClient guiClient = null;

        /**
         * The panel that displays the {@link Maze}.
         */
        private OverheadMazePanel overheadPanel = null;

        /**
         * The table the displays the scores.
         */
        private JTable scoreTable = null;
        
        //private static boolean listening = true;
        /** 
         * Create the textpane statically so that we can 
         * write to it globally using
         * the static consolePrint methods  
         */
        private static final JTextPane console = new JTextPane();
      
        /** 
         * Write a message to the console followed by a newline.
         * @param msg The {@link String} to print.
         */ 
        public static synchronized void consolePrintLn(String msg) {
                console.setText(console.getText()+msg+"\n");
        }
        
        /** 
         * Write a message to the console.
         * @param msg The {@link String} to print.
         */ 
        public static synchronized void consolePrint(String msg) {
                console.setText(console.getText()+msg);
        }
        
        /** 
         * Clear the console. 
         */
        public static synchronized void clearConsole() {
           console.setText("");
        }
        
        /**
         * Static method for performing cleanup before exiting the game.
         */

        public static void quit() {
                // Put any network clean-up code you might have here.
                // (inform other implementations on the network that you have 
                //  left, etc.)
            //stop_listening();
            // try {
            //     for (int i=0; i<num_players; i++) {
            //         outSockets[i].close();
            //         serverSocket.close();
            //     }
            // } catch (IOException e) {
            //     //System.exit(0);
            // }

            listening = false;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            

            System.exit(0);
        }


        public synchronized void unregisterClient(String name) {
            try {
                lookupSocket = new Socket(hostname, port);
                out = new ObjectOutputStream(lookupSocket.getOutputStream());
                MazewarPacket packetToServer=new MazewarPacket();
                packetToServer.type=MazewarPacket.LOOKUP_UNREGISTER;
                packetToServer.name=name;
                out.writeObject(packetToServer);
                out.close();
                lookupSocket.close();
            } catch (IOException e) {
                    System.err.println("ERROR: Couldn't get I/O for the connection.");
                    System.err.println("ERROR: Can not unregister.");
            }

        }

       
        /** 
         * The place where all the pieces are put together. 
         */
        public Mazewar() {
        
                super("ECE419 Mazewar");
                consolePrintLn("ECE419 Mazewar started!");
                
                // Create the maze
                maze = new MazeImpl(new Point(mazeWidth, mazeHeight), mazeSeed);
                assert(maze != null);
                // Have the ScoreTableModel listen to the maze to find
                // out how to adjust scores.
                ScoreTableModel scoreModel = new ScoreTableModel();
                assert(scoreModel != null);
                maze.addMazeListener(scoreModel);
                
                // Throw up a dialog to get the GUIClient name.
                String name = JOptionPane.showInputDialog("Enter your name");
                if((name == null) || (name.length() == 0)) {
                  Mazewar.quit();
                }
                
                gui_player_name = name;
                // You may want to put your network initialization code somewhere in
                // here.
                clientCommandQueue = new ArrayBlockingQueue<MazewarPacket>(128);
                clientBroadcastQueue = new ArrayBlockingQueue<MazewarPacket>(128);
                clientCommandQueueOutOfOrder = new ArrayBlockingQueue<MazewarPacket>(128);
                tokenQueue = new ArrayBlockingQueue<MazewarPacket>(1);

                MazewarPacket packetToServer=new MazewarPacket();
                MazewarPacket packetFromServer;

                try {
                    serverSocket = new ServerSocket(myPort);
                } catch (IOException e) {
                    System.err.println("ERROR: Couldn't get I/O for the connection.");
                    System.err.println("ERROR: Mazewar");
                    System.exit(1);
                }

                outSockets = new Socket[num_players];
                //inSockets = new Socket[num_players];
                out_to_clients = new ObjectOutputStream[num_players];
                //in_from_clients = new ObjectInputStream[num_players];

                //start listening for connections from other clients
                lamport_clock = new LamportClock (0,0);

                receive_thread = new Thread(this);
                receive_thread.start();

        		try {
        			//Assume naming service runs at localhost:1111 by default

        			lookupSocket = new Socket(hostname, port);

        			out = new ObjectOutputStream(lookupSocket.getOutputStream());
        			in = new ObjectInputStream(lookupSocket.getInputStream());
        			
        			//register with naming service
        			packetToServer.type=MazewarPacket.LOOKUP_REGISTER;
        			packetToServer.name=name;
                    packetToServer.locations = new ClientLocation[1];

                    packetToServer.locations[0] = new ClientLocation (InetAddress.getLocalHost().getHostAddress(), myPort);
        			
        			out.writeObject(packetToServer);
        			
        			packetFromServer = (MazewarPacket) in.readObject();
                    if (packetFromServer.type==MazewarPacket.LOOKUP_REPLY){
                        System.out.println("Registered");
                    } else {
                        System.out.println("I have problem, shutting down");
                        Mazewar.quit();
                    }
                    gui_player_id = packetFromServer.client_id;

                    //poll the naming service to see if number of players is ready
                    //poll once every second
                    packetToServer = new MazewarPacket();
                    packetToServer.type = MazewarPacket.LOOKUP_REQUEST;
                    out.writeObject(packetToServer);
                    packetFromServer = (MazewarPacket) in.readObject();

                    while (packetFromServer.type==MazewarPacket.LOOKUP_REPLY) {
                        if (packetFromServer.num_locations < num_players) {
                            packetToServer = new MazewarPacket();
                            packetToServer.type = MazewarPacket.LOOKUP_REQUEST;
                            out.writeObject(packetToServer);
                            packetFromServer = (MazewarPacket) in.readObject();
                            try {
                                 Thread.sleep(1000);
                            } catch(InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }                        
                        } else {
                            System.out.println("number of players reached");
                            break;
                        }
                    }
                    System.out.println ("i am: " + gui_player_name + " " + gui_player_id);
                    lamport_clock.set_clientid(gui_player_id);

                    //connect with other players
                    for (int i = 0; i < num_players; i++) {
                        outSockets[i] = new Socket (packetFromServer.locations[i].client_host, packetFromServer.locations[i].client_port);
                    }

                    //close naming service
                    packetToServer = new MazewarPacket();
                    packetToServer.type = MazewarPacket.MAZEWAR_BYE;
                    out.writeObject(packetToServer);
                    in.close();
                    out.close();
                    lookupSocket.close();


                    //there are 4 output streams and 4 input streams
                    //output streams and input streams use different sockets
                    //input streams are initialized at receive handler thread (down below)
                    for (int i = 0; i < num_players; i++) {
                        out_to_clients[i] = new ObjectOutputStream(outSockets[i].getOutputStream());
                    }

                    Random randomGen = new Random(System.currentTimeMillis());        
                    int pointx, pointy;
                    pointx = randomGen.nextInt(20); //I am using the default width and height here
                    pointy = randomGen.nextInt(10);
                    
                    MazewarPacket packetFromOthers;
                    MazewarPacket packetToOthers;

                    //message others for a valid spwaning point
                    for (int i = 0; i < num_players; i++) {
                        packetToOthers = new MazewarPacket();
                        packetToOthers.type = MazewarPacket.PLAYER_SETUP_REQ;
                        packetToOthers.name = gui_player_name;
                        packetToOthers.pointx = pointx;
                        packetToOthers.pointy = pointy;
                        out_to_clients[i].writeObject (packetToOthers);
                    }

                    int k = 0;
                    int j = 0;
                    //this while loop is used for very complicated logic for setting up players
                    //you can take some time to read it, better if i just explain it
                    while (k < num_players) {

                        packetFromOthers = clientCommandQueue.poll();
                        if (packetFromOthers == null) {
                            continue;
                        }

                        if (packetFromOthers.type == MazewarPacket.PLAYER_SETUP_REQ) {
                            if (packetFromOthers.name.equals(gui_player_name)) {
                                for (int i = 0; i < num_players; i++) {
                                    packetToOthers = new MazewarPacket();
                                    packetToOthers.name = packetFromOthers.name;
                                    packetToOthers.type = MazewarPacket.PLAYER_SETUP_REPLY_OK;
                                    packetToOthers.pointx = packetFromOthers.pointx;
                                    packetToOthers.pointy = packetFromOthers.pointy;
                                    out_to_clients[i].writeObject (packetToOthers);
                                }   
                            } else {
                                if (packetFromOthers.pointy == pointy && packetFromOthers.pointx == pointx) {
                                    for (int i = 0; i < num_players; i++) {
                                        packetToOthers = new MazewarPacket();
                                        packetToOthers.name = packetFromOthers.name;
                                        packetToOthers.type = MazewarPacket.PLAYER_SETUP_REPLY_FAIL;
                                        packetToOthers.pointx = packetFromOthers.pointx;
                                        packetToOthers.pointy = packetFromOthers.pointy;
                                        out_to_clients[i].writeObject (packetToOthers);
                                    }
                                } else {
                                    for (int i = 0; i < num_players; i++) {
                                        packetToOthers = new MazewarPacket();
                                        packetToOthers.name = packetFromOthers.name;
                                        packetToOthers.type = MazewarPacket.PLAYER_SETUP_REPLY_OK;
                                        packetToOthers.pointx = packetFromOthers.pointx;
                                        packetToOthers.pointy = packetFromOthers.pointy;
                                        out_to_clients[i].writeObject (packetToOthers);
                                    }     
                                }
                            }
                        }

                        if (packetFromOthers.type == MazewarPacket.PLAYER_SETUP_REPLY_OK) {
                            if (packetFromOthers.name.equals(gui_player_name)) {
                                if (packetFromOthers.pointy == pointy && packetFromOthers.pointx == pointx) {
                                    j++;
                                    if (j == num_players) {
                                        for (int i = 0; i < num_players; i++) {
                                            packetToOthers = new MazewarPacket();
                                            packetToOthers.name = gui_player_name;
                                            packetToOthers.type = MazewarPacket.PLAYER_SETUP_FINAL;
                                            packetToOthers.pointx = pointx;
                                            packetToOthers.pointy = pointy;
                                            packetToOthers.client_id = gui_player_id;
                                            out_to_clients[i].writeObject (packetToOthers);
                                        }  
                                    }
                                }
                            }
                        }

                        if (packetFromOthers.type == MazewarPacket.PLAYER_SETUP_REPLY_FAIL) {
                            if (packetFromOthers.name.equals(gui_player_name)) {
                                if (packetFromOthers.pointy == pointy && packetFromOthers.pointx == pointx) {
                                    pointx = randomGen.nextInt(20);
                                    pointy = randomGen.nextInt(10);
                                    for (int i = 0; i < num_players; i++) {
                                        packetToOthers = new MazewarPacket();
                                        packetToOthers.name = gui_player_name;
                                        packetToOthers.type = MazewarPacket.PLAYER_SETUP_REQ;
                                        packetToOthers.pointx = pointx;
                                        packetToOthers.pointy = pointy;
                                        out_to_clients[i].writeObject (packetToOthers);
                                    }
                                    j = 0;  
                                }
                            }
                        }

                        if (packetFromOthers.type == MazewarPacket.PLAYER_SETUP_FINAL) {
                            Point point = new Point (packetFromOthers.pointx, packetFromOthers.pointy);
                            if (packetFromOthers.name.equals(gui_player_name)) {
                                guiClient = new GUIClient(gui_player_name, out_to_clients, clientCommandQueue, clientBroadcastQueue,num_players);
                                maze.addClient_with_point(guiClient, point, 0);
                                this.addKeyListener(guiClient);                            
                            } else {
                                RemoteClient newRemoteClient = new RemoteClient(packetFromOthers.name, clientCommandQueue);
                                maze.addClient_with_point(newRemoteClient, point, 0);
                            }
                            k++;
                        }
                    }

                    if (gui_player_id == 0) {
                        System.out.println ("I have lowest id, i am generating token: " + gui_player_name);
                        MazewarPacket token = new MazewarPacket();
                        token.type = MazewarPacket.I_AM_THE_TOKEN;
                        token.lamport_clock = 0;
                        tokenQueue.add(token);
                    }

        		} catch (UnknownHostException e) {
        			System.err.println("ERROR: Don't know where to connect!!");
        			System.exit(1);
        		} catch (IOException e) {
        			System.err.println("ERROR: Couldn't get I/O for the connection.");
        			System.exit(1);
        		} catch (ClassNotFoundException e) {
        			e.printStackTrace();
                    		System.exit(1);
        		} catch (Exception e) {
                   		e.printStackTrace();
                    		System.exit(1);
                }

                // Create the panel that will display the maze.
                overheadPanel = new OverheadMazePanel(maze, guiClient);
                assert(overheadPanel != null);
                maze.addMazeListener(overheadPanel);
                
                // Don't allow editing the console from the GUI
                console.setEditable(false);
                console.setFocusable(false);
                console.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder()));
               
                // Allow the console to scroll by putting it in a scrollpane
                JScrollPane consoleScrollPane = new JScrollPane(console);
                assert(consoleScrollPane != null);
                consoleScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Console"));
                
                // Create the score table
                scoreTable = new JTable(scoreModel);
                assert(scoreTable != null);
                scoreTable.setFocusable(false);
                scoreTable.setRowSelectionAllowed(false);

                // Allow the score table to scroll too.
                JScrollPane scoreScrollPane = new JScrollPane(scoreTable);
                assert(scoreScrollPane != null);
                scoreScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Scores"));
                
                // Create the layout manager
                GridBagLayout layout = new GridBagLayout();
                GridBagConstraints c = new GridBagConstraints();
                getContentPane().setLayout(layout);
                
                // Define the constraints on the components.
                c.fill = GridBagConstraints.BOTH;
                c.weightx = 1.0;
                c.weighty = 3.0;
                c.gridwidth = GridBagConstraints.REMAINDER;
                layout.setConstraints(overheadPanel, c);
                c.gridwidth = GridBagConstraints.RELATIVE;
                c.weightx = 2.0;
                c.weighty = 1.0;
                layout.setConstraints(consoleScrollPane, c);
                c.gridwidth = GridBagConstraints.REMAINDER;
                c.weightx = 1.0;
                layout.setConstraints(scoreScrollPane, c);
                                
                // Add the components
                getContentPane().add(overheadPanel);
                getContentPane().add(consoleScrollPane);
                getContentPane().add(scoreScrollPane);
                
                // Pack everything neatly.
                pack();

                // Let the magic begin.
                setVisible(true);
                overheadPanel.repaint();
                this.requestFocusInWindow();
        }

        public void run() {

            //boolean listening = true;

            handler_thread [] handler_threads = new handler_thread[num_players];

            client_broadcast_thread broadcast_thread = new client_broadcast_thread(out_to_clients, clientBroadcastQueue, tokenQueue, num_players, gui_player_name, lamport_clock);
            broadcast_thread.start();

            out_of_order_queue_process order_process = new out_of_order_queue_process(clientCommandQueue, clientCommandQueueOutOfOrder, lamport_clock);
            order_process.start();

            int i = 0;
            while (listening) {
                if (i >= num_players) {
                    continue;
                }
                try {
                    handler_threads[i] = new handler_thread(serverSocket.accept(), clientCommandQueue, clientCommandQueueOutOfOrder, tokenQueue, num_players, maze, gui_player_name, out_to_clients, lamport_clock);
                    handler_threads[i].start();
                    i++;
                } catch (IOException e) {
                    //server exiting
                    System.err.println("ERROR: Couldn't get I/O for the connection.");
                    System.exit(1);
                }
            }

            System.out.println ("stopped listening");

            try {
                for (i = 0; i < num_players; i++) {
                    handler_threads[i].end_handler_thread();
                    handler_threads[i].join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

	    System.out.println ("ended all handler threads");

            order_process.out_of_order_stop();
            broadcast_thread.end_broadcast_thread();
            
	    System.out.println ("ended broadcast threads");

            try {
		        order_process.join();
		        broadcast_thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        /**
         * Entry point for the game.  
         * @param args Command-line arguments.
         */
        public static void main(String args[]) {

		if(args.length == 4 ) {
			hostname = args[0];
			port = Integer.parseInt(args[1]); //naming service port
            myPort = Integer.parseInt(args[2]);
            num_players = Integer.parseInt(args[3]);
		} else {
			System.err.println("ERROR: Invalid arguments!");
			System.exit(-1);
		}


                /* Create the GUI */
                new Mazewar();

        }
}

class handler_thread extends Thread {
    private int num_players;
    private BlockingQueue<MazewarPacket> clientCommandQueue;
    private BlockingQueue<MazewarPacket> clientCommandQueueOutOfOrder;
    private BlockingQueue<MazewarPacket> tokenQueue;
    private Socket socket = null;
    private String client_name;
    private Maze maze = null;
    private String gui_player_name;
    private int client_id;
    private boolean running = true;
    private ObjectOutputStream [] out_to_clients;
    private LamportClock lamport_clock;

    public handler_thread (Socket socket_, 
        BlockingQueue<MazewarPacket> clientCommandQueue_, 
        BlockingQueue<MazewarPacket> clientCommandQueueOutOfOrder_, 
        BlockingQueue<MazewarPacket> tokenQueue_, 
        int num_players_, 
        Maze maze_, 
        String gui_player_name_, 
        ObjectOutputStream [] out_to_clients_, 
        LamportClock lamport_clock_) {

        this.socket = socket_;
        clientCommandQueue = clientCommandQueue_;
        clientCommandQueueOutOfOrder = clientCommandQueueOutOfOrder_; 
        lamport_clock = lamport_clock_;
        tokenQueue = tokenQueue_;
        num_players = num_players_;
        client_name = "";
        maze = maze_;
        gui_player_name = gui_player_name_;
        out_to_clients = out_to_clients_;
        running = true;
    }

    public synchronized void end_handler_thread() {
        running = false;
    }

    public void run () {
            try {
                    ObjectInputStream fromClients = new ObjectInputStream(socket.getInputStream());
                    MazewarPacket packetFromOthers;
					int tokencounter = 0;
                    while (running) {
                        if (( packetFromOthers = (MazewarPacket) fromClients.readObject()) != null) {
                            if (packetFromOthers.type == MazewarPacket.PLAYER_SETUP_FINAL) {
                                client_name = packetFromOthers.name;
                                client_id = packetFromOthers.client_id;
                                System.out.println ("players joined: " + client_name + " " + client_id);
                            }

                            if (packetFromOthers.type == MazewarPacket.MAZEWAR_BYE) {
                            	System.out.println ("recieved bye packet");
                                /*if (packetFromOthers.name.equals(client_name)) {
                              		synchronized (out_to_clients) {
										out_to_clients[client_id] = null;
									}
									break;
                                }*/

                                Iterator<Client> itr = maze.getClients();
                                while (itr.hasNext()) {
                                    Client _client = itr.next();
                                    if (_client.getName().equals(client_name)) {
                                        maze.removeClient (_client);
                                    }
                                }
                              	synchronized (out_to_clients) {
									out_to_clients[client_id] = null;
								}
                                System.out.println ("player disconnected: " + client_name);
                            }
                            
                            if (packetFromOthers.type == MazewarPacket.I_AM_THE_TOKEN) {
                            	System.out.println ("revieved token: " + tokencounter++);
                                tokenQueue.add(packetFromOthers);
                                continue;
                            }

                            if (packetFromOthers.type == MazewarPacket.MAZEWAR_REQ) {
                                System.out.println ("I received a packet with clock: " + packetFromOthers.lamport_clock + " from: " + client_name);
                                if ((lamport_clock.get_lamport_clock()) == (packetFromOthers.lamport_clock - 1)) {
                                    clientCommandQueue.put(packetFromOthers);
                                    lamport_clock.incr_and_return_LamportClock();
                                } else {
                                    clientCommandQueueOutOfOrder.add(packetFromOthers); //add packet to a different queue to be process by another thread
                                }
                                continue;
                            }

                            System.out.println ("Received initialization packet");
                            clientCommandQueue.put(packetFromOthers);

                        }
                    }
                    System.out.println ("recieving thread closing");
                    fromClients.close();
            } catch (IOException e) {
                Iterator<Client> itr = maze.getClients();
                while (itr.hasNext()) {
                    Client _client = itr.next();
                    if (_client.getName().equals(client_name)) {
                        maze.removeClient (_client);
                    }
                }
                synchronized (out_to_clients) {
                    out_to_clients[client_id] = null;
                }
		running = false;
                System.out.println ("player disconnected: " + client_name);
                //Mazewar.unregisterClient (client_name);
            } catch (ClassNotFoundException e) {
                System.exit(-1);
            } catch(InterruptedException e){
                //System.exit(-1);
            }
    }
}


class client_broadcast_thread extends Thread {
    private int num_players;
    private BlockingQueue<MazewarPacket> clientBroadcastQueue;
    private BlockingQueue<MazewarPacket> tokenQueue;
    private ObjectOutputStream [] out_to_clients;
    private String gui_player_name;
    private boolean running = true;
    private LamportClock lamport_clock;

    public client_broadcast_thread (ObjectOutputStream [] out_to_clients_, BlockingQueue<MazewarPacket> clientBroadcastQueue_, BlockingQueue<MazewarPacket> tokenQueue_, int num_players_, String gui_player_name_, LamportClock lamport_clock_) {
        out_to_clients = out_to_clients_;
        clientBroadcastQueue = clientBroadcastQueue_;
        tokenQueue = tokenQueue_;
        num_players = num_players_;
        gui_player_name = gui_player_name_;
        lamport_clock = lamport_clock_;
        running = true;
    }

    public synchronized void end_broadcast_thread() {
        running = false;
    }

    public void multicastPacket (MazewarPacket packetToOthers) throws IOException {
        int i = 0;
        try {
            for (i=0; i<num_players; i++) {
                synchronized (out_to_clients) {
                    if (out_to_clients[i] != null)
                    out_to_clients[i].writeObject(packetToOthers);                
                }
            }                        
        } catch (IOException e) {
            System.out.println ("broadcast IOexception");                
        }
    }

    public void run () {
            try {
		MazewarPacket headQueuePacket=null;
                while (running) {
                    MazewarPacket token = tokenQueue.poll();
                    if (token != null) {
                        headQueuePacket = clientBroadcastQueue.poll();
                        if (headQueuePacket != null) {
                            token.lamport_clock = token.lamport_clock + 1;
                            headQueuePacket.lamport_clock = token.lamport_clock;
                            multicastPacket (headQueuePacket);     
                        }
			int i = ((lamport_clock.get_clientid())+1)%num_players;
                        while (true) {
                        	if (out_to_clients[i] != null) {
                        		out_to_clients[i].writeObject(token);
                        		break;
                        	} else {
                        		i = (i+1)%num_players;
                        	}
                        }

                    }
                }

               MazewarPacket token = tokenQueue.poll();
                if (token != null) {
                    while (true) {
                        int i = ((lamport_clock.get_clientid())+1)%num_players;
                        if (out_to_clients[i] != null) {
                        	out_to_clients[i].writeObject(token);
                        	break;
                        } else {
                        	i = (i+1)%num_players;
                        	if (i == lamport_clock.get_clientid()) {
                        		break;
                        	}
                        }
                    }
                }

                System.out.println ("broadcast thread closing");
            } catch (IOException e) {
                System.out.println ("send problem here");
            } 
            
    }
}

class out_of_order_queue_process extends Thread {
    private boolean running = true;
    private LamportClock lamport_clock;
    private BlockingQueue<MazewarPacket> clientCommandQueue;
    private BlockingQueue<MazewarPacket> clientCommandQueueOutOfOrder;

    public out_of_order_queue_process (BlockingQueue<MazewarPacket> clientCommandQueue_, BlockingQueue<MazewarPacket> clientCommandQueueOutOfOrder_, LamportClock lamport_clock_) {
        lamport_clock = lamport_clock_;
        running = true;
        clientCommandQueue = clientCommandQueue_;
        clientCommandQueueOutOfOrder = clientCommandQueueOutOfOrder_; 
    }
    
    public synchronized void out_of_order_stop () {
    	running = false;
    }

    public void run () {
        while (running) {
            MazewarPacket nextPacket = clientCommandQueueOutOfOrder.poll();
            if (nextPacket != null) { //test if there is anything in the queue
                if ((lamport_clock.get_lamport_clock()) == (nextPacket.lamport_clock - 1)) { //if there is, check if its clock is 1 bigger than our own
                    clientCommandQueue.add(nextPacket); //if its good, then we put it on the primary queue
                    lamport_clock.incr_and_return_LamportClock();
                } else {
                    clientCommandQueueOutOfOrder.add(nextPacket); //if its bad we add it back in, this would cycle the packets
                }
            }
        }
    }
}

class LamportClock {
    private int lamport_clock;
    private int client_id;

    public LamportClock (int lamport_clock_, int client_id_) {
        lamport_clock = lamport_clock_;
        client_id = client_id_;
    }

    public synchronized int incr_and_return_LamportClock() {
        lamport_clock++;
        return lamport_clock;
    }

    public synchronized int get_lamport_clock() {
        return lamport_clock;
    }

    public synchronized int get_clientid () {
        return client_id;
    }

    public synchronized void set_clientid (int new_id) {
        client_id = new_id;
    }
}
