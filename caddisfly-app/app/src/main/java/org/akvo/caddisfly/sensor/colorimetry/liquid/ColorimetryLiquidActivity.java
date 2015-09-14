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

package org.akvo.caddisfly.sensor.colorimetry.liquid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.view.Display;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewAnimator;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.app.CaddisflyApp;
import org.akvo.caddisfly.helper.ShakeDetector;
import org.akvo.caddisfly.helper.SoundPoolPlayer;
import org.akvo.caddisfly.helper.SwatchHelper;
import org.akvo.caddisfly.model.ColorInfo;
import org.akvo.caddisfly.model.Result;
import org.akvo.caddisfly.model.ResultDetail;
import org.akvo.caddisfly.model.Swatch;
import org.akvo.caddisfly.model.TestInfo;
import org.akvo.caddisfly.preference.AppPreferences;
import org.akvo.caddisfly.sensor.Camera2DialogFragment;
import org.akvo.caddisfly.sensor.CameraDialog;
import org.akvo.caddisfly.sensor.CameraDialogFragment;
import org.akvo.caddisfly.ui.BaseActivity;
import org.akvo.caddisfly.util.AlertUtil;
import org.akvo.caddisfly.util.ApiUtil;
import org.akvo.caddisfly.util.ColorUtil;
import org.akvo.caddisfly.util.ImageUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class ColorimetryLiquidActivity extends BaseActivity
        implements ResultDialogFragment.ResultDialogListener,
        HighLevelsDialogFragment.MessageDialogListener,
        DiagnosticResultDialog.DiagnosticResultDialogListener {
    private final Handler delayHandler = new Handler();
    private boolean mIsCalibration;
    private double mSwatchValue;
    private int mDilutionLevel = 0;
    private DiagnosticResultDialog mResultFragment;
    private TextView mDilutionTextView;
    private TextView mDilutionTextView1;
    private SoundPoolPlayer sound;
    private ShakeDetector mShakeDetector;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private boolean mWaitingForStillness = false;
    private ViewAnimator mViewAnimator;
    private CameraDialog mCameraFragment;
    private Runnable delayRunnable;
    private PowerManager.WakeLock wakeLock;
    private ArrayList<Result> mResults;
    private boolean mTestCompleted;
    private boolean mHighLevelsFound;
    private boolean mIgnoreShake;
    //pointer to last dialog opened so it can be dismissed on activity getting destroyed
    private AlertDialog alertDialogToBeDestroyed;
    private boolean mIsFirstResult;

    @SuppressWarnings("SameParameterValue")
    private static void setAnimatorDisplayedChild(ViewAnimator viewAnimator, int whichChild) {
        Animation inAnimation = viewAnimator.getInAnimation();
        Animation outAnimation = viewAnimator.getOutAnimation();
        viewAnimator.setInAnimation(null);
        viewAnimator.setOutAnimation(null);
        viewAnimator.setDisplayedChild(whichChild);
        viewAnimator.setInAnimation(inAnimation);
        viewAnimator.setOutAnimation(outAnimation);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            if (mIsCalibration) {
                getSupportActionBar().setTitle(String.format("%s %.2f %s",
                        getResources().getString(R.string.calibrate),
                        mSwatchValue, CaddisflyApp.getApp().getCurrentTestInfo().getUnit()));
            } else {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_colorimetry_liquid);

        mIsCalibration = getIntent().getBooleanExtra("isCalibration", false);
        mSwatchValue = getIntent().getDoubleExtra("swatchValue", 0);

        sound = new SoundPoolPlayer(this);

        mDilutionTextView = (TextView) findViewById(R.id.textDilution);
        mDilutionTextView1 = (TextView) findViewById(R.id.textDilution2);

        if (!mIsCalibration && CaddisflyApp.getApp().getCurrentTestInfo().getCanUseDilution()) {
            mDilutionTextView.setVisibility(View.VISIBLE);
            mDilutionTextView1.setVisibility(View.VISIBLE);
        } else {
            mDilutionTextView.setVisibility(View.GONE);
            mDilutionTextView1.setVisibility(View.GONE);
        }

        mViewAnimator = (ViewAnimator) findViewById(R.id.viewAnimator);

        //Set up the shake detector
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mShakeDetector = new ShakeDetector(new ShakeDetector.OnShakeListener() {
            @Override
            public void onShake() {
                if ((mIgnoreShake) || mWaitingForStillness || mCameraFragment == null) {
                    return;
                }

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    if (isDestroyed()) {
                        return;
                    }
                }

                mWaitingForStillness = true;

                showError(getString(R.string.errorTestInterrupted), null);
            }
        }, new ShakeDetector.OnNoShakeListener() {
            @Override
            public void onNoShake() {
                if (mWaitingForStillness) {
                    mWaitingForStillness = false;
                    dismissShakeAndStartTest();
                }
            }
        });
        mShakeDetector.minShakeAcceleration = 5;
        mShakeDetector.maxShakeDuration = 2000;

        Button noDilutionButton = (Button) findViewById(R.id.buttonNoDilution);
        Button percentButton1 = (Button) findViewById(R.id.buttonDilution1);
        Button percentButton2 = (Button) findViewById(R.id.buttonDilution2);

        //todo: remove hardcoding of dilution times
        percentButton1.setText(String.format(getString(R.string.timesDilution), 2));
        percentButton2.setText(String.format(getString(R.string.timesDilution), 5));

        noDilutionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDilutionLevel = 0;
                mDilutionTextView.setText(R.string.noDilution);
                mDilutionTextView1.setText(R.string.noDilution);
                mViewAnimator.showNext();
            }
        });

        percentButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDilutionLevel = 1;
                String dilutionLabel = String.format(getString(R.string.timesDilution), 2);
                mDilutionTextView.setText(dilutionLabel);
                mDilutionTextView1.setText(dilutionLabel);
                mViewAnimator.showNext();
            }
        });

        percentButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDilutionLevel = 2;
                String dilutionLabel = String.format(getString(R.string.timesDilution), 5);
                mDilutionTextView.setText(dilutionLabel);
                mDilutionTextView1.setText(dilutionLabel);
                mViewAnimator.showNext();
            }
        });


        findViewById(R.id.buttonStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mViewAnimator.showNext();

                acquireWakeLock();

                // disable the key guard when device wakes up and shake alert is displayed
                getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                );


                InitializeTest();

                mSensorManager.registerListener(mShakeDetector, mAccelerometer,
                        SensorManager.SENSOR_DELAY_UI);
            }
        });
    }

    private void InitializeTest() {

        mSensorManager.unregisterListener(mShakeDetector);

        mIgnoreShake = AppPreferences.getIgnoreShake();
        mTestCompleted = false;
        mHighLevelsFound = false;

        mWaitingForStillness = true;

        final Context context = this;
        Camera camera = null;
        try {
            camera = CaddisflyApp.getCamera(context, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    releaseResources();
                    dialogInterface.dismiss();
                    finish();
                }
            });
        } finally {
            if (camera != null) {
                camera.release();
            }
        }
    }

    /**
     * Acquire a wake lock to prevent the screen from turning off during the analysis process
     */
    private void acquireWakeLock() {
        if (wakeLock == null || !wakeLock.isHeld()) {
            PowerManager pm = (PowerManager) getApplicationContext()
                    .getSystemService(Context.POWER_SERVICE);
            //noinspection deprecation
            wakeLock = pm
                    .newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE, "CameraSensorWakeLock");
            wakeLock.acquire();
        }
    }

    /**
     * Start the test by displaying the progress bar
     */
    private void dismissShakeAndStartTest() {
        mSensorManager.unregisterListener(mShakeDetector);

        startTest();
    }

    /**
     * Show an error message dialog
     *
     * @param message the message to be displayed
     * @param bitmap  any bitmap image to displayed along with error message
     */
    private void showError(String message, final Bitmap bitmap) {

        releaseResources();

        setAnimatorDisplayedChild(mViewAnimator, 1);

        sound.playShortResource(R.raw.err);

        alertDialogToBeDestroyed = AlertUtil.showError(this, R.string.error, message, bitmap, R.string.retry,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        InitializeTest();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(getIntent());
                        setResult(Activity.RESULT_CANCELED, intent);
                        releaseResources();
                        finish();
                    }
                }
        );
    }

    /**
     * In diagnostic mode show the diagnostic results dialog
     *
     * @param testFailed    if test has failed then dialog know to show the retry button
     * @param result        the result shown to the user
     * @param color         the color that was used to get above result
     * @param isCalibration is it in calibration mode, then show only colors, no results
     */
    private void showDiagnosticResultDialog(boolean testFailed, double result, int color, boolean isCalibration) {
        mResultFragment = DiagnosticResultDialog.newInstance(mResults, testFailed, result, color, isCalibration);
        final FragmentTransaction ft = getFragmentManager().beginTransaction();

        Fragment prev = getFragmentManager().findFragmentByTag("gridDialog");
        if (prev != null) {
            ft.remove(prev);
        }
        mResultFragment.setCancelable(false);
        mResultFragment.show(ft, "gridDialog");
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsCalibration = getIntent().getBooleanExtra("isCalibration", false);
        mSwatchValue = getIntent().getDoubleExtra("swatchValue", 0);

        TestInfo testInfo = CaddisflyApp.getApp().getCurrentTestInfo();

        TextView textResult = ((TextView) findViewById(R.id.textResult));
        if (mIsCalibration) {
            textResult.setText(String.format("%.2f %s", mSwatchValue, testInfo.getUnit()));
            textResult.setVisibility(View.VISIBLE);
        } else {
            textResult.setVisibility(View.GONE);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.appName);
            }

            // disable the key guard when device wakes up and shake alert is displayed
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }

        Resources res = getResources();
        Configuration conf = res.getConfiguration();

        //set the title to the test contaminant name
        ((TextView) findViewById(R.id.textTitle)).setText(testInfo.getName(conf.locale.getLanguage()));
        ((TextView) findViewById(R.id.textTitle2)).setText(testInfo.getName(conf.locale.getLanguage()));
        ((TextView) findViewById(R.id.textTitle3)).setText(testInfo.getName(conf.locale.getLanguage()));

        if (testInfo.getCode().isEmpty()) {
            alertCouldNotLoadConfig();
        } else if (mIsCalibration || !testInfo.getCanUseDilution()) {
            releaseResources();
            setAnimatorDisplayedChild(mViewAnimator, 1);
        } else if (!mTestCompleted) {
            InitializeTest();
        }
    }

    /**
     * Display error message for configuration not loading correctly
     */
    private void alertCouldNotLoadConfig() {
        String message = String.format("%s\r\n\r\n%s",
                getString(R.string.errorLoadingConfiguration),
                getString(R.string.pleaseContactSupport));
        AlertUtil.showError(this, R.string.error, message, null, R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                }, null);
    }

    /**
     * Get the test result by analyzing the bitmap
     *
     * @param bitmap the bitmap of the photo taken during analysis
     */
    private void getAnalyzedResult(Bitmap bitmap) {

        //Extract the color from the photo which will be used for comparison
        ColorInfo photoColor = ColorUtil.getColorFromBitmap(bitmap, ColorimetryLiquidConfig.SAMPLE_CROP_LENGTH_DEFAULT);

        TestInfo testInfo = CaddisflyApp.getApp().getCurrentTestInfo();

        ArrayList<ResultDetail> results = new ArrayList<>();

        //In diagnostic mode show results based on other color models / number of calibration steps
        if (AppPreferences.isDiagnosticMode()) {
            ArrayList<Swatch> swatches = new ArrayList<>();

            //add only the first and last swatch for a 2 step analysis
            swatches.add(testInfo.getSwatches().get(0));
            swatches.add(testInfo.getSwatches().get(testInfo.getSwatches().size() - 1));

            results.add(SwatchHelper.analyzeColor(photoColor, swatches, ColorUtil.ColorModel.LAB));

            results.add(SwatchHelper.analyzeColor(photoColor, swatches, ColorUtil.ColorModel.RGB));

            //add the middle swatch for a 3 step analysis
            swatches.add(1, testInfo.getSwatches().get((testInfo.getSwatches().size() / 2) - 1));

            results.add(SwatchHelper.analyzeColor(photoColor, swatches, ColorUtil.ColorModel.LAB));

            results.add(SwatchHelper.analyzeColor(photoColor, swatches, ColorUtil.ColorModel.RGB));

            //use all the swatches for an all steps analysis
            results.add(SwatchHelper.analyzeColor(photoColor,
                    CaddisflyApp.getApp().getCurrentTestInfo().getSwatches(), ColorUtil.ColorModel.RGB));

            results.add(SwatchHelper.analyzeColor(photoColor,
                    CaddisflyApp.getApp().getCurrentTestInfo().getSwatches(), ColorUtil.ColorModel.LAB));
        }

        results.add(0, SwatchHelper.analyzeColor(photoColor,
                CaddisflyApp.getApp().getCurrentTestInfo().getSwatches(), ColorUtil.DEFAULT_COLOR_MODEL));

        Result result = new Result(bitmap, results);

        mResults.add(result);
    }

    private void startTest() {

        mResults = new ArrayList<>();

        mWaitingForStillness = false;
        mIsFirstResult = true;

        sound.playShortResource(R.raw.beep);
        mShakeDetector.minShakeAcceleration = 1;
        mShakeDetector.maxShakeDuration = 3000;
        mSensorManager.registerListener(mShakeDetector, mAccelerometer,
                SensorManager.SENSOR_DELAY_UI);

        (new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                        AppPreferences.getUseCamera2Api()) {
                    mCameraFragment = Camera2DialogFragment.newInstance();
                } else {
                    mCameraFragment = CameraDialogFragment.newInstance();
                }

                mCameraFragment.setPictureTakenObserver(new CameraDialogFragment.PictureTaken() {
                    @Override
                    public void onPictureTaken(byte[] bytes, boolean completed) {
                        Bitmap bitmap = ImageUtil.getBitmap(bytes);


                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ||
                                !AppPreferences.getUseCamera2Api()) {

                            Display display = getWindowManager().getDefaultDisplay();
                            int rotation = 0;
                            switch (display.getRotation()) {
                                case Surface.ROTATION_0:
                                    rotation = 90;
                                    break;
                                case Surface.ROTATION_90:
                                    rotation = 0;
                                    break;
                                case Surface.ROTATION_180:
                                    rotation = 270;
                                    break;
                                case Surface.ROTATION_270:
                                    rotation = 180;
                                    break;
                            }

                            bitmap = ImageUtil.rotateImage(bitmap, rotation);
                        }

                        Bitmap croppedBitmap = ImageUtil.getCroppedBitmap(bitmap,
                                ColorimetryLiquidConfig.SAMPLE_CROP_LENGTH_DEFAULT);

                        //Ignore the first result as camera may not have focused correctly
                        if (!mIsFirstResult) {
                            if (croppedBitmap != null) {
                                getAnalyzedResult(croppedBitmap);
                            } else {
                                showError("Cartridge not placed correctly", ImageUtil.getBitmap(bytes));
                                mCameraFragment.stopCamera();
                                mCameraFragment.dismiss();
                                return;
                            }
                        }
                        mIsFirstResult = false;

                        if (completed) {
                            AnalyzeFinalResult(bytes);
                            mCameraFragment.dismiss();
                        } else {
                            sound.playShortResource(R.raw.beep);
                        }
                    }
                });

                acquireWakeLock();

                delayRunnable = new Runnable() {
                    @Override
                    public void run() {
                        final FragmentTransaction ft = getFragmentManager().beginTransaction();
                        Fragment prev = getFragmentManager().findFragmentByTag("cameraDialog");
                        if (prev != null) {
                            ft.remove(prev);
                        }
                        ft.addToBackStack(null);
                        try {
                            mCameraFragment.show(ft, "cameraDialog");
                            mCameraFragment.takePictures(AppPreferences.getSamplingTimes(),
                                    ColorimetryLiquidConfig.DELAY_BETWEEN_SAMPLING);
                        } catch (Exception e) {
                            e.printStackTrace();
                            finish();
                        }
                    }
                };

                delayHandler.postDelayed(delayRunnable, ColorimetryLiquidConfig.DELAY_BETWEEN_SAMPLING);

            }
        }).execute();
    }

    /**
     * Analyze the result and display the appropriate success/fail message
     * <p/>
     * If the result value is too high then display the high contamination level message
     *
     * @param data image data to be displayed if error in analysis
     */
    private void AnalyzeFinalResult(byte[] data) {

        releaseResources();

        boolean isCalibration = getIntent().getBooleanExtra("isCalibration", false);

        String message = getString(R.string.errorTestFailed);

        if (mResults.size() == 0) {
            showError("Cartridge not placed correctly", ImageUtil.getBitmap(data));
        } else {

            double result = SwatchHelper.getAverageResult(mResults);

            if (mDilutionLevel < 2 && result >= CaddisflyApp.getApp().getCurrentTestInfo().getDilutionRequiredLevel() &&
                    CaddisflyApp.getApp().getCurrentTestInfo().getCanUseDilution()) {
                mHighLevelsFound = true;
            }

            //todo: remove hard coding of dilution levels
            switch (mDilutionLevel) {
                case 1:
                    result = result * 2;
                    break;
                case 2:
                    result = result * 5;
                    break;
            }

            int color = SwatchHelper.getAverageColor(mResults);
            Intent intent = new Intent(getIntent());
            intent.putExtra("result", result);
            intent.putExtra("color", color);
            //intent.putExtra("questionId", mQuestionId);
            intent.putExtra("response", String.format("%.2f", result));
            setResult(Activity.RESULT_OK, intent);
            mTestCompleted = true;

            if (isCalibration && color != Color.TRANSPARENT) {
                sound.playShortResource(R.raw.done);
                if (AppPreferences.isDiagnosticMode()) {
                    saveImageForDiagnostics(data, result);
                    showDiagnosticResultDialog(false, result, color, true);
                } else {
                    finish();
                }
            } else {
                if (result < 0 || color == Color.TRANSPARENT) {
                    if (AppPreferences.isDiagnosticMode()) {
                        sound.playShortResource(R.raw.err);
                        //save the image for diagnostics
                        saveImageForDiagnostics(data, result);
                        showDiagnosticResultDialog(true, 0, color, isCalibration);
                    } else {
                        showError(message, ImageUtil.getBitmap(data));
                    }
                } else {

                    if (AppPreferences.isDiagnosticMode()) {
                        sound.playShortResource(R.raw.done);
                        //save the image for diagnostics
                        saveImageForDiagnostics(data, result);
                        showDiagnosticResultDialog(false, result, color, false);
                    } else {
                        String title = CaddisflyApp.getApp().getCurrentTestInfo().getName(getResources().getConfiguration().locale.getLanguage());

                        if (mHighLevelsFound && mDilutionLevel < 2) {
                            sound.playShortResource(R.raw.beep_long);
                            //todo: remove hard coding of dilution levels
                            switch (mDilutionLevel) {
                                case 0:
                                    message = String.format(getString(R.string.tryWithDilutedSample), 2);
                                    break;
                                case 1:
                                    message = String.format(getString(R.string.tryWithDilutedSample), 5);
                                    break;
                            }

                            HighLevelsDialogFragment mHighLevelsDialogFragment = HighLevelsDialogFragment.newInstance(title, message, mDilutionLevel);
                            final FragmentTransaction ft = getFragmentManager().beginTransaction();

                            Fragment prev = getFragmentManager().findFragmentByTag("resultDialog");
                            if (prev != null) {
                                ft.remove(prev);
                            }
                            mHighLevelsDialogFragment.setCancelable(false);
                            mHighLevelsDialogFragment.show(ft, "resultDialog");

                        } else {
                            sound.playShortResource(R.raw.done);
                            ResultDialogFragment mResultDialogFragment = ResultDialogFragment.newInstance(title, result,
                                    mDilutionLevel, CaddisflyApp.getApp().getCurrentTestInfo().getUnit());
                            final FragmentTransaction ft = getFragmentManager().beginTransaction();

                            Fragment prev = getFragmentManager().findFragmentByTag("resultDialog");
                            if (prev != null) {
                                ft.remove(prev);
                            }
                            mResultDialogFragment.setCancelable(false);
                            mResultDialogFragment.show(ft, "resultDialog");
                        }
                    }
                }
            }
        }
    }

    private void saveImageForDiagnostics(byte[] data, double result) {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, intentFilter);
        int batteryPercent = -1;

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batteryPercent = (int) ((level / (float) scale) * 100);
        }

        if (mIsCalibration) {
            result = mSwatchValue;
        }

        String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
        ImageUtil.saveImage(data, CaddisflyApp.getApp().getCurrentTestInfo().getCode(), date + "_"
                + (mIsCalibration ? "C" : "T") + "_" + String.format("%.2f", result)
                + "_" + batteryPercent + "_" + ApiUtil.getEquipmentId(this));
    }

    @Override
    public void onSuccessFinishDialog() {
        finish();
    }

    private void releaseResources() {
        mSensorManager.unregisterListener(mShakeDetector);
        delayHandler.removeCallbacks(delayRunnable);
        if (mCameraFragment != null) {
            try {
                mCameraFragment.dismiss();
            } catch (Exception ignored) {

            }
            mCameraFragment.stopCamera();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
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

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        releaseResources();
        Intent intent = new Intent(getIntent());
        this.setResult(Activity.RESULT_CANCELED, intent);
        if (alertDialogToBeDestroyed != null) {
            alertDialogToBeDestroyed.dismiss();
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        releaseResources();
        Intent intent = new Intent(getIntent());
        this.setResult(Activity.RESULT_CANCELED, intent);
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_back_out, R.anim.slide_back_in);
    }

    @Override
    public void onFinishDiagnosticResultDialog(boolean retry, boolean cancelled, boolean isCalibration) {
        setAnimatorDisplayedChild(mViewAnimator, 1);
        mResultFragment.dismiss();
        if (mHighLevelsFound && !isCalibration) {
            mCameraFragment.dismiss();
            sound.playShortResource(R.raw.beep_long);
            String title = CaddisflyApp.getApp().getCurrentTestInfo().getName(getResources().getConfiguration().locale.getLanguage());

            //todo: remove hard coding of dilution levels
            String message = "";
            switch (mDilutionLevel) {
                case 0:
                    message = String.format(getString(R.string.tryWithDilutedSample), 2);
                    break;
                case 1:
                    message = String.format(getString(R.string.tryWithDilutedSample), 5);
                    break;
            }

            HighLevelsDialogFragment mHighLevelsDialogFragment = HighLevelsDialogFragment.newInstance(title, message, mDilutionLevel);
            final FragmentTransaction ft = getFragmentManager().beginTransaction();

            Fragment prev = getFragmentManager().findFragmentByTag("resultDialog");
            if (prev != null) {
                ft.remove(prev);
            }
            mHighLevelsDialogFragment.setCancelable(false);
            mHighLevelsDialogFragment.show(ft, "resultDialog");

        } else if (retry) {
            mCameraFragment.dismiss();
            InitializeTest();
        } else {
            if (cancelled) {
                Intent intent = new Intent(getIntent());
                this.setResult(Activity.RESULT_CANCELED, intent);
            }
            releaseResources();
            finish();
        }
    }

    @Override
    public void onFinishDialog() {
        Intent intent = new Intent(getIntent());
        intent.putExtra("response", String.valueOf(""));
        this.setResult(Activity.RESULT_OK, intent);
        releaseResources();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sound.release();
    }
}