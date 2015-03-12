/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package lk.vega.cantool;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lk.vega.cantool.can.CanMessage;
import lk.vega.cantool.can.CanMessagePrinter;
import lk.vega.cantool.can.CanMessageProcessor;
import lk.vega.usbserial.driver.UsbSerialPort;
import lk.vega.usbserial.util.HexDump;
import lk.vega.usbserial.util.SerialInputOutputManager;

/**
 * Monitors a single {@link UsbSerialPort} instance, showing all data
 * received.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialConsoleActivity extends Activity {

    private final String TAG = SerialConsoleActivity.class.getSimpleName();

    /**
     * Driver instance, passed in statically via
     * {@link #show(android.content.Context, UsbSerialPort)}.
     * <p/>
     * <p/>
     * This is a devious hack; it'd be cleaner to re-create the driver using
     * arguments passed in with the {@link #startActivity(android.content.Intent)} intent. We
     * can get away with it because both activities will run in the same
     * process, and this is a simple demo.
     */
    private static UsbSerialPort sPort = null;

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;
    private Spinner mSpinner;
    private boolean initialized;
    private Queue<byte[]> rawMsgQueue = new LinkedBlockingQueue<>();
    private Queue<CanMessage> canMsgQueue = new LinkedBlockingQueue<>();

    private static final Integer[] BAUD_RATES = {300, 1200, 2400, 4800, 9600, 14400, 19200, 28800, 38400, 57600, 115200};
    private int currentBaudRate = BAUD_RATES[BAUD_RATES.length - 1];

    private final ExecutorService serialIoExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService msgQueueProcessorExecutor = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService canMessagePrinterExecutor = Executors.newScheduledThreadPool(1);

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    updateReceivedData(data);

                    //TODO: Azeez - no need to run this on UI thread
                    /*SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            SerialConsoleActivity.this.updateReceivedData(data);
                        }
                    });*/
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        mSpinner = (Spinner) findViewById(R.id.baudRateSpinner);
        ArrayAdapter<Integer> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BAUD_RATES);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!initialized) {
                    mSpinner.setSelection(BAUD_RATES.length -1);
                    initialized = true;
                } else {
                    int sid = mSpinner.getSelectedItemPosition();
                    currentBaudRate = BAUD_RATES[sid];
                    closePort();
                    openPort();
                }
                Toast.makeText(getBaseContext(), "Baud rate set to " + currentBaudRate, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        msgQueueProcessorExecutor.scheduleWithFixedDelay(new CanMessageProcessor(rawMsgQueue, canMsgQueue), 0, 1000, TimeUnit.MILLISECONDS);
        canMessagePrinterExecutor.scheduleWithFixedDelay(new CanMessagePrinter(canMsgQueue, this), 0, 2000, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        closePort();
        finish();
    }

    private void closePort() {
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
//            sPort = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        openPort();
    }

    private void openPort() {
        if (sPort == null) {
            mTitleTextView.setText("No serial device.");
            return;
        }

        final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
        if (connection == null) {
            mTitleTextView.setText("Opening device failed");
        } else {
            try {
                sPort.open(connection);
                sPort.setParameters(currentBaudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
                onDeviceStateChange();
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
//                sPort = null;
            }
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            serialIoExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        rawMsgQueue.add(data);
//        printHexDump(data); //TODO: remove this line after fixing CanMessageProcessor
    }

    public void printHexDump(final byte[] data) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final String message = HexDump.dumpHexString(data) + "\n\n";
                mDumpTextView.append(message);
                mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
            }
        });
    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param port
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, SerialConsoleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

}
