package ocr.example;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

import com.blankj.utilcode.util.ImageUtils;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;

import ocr.example.util.TesseractOrcUtil;

public class ShowImageActivity extends AppCompatActivity {
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
        //显示图片
        mHandler = new ShowImageHandler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mDisposeBmpDialog = ProgressDialog.show(this, "提示", "正在处理图片...");
        mDisposeBmpDialog.show();

        //初始化 open cv 3_4_0
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                switch (status) {
                    case LoaderCallbackInterface.SUCCESS:
                        //获取图片
                        File file = new File(getExternalFilesDir(null), "pic.jpg");
                        Bitmap rawBmp = ImageUtils.getBitmap(file);

                        //把原图变成 open cv 识别的Mat
                        Mat rawMat = new Mat();
                        Utils.bitmapToMat(rawBmp, rawMat);

                        //把原图变成灰图
                        Mat grayMat = new Mat();
                        Imgproc.cvtColor(rawMat, grayMat, Imgproc.COLOR_BGR2GRAY);

//                        Mat threshold = new Mat();
//                        Imgproc.adaptiveThreshold(grayMat, threshold, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 3, 0);
                        // 二值阈值化
//                         Imgproc.threshold(grayMat,threshold,100,255,Imgproc.THRESH_BINARY);
                        // 阈值化到零
//                         Imgproc.threshold(grayMat,threshold,100,255,Imgproc.THRESH_TOZERO);
                        // 截断阈值化
//                         Imgproc.threshold(grayMat,threshold,100,255,Imgproc.THRESH_TRUNC);
                        // 反转二值阈值化
//                         Imgproc.threshold(grayMat,threshold,100,255,Imgproc.THRESH_BINARY_INV);
                        // 反转阈值化到零
//                         Imgproc.threshold(grayMat,threshold,100,255,Imgproc.THRESH_TOZERO_INV);

                        Bitmap grayBitmap = Bitmap.createBitmap(rawBmp.getWidth(), rawBmp.getHeight(), Bitmap.Config.ARGB_4444);
                        Utils.matToBitmap(grayMat, grayBitmap);

                        Message obtain = Message.obtain();
                        obtain.what = MESSAGE_SHOW_IMAGE;
                        obtain.obj = grayBitmap;
                        mHandler.sendMessage(obtain);
                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }
            }
        });
    }

    private class ShowImageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_SHOW_IMAGE:
                    mDisposeBmpDialog.dismiss();
                    Bitmap bitmap = (Bitmap) msg.obj;
                    mShowImage.setImageBitmap(bitmap);
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
