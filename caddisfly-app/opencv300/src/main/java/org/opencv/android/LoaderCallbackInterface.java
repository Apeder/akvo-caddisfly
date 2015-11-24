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

package org.opencv.android;

/**
 * Interface for callback object in case of asynchronous initialization of OpenCV.
 */
public interface LoaderCallbackInterface {
    /**
     * OpenCV initialization finished successfully.
     */
    int SUCCESS = 0;
    /**
     * Google Play Market cannot be invoked.
     */
    int MARKET_ERROR = 2;
    /**
     * OpenCV library installation has been canceled by the user.
     */
    int INSTALL_CANCELED = 3;
    /**
     * This version of OpenCV Manager Service is incompatible with the app. Possibly, a service update is required.
     */
    int INCOMPATIBLE_MANAGER_VERSION = 4;
    /**
     * OpenCV library initialization has failed.
     */
    int INIT_FAILED = 0xff;

    /**
     * Callback method, called after OpenCV library initialization.
     *
     * @param status status of initialization (see initialization status constants).
     */
    void onManagerConnected(int status);

    /**
     * Callback method, called in case the package installation is needed.
     *
     * @param callback answer object with approve and cancel methods and the package description.
     */
    void onPackageInstall(final int operation, InstallCallbackInterface callback);
}
