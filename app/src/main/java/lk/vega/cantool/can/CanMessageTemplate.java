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

import android.util.Log;

import lk.vega.cantool.can.messages.CanMessageProcessor;

/**
 * Represents the template for a CAN message
 */
public class CanMessageTemplate {

    private byte[] id;
    private String name;
    private String description;
    private CanMessageProcessor processor;

    public CanMessageTemplate() {
    }

    public CanMessageTemplate(byte[] id, String name, String description, String processorClass) {
        this.id = id;
        this.name = name;
        this.description = description;
        try {
            processor = (CanMessageProcessor) Class.forName(processorClass).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            String msg = "Could not instantiate CanMessageProcessor class " + processorClass;
            Log.e(CanMessageTemplate.class.getSimpleName(), msg);
            throw new RuntimeException(msg, e);
        }
    }

    public byte[] getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CanMessageProcessor getProcessor() {
        return processor;
    }
}
