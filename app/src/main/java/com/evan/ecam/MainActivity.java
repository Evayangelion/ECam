package com.evan.ecam;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ECam";
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession.StateCallback mSessionStateCallback;        //获取的会话类状态回调
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback;    //获取会话类的获取数据回调
    private CaptureRequest.Builder mPreviewRequestBuilder;                            //获取数据请求配置类
    private CaptureRequest mPreviewRequest;
    private CameraDevice.StateCallback mStateCallback;                        //摄像头状态回调
    private CameraCaptureSession mCameraCaptureSession;                    //获取数据会话类
    private ImageReader mImageReader;                                        //照片读取器
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private String[] mCameraIdList;
    private String mCurrentSelectedCamera;
    private AutoFitTextureView mTextureView; //need hardware acceleration
    private Surface mSurface;
    private SurfaceTexture mSurfaceTexture;
    private List<Size> mOutputSizes;
    private Size mPreviewSize;

    private Spinner mSpinner;
    private ArrayAdapter<String> mAdapter;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.textureView);
        mSpinner = findViewById(R.id.spinner);

        checkCameraPermission();
        initViews();
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //Log.d(TAG, "onImageAvailable");
            Image img = reader.acquireNextImage();
            ByteBuffer buffer = img.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            img.close();
        }

    };
    private void initViews() {
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.e(TAG, "TextureView 启用成功");
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.e(TAG, "SurfaceTexture 变化");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.e(TAG, "SurfaceTexture 销毁");//这里返回true则是交由系统执行释放，如果是false则需要自己调用surface.release();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        mStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                Log.d(TAG, "onOpened: ");
                mCameraDevice = cameraDevice;
                createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                Log.d(TAG, "onDisconnected: ");
                cameraDevice.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {
                Log.d(TAG, "onError: ");
                cameraDevice.close();
                mCameraDevice = null;
                Log.e(TAG, "CameraDevice.StateCallback onError errorCode= " + error);
            }
        };


    }

    private void createCameraPreviewSession() {
        Log.d(TAG, "createCameraPreviewSession");

        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if(texture == null){
                Log.d(TAG, "createCameraPreviewSession: texture is null");
            }
            Log.d(TAG, "NOW USING SIZE:" + mPreviewSize.getWidth() +" * " + mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            Log.d(TAG, "onConfigured: surface done");

            mImageReader = ImageReader.newInstance(1080, 720, ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            mPreviewRequestBuilder= mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(surface,mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "onConfigured: ");
                            // 相机已经关闭
                            if (null == mCameraDevice) {
                                Log.d(TAG, "onConfigured: device fucked");
                                return;
                            }
                            Log.d(TAG, "onConfigured: w");
                            // 当session准备好后，我们开始显示预览
                            mCameraCaptureSession = cameraCaptureSession;
                            try {
                                // 相机预览时应连续自动对焦
                                //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // 设置闪光灯在必要时自动打开
                                //setAutoFlash(mPreviewRequestBuilder);
                                // 最终,显示相机预览
                                Log.d(TAG, "onConfigured: t");
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                Log.d(TAG, "onConfigured: f");
                                mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mSessionCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "CameraCaptureSession.StateCallback onConfigureFailed");
                        }

                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            mCameraIdList = mCameraManager.getCameraIdList();//获取可用摄像头列表
            if (0 == mCameraIdList.length) {
                Log.e(TAG, "NO FUCKING CAMERA DETECTED");
                return;
            } else {
                Log.d(TAG, "AVAILABLE CAMERA AMOUNT :" + mCameraIdList.length);
            }

            //下拉菜单选择camera
            //todo 不知道为什么，只有两个摄像头可用
            mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mCameraIdList);
            mSpinner.setAdapter(mAdapter);
            mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                    String val = mAdapter.getItem(pos).toString();
                    Toast.makeText(MainActivity.this, "using camera id :" + val, Toast.LENGTH_SHORT).show();
                    mCurrentSelectedCamera = val;
                    closeCamera(); // close current and open a new camera
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "openCamera: no Camera Permission");
                        return;
                    }
                    try {
                        mCameraManager.openCamera(mCurrentSelectedCamera, mStateCallback, mBackgroundHandler);
                        Log.d(TAG, "tried open cameraId: " + mCurrentSelectedCamera);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                public void onNothingSelected(AdapterView<?> arg0) {}
            });

            mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());


            for (String cameraId : mCameraIdList) {
                Log.e(TAG, "selectCamera: cameraId=" + cameraId);
                //获取相机特征,包含前后摄像头信息，分辨率等
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);//获取这个摄像头的面向
                StreamConfigurationMap configs = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mOutputSizes = Arrays.asList(configs.getOutputSizes(SurfaceTexture.class));
                for (Size sz : mOutputSizes) {
                    Log.e(TAG, "mOutputSizes: available size :" + sz);
                }
                mPreviewSize = mOutputSizes.get(0);
                mTextureView.setAspectRation(mPreviewSize.getWidth(), mPreviewSize.getHeight());

                //CameraCharacteristics.LENS_FACING_BACK 后摄像头
                //CameraCharacteristics.LENS_FACING_FRONT 前摄像头
                //CameraCharacteristics.LENS_FACING_EXTERNAL 外部摄像头,比如OTG插入的摄像头
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.e(TAG, "selectCamera: we've got a back cam :" + cameraId);
                    mCurrentSelectedCamera = cameraId;
                    continue;
                } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    Log.e(TAG, "selectCamera: we've got a front cam :" + cameraId);
                    continue;
                }
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "openCamera: no Camera Permission");
                    return;
                }
                mCameraManager.openCamera(mCurrentSelectedCamera, mStateCallback, mBackgroundHandler);

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void checkCameraPermission() {
        Log.d(TAG, "checkCameraPermission");
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            Log.d(TAG, "REQUEST PERMISSION");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }else{
            Log.d(TAG, "PERMISSION GRANTED");
        }
    }

    public void closeCamera(){
        if (null != mCameraCaptureSession) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }


}