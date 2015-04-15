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

package org.akvo.caddisfly.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.software.shell.fab.ActionButton;

import org.akvo.caddisfly.Config;
import org.akvo.caddisfly.R;
import org.akvo.caddisfly.app.MainApp;
import org.akvo.caddisfly.util.AlertUtils;
import org.akvo.caddisfly.util.ApiUtils;
import org.akvo.caddisfly.util.DateUtils;
import org.akvo.caddisfly.util.NetworkUtils;
import org.akvo.caddisfly.util.PreferencesUtils;
import org.akvo.caddisfly.util.UpdateCheckTask;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;


public class MainActivity extends ActionBarActivity {

    private static final int REQUEST_TEST = 1;
    private static final int REQUEST_LANGUAGE = 2;
    TextView mWatchTextView;
    TextView mDemoTextView;
    Boolean external = false;
    private WeakRefHandler handler = new WeakRefHandler(this);
    private boolean mShouldFinish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ApiUtils.lockScreenOrientation(this);

        MainApp.hasCameraFlash = ApiUtils.checkCameraFlash(this);

        long updateLastCheck = PreferencesUtils.getLong(this, R.string.lastUpdateCheckKey);

        // last update check date
        Calendar lastCheckDate = Calendar.getInstance();
        lastCheckDate.setTimeInMillis(updateLastCheck);

        Calendar currentDate = Calendar.getInstance();
        if (DateUtils.getDaysDifference(lastCheckDate, currentDate) > 0) {
            checkUpdate(true);
        }

