package com.mycompany.myrubikscube.android;

import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_8U;


import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageUtil {
    private static final String TAG = "RubikCubeSolverUtil";

    // color definition
    static final double[][] colorData = {
        {255, 215, 0, 0},    // Y
        {254, 80, 0, 0},     // O
        {0, 154, 68, 0},     // G
        {255, 255, 255, 0},  // W
        {186, 23, 47, 0},    // R
        {0, 61, 165, 0},     // B
    };
    static final String[] colorLabel = {"D", "L", "F", "U", "R", "B"};
    static final String[] colorName = {"Yellow", "Orange", "Green", "White", "Red", "Blue"};
    static final List<Integer> colorResponse = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5));
    // top -> left -> down -> right
    static final String[] arrSideColors = {
        "BRGO",  // Yellow
        "YGWB",  // Orange
        "YRWO",  // Green
        "GRBO",  // White
        "YBWG",  // Red
        "YOBR",  // Blue
    };

    static final String[] verifyMsg = {
        "There is not exactly one facelet of each color.",               // -1
        "Not all 12 edges exist exactly once.",                          // -2
        "Flip error: One edge has to be flipped.",                       // -3
        "Not all 8 corners exist exactly once.",                         // -4
        "Twist error: One corner has to be twisted.",                    // -5
        "Parity error: Two corners or two edges have to be exchanged.",  // -6
    };

    static String convertCubeAnnotation(String scannedCube) {
        if (scannedCube.length() != 54) {
            throw new IllegalArgumentException("Cube string must be 54 characters.");
        }
        // scanned string uses indices
        //   0 = Yellow, 1 = Orange, 2 = Green, 3 = White, 4 = Red, 5 = Blue.
        String face0 = scannedCube.substring(0, 9);   // Yellow →  become Down (D)
        String face1 = scannedCube.substring(9, 18);   // Orange →  become Left (L)
        String face2 = scannedCube.substring(18, 27);  // Green →  become Front (F)
        String face3 = scannedCube.substring(27, 36);  // White →  become Up (U)
        String face4 = scannedCube.substring(36, 45);  // Red →  Right (R)
        String face5 = scannedCube.substring(45, 54);  // Blue →  Back (B)

        // Remap the letters for each face.
        face0 = mapFaceLetters(face0, 'Y', 'D');  // yellow (Y) becomes D (Down)
        face1 = mapFaceLetters(face1, 'O', 'L');  // orange (O) becomes L (Left)
        face2 = mapFaceLetters(face2, 'G', 'F');  // green (G) becomes F (Front)
        face3 = mapFaceLetters(face3, 'W', 'U');  // white (W) becomes U (Up)
        // face4 and face5 remain as R and B respectively.

        // Reorder to min2phase order: U, R, F, D, L, B
        String reordered = face3 + face4 + face2 + face0 + face1 + face5;
        return reordered;
    }

    private static String mapFaceLetters(String face, char expected, char target) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < face.length(); i++) {
            char c = face.charAt(i);
            sb.append(c == expected ? target : c);
        }
        return sb.toString();
    }

    static Mat calcBoxColorAve(Mat mat, int boxX, int boxY, int boxLen) {
        // extract box as sub matrix
        Mat boxMat = mat.submat(new Rect(boxX, boxY, boxLen, boxLen));

        // create mask
        Mat mask = new Mat(boxMat.cols(), boxMat.rows(), CV_8U);
        mask.setTo(new Scalar(0.0));
        int innerBoxLen = (int) (boxLen * 0.6);
        int innerX = (int) ((boxLen - innerBoxLen) / 2);
        int innerY = (int) ((boxLen - innerBoxLen) / 2);
        Imgproc.rectangle(mask, new Rect(innerX, innerY, innerBoxLen, innerBoxLen), new Scalar(255, 255, 255), -1);

        Scalar mean = Core.mean(boxMat, mask);
        Mat ret = new Mat(1, mean.val.length, CV_32F);
        for (int i = 0; i < mean.val.length; i++) {
            ret.put(0, i, mean.val[i]);
        }
        Log.v(TAG, "mean(matrix) : " + ret.dump());
        return ret;
    }

    static Mat calcMovingAveColor(@Nullable Mat matPrev, Mat matCurrent, float alpha) {
        if (matPrev == null) {
            return matCurrent;
        }
        assert matPrev.rows() == matCurrent.rows();
        assert matPrev.cols() == matCurrent.cols();
        Mat ret = new Mat(matPrev.rows(), matPrev.cols(), CV_32F);
        for (int i = 0; i < matPrev.cols(); i++) {
            ret.put(0, i, matPrev.get(0, i)[0] * alpha + matCurrent.get(0, i)[0] * (1 - alpha));
        }
        return ret;
    }

    static public Mat getMatFromImage(ImageProxy image) {
        if (image.getFormat() == ImageFormat.YUV_420_888) {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            Mat yuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
            yuv.put(0, 0, nv21);
            Mat mat = new Mat();
            Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2RGB_NV21, 3);
            return mat;
        } else if (image.getFormat() == PixelFormat.RGBA_8888) {
            ByteBuffer argbBuffer = image.getPlanes()[0].getBuffer(); // ARGBARGB...
            int argbSize = argbBuffer.remaining();
            byte[] argb_buf = new byte[argbSize];
            argbBuffer.get(argb_buf, 0, argbSize);
            Mat bgra = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC4);
            bgra.put(0, 0, argb_buf);
            Mat rgb = new Mat();
            Imgproc.cvtColor(bgra, rgb, Imgproc.COLOR_BGRA2BGR);
            return rgb;
        }
        Log.e(TAG, "Unexpected format : " + image.getFormat());
        assert false;
        return null;
    }

    static public Uri generateAnimationLink(String solution) {
        String baseUrl = "https://ruwix.com/widget/3d/?";
        String url = baseUrl + String.format("label=%s", "RubikCubeSolver");
        url += String.format("&alg=%s", Uri.encode(solution));
        url += String.format("&hover=%s" , "4");
        url += String.format("&speed=%s" , "1000");
        url += String.format("&flags=%s" , "showalg");
        url += String.format("&colors=%s" , Uri.encode("U:y L:r F:g R:o B:b D:w", ":"));
        url += String.format("&pov=%s" , "Ufr");
        url += String.format("&algdisplay=%s" , "rotations");

        url = url.replace("'", "%27");

        Log.i(TAG, "url : " + url);
        return Uri.parse(url);
    }
}
