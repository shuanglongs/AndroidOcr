package org.opencv.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class JavaCamera2View2 extends CameraBridgeViewBase {

    public static final String TAG = "Java_Camera2_View2";

    //创建工作线程
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private CameraManager mCameraManager;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader mImageReader;
    private Image mImage;
    private SurfaceTexture surfaces;
    private Integer mSensorOrientation;
    private Size mLargest;
    //设备闪光灯是否可用，默认为不可用
    private boolean isFlashInfoAvailable = false;
    //获取 SurfaceTexture 使用的尺寸大小列表
    private Size[] mOutputSizes;
    //相机状态
    private static int CAMERA_STATE = -1;
    private static final int CAMERA_STATE_PREVIEW = 0;
    private static final int CAMERA_STATE_PICTURE = 1;
    private int h = 0;
    private int w = 0;
    private int mPreviewFormat = ImageFormat.YUV_420_888;

    public JavaCamera2View2(Context context, int cameraId) {
        super(context, cameraId);
    }

    public JavaCamera2View2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean connectCamera(int width, int height) {
        //开启相机openCamera()

        mFrameWidth = width;
        mFrameHeight = height;
        AllocateCache();
        openCamera();

        Log.d(TAG, "connectCamera --> width --> " + width + " ,height --> " + height);

        return true;
    }

    @Override
    protected void disconnectCamera() {
        Log.d(TAG, "disconnectCamera");
        if (mCameraManager != null) {
            mCameraManager = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        if (mImage != null) {
            mImage.close();
            mImage = null;
        }

        if (session != null) {
            session.close();
            session = null;
        }

        if (surfaces != null) {
            surfaces.release();
            surfaces = null;
        }

        if (mHandlerThread != null) {
            mHandlerThread = null;
        }

        if (mHandler != null) {
            mHandler = null;
        }

        if (camera != null) {
            camera.close();
            camera = null;
        }
    }

    /**
     * 监听 Image 是否可用
     */
    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {

        @Override
        public void onImageAvailable(ImageReader reader) {

            Image image = reader.acquireLatestImage();
            if (image == null)
                return;

            // sanity checks - 3 planes
            Image.Plane[] planes = image.getPlanes();
            Log.d(TAG,"planes --> " + planes.length);

            assert (planes.length == 3);
            assert (image.getFormat() == mPreviewFormat);
            assert (planes[0].getPixelStride() == 1);
            assert (planes[1].getPixelStride() == 2);
            assert (planes[2].getPixelStride() == 2);

            ByteBuffer y_plane = planes[0].getBuffer();
            ByteBuffer uv_plane = planes[0].getBuffer();

            Mat y_mat = new Mat(h, w, CvType.CV_8UC1, y_plane);
            Mat uv_mat = new Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane);
            JavaCamera2Frame tempFrame = new JavaCamera2Frame(y_mat, uv_mat, w, h);

            deliverAndDrawFrame(tempFrame);
            tempFrame.release();
            image.close();
        }
    }

    /**
     * 创建工作线程
     */
    private Handler creationWorkThread(String name) {
        mHandlerThread = new HandlerThread(name);
        mHandlerThread.start();
        return new Handler(mHandlerThread.getLooper());
    }

    /**
     * 检查获取相机设备功能是否可用
     */
    private boolean queryCameraDeviceCharacteristics(String cameraId) throws CameraAccessException {

        CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
        //判断相机摄像头如果不是后摄像头就跳过本次循环
        Integer integer = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (integer == null && integer != cameraCharacteristics.LENS_FACING_BACK) {
            return false;
        }
        //此摄像机设备支持的可用流配置; 还包括每个格式/大小组合的最小帧持续时间和停顿持续时间。
        StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return false;
        } else {
            mLargest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

            h = mLargest.getHeight();
            w = mLargest.getWidth();
        }

        //获取传感器的方向
        mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        //获取摄像头设备是否可用
        isFlashInfoAvailable = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        return true;
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


    @SuppressLint("MissingPermission")
    private boolean openCamera() {
        try {
            //创建工作线程
            mHandler = creationWorkThread("Camera");

            mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            if (mCameraManager == null) {
                return false;
            }
            String[] cameraIdList = mCameraManager.getCameraIdList();
            if (cameraIdList.length == 0) {
                return false;
            }
            for (String id : cameraIdList) {
                //查询相机设备是否可用和可用的功能
                if (!queryCameraDeviceCharacteristics(id)) {
                    continue;
                }

                //创建ImageReader
                mImageReader = ImageReader.newInstance(mLargest.getWidth(), mLargest.getHeight(), ImageFormat.JPEG, 2);
                ImageAvailableListener imageAvailableListener = new ImageAvailableListener();
                mImageReader.setOnImageAvailableListener(imageAvailableListener, mHandler);

                //打开给定ID的相机的连接。
                CameraDeviceStateCallback cameraDeviceStateCallback = new CameraDeviceStateCallback();
                mCameraManager.openCamera(id, cameraDeviceStateCallback, mHandler);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


        return false;
    }

    /**
     * CameraDevice 连接状态监听，在监听里面创建预览请求
     */
    private class CameraDeviceStateCallback extends CameraDevice.StateCallback {

        @Override
        public void onOpened(CameraDevice camera) {
            JavaCamera2View2.this.camera = camera;
            try {

                List<Surface> surfaceList = new ArrayList<>();
                Surface surface = mImageReader.getSurface();
                surfaceList.add(surface);

                //如果相机正常开启，就创建一个预览请求
                CaptureRequest.Builder captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequest.addTarget(surface);

                //设置请求相机的参数
                captureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureRequest.setTag(camera);

                //创建捕获会话
                CameraCaptureSessionStateCallback cameraCaptureSessionStateCallback = new CameraCaptureSessionStateCallback(captureRequest.build());
                camera.createCaptureSession(surfaceList, cameraCaptureSessionStateCallback, mHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            if (camera != null) {
                camera.close();
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            if (camera != null) {
                camera.close();
            }
        }
    }

    /**
     * 相机参数请求会话的状态监听
     */
    private class CameraCaptureSessionStateCallback extends CameraCaptureSession.StateCallback {

        private CaptureRequest request;

        public CameraCaptureSessionStateCallback(CaptureRequest request) {
            this.request = request;
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            JavaCamera2View2.this.session = session;
            try {
                CameraCaptureCallback cameraCaptureCallback = new CameraCaptureCallback();
                session.setRepeatingRequest(request, cameraCaptureCallback, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            if (session != null) {
                session.close();
            }
        }
    }

    /**
     * 相机捕获回调
     */
    private class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            JavaCamera2View2.this.session = session;
            switch (CAMERA_STATE) {
                case CAMERA_STATE_PREVIEW:
                    break;
                case CAMERA_STATE_PICTURE:
                    break;
            }

        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            if (session != null) {
                session.close();
            }
        }

    }

    private class JavaCamera2Frame implements CvCameraViewFrame {

        private Mat mYuvFrameData;
        private Mat mUVFrameData;
        private Mat mRgba;
        private int mWidth;
        private int mHeight;

        @Override
        public Mat gray() {
            return mYuvFrameData.submat(0, mHeight, 0, mWidth);
        }

        @Override
        public Mat rgba() {
            if (mPreviewFormat == ImageFormat.NV21)
                Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGBA_NV21, 4);
            else if (mPreviewFormat == ImageFormat.YV12)
                Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGB_I420, 4); // COLOR_YUV2RGBA_YV12 produces inverted colors
            else if (mPreviewFormat == ImageFormat.YUV_420_888) {
                assert (mUVFrameData != null);
                Imgproc.cvtColorTwoPlane(mYuvFrameData, mUVFrameData, mRgba, Imgproc.COLOR_YUV2RGBA_NV21);
            } else
                throw new IllegalArgumentException("Preview Format can be NV21 or YV12");

            return mRgba;
        }

        public JavaCamera2Frame(Mat Yuv420sp, int width, int height) {
            super();
            mWidth = width;
            mHeight = height;
            mYuvFrameData = Yuv420sp;
            mUVFrameData = null;
            mRgba = new Mat();
        }

        public JavaCamera2Frame(Mat Y, Mat UV, int width, int height) {
            super();
            mWidth = width;
            mHeight = height;
            mYuvFrameData = Y;
            mUVFrameData = UV;
            mRgba = new Mat();
        }

        public void release() {
            mRgba.release();
        }
    }
}
