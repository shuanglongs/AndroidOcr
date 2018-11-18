package ocr.example;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.blankj.utilcode.util.ImageUtils;
import com.blankj.utilcode.util.LogUtils;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;

public class ShowImageActivity extends AppCompatActivity {
    public static final String TAG = "Show_Image_Activity";
    private final static int MESSAGE_SHOW_IMAGE = 100;
    private final static int MESSAGE_SHOW_TEXT = 110;
    private ShowImageHandler mHandler;
    private ImageView mShowImage;
    private TextView mShowText;
    private ProgressDialog mDisposeBmpDialog;
    private ProgressDialog mIdentifyTextDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_image);

        mShowImage = findViewById(R.id.iv_show_image);
        mShowText = findViewById(R.id.tv_show_text);

        mHandler = new ShowImageHandler();

        /**
         * OCR的识别率取决于两个方面，图片质量和OCR engine的能力。
         * 通常为了提高识别率，需要对图片作预处理。比如常见的二值化(黑白)，放大，切割，锐化等。
         * 可以直接调用leptonica接口实现。至于Tesseract Engine，只能说是非常好的英文OCR engine，
         * 处理中文还是有待提高。选择好一个OCR engine之后，能做的估计也就是在图片的预处理上下功夫了。
         * */

    }

    @Override
    protected void onResume() {
        super.onResume();

        mDisposeBmpDialog = ProgressDialog.show(this, "提示", "正在处理图片...");
        mDisposeBmpDialog.show();

        //初始化 open cv
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV library not found!");
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mBaseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mBaseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    new ImgProcessThread().start();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    private class ShowImageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_SHOW_IMAGE:
                    mDisposeBmpDialog.dismiss();

                    Bitmap bitmap = (Bitmap) msg.obj;
                    LogUtils.d(TAG, "bitmap is w - h --> " + bitmap.getWidth()
                            + " , " + bitmap.getHeight());

                    Bitmap scale = ImageUtils.scale(bitmap, (int)(bitmap.getWidth() / 1.1),
                            (int)(bitmap.getHeight() / 1.1));
                    LogUtils.d(TAG, "bitmap is w - h --> " + scale.getWidth()
                            + " , " + scale.getHeight());

                    mShowImage.setImageBitmap(scale);
                    mShowImage.invalidate();

                    mIdentifyTextDialog = ProgressDialog.show(ShowImageActivity.this, "提示", "正在识别文字...");
                    mIdentifyTextDialog.show();
                    new OcrThread(bitmap).start();

                    break;
                case MESSAGE_SHOW_TEXT:
                    String text = (String) msg.obj;
                    mShowText.setText(text);

                    mIdentifyTextDialog.dismiss();
                    break;
            }

        }
    }

    private class ImgProcessThread extends Thread {
        @Override
        public void run() {
            super.run();
            //获取图片
            File file = new File(getExternalFilesDir(null), "pic.jpg");
            Bitmap rawBmp = ImageUtils.getBitmap(file);

            //把原图变成 open cv 识别的Mat
            Mat rawMat = new Mat();
            Utils.bitmapToMat(rawBmp, rawMat);

            //把原图变成灰图
            Mat grayMat = new Mat();
            Imgproc.cvtColor(rawMat, grayMat, Imgproc.COLOR_BGR2GRAY);

            //高斯滤波
            Mat blurMat = new Mat();
            Imgproc.GaussianBlur(grayMat, blurMat, new Size(3, 3), 1);

            // 二值阈值化
            Mat thresholdMat = new Mat();
            Imgproc.threshold(blurMat, thresholdMat, 93, 255, Imgproc.THRESH_BINARY);

            Bitmap grayBitmap = Bitmap.createBitmap(rawBmp.getWidth(), rawBmp.getHeight(), Bitmap.Config.ARGB_4444);
            Utils.matToBitmap(thresholdMat, grayBitmap);

            Message obtain = Message.obtain();
            obtain.what = MESSAGE_SHOW_IMAGE;
            obtain.obj = grayBitmap;
            mHandler.sendMessage(obtain);
        }
    }

    private class OcrThread extends Thread {
        private Bitmap bitmap;

        public OcrThread(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        public void run() {
            super.run();
            //识别文字
            String text = TesseractOrcUtil.getText(bitmap);
            Message obtain = Message.obtain();
            obtain.what = MESSAGE_SHOW_TEXT;
            obtain.obj = text;
            mHandler.sendMessage(obtain);
        }
    }
}
