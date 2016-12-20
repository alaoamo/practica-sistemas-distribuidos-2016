/*
* Copyright (c) Joan-Manuel Marques 2013. All rights reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
*
* This file is part of the practical assignment of Distributed Systems course.
*
* This code is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This code is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this code.  If not, see <http://www.gnu.org/licenses/>.
*/

package recipes_service.tsae.sessions;


import java.io.IOException;
import java.net.Socket;

import communication.ObjectInputStream_DS;
import communication.ObjectOutputStream_DS;
import recipes_service.ServerData;
import java.util.List;
import java.util.Vector;
//import java.util.ArrayList;

import recipes_service.communication.Message;
import recipes_service.communication.MessageAErequest;
import recipes_service.communication.MessageEndTSAE;
import recipes_service.communication.MessageOperation;
import recipes_service.communication.MsgType;
import recipes_service.data.AddOperation;
import recipes_service.data.Operation;
import recipes_service.data.OperationType;
import recipes_service.data.RemoveOperation;
import recipes_service.tsae.data_structures.TimestampMatrix;
import recipes_service.tsae.data_structures.TimestampVector;



/**
 * @author Joan-Manuel Marques
 * December 2012
 *
 */
public class TSAESessionPartnerSide extends Thread{
	private Socket socket = null;
	private ServerData serverData = null;
	
	public TSAESessionPartnerSide(Socket socket, ServerData serverData) {
		super("TSAEPartnerSideThread");
		this.socket = socket;
		this.serverData = serverData;
	}
	public void run() {
		
//		System.out.println("Partner starts TSAE session...");
		
		try {
			ObjectOutputStream_DS out = new ObjectOutputStream_DS(socket.getOutputStream());
			ObjectInputStream_DS in = new ObjectInputStream_DS(socket.getInputStream());
//			System.out.println("Partner - opened streams");		
			// receive originator's summary and ack
			Message msg = (Message) in.readObject();
			if (msg.type() == MsgType.AE_REQUEST){
				MessageAErequest aeMsg = (MessageAErequest) msg;	
//				System.out.println("Partner - received AE Request");			
				// get a copy of the actual ACK and Summary
				TimestampMatrix localAck;
				TimestampVector localSummary;	
		        /**
		         * Recopila una instant�nea de localSummary y localAck. 
		         * Se sincroniza para que la matriz o el resumen no cambie entre ellos. 
		         * Tambi�n este es el �nico lugar donde el vector local de la matriz 
		         * se actualiza con el localSummary.
		         * 
		         */				
// 			send operations  
//			Synchronized them
            synchronized (serverData) {
//            	System.out.println("Partner - begin to clone summary");
                localSummary = serverData.getSummary().clone();
//                System.out.println("Partner - finish to clone summary");
//                System.out.println(localSummary.toString());             
//                serverData.getAck().update(serverData.getId(), localSummary);
//                System.out.println("Partner - begin to clone ack");
                localAck = serverData.getAck().clone();
//                System.out.println("Partner - finish to clone ack");
//                System.out.println(localAck.toString());
            }          
//          System.out.println("Partner - collected local Summary and Ack");
	        /**
	         * Obtiene todas las operaciones m�s recientes que las que tiene 
	         * el otro lado (el resumen recibido) y las env�a al otro lado.
	         * 
	         */           			
            for (Operation op : serverData.getLog().listNewer(aeMsg.getSummary())) {
                out.writeObject(new MessageOperation(op));
            }          
//          System.out.println("Partner - sent operations");
	        /**
	         * Env�a el resumen y la matriz ack al otro lado
	         * 
	         */           
// 			send to originator: local's summary and ack			 
            msg = new MessageAErequest(localSummary, localAck);  
            out.writeObject(msg);			
//          System.out.println("Partner - sent AE Request");
//			receive operations
			msg = (Message) in.readObject();		
// 			System.out.println(Arrays.toString(arrrayoperations)); 
 
            List<MessageOperation> listoperations = new Vector<MessageOperation>();
//           
		    /**
	         * Recopila todas las operaciones que tiene el otro lado pero el servidor actual no.
	         * La ejecuci�n sucede despu�s de que sepamos que TSAE tuvo �xito.
	         * 
	         */     
			while (msg.type() == MsgType.OPERATION){

//			System.out.println("Partner - received operation: " + msg.getClass().toString());			
			listoperations.add((MessageOperation) msg);
//			System.out.println("Partner - remembered operation");	
			msg = (Message) in.readObject();
			}			
// 			receive message to inform about the ending of the TSAE session
			if (msg.type() == MsgType.END_TSAE){
//			System.out.println("Partner - received EndTSAE");				
// 			send and "end of TSAE session" message
			msg = new MessageEndTSAE();  
			out.writeObject(msg);			
//			System.out.println("Partner - sent EndTSAE");	
            /**
             * TSEA tuvo �xito => ejecuta las operaciones recopiladas 
             * y actualizar los datos del servidor local.
             * 
             */
			 synchronized (serverData) {
                 for (MessageOperation op : listoperations) {
                     if (op.getOperation().getType() == OperationType.ADD) {
                         serverData.execOperation((AddOperation) op.getOperation());
                     } else {
                         serverData.execOperation((RemoveOperation) op.getOperation());
                     }
                 }
                 serverData.getSummary().updateMax(aeMsg.getSummary());
                 serverData.getAck().updateMax(aeMsg.getAck());
                 serverData.getLog().purgeLog(serverData.getAck());
             }
			
			}
			}

			socket.close();		
		} catch (IOException e) {
	    } catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
//		System.out.println("...partner finished TSAE session ");
		
	}
}
