/*
 * Copyright (C) Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Caddisfly
 *
 * Akvo Caddisfly is free software: you can redistribute it and modify it under the terms of
 * the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 * either version 3 of the License or any later version.
 *
 * Akvo Caddisfly is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License included below for more details.
 *
 * The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.caddisfly.sensor.ec;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.app.CaddisflyApp;
import org.akvo.caddisfly.helper.TestConfigHelper;
import org.akvo.caddisfly.model.TestInfo;
import org.akvo.caddisfly.preference.AppPreferences;
import org.akvo.caddisfly.sensor.SensorConstants;
import org.akvo.caddisfly.sensor.colorimetry.strip.util.Constant;
import org.akvo.caddisfly.ui.BaseActivity;
import org.akvo.caddisfly.usb.UsbService;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class SensorActivity extends BaseActivity {

    private static final String TAG = "SensorActivity";
    private static final int REQUEST_DELAY_MILLIS = 2000;
    private static final int IDENTIFY_DELAY_MILLIS = 500;
    private static final int ANIMATION_DURATION = 500;
    private static final int ANIMATION_DURATION_LONG = 1500;
    private static final int FINISH_DELAY_MILLIS = 3000;
    private final StringBuilder mReadData = new StringBuilder();
    private final Handler handler = new Handler();
    private TestInfo mCurrentTestInfo;
    private Toast debugToast;
    private boolean mIsInternal = false;
    private LinearLayout layoutResult;
    private ProgressBar progressWait;
    private TextView textResult;
    private TextView textResult2;
    private TextView textUnit;
    private TextView textUnit2;
    private Button buttonAcceptResult;
    private TextView textSubtitle;
    private String mReceivedData = "";
    private UsbService usbService;
    private MyHandler mHandler;
    private ImageView imageUsbConnection;
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
            if (usbService.isUsbConnected()) {
                textSubtitle.setText(R.string.sensorConnected);
                imageUsbConnection.animate().alpha(0f).setDuration(ANIMATION_DURATION);
                progressWait.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };
    // Notifications from UsbService will be received here.
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equals(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)) {
                Toast.makeText(arg0, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                displayNotConnectedView();
            } else if (arg1.getAction().equals(UsbService.ACTION_NO_USB)) {
                displayNotConnectedView();
            } else if (arg1.getAction().equals(UsbService.ACTION_USB_DISCONNECTED)) {
                Toast.makeText(arg0, "USB disconnected", Toast.LENGTH_SHORT).show();
                displayNotConnectedView();
            } else if (arg1.getAction().equals(UsbService.ACTION_USB_NOT_SUPPORTED)) {
                Toast.makeText(arg0, "USB device not supported", Toast.LENGTH_SHORT).show();
                displayNotConnectedView();
            }
        }
    };
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            requestResult();
            handler.postDelayed(this, REQUEST_DELAY_MILLIS);
        }
    };
    private ArrayList<String> results = new ArrayList<>();
    private int deviceStatus = 0;

    private final Runnable validateDeviceRunnable = new Runnable() {
        @Override
        public void run() {
            String data = "device\r\n";
            if (usbService != null && usbService.isUsbConnected()) {
                // if UsbService was correctly bound, Send data
                usbService.write(data.getBytes(StandardCharsets.UTF_8));
            } else {
                displayNotConnectedView();
            }

            switch (deviceStatus) {

                case 0:
                    handler.postDelayed(this, IDENTIFY_DELAY_MILLIS);
                    break;
                case 1:
                    handler.postDelayed(runnable, 100);
                    break;
                default:
                    Toast.makeText(getBaseContext(), getString(R.string.connectCorrectSensor,
                            mCurrentTestInfo.getName()),
                            Toast.LENGTH_SHORT).show();
                    finish();
                    break;
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService

        // Start UsbService(if it was not started before) and Bind it
        startService(UsbService.class, usbConnection, null);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setTitle(R.string.sensor);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(runnable);
        handler.removeCallbacks(validateDeviceRunnable);
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    @SuppressWarnings("SameParameterValue")
    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {

        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        deviceStatus = 0;

        handler.postDelayed(validateDeviceRunnable, 100);
    }

    private void requestResult() {
        String data = "r\r\n";
        if (usbService != null && usbService.isUsbConnected()) {
            // if UsbService was correctly bound, Send data
            usbService.write(data.getBytes(StandardCharsets.UTF_8));
        } else {
            displayNotConnectedView();
        }
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sensor);

        final Intent intent = getIntent();
        String mUuid = intent.getStringExtra(Constant.UUID);
        CaddisflyApp.getApp().loadTestConfigurationByUuid(mUuid);
        mCurrentTestInfo = CaddisflyApp.getApp().getCurrentTestInfo();

        mIsInternal = intent.getBooleanExtra("internal", false);
        mHandler = new MyHandler(this);

        textSubtitle = (TextView) findViewById(R.id.textSubtitle);
        progressWait = (ProgressBar) findViewById(R.id.progressWait);
        textResult = (TextView) findViewById(R.id.textResult);
        textResult2 = (TextView) findViewById(R.id.textResult2);
        textUnit = (TextView) findViewById(R.id.textUnit);
        textUnit2 = (TextView) findViewById(R.id.textUnit2);
        imageUsbConnection = (ImageView) findViewById(R.id.imageUsbConnection);

        textSubtitle.setText(R.string.deviceConnectSensor);

        buttonAcceptResult = (Button) findViewById(R.id.buttonAcceptResult);
        buttonAcceptResult.setVisibility(View.INVISIBLE);
        buttonAcceptResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Build the result json to be returned
                TestInfo testInfo = CaddisflyApp.getApp().getCurrentTestInfo();

                Intent resultIntent = new Intent(intent);

                JSONObject resultJson = TestConfigHelper.getJsonResult(testInfo, results, -1, "", null);
                resultIntent.putExtra(SensorConstants.RESPONSE, resultJson.toString());

                // TODO: Remove this when obsolete
                // Backward compatibility. Return plain text result
                resultIntent.putExtra(SensorConstants.RESPONSE_COMPAT, results.get(0));

                setResult(Activity.RESULT_OK, resultIntent);
                finish();
            }
        });

        layoutResult = (LinearLayout) findViewById(R.id.layoutResult);

        if (mCurrentTestInfo != null && !mCurrentTestInfo.getName().isEmpty()) {
            ((TextView) findViewById(R.id.textTitle)).setText(
                    mCurrentTestInfo.getName());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void displayNotConnectedView() {
        if (!isFinishing()) {
            mReadData.setLength(0);
            progressWait.setVisibility(View.GONE);
            layoutResult.animate().alpha(0f).setDuration(ANIMATION_DURATION);
            imageUsbConnection.animate().alpha(1f).setDuration(ANIMATION_DURATION_LONG);
            buttonAcceptResult.setVisibility(View.GONE);
            textSubtitle.setText(R.string.deviceConnectSensor);

            if (!mIsInternal) {
                (new Handler()).postDelayed(new Runnable() {
                    public void run() {
                        if (!isFinishing()) {
                            Toast.makeText(getBaseContext(), getString(R.string.connectCorrectSensor,
                                    mCurrentTestInfo.getName()),
                                    Toast.LENGTH_SHORT).show();

                            finish();
                        }

                    }
                }, FINISH_DELAY_MILLIS);
            }
        }
    }

    private void displayResult(String value) {

        if (AppPreferences.getShowDebugMessages()) {
            Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
        }

        if (value.startsWith(".") || value.startsWith(",")) {
            return;
        }

        value = value.trim();
        if (value.contains("\r\n")) {
            String[] values = value.split("\r\n");
            if (values.length > 0) {
                value = values[1];
            }
        }

        value = value.trim();
        if (!value.isEmpty()) {
            if (deviceStatus == 0) {
                if (value.contains(" ")) {
                    if (value.startsWith(mCurrentTestInfo.getDeviceId())) {
                        deviceStatus = 1;
                    } else {
                        deviceStatus = 2;
                    }
                }
                return;
            }

            if (value.startsWith(mCurrentTestInfo.getDeviceId())) {
                return;
            }

            String[] resultArray = value.split(",");

            if (AppPreferences.getShowDebugMessages()) {
                final String finalValue = value;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (debugToast == null) {
                            debugToast = Toast.makeText(getBaseContext(), finalValue, Toast.LENGTH_LONG);
                        }
                        debugToast.setText(finalValue);
                        debugToast.show();
                    }
                });
            }

            if (resultArray.length == mCurrentTestInfo.getSubTests().size()) {

                String responseFormat = mCurrentTestInfo.getResponseFormat().replace("$", "").replace(" ", "").replace(",", "").trim();
                results.clear();

                String[] tempString = new String[mCurrentTestInfo.getSubTests().size()];

                for (int i = 0; i < resultArray.length; i++) {
                    resultArray[i] = resultArray[i].trim();

                    try {
                        double result = Double.parseDouble(resultArray[i]);
                        tempString[responseFormat.indexOf(String.valueOf(i + 1))] = String.format(Locale.US, "%.1f", result);
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }

                for (String aTempString : tempString) {
                    results.add(aTempString);
                }

                if (mCurrentTestInfo.getSubTests().size() > 0 && results.size() > 0) {
                    textResult.setText(results.get(0));
                    textUnit.setText(mCurrentTestInfo.getSubTests().get(0).getUnit());
                    textResult.setVisibility(View.VISIBLE);
                    textUnit.setVisibility(View.VISIBLE);
                    progressWait.setVisibility(View.GONE);
                    buttonAcceptResult.setVisibility(View.VISIBLE);
                    textSubtitle.setText(R.string.sensorConnected);
                } else {
                    textResult.setVisibility(View.GONE);
                    textUnit.setVisibility(View.GONE);
                    buttonAcceptResult.setVisibility(View.GONE);
                    textSubtitle.setText(R.string.dipSensorInSample);
                }

                if (mCurrentTestInfo.getSubTests().size() > 1 && results.size() > 1) {
                    textResult2.setText(results.get(1));
                    textUnit2.setText(mCurrentTestInfo.getSubTests().get(1).getUnit());
                    textResult2.setVisibility(View.VISIBLE);
                    textUnit2.setVisibility(View.VISIBLE);
                } else {
                    textResult2.setVisibility(View.GONE);
                    textUnit2.setVisibility(View.GONE);
                }

                if (mIsInternal) {
                    buttonAcceptResult.setVisibility(View.GONE);
                }

                layoutResult.animate().alpha(1f).setDuration(ANIMATION_DURATION);
                imageUsbConnection.animate().alpha(0f).setDuration(ANIMATION_DURATION);
            }
        }
    }

    /*
     * This handler will be passed to UsbService.
     * Data received from serial port is displayed through this handler
     */
    private static class MyHandler extends Handler {
        private final WeakReference<SensorActivity> mActivity;

        MyHandler(SensorActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == UsbService.MESSAGE_FROM_SERIAL_PORT) {
                String data = (String) msg.obj;
                SensorActivity sensorActivity = mActivity.get();
                if (sensorActivity != null) {
                    sensorActivity.mReceivedData += data;
                    if (sensorActivity.mReceivedData.contains("\r\n")) {
                        sensorActivity.displayResult(sensorActivity.mReceivedData);
                        sensorActivity.mReceivedData = "";
                    }
                }
            }
        }
    }
}
