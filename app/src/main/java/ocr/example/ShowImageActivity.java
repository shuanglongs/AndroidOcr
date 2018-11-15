package ocr.example;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import com.blankj.utilcode.util.ImageUtils;
import com.blankj.utilcode.util.ScreenUtils;

import java.io.File;

public class ShowImageActivity extends AppCompatActivity {
    private final static int MESSAGE_SHOW_IMAGE = 100;
    private ShowImageHandler mHandler;
    private ImageView mShowImage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenUtils.setLandscape(this);
        setContentView(R.layout.activity_show_image);

        mShowImage = findViewById(R.id.iv_show_image);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                File file = new File(getExternalFilesDir(null), "pic.jpg");
                String absolutePath = file.getAbsolutePath();
                Bitmap bitmap = BitmapFactory.decodeFile(absolutePath);
                mShowImage.setImageBitmap(bitmap);
                mShowImage.invalidate();
            }
        },2000);




//        mHandler = new ShowImageHandler();

//        new ShowImageThread().start();

    }


    private class ShowImageThread extends Thread{
        @Override
        public void run() {
            super.run();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            File file = new File(getExternalFilesDir(null), "pic.jpg");
            String absolutePath = file.getAbsolutePath();

            Bitmap bitmap = BitmapFactory.decodeFile(absolutePath);

//            Bitmap bitmap = ImageUtils.getBitmap(file);
            Message obtain = Message.obtain();
            obtain.what = MESSAGE_SHOW_IMAGE;
            obtain.obj = bitmap;
            mHandler.sendMessage(obtain);

        }
    }

    private class ShowImageHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case MESSAGE_SHOW_IMAGE:
                    Bitmap bitmap = (Bitmap) msg.obj;
                    mShowImage.setImageBitmap(bitmap);
                    mShowImage.invalidate();
                    break;
            }

        }
    }
}
