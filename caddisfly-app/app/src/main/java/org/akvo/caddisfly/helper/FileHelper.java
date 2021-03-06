/*
 * Copyright (C) Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Caddisfly.
 *
 * Akvo Caddisfly is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Akvo Caddisfly is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Akvo Caddisfly. If not, see <http://www.gnu.org/licenses/>.
 */

package org.akvo.caddisfly.helper;

import org.akvo.caddisfly.app.CaddisflyApp;
import org.akvo.caddisfly.util.FileUtil;

import java.io.File;

public final class FileHelper {

    /**
     * The user created configuration file name
     */
    // Folders
    public static final String ROOT_DIRECTORY = File.separator + "Akvo Caddisfly";
    private static final String DIR_CALIBRATION = ROOT_DIRECTORY + File.separator + "calibration"; // Calibration files
    private static final String DIR_CONFIG = ROOT_DIRECTORY + File.separator + "custom-config"; // Calibration files
    private static final String DIR_IMAGE = ROOT_DIRECTORY + File.separator + "image"; // Calibration files
    private static final String DIR_CARD = ROOT_DIRECTORY + File.separator + "color-card"; // Calibration files

    private FileHelper() {
    }

    /**
     * Get the appropriate files directory for the given FileType. The directory may or may
     * not be in the app-specific External Storage. The caller cannot assume anything about
     * the location.
     *
     * @param type FileType to determine the type of resource attempting to use.
     * @return File representing the root directory for the given FileType.
     */
    @SuppressWarnings("SameParameterValue")
    public static File getFilesDir(FileType type) {
        return getFilesDir(type, "");
    }

    /**
     * Get the appropriate files directory for the given FileType. The directory may or may
     * not be in the app-specific External Storage. The caller cannot assume anything about
     * the location.
     *
     * @param type    FileType to determine the type of resource attempting to use.
     * @param subPath a sub directory to be created
     * @return File representing the root directory for the given FileType.
     */
    public static File getFilesDir(FileType type, String subPath) {
        String path;
        switch (type) {
            case CALIBRATION:
                path = FileUtil.getFilesStorageDir(CaddisflyApp.getApp(), false) + DIR_CALIBRATION;
                break;
            case CONFIG:
                path = FileUtil.getFilesStorageDir(CaddisflyApp.getApp(), false) + DIR_CONFIG;
                break;
            case IMAGE:
                path = FileUtil.getFilesStorageDir(CaddisflyApp.getApp(), false) + DIR_IMAGE;
                break;
            case CARD:
                path = FileUtil.getFilesStorageDir(CaddisflyApp.getApp(), false) + DIR_CARD;
                break;
            default:
                path = FileUtil.getFilesStorageDir(CaddisflyApp.getApp(), true);
        }
        File dir = new File(path);
        if (!subPath.isEmpty()) {
            dir = new File(dir, subPath);
        }
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * The different types of files
     */
    public enum FileType {
        CALIBRATION, CONFIG, IMAGE, CARD
    }

}
