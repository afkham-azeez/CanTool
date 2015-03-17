/*
 * (C) Copyright 2015 CodeGen International (http://codegen.net) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Afkham Azeez (afkham@gmail.com)
 */
package lk.vega.cantool.can;

import java.util.Arrays;
import java.util.Queue;
import static lk.vega.cantool.can.CanMessage.CAN_MSG_SIZE_BYTES;

/**
 * Processes byte[] raw messages and creates {@link lk.vega.cantool.can.CanMessage}s
 * <p/>
 * Not thread safe
 */
public class CanMessageProcessor implements Runnable {

    private byte[] overflow;
    private Queue<byte[]> rawMsgQueue;
    private Queue<CanMessage> canMessageQueue;

    public CanMessageProcessor(Queue<byte[]> rawMsgQueue, Queue<CanMessage> canMessageQueue) {
        this.rawMsgQueue = rawMsgQueue;
        this.canMessageQueue = canMessageQueue;
    }

    @Override
    public void run() {
        /*byte[] content = new byte[]{0x01, 0x34, 0x56, 0x78, 0x09, (byte) 0xAB, (byte) 0xEF, (byte) 0xCA, (byte) 0xBC, 0x12};
        canMessageQueue.add(new CanMessage(content));

        content = new byte[]{0x01, 0x35, 0x56, 0x78, 0x09, (byte) 0xAB, (byte) 0xEF, (byte) 0xCA, (byte) 0xBC, 0x13};
        canMessageQueue.add(new CanMessage(content));

        content = new byte[]{0x01, 0x36, 0x56, 0x78, 0x09, (byte) 0xAB, (byte) 0xEF, (byte) 0xCA, (byte) 0xBC, 0x14};
        canMessageQueue.add(new CanMessage(content));*/

        byte[] rawMsg = null;
        do {
            if (rawMsg != null) {
                process(rawMsg);
            }
            rawMsg = rawMsgQueue.poll();
        } while (rawMsg != null);
    }

    public void reset(){
        overflow = null;
        rawMsgQueue.clear();
        canMessageQueue.clear();
    }

    private void process(byte[] rawMsg) {
        if (overflow == null) { // there was no overflow from the previous round
            if (rawMsg.length < CAN_MSG_SIZE_BYTES) {
                overflow = Arrays.copyOf(rawMsg, rawMsg.length);
            } else {
                do {
                    // loop until all msgs are retrieved
                    byte[] processedMsg = Arrays.copyOf(rawMsg, CAN_MSG_SIZE_BYTES);
                    canMessageQueue.add(new CanMessage(processedMsg));
                    if (rawMsg.length >= CAN_MSG_SIZE_BYTES) {
                        rawMsg = Arrays.copyOfRange(rawMsg, CAN_MSG_SIZE_BYTES, rawMsg.length);
                    }
                    if (rawMsg.length < CAN_MSG_SIZE_BYTES) {
                        // overflow
                        overflow = Arrays.copyOf(rawMsg, rawMsg.length);
                    }
                } while (rawMsg.length > CAN_MSG_SIZE_BYTES);
            }
        } else { // there was an overflow from the previous round
            // Append the overflow to the bytes from the new message, and recursively call process(byte[])
            byte[] newRawMsg = new byte[overflow.length + rawMsg.length];
            System.arraycopy(overflow, 0, newRawMsg, 0, overflow.length);
            System.arraycopy(rawMsg, 0, newRawMsg, overflow.length, rawMsg.length);
            if (newRawMsg.length < CAN_MSG_SIZE_BYTES) {
                overflow = newRawMsg;
            } else {
                overflow = null;
                process(newRawMsg);
            }
        }
    }
}