        final Context context = this;
        final ActionButton trainingLinkButton = (ActionButton) findViewById(R.id.trainingVideoLink);
        trainingLinkButton.playShowAnimation();
        trainingLinkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NetworkUtils.openWebBrowser(context, Config.TRAINING_VIDEO_LINK);
            }
        });

        Button startSurveyButton = (Button) findViewById(R.id.surveyButton);
        startSurveyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startSurvey();
            }
        });

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.drawable.ic_actionbar_logo);

        mWatchTextView = (TextView) findViewById(R.id.watchTextView);
        mDemoTextView = (TextView) findViewById(R.id.demoTextView);

    }

    /**
     * @param background true: check for update silently, false: show messages to user
     */
    void checkUpdate(boolean background) {
        UpdateCheckTask updateCheckTask = new UpdateCheckTask(this, background, MainApp.getVersion(this));
        updateCheckTask.execute();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mWatchTextView.measure(0, 0);
        mDemoTextView.measure(0, 0);
        float width = Math.max(mWatchTextView.getMeasuredWidth(), mDemoTextView.getMeasuredWidth());
        mDemoTextView.setWidth((int) width);

        CheckLocale();

    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
    }

    private void CheckLocale() {
        assert getApplicationContext() != null;

        Locale locale = new Locale(
                PreferencesUtils.getString(this, R.string.languageKey, Config.DEFAULT_LOCALE));
//        Locale.setDefault(locale);
        Resources res = getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        Configuration config = res.getConfiguration();

        if (!config.locale.equals(locale)) {
            config.locale = locale;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLayoutDirection(locale);
            }
            res.updateConfiguration(config, dm);

            if (!external) {
                Message msg = handler.obtainMessage();
                handler.sendMessage(msg);
            }
        }
    }

    public void startSurvey() {
        Intent LaunchIntent = getPackageManager()
                .getLaunchIntentForPackage(Config.FLOW_SURVEY_PACKAGE_NAME);
        if (LaunchIntent == null) {
            AlertUtils.showMessage(this, R.string.error, R.string.errorAkvoFlowRequired);
        } else {
            startActivity(LaunchIntent);
            mShouldFinish = true;

            (new Handler()).postDelayed(new Runnable() {
                public void run() {
                    if (mShouldFinish) {
                        finish();
                    }
                }
            }, 6000);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            final Intent intent = new Intent(this, SettingsActivity.class);
//            intent.putExtra( PreferenceActivity.EXTRA_SHOW_FRAGMENT, PreferencesGeneralFragment.class.getName() );
//            intent.putExtra( PreferenceActivity.EXTRA_NO_HEADERS, false );
            startActivityForResult(intent, REQUEST_LANGUAGE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mShouldFinish = false;

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        String mQuestionTitle;
        MainApp mainApp = (MainApp) getApplicationContext();

        if (Config.FLOW_ACTION_EXTERNAL_SOURCE.equals(action) && type != null) {
            if ("text/plain".equals(type)) { //NON-NLS
                external = true;
                mQuestionTitle = getIntent().getStringExtra("questionTitle");
                String code = mQuestionTitle.substring(Math.max(0, mQuestionTitle.length() - 5));
                mainApp.setSwatches(code);

                if (mainApp.currentTestInfo == null) {

                    String errorTitle;
                    if (mQuestionTitle.length() > 0) {
                        if (mQuestionTitle.length() > 30) {
                            mQuestionTitle = mQuestionTitle.substring(0, 30);
                        }
                        errorTitle = mQuestionTitle;
                    } else {
                        errorTitle = getString(R.string.error);
                    }

                    AlertUtils.showAlert(this, errorTitle,
                            R.string.errorTestNotAvailable,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        DialogInterface dialogInterface,
                                        int i) {
                                    finish();
                                }
                            }, null
                    );
                } else {
                    if (!MainApp.hasCameraFlash) {
                        AlertUtils.showError(this, R.string.error,
                                getString(R.string.errorCameraFlashRequired),
                                null,
                                R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        finish();
                                    }
                                },
                                null);
                    } else {
                        startTest();
                    }
                }
            }
        }
    }

    public void startTest() {
        Context context = this;
        MainApp mainApp = (MainApp) context.getApplicationContext();
        if (mainApp.currentTestInfo.getType() == 0) {

            if (mainApp.getCalibrationErrorCount() > 0) {
                Configuration conf = getResources().getConfiguration();

                AlertUtils.showAlert(context,
                        mainApp.currentTestInfo.getName(conf.locale.getLanguage()),
                        R.string.errorCalibrationIncomplete,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialogInterface,
                                    int i) {
                                final Intent intent = new Intent(getBaseContext(), SettingsActivity.class);
                                intent.putExtra("calibrate", true);
                                startActivity(intent);
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialogInterface,
                                    int i) {
                                finish();
                            }
                        }
                );
                return;
            }

            final Intent intent = new Intent(context, CameraSensorActivity.class);
            //intent.setClass(context, CameraSensorActivity.class);
            startActivityForResult(intent, REQUEST_TEST);
        } else if (mainApp.currentTestInfo.getType() == 1) {
            final Intent intent = new Intent(context, SensorActivity.class);
            //intent.setClass(context, SensorActivity.class);
            startActivityForResult(intent, REQUEST_TEST);
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_LANGUAGE:
                if (resultCode == Activity.RESULT_OK) {
                    this.recreate();
                }
                break;
            case REQUEST_TEST:
                if (resultCode == Activity.RESULT_OK) {
                    Intent intent = new Intent(getIntent());
                    //intent.putExtra("result", data.getDoubleExtra("result", -1));
                    //intent.putExtra("questionId", mQuestionId);
                    intent.putExtra("response", data.getStringExtra("response"));
                    this.setResult(Activity.RESULT_OK, intent);
                    finish();
                } else {
                    finish();
                    //displayView(Config.CHECKLIST_SCREEN_INDEX, true);
                }
                break;
            default:
        }
    }

    private static class WeakRefHandler extends Handler {
        private WeakReference<Activity> ref;

        public WeakRefHandler(Activity ref) {
            this.ref = new WeakReference<>(ref);
        }

        @Override
        public void handleMessage(Message msg) {
            Activity f = ref.get();
            f.recreate();
        }
    }


}
