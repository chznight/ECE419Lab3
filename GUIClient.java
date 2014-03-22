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

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.util.Random;
import java.util.Vector;
import java.lang.Runnable;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/**
 * An implementation of {@link LocalClient} that is controlled by the keyboard
 * of the computer on which the game is being run.  
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: GUIClient.java 343 2004-01-24 03:43:45Z geoffw $
 */

public class GUIClient extends LocalClient implements KeyListener, Runnable{

        private ObjectOutputStream out;
        private String name;

        private final Thread thread;
        /** 
         * Flag to say whether the control thread should be
         * running.
         */
        public BlockingQueue<MazewarPacket> clientCommandQueue;
        private boolean active = false;
        /**
         * Create a GUI controlled {@link LocalClient}.  
         */
        public GUIClient(String name_, ObjectOutputStream out_, BlockingQueue<MazewarPacket> clientCommandQueue_) {
                super(name_);
                name = name_;
                out = out_;
                clientCommandQueue = clientCommandQueue_;
                thread = new Thread(this);
        }
        


        /**
         * Handle a key press.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyPressed(KeyEvent e) {
                MazewarPacket packetToServer = new MazewarPacket();
                packetToServer.name = name;
                // If the user pressed Q, invoke the cleanup code and quit. 
                if((e.getKeyChar() == 'q') || (e.getKeyChar() == 'Q')) {
                        packetToServer.type = MazewarPacket.MAZEWAR_BYE;
                        try {
                                out.writeObject(packetToServer);
                        } catch (IOException e2) {
                                System.err.println("ERROR: Couldn't get I/O for the connection.");
                                System.exit(1);
                        }
                        Mazewar.quit();
                // Up-arrow moves forward.
                } else if(e.getKeyCode() == KeyEvent.VK_UP) {
                        packetToServer.type = MazewarPacket.MAZEWAR_REQ;
                        packetToServer.command = MazewarPacket.MAZEWAR_MOVEFORWARD;
                        try {
                                out.writeObject(packetToServer);
                        } catch (IOException e2) {
                                System.err.println("ERROR: Couldn't get I/O for the connection.");
                                System.exit(1);
                        }
                        //forward();
                // Down-arrow moves backward.
                } else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                        packetToServer.type = MazewarPacket.MAZEWAR_REQ;
                        packetToServer.command = MazewarPacket.MAZEWAR_MOVEBACKWARD;
                        try {
                                out.writeObject(packetToServer);
                        } catch (IOException e2) {
                                System.err.println("ERROR: Couldn't get I/O for the connection.");
                                System.exit(1);
                        }
                        //backup();
                // Left-arrow turns left.
                } else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
                        packetToServer.type = MazewarPacket.MAZEWAR_REQ;
                        packetToServer.command = MazewarPacket.MAZEWAR_ROTATELEFT;
                        try {
                                out.writeObject(packetToServer);
                        } catch (IOException e2) {
                                System.err.println("ERROR: Couldn't get I/O for the connection.");
                                System.exit(1);
                        }
                        //turnLeft();
                // Right-arrow turns right.
                } else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
                        packetToServer.type = MazewarPacket.MAZEWAR_REQ;
                        packetToServer.command = MazewarPacket.MAZEWAR_ROTATERIGHT;
                        try {
                                out.writeObject(packetToServer);
                        } catch (IOException e2) {
                                System.err.println("ERROR: Couldn't get I/O for the connection.");
                                System.exit(1);
                        }
                // Spacebar fires.
                } else if(e.getKeyCode() == KeyEvent.VK_SPACE) {
                        packetToServer.type = MazewarPacket.MAZEWAR_REQ;
                        packetToServer.command = MazewarPacket.MAZEWAR_FIRE;
                        try {
                                out.writeObject(packetToServer);
                        } catch (IOException e2) {
                                System.err.println("ERROR: Couldn't get I/O for the connection.");
                                System.exit(1);
                        }
                        //fire();
                }
        }
        
        /**
         * Handle a key release. Not needed by {@link GUIClient}.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyReleased(KeyEvent e) {
        }
        
        /**
         * Handle a key being typed. Not needed by {@link GUIClient}.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyTyped(KeyEvent e) {
        }
        public synchronized void registerMaze(Maze maze) {
                assert(maze != null);
                super.registerMaze(maze);

                // Get the control thread going.
                active = true;
                thread.start();
        }
        
        /** 
         * Override the abstract {@link Client}'s unregisterMaze method so we know when to stop the 
         * control thread. 
         */
        public synchronized void unregisterMaze() {
                // Signal the control thread to stop
                active = false; 
                // Wait half a second for the thread to complete.
                try {
                        thread.join(500);
                } catch(Exception e) {
                        // Shouldn't happen
                }
                super.unregisterMaze();
        }
        
        public void run() {
                // Put a spiffy message in the console
                try {
                // Loop while we are active
                        while(active) {
                                
                                MazewarPacket headQueuePacket;
                                headQueuePacket = clientCommandQueue.peek();
                                if (headQueuePacket == null)
                                {
                                        continue;
                                }
                                if (headQueuePacket.name.equals(name)) {
                                        clientCommandQueue.poll();
                                        if (headQueuePacket.command == MazewarPacket.MAZEWAR_MOVEFORWARD)
                                                forward();
                                        else if (headQueuePacket.command == MazewarPacket.MAZEWAR_MOVEBACKWARD)
                                                backup();
                                        else if (headQueuePacket.command == MazewarPacket.MAZEWAR_ROTATELEFT)
                                                turnLeft();
                                        else if (headQueuePacket.command == MazewarPacket.MAZEWAR_ROTATERIGHT)
                                                turnRight();
                                        else if (headQueuePacket.command == MazewarPacket.MAZEWAR_FIRE)
                                                fire();
                                        else {
                                                //do nothing
                                        }       
                                }
                                //forward();
                                //thread.sleep(2000);
                                //backup();
                                //thread.sleep(2000);
                        }
                                
                } catch(Exception e) {
                                // Shouldn't happen.
                }
        }

}
