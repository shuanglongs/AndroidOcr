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

    private StringBuilder sb = new StringBuilder();


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_image);

        mShowImage = findViewById(R.id.iv_show_image);
        mShowText = findViewById(R.id.tv_show_text);

        mHandler = new ShowImageHandler();
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

                    /*小米手机需要缩小bitmap，不然不显示*/
//                    Bitmap bitmap = (Bitmap) msg.obj;
//                    LogUtils.d(TAG, "bitmap is w - h --> " + bitmap.getWidth()
//                            + " , " + bitmap.getHeight());
//                    Bitmap scale = ImageUtils.scale(bitmap, (int)(bitmap.getWidth() / 1.1),
//                            (int)(bitmap.getHeight() / 1.1));
//                    LogUtils.d(TAG, "bitmap is w - h --> " + scale.getWidth()
//                            + " , " + scale.getHeight());


                    Bitmap bitmap = (Bitmap) msg.obj;

                    mShowImage.setImageBitmap(bitmap);
                    mShowImage.invalidate();

                    mIdentifyTextDialog = ProgressDialog.show(ShowImageActivity.this, "提示", "正在识别文字...");
                    mIdentifyTextDialog.show();
                    new OcrThread(bitmap).start();

                    break;
                case MESSAGE_SHOW_TEXT:
                    String text = (String) msg.obj;
                    sb.append("\r\n").append(text).append("\r\n");
                    mShowText.setText(sb.toString());
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

            LogUtils.d(TAG, "bitmap is w - h --> " + rawBmp.getWidth() + " , " + rawBmp.getHeight());


            //横竖屏识别
//            imgProc(rawBmp,0,0,rawBmp.getWidth(),rawBmp.getHeight(),110);


            //横屏：裁剪图片，提高识别率

            //1.
            int clipX1 = 300;
            int clipY1 = 70;
            int clipWidth1 = rawBmp.getWidth() - clipX1 - 300;
            int clipHeight1 = 200;
            imgProc(rawBmp, clipX1, clipY1, clipWidth1, clipHeight1, 120);

            //2.
            int clipX2 = 1100;
            int clipY2 = 550;
            int clipWidth2 = rawBmp.getWidth() - clipX2 - 200;
            int clipHeight2 = 190;
            imgProc(rawBmp, clipX2, clipY2, clipWidth2, clipHeight2, 120);

            //3.
            int clipX3 = 1100;
            int clipY3 = 760;
            int clipWidth3 = rawBmp.getWidth() - clipX3 - 200;
            int clipHeight3 = 160;
            imgProc(rawBmp, clipX3, clipY3, clipWidth3, clipHeight3, 120);

            //4.
            int clipX4 = 1100;
            int clipY4 = 890;
            int clipWidth4 = rawBmp.getWidth() - clipX4 - 200;
            int clipHeight4 = 450;
            imgProc(rawBmp, clipX4, clipY4, clipWidth4, clipHeight4, 120);

            //5.
            int clipX5 = 1100;
            int clipY5 = 1330;
            int clipWidth5 = rawBmp.getWidth() - clipX5 - 550;
            int clipHeight5 = 320;
            imgProc(rawBmp, clipX5, clipY5, clipWidth5, clipHeight5, 110);


        }
    }


    private void imgProc(Bitmap rawBitmap, int clipX, int clipY, int clipWidth, int clipHeight, double thresh) {

        Bitmap clipBitmap = ImageUtils.clip(rawBitmap, clipX, clipY, clipWidth, clipHeight);

        //把原图变成 open cv 识别的Mat
        Mat rawMat = new Mat();
        Utils.bitmapToMat(clipBitmap, rawMat);

        //把原图变成灰图
        Mat grayMat = new Mat();
        Imgproc.cvtColor(rawMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        //高斯滤波
        Mat blurMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurMat, new Size(3, 3), 1);

        // 二值阈值化
        Mat thresholdMat = new Mat();
        Imgproc.threshold(blurMat, thresholdMat, thresh, 255, Imgproc.THRESH_BINARY);

        Bitmap finalBitmap = Bitmap.createBitmap(clipBitmap.getWidth(), clipBitmap.getHeight(), Bitmap.Config.ARGB_4444);
        Utils.matToBitmap(thresholdMat, finalBitmap);

        Message obtain = Message.obtain();
        obtain.what = MESSAGE_SHOW_IMAGE;
        obtain.obj = finalBitmap;
        mHandler.sendMessage(obtain);

    }


    private class OcrThread extends Thread {
        private Bitmap bitmap;

        public OcrThread(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        public void run() {
            super.run();
            synchronized (bitmap) {
                //识别文字
                String text = TesseractOrcUtil.getText(bitmap);
                Message obtain = Message.obtain();
                obtain.what = MESSAGE_SHOW_TEXT;
                obtain.obj = text;
                mHandler.sendMessage(obtain);
            }
        }
    }
}
