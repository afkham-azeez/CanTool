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

import java.util.Queue;

import lk.vega.cantool.SerialConsoleActivity;

/**
 * Prints CAN messages to the screen
 */
public class CanMessagePrinter implements Runnable {
    private Queue<CanMessage> canMsgQueue;
    private SerialConsoleActivity serialConsoleActivity;

    public CanMessagePrinter(Queue<CanMessage> canMsgQueue, SerialConsoleActivity serialConsoleActivity) {
        this.canMsgQueue = canMsgQueue;
        this.serialConsoleActivity = serialConsoleActivity;
    }

    @Override
    public void run() {
        CanMessage canMessage = null;
        do {
            if (canMessage != null) {
//                serialConsoleActivity.printCanMessage(canMessage);
                serialConsoleActivity.printHexDump(canMessage.getRaw());
            }
            canMessage = canMsgQueue.poll();
        } while (canMessage != null);
    }
}
