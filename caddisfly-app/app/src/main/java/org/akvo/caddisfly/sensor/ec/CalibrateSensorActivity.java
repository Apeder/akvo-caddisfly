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

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.app.CaddisflyApp;
import org.akvo.caddisfly.ui.BaseActivity;
import org.akvo.caddisfly.usb.UsbService;
import org.akvo.caddisfly.util.AlertUtil;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class CalibrateSensorActivity extends BaseActivity implements EditSensorIdentity.OnFragmentInteractionListener {

    private static final String TAG = "CalibrateSensorActivity";

    private static final String LINE_FEED = "\r\n";
    private static final int INITIAL_DELAY_MILLIS = 2000;
    private static final int PROGRESS_MAX = 15;
    private static final int CALIBRATION_DELAY_MILLIS = 15000;
    private static final int SAVING_DELAY_MILLIS = 4000;
    private final int[] calibrationPoints = new int[]{141, 235, 470, 1413, 3000, 12880};
    // Notifications from UsbService will be received here.
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, @NonNull Intent arg1) {
//            if (arg1.getAction().equals(UsbService.ACTION_USB_PERMISSION_GRANTED)) // USB PERMISSION GRANTED
//            {
//                Toast.makeText(arg0, "USB Ready", Toast.LENGTH_SHORT).show();
//            } else
//            else if (arg1.getAction().equals(UsbService.ACTION_NO_USB)) // NO USB CONNECTED
//            {
//                Toast.makeText(arg0, "No USB connected", Toast.LENGTH_SHORT).show();
//            }

            if (arg1.getAction().equals(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)) {
                Toast.makeText(arg0, "USB Permission not granted", Toast.LENGTH_SHORT).show();
            } else if (arg1.getAction().equals(UsbService.ACTION_USB_DISCONNECTED)) {
                Toast.makeText(arg0, "USB disconnected", Toast.LENGTH_SHORT).show();
            } else if (arg1.getAction().equals(UsbService.ACTION_USB_NOT_SUPPORTED)) {
                Toast.makeText(arg0, "USB device not supported", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private final WeakRefHandler progressHandler = new WeakRefHandler(this);
    private ProgressDialog progressDialog;
    private ViewAnimator viewAnimator;
    private boolean deviceHasId = false;
    private int calibrationIndex = 0;
    private TextView textHeading;
    private TextView textSubtitle;
    private TextView textInformation;
    private Context mContext;
    private UsbService usbService;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            requestResult();
        }
    };
    private TextView textId;
    private UsbDataHandler mHandler;
    @Nullable
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, @NonNull IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };
    @NonNull
    private String mReceivedData = "";
    private FloatingActionButton fabEdit;

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService

        // Start UsbService(if it was not started before) and Bind it
        startService(UsbService.class, usbConnection, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
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

    @SuppressWarnings("SameParameterValue")
    private void startService(Class<?> service, ServiceConnection serviceConnection, @Nullable Bundle extras) {

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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate_sensor);

        mContext = this;

        mHandler = new UsbDataHandler(this);

        viewAnimator = (ViewAnimator) findViewById(R.id.viewAnimator);

        Button buttonStartCalibrate = (Button) findViewById(R.id.buttonStartCalibrate);
        Button buttonFinishCalibrate = (Button) findViewById(R.id.buttonFinishCalibrate);
        Button buttonNext = (Button) findViewById(R.id.buttonNext);

        fabEdit =
                (FloatingActionButton) findViewById(R.id.fabEdit);


        Configuration conf = getResources().getConfiguration();
        if (!CaddisflyApp.getApp().getCurrentTestInfo().getName(conf.locale.getLanguage()).isEmpty()) {
            ((TextView) findViewById(R.id.textTitle)).setText(
                    CaddisflyApp.getApp().getCurrentTestInfo().getName(conf.locale.getLanguage()));
        }

        textHeading = (TextView) findViewById(R.id.textHeading);
        textSubtitle = (TextView) findViewById(R.id.textSubtitle);
        textInformation = (TextView) findViewById(R.id.textInformation);
        textId = (TextView) findViewById(R.id.textId);

        fabEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEditDetailsDialog();
            }
        });

        buttonStartCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (usbService.isUsbConnected()) {

                    final ProgressDialog connectingProgressDialog = ProgressDialog.show(mContext,
                            getString(R.string.pleaseWait), getString(R.string.deviceConnecting), true, false);

                    new Handler().postDelayed(runnable, 100);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (deviceHasId) {
                                viewAnimator.showNext();
                                fabEdit.setVisibility(View.VISIBLE);
                                textSubtitle.setText(R.string.verifySensorDetails);

                            } else {
                                showEditDetailsDialog();
                            }
                            connectingProgressDialog.dismiss();
                        }
                    }, INITIAL_DELAY_MILLIS);

                } else {
                    AlertUtil.showMessage(mContext, R.string.sensorNotFound, R.string.deviceConnectSensor);
                }
            }
        });

        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (deviceHasId) {
                    viewAnimator.showNext();
                    fabEdit.setVisibility(View.INVISIBLE);

                    displayInformation(calibrationIndex);
                } else {
                    showEditDetailsDialog();
                }

            }
        });

        buttonFinishCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (usbService.isUsbConnected()) {
                    calibratePoint(calibrationPoints, calibrationIndex);
                    calibrationIndex++;
                } else {
                    AlertUtil.showMessage(mContext, R.string.sensorNotFound, R.string.deviceConnectSensor);
                }
            }
        });
    }

    private void showEditDetailsDialog() {
        final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        EditSensorIdentity editSensorIdentity = EditSensorIdentity.newInstance();
        editSensorIdentity.show(ft, "editSensorIdentity");
    }

    private void displayInformation(int index) {

        textHeading.setText(String.format(Locale.US,
                getString(R.string.calibratePoint),
                calibrationPoints[index]));
        textSubtitle.setText(String.format(Locale.US, "Step %d of 6", index + 1));
        textInformation.setText(String.format(Locale.US,
                getString(R.string.getEcSolutionReady),
                calibrationPoints[index]));

    }

    private void requestResult() {
        String data = "GET ID\r\n";
        if (usbService != null) {
            usbService.write(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void calibratePoint(final int[] calibrations, final int index) {

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(R.string.pleaseWait);
        progressDialog.setMessage(getString(R.string.calibrating));
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(PROGRESS_MAX);
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (progressDialog.getProgress() <= progressDialog
                            .getMax()) {
                        Thread.sleep(1000);
                        progressHandler.sendMessage(progressHandler.obtainMessage());
                        if (progressDialog.getProgress() == progressDialog
                                .getMax()) {
                            progressDialog.dismiss();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }).start();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {

                String requestCommand = "SET POINT" + calibrationIndex + " " + calibrations[index] + LINE_FEED;

                usbService.write(requestCommand.getBytes(StandardCharsets.UTF_8));

                (new Handler()).postDelayed(new Runnable() {
                    public void run() {

                        progressDialog.dismiss();

                        if (calibrationIndex > 5) {

                            AlertUtil.showAlert(mContext, R.string.calibrationSuccessful,
                                    R.string.sensorCalibrated, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            finish();
                                        }
                                    }, null, null);

                        } else {
                            displayInformation(calibrationIndex);
                        }
                    }
                }, 1000);
            }
        }, CALIBRATION_DELAY_MILLIS);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setTitle(R.string.calibrate);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_back_out, R.anim.slide_back_in);
    }

    private void displayId(String value) {
        value = value.replace(LINE_FEED, "");
        textId.setText(value);
        deviceHasId = (!value.trim().equals("") && !value.trim().equals("0"));
    }

    @Override
    public void onFragmentInteraction(String value) {
        String requestCommand = "SET ID " + value + LINE_FEED;

        usbService.write(requestCommand.getBytes(StandardCharsets.UTF_8));

        final ProgressDialog savingProgressDialog = ProgressDialog.show(mContext,
                getString(R.string.pleaseWait), getString(R.string.saving), true, false);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                String getIdRequest = "GET ID" + LINE_FEED;
                usbService.write(getIdRequest.getBytes(StandardCharsets.UTF_8));

                savingProgressDialog.dismiss();

                displayInformation(calibrationIndex);

                if (viewAnimator.getDisplayedChild() == 0) {
                    viewAnimator.showNext();
                    fabEdit.setVisibility(View.VISIBLE);
                }
            }
        }, SAVING_DELAY_MILLIS);

    }

    /**
     * Handler to restart the app after language has been changed
     */
    private static class WeakRefHandler extends Handler {
        @NonNull
        private final WeakReference<CalibrateSensorActivity> ref;

        WeakRefHandler(CalibrateSensorActivity ref) {
            this.ref = new WeakReference<>(ref);
        }

        @Override
        public void handleMessage(Message msg) {
            CalibrateSensorActivity f = ref.get();
            if (f != null) {
                f.progressDialog.incrementProgressBy(1);
            }
        }
    }

    /*
    * This handler will be passed to UsbService.
    * Data received from serial port is displayed through this handler
    */
    private static class UsbDataHandler extends Handler {
        @NonNull
        private final WeakReference<CalibrateSensorActivity> mActivity;

        UsbDataHandler(CalibrateSensorActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == UsbService.MESSAGE_FROM_SERIAL_PORT) {
                String data = (String) msg.obj;
                CalibrateSensorActivity sensorActivity = mActivity.get();
                if (sensorActivity != null) {
                    sensorActivity.mReceivedData += data;
                    if (sensorActivity.mReceivedData.contains(LINE_FEED)) {
                        if (!sensorActivity.mReceivedData.contains("OK")) {
                            sensorActivity.displayId(sensorActivity.mReceivedData);
                        }
                        sensorActivity.mReceivedData = "";
                    }
                }
            }
        }
    }
}
