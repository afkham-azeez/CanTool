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
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lk.vega.cantool.can.CanMessage;
import lk.vega.cantool.can.CanMessagePrinter;
import lk.vega.cantool.can.CanMessageProcessor;
import lk.vega.cantool.can.CanConstants;
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

    public static final String BAUD_RATE_KEY = "baudRate";
    public static final String BAUD_RATE_ITEM_POSITION_KEY = "baudRateItemPosition";
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
    private Button mStartButton;
    private Button mClearButton;
    private Button mRawCanButton;
    private Button mEmailButton;
    private Button mSendCanMsgButton;
    private Button mCanSyncButton;
    private EditText mCanMsgEditView;

    private boolean initialized;
    private boolean isCanView = true;

    private Queue<byte[]> rawMsgQueue = new LinkedBlockingQueue<>();
    private Queue<CanMessage> canMsgQueue = new LinkedBlockingQueue<>();

    private static final Integer[] BAUD_RATES = {300, 1200, 2400, 4800, 9600, 14400, 19200, 28800, 38400, 57600, 115200};
    private int currentBaudRate = BAUD_RATES[BAUD_RATES.length - 1];

    private final ExecutorService serialIoExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService msgQueueProcessorExecutor = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService canMessagePrinterExecutor = Executors.newScheduledThreadPool(1);

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
    private CanMessageProcessor canMessageProcessor;


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
                }
            };
    private boolean scanStarted;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.canDataScroller);
        mSpinner = (Spinner) findViewById(R.id.baudRateSpinner);
        mStartButton = (Button) findViewById(R.id.startButton);
        mClearButton = (Button) findViewById(R.id.clearButton);
        mRawCanButton = (Button) findViewById(R.id.rawCanButton);
        mEmailButton = (Button) findViewById(R.id.shareButton);
        mSendCanMsgButton = (Button) findViewById(R.id.sendCanMsg);
        mCanSyncButton = (Button) findViewById(R.id.canSync);
        mCanMsgEditView = (EditText) findViewById(R.id.canMsg);

        mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());

        initBaudRateSpinner();
        initStartButton();
        initClearButton();
        initRawCanButton();
        initEmailButton();
        initSendCanMsgButton();
        initSendCanSyncButton();

        canMessageProcessor = new CanMessageProcessor(rawMsgQueue, canMsgQueue);
        msgQueueProcessorExecutor.scheduleWithFixedDelay(canMessageProcessor, 0, 1, TimeUnit.MILLISECONDS);
        canMessagePrinterExecutor.scheduleWithFixedDelay(new CanMessagePrinter(canMsgQueue, this), 0, 250, TimeUnit.MILLISECONDS);
    }

    private void initRawCanButton() {
        mRawCanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isCanView = !isCanView;
                if (isCanView) {
                    mRawCanButton.setText(getResources().getString(R.string.can));
                    mRawCanButton.setBackgroundColor(Color.rgb(0xa4, 0xc6, 0x39));
                    Toast.makeText(getBaseContext(), "Format set to CAN", Toast.LENGTH_SHORT).show();
                } else {
                    mRawCanButton.setText(getResources().getString(R.string.raw));
                    mRawCanButton.setBackgroundColor(Color.RED);
                    Toast.makeText(getBaseContext(), "Format set to Raw", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initSendCanSyncButton() {
        mCanSyncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClearButton.callOnClick();
                canMessageProcessor.reset();
                canMessageProcessor.setWaitingForSyncAck(true);
                if (mSerialIoManager == null) {
                    mStartButton.callOnClick();
                }
                mSerialIoManager.writeAsync(HexDump.hexStringToByteArray(CanConstants.CAN_SYNC));
                Toast.makeText(getBaseContext(), "Sync successful", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initSendCanMsgButton() {
        mSendCanMsgButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String canMsg = mCanMsgEditView.getText().toString();
                if (!canMsg.isEmpty() && canMsg.length() == CanMessage.CAN_MSG_SIZE_BYTES * 2) {
                    if (mSerialIoManager == null) {
                        mStartButton.callOnClick();
                    }
                    mSerialIoManager.writeAsync(HexDump.hexStringToByteArray(canMsg));
                    Toast.makeText(getBaseContext(), "CAN message [" + canMsg + "] sent", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getBaseContext(),
                            "Invalid CAN message length [" + canMsg.length() + "]. CAN message size is " + CanMessage.CAN_MSG_SIZE_BYTES + " bytes",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initEmailButton() {
        mEmailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String data = mDumpTextView.getText().toString();
                if (data != null && !data.isEmpty()) {
                    String shareBody = "Device: " + sPort.getClass().getSimpleName() + "\n\n" + "Data\n" + data;
                    Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                    sharingIntent.setType("text/plain");
                    sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Vega CAN data [" + dateFormat.format(new Date()) + "]");
                    sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                    startActivity(sharingIntent);
                } else {
                    Toast.makeText(getBaseContext(), "Nothing to share", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initClearButton() {
        mClearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDumpTextView.setText("");
            }
        });
    }

    private void initStartButton() {
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanStarted = !scanStarted;
                if (scanStarted) {
                    mStartButton.setText(getResources().getString(R.string.stop));
                    mStartButton.setBackgroundColor(Color.RED);
                    openPort();
                    // Send handshake
//                    mSerialIoManager.writeAsync(HexDump.hexStringToByteArray(CanConstants.CAN_SYNC));
                    Toast.makeText(getBaseContext(), "Scan started", Toast.LENGTH_SHORT).show();
                } else {
                    mStartButton.setText(getResources().getString(R.string.start));
                    mStartButton.setBackgroundColor(Color.rgb(0xa4, 0xc6, 0x39));
                    closePort();
                    Toast.makeText(getBaseContext(), "Scan stopped", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initBaudRateSpinner() {
        ArrayAdapter<Integer> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BAUD_RATES);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SerialConsoleActivity.this);
                if (!initialized) {
                    currentBaudRate = preferences.getInt(BAUD_RATE_KEY, BAUD_RATES[BAUD_RATES.length - 1]);
                    int sid = preferences.getInt(BAUD_RATE_ITEM_POSITION_KEY, BAUD_RATES.length - 1);
                    mSpinner.setSelection(sid);
                    initialized = true;
                } else {
                    int sid = mSpinner.getSelectedItemPosition();
                    currentBaudRate = BAUD_RATES[sid];
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt(BAUD_RATE_KEY, currentBaudRate);
                    editor.putInt(BAUD_RATE_ITEM_POSITION_KEY, sid);
                    editor.apply();
                    closePort();
                    scanStarted = false;
                    mStartButton.setText(getResources().getString(R.string.start));
                }
                Toast.makeText(getBaseContext(), "Baud rate set to " + currentBaudRate, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Nothing to do
            }
        });
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
        if (isCanView) {
            rawMsgQueue.add(data);
        } else {
            printHexDump(data);
        }
    }

    public void printHexDump(final byte[] data) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final String message = HexDump.toHexString(data);
                mDumpTextView.append(message + "\n");
                mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
            }
        });
    }

    public void printCanMessage(final CanMessage canMessage) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final String message = HexDump.toHexString(canMessage.getMessageId()) + "    " +
                        HexDump.toHexString(canMessage.getData());
                mDumpTextView.append(message + "\n");
                mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
            }
        });
    }

//    public void printCanMessage(final CanMessage canMessage) {
        /*this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Get the TableLayout
                TableLayout tl = (TableLayout) findViewById(R.id.canDataTable);

                // Create a TableRow and give it an ID
                TableRow tr = new TableRow(SerialConsoleActivity.this);
                tr.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                // Create a TextView to show CAN message ID
                updateCanData(tr, canMessage.getMessageId());

                // Create a TextView to show CAN message data
                updateCanData(tr, canMessage.getData());

                // Create a TextView to show the raw CAN message
                updateCanData(tr, canMessage.getRaw());

                // Add the TableRow to the TableLayout
                tl.addView(tr, new TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

//                mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
            }

            private void updateCanData(TableRow tr, byte[] data) {
                TextView canId = new TextView(SerialConsoleActivity.this);
                canId.setText(HexDump.toHexString(data));
//                canId.setTextColor(Color.BLACK);
                canId.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                tr.addView(canId);
            }
        });*/
//    }

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
