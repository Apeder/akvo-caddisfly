package org.akvo.akvoqr.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by linda on 10/3/15.
 */
public class PreviewUtils {


    public static double focusStandardDev(Mat src)
    {
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();

        Core.meanStdDev(src, mean, stddev);

        Scalar mu = new Scalar(0);
        Scalar sigma = new Scalar(0);

        double focusMeasure = 0;

        for(int i=0;i<mean.rows();i++) {
            for (int j = 0; j < mean.cols(); j++) {
                double[] d = mean.get(i, j);
                if (d[0] > 0 ) {
                    mu = new Scalar(d);
                }
                System.out.println("***mu: " + mu.toString());

            }
        }

        for(int i=0;i<stddev.rows();i++)
        {
            for(int j=0; j< stddev.cols();j++)
            {
                double[] d = stddev.get(i,j);
                if(d[0] > 0) {
                    sigma = new Scalar(d);
                }
                System.out.println("***sigma: " + sigma.toString());

            }
        }

        focusMeasure = (sigma.val[0]*sigma.val[0]) / mu.val[0];

        return focusMeasure;
    }

    public static double focusLaplacian(Mat src) {

        int kernel_size = 3;
        int scale = 1;
        int delta = 0;
        int ddepth = CvType.CV_8UC1;
        double maxLap = -32767;

        Mat src_gray = new Mat();
        Mat dst = new Mat();

        Imgproc.GaussianBlur(src, src, new Size(3, 3), 0, 0, Core.BORDER_DEFAULT);
        Imgproc.cvtColor(src, src_gray, Imgproc.COLOR_BGR2GRAY);

        Imgproc.Laplacian(src_gray, dst, ddepth, kernel_size, scale, delta, Core.BORDER_DEFAULT);

        if (!dst.empty()) {

            for (int i = 0; i < dst.rows(); i++) {

                for (int j = 0; j < dst.cols(); j++) {

                    double[] pixelData = dst.get(i, j);
                    if (pixelData != null && pixelData.length > 0) {
                        if (pixelData[0] > maxLap)
                            maxLap = pixelData[0];
                    }

                }
            }
        }
        return maxLap;
    }

    public static double getMaxLuminosity(Mat bgr)
    {
        Mat Lab = new Mat();
        List<Mat> channels = new ArrayList<>();
        Imgproc.cvtColor(bgr, Lab, Imgproc.COLOR_RGB2Lab);
        Core.split(Lab, channels);

        //find min and max luminosity
        Core.MinMaxLocResult result = Core.minMaxLoc(channels.get(0));

        return result.maxVal;
    }

    // method for shadow detection
    public static double getShadowValue(Mat warpMat) throws JSONException
    {
        //NB: warpMat should be bgr color scheme
        Mat workMat = warpMat.clone();
        Mat gray = new Mat();

        //how much shadow do we tolerate?
        double maxDiff = 30;
        double totalLinesWithShadow = 0;

        String json = AssetsManager.getInstance().loadJSONFromAsset("calibration.json");
        JSONObject calObj = new JSONObject(json);

        JSONObject calData = calObj.getJSONObject("calData");
        double hsize = calData.getDouble("hsize");
        double vsize = calData.getDouble("vsize");
        double hratio = warpMat.width()/hsize;
        double vratio = warpMat.height()/vsize;

        JSONObject whiteData = calObj.getJSONObject("whiteData");
        JSONArray lines = whiteData.getJSONArray("lines");

        JSONArray pArr;
        for(int i=0; i < lines.length(); i++)
        {
            JSONObject lineObj = lines.getJSONObject(i);
            pArr = lineObj.getJSONArray("p");
            double width = lineObj.getDouble("width");

            //if line is vertical, xdiff will be zero. Then we take the value of width to be the xdiff
            //subtract x values
            double xdiff = Math.max(width * hratio, (pArr.getDouble(2) - pArr.getDouble(0)) * hratio);

            //if line is horizontal, ydiff will be zero. Then we take the value of width to be the ydiff
            //subtract y values
            double ydiff = Math.max(width * vratio, (pArr.getDouble(3) - pArr.getDouble(1))*vratio);


            //create rectangle to make a submat
            Rect whiteRect = new Rect(
                    (int)Math.floor(pArr.getDouble(0) * hratio),
                    (int)Math.floor(pArr.getDouble(1) * vratio),
                    (int)Math.floor(xdiff),
                    (int)Math.floor(ydiff));

            Mat submat = workMat.submat(whiteRect).clone();
           // System.out.println("***shadow submat width, height: " + submat.width() + ", " + submat.height());

            //convert to gray
            Imgproc.cvtColor(submat, gray, Imgproc.COLOR_BGR2GRAY);

            //blur the image to exclude noise
            Imgproc.medianBlur(gray, gray, 5);

            //get the min and max value of the gray mat
            Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(gray);
            double grayDiff = minMaxLocResult.maxVal - minMaxLocResult.minVal;

            //System.out.println("***shadow grayDiff: " + i + " = " + grayDiff);

            //if the difference between min gray and max gray is higher than maxDiff
            //we have a shadow
            if(grayDiff > maxDiff)
            {
                totalLinesWithShadow ++;
            }

            //visualise for debugging purposes
           /* Point point1 = new Point((int)Math.floor(pArr.getDouble(0) * hratio),
                    (int)Math.floor(pArr.getDouble(1) * vratio));
            Point point2 = new Point((int)Math.floor(pArr.getDouble(2) * hratio),
                    (int)Math.floor(pArr.getDouble(3) * vratio));

            Imgproc.rectangle(warpMat, point1, point2, new Scalar(0, 0, 255, 255), 1);
            //visualise min and max
            Point minLoc = new Point(minMaxLocResult.minLoc.x + (pArr.getDouble(0) * hratio),
                    minMaxLocResult.minLoc.y + (pArr.getDouble(1) * vratio));
            Point maxLoc = new Point(minMaxLocResult.maxLoc.x + (pArr.getDouble(2) * hratio),
                    minMaxLocResult.maxLoc.y);

            Scalar color;
            if(xdiff>ydiff)
            {
               color = new Scalar(255, 0, 255, 255);
            }
            else
            {
               color = new Scalar(0, 255, 255, 255);
            }
            Imgproc.circle(warpMat, minLoc, 5, new Scalar(0, 255, 0, 255), -1);
           Imgproc.circle(warpMat, maxLoc, 10, color, -1);
           */
        }

        workMat.release();
        gray.release();

        return totalLinesWithShadow;
    }
}