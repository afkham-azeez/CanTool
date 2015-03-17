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

/**
 * Represents a CAN message
 */
public class CanMessage {
    public static final int CAN_MSG_SIZE_BYTES = 10;
    public static final int CAN_MSG_ID_SIZE_BYTES = 2;

    private byte[] messageId;
    private byte[] data;

    private byte[] raw;

    public CanMessage(byte[] raw) {
        this.raw = raw;
        messageId = new byte[CAN_MSG_ID_SIZE_BYTES];
        System.arraycopy(raw, 0, messageId, 0, CAN_MSG_ID_SIZE_BYTES);

        data = new byte[CAN_MSG_SIZE_BYTES - CAN_MSG_ID_SIZE_BYTES];
        System.arraycopy(raw, CAN_MSG_ID_SIZE_BYTES, data, 0, CAN_MSG_SIZE_BYTES - CAN_MSG_ID_SIZE_BYTES);
    }

    public byte[] getRaw() {
        return raw;
    }

    public byte[] getMessageId() {
        return messageId;
    }

    public byte[] getData() {
        return data;
    }
}
