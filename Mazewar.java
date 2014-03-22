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

/**
 * The entry point and glue code for the game.  It also contains some helpful
 * global utility methods.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame implements Runnable {


        private static Socket mazewarSocket = null;
        private static ObjectOutputStream out;
        private static ObjectInputStream in;
        public BlockingQueue<MazewarPacket> clientCommandQueue;
        private String gui_player_name;
        private static String hostname;
        private static int port;

        private final Thread receive_thread;

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
               System.exit(0);
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
                clientCommandQueue = new ArrayBlockingQueue<MazewarPacket>(1024);
                
        		try {
        			//Assume server runs at localhost:1111 by default

        			mazewarSocket = new Socket(hostname, port);

        			out = new ObjectOutputStream(mazewarSocket.getOutputStream());
        			in = new ObjectInputStream(mazewarSocket.getInputStream());
        			
        			MazewarPacket packetToServer=new MazewarPacket();
        			MazewarPacket packetFromServer;
        			
        			packetToServer.type=MazewarPacket.MAZEWAR_REG;
        			packetToServer.name=name;
        			
        			out.writeObject(packetToServer);
        			
        			packetFromServer = (MazewarPacket) in.readObject();
        			
        			while (packetFromServer.type==MazewarPacket.MAZEWAR_ERROR){
                        System.out.println("ERROR: name in use");
                        name = JOptionPane.showInputDialog("Enter your name");
				    if((name == null) || (name.length() == 0)) {
				        Mazewar.quit();
				    }
				     packetToServer=new MazewarPacket();
		   		     packetToServer.type=MazewarPacket.MAZEWAR_REG;
		   		     packetToServer.name=name;
		   			out.writeObject(packetToServer);
		   			packetFromServer = (MazewarPacket) in.readObject();
        			}


                    while( (packetFromServer=(MazewarPacket) in.readObject()) != null) {
                        if (packetFromServer.type == MazewarPacket.MAZEWAR_INITIALIZE_PLAYERS){
                            Point point = new Point (packetFromServer.pointx, packetFromServer.pointy);
                            if (packetFromServer.name.equals(gui_player_name)) {
                            // Create the GUIClient and connect it to the KeyListener queue
                                guiClient = new GUIClient(gui_player_name, out, clientCommandQueue);
                                maze.addClient_with_point(guiClient, point, 0);
                                this.addKeyListener(guiClient);
                            } else {
                                RemoteClient newRemoteClient = new RemoteClient(packetFromServer.name, clientCommandQueue);
                                maze.addClient_with_point(newRemoteClient, point, 0);
                            }
                        } else if (packetFromServer.type == MazewarPacket.MAZEWAR_STR) {
                            break;
                        } else if (packetFromServer.type == MazewarPacket.MAZEWAR_ERROR) {
                        	   Mazewar.quit();
                        }
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


                receive_thread = new Thread(this);
                receive_thread.start();


                
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
            MazewarPacket packetFromServer;
            while  (true) {
                try {
                    while( (packetFromServer=(MazewarPacket) in.readObject()) != null) {
                        if (packetFromServer.type == MazewarPacket.MAZEWAR_REQ) {
                            clientCommandQueue.put(packetFromServer);
                            //System.out.println ("Received packet from server, added to queue");
                        } 

                    }

                } catch(Exception e) {
                    System.exit(1);
                } 
            }
        }
        
        /**
         * Entry point for the game.  
         * @param args Command-line arguments.
         */
        public static void main(String args[]) {

		if(args.length == 2 ) {
			hostname = args[0];
			port = Integer.parseInt(args[1]);
		} else {
			System.err.println("ERROR: Invalid arguments!");
			System.exit(-1);
		}
                /* Create the GUI */
                new Mazewar();

        }
}
