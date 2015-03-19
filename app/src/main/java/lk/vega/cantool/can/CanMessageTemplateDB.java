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

import android.content.Context;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import lk.vega.usbserial.util.HexDump;

/**
 * TODO: Class comments
 */
public class CanMessageTemplateDB {

    /**
     * key - CAN message ID
     */
    private static Map<String, CanMessageTemplate> templates;

    public static void parse(Context context) {
        XmlPullParserFactory pullParserFactory;
        String canMessageTemplateFilename = "can_messages.xml";
        try {
            pullParserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = pullParserFactory.newPullParser();

            InputStream in = context.getAssets().open(canMessageTemplateFilename);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);

            parseXML(parser);
        } catch (XmlPullParserException | IOException e) {
            Log.e(CanMessageTemplateDB.class.getSimpleName(), "Error occurred while loading " + canMessageTemplateFilename, e);
        }
    }

    private static void parseXML(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        CanMessageTemplate template = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName;
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT:
                    templates = new HashMap<>();
                    break;
                case XmlPullParser.START_TAG:
                    tagName = parser.getName();
                    if (tagName.equals("message")) {
                        String name = parser.getAttributeValue("", "name");
                        String description = parser.getAttributeValue("", "description");
                        String id = parser.getAttributeValue("", "id");
                        template = new CanMessageTemplate(HexDump.hexStringToByteArray(id), name, description);
                    }
                    /*else if (currentProduct != null) {
                        if (tagName == "productname") {
                            currentProduct.name = parser.nextText();
                        } else if (tagName == "productcolor") {
                            currentProduct.color = parser.nextText();
                        } else if (tagName == "productquantity") {
                            currentProduct.quantity = parser.nextText();
                        }
                    }*/
                    break;
                case XmlPullParser.END_TAG:
                    tagName = parser.getName();
                    if (tagName.equals("message") && template != null) {
                        templates.put(template.getName(), template);
                    }
            }
            eventType = parser.next();
        }
    }
}
