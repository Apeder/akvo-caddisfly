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
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.app.CaddisflyApp;
import org.akvo.caddisfly.ui.BaseActivity;
import org.akvo.caddisfly.util.AlertUtil;
import org.akvo.caddisfly.util.PreferencesUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class CalibrateSensorActivity extends BaseActivity {

    private static final String DEBUG_TAG = "SensorActivity";
    // Notifications from UsbService will be received here.
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equals(UsbService.ACTION_USB_PERMISSION_GRANTED)) // USB PERMISSION GRANTED
            {
                Toast.makeText(arg0, "USB Ready", Toast.LENGTH_SHORT).show();
            } else if (arg1.getAction().equals(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)) // USB PERMISSION NOT GRANTED
            {
                Toast.makeText(arg0, "USB Permission not granted", Toast.LENGTH_SHORT).show();
            } else if (arg1.getAction().equals(UsbService.ACTION_NO_USB)) // NO USB CONNECTED
            {
                Toast.makeText(arg0, "No USB connected", Toast.LENGTH_SHORT).show();
            } else if (arg1.getAction().equals(UsbService.ACTION_USB_DISCONNECTED)) // USB DISCONNECTED
            {
                Toast.makeText(arg0, "USB disconnected", Toast.LENGTH_SHORT).show();
            } else if (arg1.getAction().equals(UsbService.ACTION_USB_NOT_SUPPORTED)) // USB NOT SUPPORTED
            {
                Toast.makeText(arg0, "USB device not supported", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private TextView textSubtitle;
    private Context mContext;
    private UsbService usbService;
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };
    private EditText mCurrentEditText;

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
    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        Log.d(DEBUG_TAG, "Start Service");

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

    @SuppressWarnings("SameParameterValue")
    private void setEditTextFocus(EditText editText, boolean isFocused) {
        editText.setCursorVisible(isFocused);
        editText.setFocusable(isFocused);
        editText.setFocusableInTouchMode(isFocused);

        if (isFocused) {
            editText.requestFocus();
        }

        mCurrentEditText = editText;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate_sensor);

        mContext = this;

        final ViewAnimator viewAnimator = (ViewAnimator) findViewById(R.id.viewAnimator);

        Button buttonStartCalibrate = (Button) findViewById(R.id.buttonStartCalibrate);
        final EditText editLowValue = (EditText) findViewById(R.id.editLowValue);
        final EditText editMiddleValue = (EditText) findViewById(R.id.editMiddleValue);
        final EditText editHighValue = (EditText) findViewById(R.id.editHighValue);

        Configuration conf = getResources().getConfiguration();
        if (!CaddisflyApp.getApp().getCurrentTestInfo().getName(conf.locale.getLanguage()).isEmpty()) {
            ((TextView) findViewById(R.id.textTitle)).setText(
                    CaddisflyApp.getApp().getCurrentTestInfo().getName(conf.locale.getLanguage()));
        }

        textSubtitle = (TextView) findViewById(R.id.textSubtitle);

        String calibratedDate = PreferencesUtil.getString(this, R.string.sensorCalibratedDateKey, "");
        if (!calibratedDate.isEmpty()) {
            textSubtitle.setText(String.format("Calibrated: %s", calibratedDate));
        }

        final Context context = this;
        buttonStartCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (usbService.isUsbConnected()) {

                    final ProgressDialog dialog = ProgressDialog.show(mContext,
                            getString(R.string.pleaseWait), getString(R.string.deviceConnecting), true);
                    dialog.setCancelable(false);
                    dialog.show();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            textSubtitle.setText(R.string.lowEcMeasurement);
                            viewAnimator.showNext();

                            setEditTextFocus(editLowValue, true);
                            InputMethodManager imm = (InputMethodManager) context.getSystemService(
                                    Context.INPUT_METHOD_SERVICE);
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

                        }
                    }, 2000);

                } else {
                    AlertUtil.showMessage(mContext, R.string.sensorNotFound, R.string.deviceConnectSensor);
                }
            }
        });

        editLowValue.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_DONE) ||
                        ((event.getKeyCode() == KeyEvent.KEYCODE_ENTER) &&
                                (event.getAction() == KeyEvent.ACTION_DOWN))) {
                    if (validInput(editLowValue.getText().toString())) {
                        closeKeyboard(editLowValue);

                        if (usbService.isUsbConnected()) {
//                            if (mEcValue != -1) {

                            final ProgressDialog dialog = ProgressDialog.show(mContext,
                                    getString(R.string.pleaseWait), getString(R.string.calibrating), true);
                            dialog.setCancelable(false);
                            dialog.show();

                            new Handler().postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    String requestCommand = "SET POINT1 " + editLowValue.getText() + "\r\n";
                                    usbService.write(requestCommand.getBytes());
                                    (new Handler()).postDelayed(new Runnable() {
                                        public void run() {
                                            dialog.dismiss();
                                            textSubtitle.setText(R.string.middleEcMeasurement);
                                            viewAnimator.showNext();
                                            setEditTextFocus(editMiddleValue, true);

                                            InputMethodManager imm = (InputMethodManager) context.getSystemService(
                                                    Context.INPUT_METHOD_SERVICE);
                                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

                                        }
                                    }, 1000);
                                }
                            }, 4000);
                        } else {
                            AlertUtil.showMessage(mContext, R.string.sensorNotFound, R.string.deviceConnectSensor);
                        }
                    } else {
                        editLowValue.setError(getString(R.string.pleaseEnterValue));
                    }
                }
                return true;

            }
        });

        editMiddleValue.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_DONE) ||
                        ((event.getKeyCode() == KeyEvent.KEYCODE_ENTER) &&
                                (event.getAction() == KeyEvent.ACTION_DOWN))) {
                    if (validInput(editMiddleValue.getText().toString())) {
                        closeKeyboard(editMiddleValue);

                        if (usbService.isUsbConnected()) {
//                            if (mEcValue != -1) {

                            final ProgressDialog dialog = ProgressDialog.show(mContext,
                                    getString(R.string.pleaseWait), getString(R.string.calibrating), true);
                            dialog.setCancelable(false);
                            dialog.show();

                            new Handler().postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    String requestCommand = "SET POINT3" + editMiddleValue.getText() + "\r\n";
                                    usbService.write(requestCommand.getBytes());
                                    (new Handler()).postDelayed(new Runnable() {
                                        public void run() {
                                            dialog.dismiss();
                                            textSubtitle.setText(R.string.highEcMeasurement);
                                            viewAnimator.showNext();
                                            setEditTextFocus(editHighValue, true);

                                            InputMethodManager imm = (InputMethodManager) context.getSystemService(
                                                    Context.INPUT_METHOD_SERVICE);
                                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

                                        }
                                    }, 1000);
                                }
                            }, 4000);
                        } else {
                            AlertUtil.showMessage(mContext, R.string.sensorNotFound, R.string.deviceConnectSensor);
                        }
                    } else {
                        editMiddleValue.setError(getString(R.string.pleaseEnterValue));
                    }
                }
                return true;

            }
        });

        editHighValue.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (validInput(editHighValue.getText().toString())) {
                    closeKeyboard(editHighValue);

                    if (usbService.isUsbConnected()) {

                        final ProgressDialog dialog = ProgressDialog.show(mContext,
                                getString(R.string.pleaseWait),
                                getString(R.string.calibrating), true);
                        dialog.setCancelable(false);
                        dialog.show();

                        new Handler().postDelayed(new Runnable() {

                            @Override
                            public void run() {

                                String requestCommand = "SET POINT6 " + editHighValue.getText() + "\r\n";
                                usbService.write(requestCommand.getBytes());
                                (new Handler()).postDelayed(new Runnable() {
                                    public void run() {
                                        dialog.dismiss();
                                        PreferencesUtil.setString(context, R.string.sensorCalibratedDateKey,
                                                new SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.US).format(new Date()));
                                        AlertUtil.showAlert(mContext, R.string.calibrationSuccessful,
                                                R.string.sensorCalibrated, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialogInterface, int i) {
                                                        finish();
                                                    }
                                                }, null, null);
                                    }
                                }, 1000);
                            }
                        }, 4000);
                    } else {
                        AlertUtil.showMessage(mContext, R.string.sensorNotFound, R.string.deviceConnectSensor);
                    }

                } else {
                    editHighValue.setError(getString(R.string.pleaseEnterValue));
                }
                return true;

            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setTitle(R.string.calibrate);
    }

    private boolean validInput(String input) {
        return input.trim().length() > 0;
    }

    private void closeKeyboard(EditText input) {
        if (input != null) {
            InputMethodManager imm = (InputMethodManager) this.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                closeKeyboard(mCurrentEditText);
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        closeKeyboard(mCurrentEditText);
        overridePendingTransition(R.anim.slide_back_out, R.anim.slide_back_in);
    }
}
