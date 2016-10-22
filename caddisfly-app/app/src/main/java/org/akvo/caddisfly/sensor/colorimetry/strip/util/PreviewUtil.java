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

package org.akvo.caddisfly.sensor.colorimetry.strip.util;

import org.akvo.caddisfly.sensor.colorimetry.strip.calibration.CalibrationCard;
import org.akvo.caddisfly.sensor.colorimetry.strip.model.CalibrationData;
import org.akvo.caddisfly.util.detector.FinderPatternInfo;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.Locale;

/**
 * Created by linda on 10/3/15
 */
public class PreviewUtil {
    public static double[] getDiffLuminosity(Mat src_gray) {
        //find min and max luminosity
        Core.MinMaxLocResult result = Core.minMaxLoc(src_gray);
        return new double[]{result.minVal, result.maxVal};
    }

    /*method for shadow detection
   * @param Mat : a 'cut-out' of the test card between the centers of the finder patterns.
   * @return :  percentage of the points that deviate more than @link Constant.CONTRAST_DEVIATION_PERCENTAGE from the average luminosity
   *  points with luminosity with a larger difference than Constant.CONTRAST_MAX_DEVIATION_PERCENTAGE count 10 times in the result.
    */
    public static double getShadowPercentage(Mat bgr, CalibrationData data) {

        double sumLum = 0;
        int countDev = 0;
        int countMaxDev = 0;
        double deviation;

        Mat lab = new Mat();
        Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab);

        double[][] points = CalibrationCard.createWhitePointArray(lab, data);

        //get the sum total of luminosity values
        for (double[] point : points) {
            sumLum += point[2];
        }

        double avgLum = sumLum / points.length;
        double avgLumReciprocal = 1.0 / avgLum;

        for (double[] point : points) {
            double lum = point[2];
            deviation = Math.abs(lum - avgLum) * avgLumReciprocal;

            // count number of points that differ more than CONTRAST_DEVIATION_FRACTION from the average
            if (deviation > Constant.CONTRAST_DEVIATION_FRACTION) {
                countDev++;
            }

            // count number of points that differ more than CONTRAST_MAX_DEVIATION_FRACTION from the average
            if (deviation > Constant.CONTRAST_MAX_DEVIATION_FRACTION) {
                countMaxDev++;
            }
        }

        // the countMaxDev is already counted once in countDev. The following formula
        // lets points that are way off count 10 times as heavy in the result.
        // maximise to 100%
        double result = Math.min(countDev + 9 * countMaxDev, points.length);

        lab.release();
        return (result / points.length) * 100.0;
    }

    private static float distance(double x1, double y1, double x2, double y2) {
        return (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    //method to calculate the amount of perspective, based on the difference of distances at the top and sides
    // horizontal and vertical are according to calibration card in landscape view
    public static float[] getTilt(FinderPatternInfo info) {
        if (info == null) {
            return null;
        }

        // compute distances
        // in info, we have topLeft, topRight, bottomLeft, bottomRight
        float hDistanceTop = distance(info.getBottomLeft().getX(), info.getBottomLeft().getY(), info.getTopLeft().getX(), info.getTopLeft().getY());
        float hDistanceBottom = distance(info.getBottomRight().getX(), info.getBottomRight().getY(), info.getTopRight().getX(), info.getTopRight().getY());
        float vDistanceLeft = distance(info.getBottomLeft().getX(), info.getBottomLeft().getY(), info.getBottomRight().getX(), info.getBottomRight().getY());
        float vDistanceRight = distance(info.getTopRight().getX(), info.getTopRight().getY(), info.getTopLeft().getX(), info.getTopLeft().getY());

        // return ratio of horizontal distances top and bottom and ratio of vertical distances left and right
        return new float[]{hDistanceTop / hDistanceBottom, vDistanceLeft / vDistanceRight};
    }

    public static String fromSecondsToMMSS(int seconds) throws Exception {
        if (seconds > 3600)
            throw new Exception("more than an hour");

        int m = seconds / 60;
        int s = seconds - (m * 60);

        String mm = m > 0 ? String.format(Locale.US, "%2d", m) + ":" : "";
        String ss = m > 0 ? String.format(Locale.US, "%02d", s) : String.format(Locale.US, "%2d", s);

        return mm + ss;
    }
}
