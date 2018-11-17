package ocr.example;

import android.graphics.Bitmap;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.blankj.utilcode.util.ResourceUtils;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;


public class TesseractOrcUtil {

    public static final String TAG = "Tess_Orc";
    private static final String TESSBASE_PATH = Environment.getExternalStorageDirectory().toString();
    private static final String DEFAULT_LANGUAGE = "eng";
    public static final String TESSDATA_PATH = TESSBASE_PATH + "/tessdata/";
    private static final String[] EXPECTED_CUBE_DATA_FILES_ENG = {
            "eng.cube.bigrams",
            "eng.cube.fold",
            "eng.cube.lm",
            "eng.cube.nn",
            "eng.cube.params",
            "eng.cube.size",
            "eng.cube.word-freq",
            "eng.tesseract_cube.nn"
    };


    public static String getText(Bitmap bitmap) {
        TessBaseAPI baseApi = null;
        try {
            if (checkDataFile() /*&& checkCubeData()*/) {
                baseApi = new TessBaseAPI();
                boolean success = baseApi.init(TESSBASE_PATH, DEFAULT_LANGUAGE);
                if (success) {
                    baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                    baseApi.setVariable(TessBaseAPI.VAR_SAVE_BLOB_CHOICES, TessBaseAPI.VAR_TRUE);
                    baseApi.setImage(bitmap);
                    String recognizedText = baseApi.getUTF8Text();
                    if (!TextUtils.isEmpty(recognizedText)) {
                        return recognizedText;
                    } else {
                        Log.d(TAG, "No recognized text found.");
                    }
                } else {
                    Log.d(TAG, "baseApi.init --> " + success);
                }
            }
            return null;
        } finally {
            if (baseApi != null)
                baseApi.end();
        }
    }

    private static boolean checkDataFile() {
        for (String languageCode : DEFAULT_LANGUAGE.split("\\+")) {
            if (!languageCode.startsWith("~")) {
                File expectedFile = new File(TESSDATA_PATH + File.separator + languageCode + ".traineddata");
                if (!expectedFile.exists()) {
                    Log.d(TAG, "Make sure that you've copied " + languageCode + ".traineddata to " + TESSDATA_PATH);
                    ResourceUtils.copyFileFromAssets("tessdata", TesseractOrcUtil.TESSDATA_PATH);
                    return true;
                }
            }
        }
        return true;
    }

    private static boolean checkCubeData() {
        for (String expectedFilename : EXPECTED_CUBE_DATA_FILES_ENG) {
            String expectedFilePath = TESSDATA_PATH + expectedFilename;
            File expectedFile = new File(expectedFilePath);
            if (!expectedFile.exists()) {
                Log.d(TAG, "Make sure that you've copied " + expectedFilename + " to " + expectedFilePath);
                return false;
            }
        }
        return true;
    }


}
