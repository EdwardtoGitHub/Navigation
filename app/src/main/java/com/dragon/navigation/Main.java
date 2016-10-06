package com.dragon.navigation;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.dragon.navigation.util.AMapUtil;
import com.dragon.navigation.util.MyTextView;
import com.dragon.navigation.util.ToastUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This file created by dragon on 2016/7/26 19:40,belong to com.dragon.arnav.basicFuction.camera2 .
 */
public class Main extends Activity implements View.OnClickListener,SensorEventListener,
        TextWatcher,Inputtips.InputtipsListener {
    private static final String TAG="Main";
    private ArPoiSearch mArPoiSearch;
    private Location mLocation;
    private AutoCompleteTextView mSearchText;
//    private EditText editCity;

    private TextureView textureView;
    //用SparseIntArray来代替hashMap，进行性能优化。
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    //    静态初始化块
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }
    //    以下定义是摄像头相关和摄像头会话相关
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
//    *****************************************************************

    private MyTextView mCurrentCity;
    private TextView btnPoiSearch;
    private Button btnMy;
    private ImageView imgZnz;
//****************************************************************
    float currentDegree = 0f;
    SensorManager mSensorManager;
    private Sensor accelerometer;//加速度传感器
    private Sensor magnetic;//地磁传感器

    private float[] accelerometerValues = new float[3];
    private float[] magneticFieldValues = new float[3];




    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main_camera2);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
//********************Location***************************
        mCurrentCity=(MyTextView)findViewById(R.id.current_city);
        mLocation = new Location(Main.this,savedInstanceState,mCurrentCity);
        mLocation.initLoction();

//********************POI********************************
        mArPoiSearch = new ArPoiSearch(this,"大学","","深圳市");
        mArPoiSearch.doSearchSearch();
        mArPoiSearch.getPoiResult();



        btnMy = (Button)findViewById(R.id.My);
        btnMy.setOnClickListener(this);
        btnPoiSearch = (TextView)findViewById(R.id.btn_search);
        btnPoiSearch.setOnClickListener(this);


//        ToastUtil.show(Main.this,mLocation.getLocationResult().getAddress());
        imgZnz = (ImageView)findViewById(R.id.Compass);

//        ****************************************************
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
//        List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
//        Log.e("dragon",deviceSensors+"");
//        实例化加速度传感器
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        实例化地磁传感器
        magnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        calculateOrientation();
//        *******************************************************
        mSearchText = (AutoCompleteTextView) findViewById(R.id.searchText);
        mSearchText.addTextChangedListener(this);// 添加文本输入框监听事件
    }


    //    定义了一个独立的监听类
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
                                  int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        String newText = s.toString().trim();
        if (!AMapUtil.IsEmptyOrNullString(newText)) {
            InputtipsQuery inputquery = new InputtipsQuery(newText, "");
            Inputtips inputTips = new Inputtips(Main.this, inputquery);
            inputTips.setInputtipsListener(this);
            inputTips.requestInputtipsAsyn();
        }
    }
    @Override
    public void onGetInputtips(List<Tip> tipList, int rCode) {
        if (rCode == 1000) {// 正确返回
            List<String> listString = new ArrayList<String>();
            for (int i = 0; i < tipList.size(); i++) {
                listString.add(tipList.get(i).getName());
            }
            ArrayAdapter<String> aAdapter = new ArrayAdapter<String>(
                    getApplicationContext(),
                    R.layout.route_inputs, listString);
            mSearchText.setAdapter(aAdapter);
            aAdapter.notifyDataSetChanged();
        } else {
            ToastUtil.showerror(this, rCode);
        }

    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
//        摄像头打开激发该方法
        public void onOpened(CameraDevice camera) {
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
//            开始预览
            try{
                createCameraPreview();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        //        摄像头断开连接时的方法
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
            Main.this.cameraDevice = null;
        }
        //        打开摄像头出现错误时激发方法
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            Main.this.cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    protected void initCamera2(){
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
//            如果没打开则返回
            return;
        }
//        摄像头管理器，专门用于检测、打开系统摄像头，并连接CameraDevices.
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.e(TAG,"cameraDevice.getId "+cameraDevice.getId());//查看选中的摄像头ID
//            获取指定摄像头的相关特性（此处摄像头已经打开）
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());

//            定义图像尺寸
            Size[] jpegSizes;
//                获取摄像头支持的最大尺寸
            jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
////            初始化一个尺寸
//            int width = 640;
//            int height = 480;

            int width = jpegSizes[0].getWidth();
            int height = jpegSizes[0].getHeight();

//            创建一个ImageReader对象，用于获得摄像头的图像数据
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
//动态数组
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);

            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
//生成请求对象（TEMPLATE_STILL_CAPTURE此处请求是拍照）
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//            将ImageReader的surface作为captureBuilder的输出目标
            captureBuilder.addTarget(reader.getSurface());
////设置自动对焦模式
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
////            设置自动曝光模式
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);//推荐采用这种最简单的设置请求模式
            // 获取设备方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
//            根据设置方向设置照片显示的方向
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

//            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            //拍照开始或是完成时调用，用来监听CameraCaptureSession的创建过程
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
//                    Toast.makeText(Main.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
//            设置默认的预览大小
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
//            请求预览
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureRequestBuilder.addTarget(surface);
//            创建cameraCaptureSession,第一个参数是图片集合，封装了所有图片surface,第二个参数用来监听这处创建过程
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(Main.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
//        实例化摄像头
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
//            指定要打开的摄像头
            cameraId = manager.getCameraIdList()[0];
//            获取打开摄像头的属性
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//The available stream configurations that this camera device supports; also includes the minimum frame durations and the stall durations for each format/size combination.
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
//            打开摄像头，第一个参数代表要打开的摄像头，第二个参数用于监测打开摄像头的当前状态，第三个参数表示执行callback的Handler,
//            如果程序希望在当前线程中执行callback，像下面的设置为null即可。
            if (ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, stateCallback, null);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera 1");
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
//        设置模式为自动
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        //        注册监听事件
        mSensorManager.registerListener(Main.this,accelerometer,SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(Main.this,magnetic,SensorManager.SENSOR_DELAY_GAME);
        mLocation.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            Log.e(TAG, "textureViewAvailable");
//            openCamera();
            initCamera2();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        mSensorManager.unregisterListener(this);
        closeCamera();
        stopBackgroundThread();
        super.onPause();
        mLocation.onPause();
        mLocation.deactivate();
    }
    @Override
    protected void onStop(){
        mSensorManager.unregisterListener(this);
        super.onStop();
    }


    /**
     * 方法必须重写
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mLocation.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocation.onDestroy();
    }


    private float calculateOrientation(){
//        SensorManager mSensorMgr = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
//        HandlerThread mHandlerThread = new HandlerThread("sensorThread");
//        mHandlerThread.start();
//        Handler handler = new Handler(mHandlerThread.getLooper());
//        mSensorMgr.registerListener(this, mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
//                SensorManager.SENSOR_DELAY_FASTEST, handler);
        float[] values = new float[3];
        float[] R = new float[9];
        SensorManager.getRotationMatrix(R, null, accelerometerValues,
                magneticFieldValues);
        SensorManager.getOrientation(R, values);
        values[0] = (float) Math.toDegrees(values[0]);
        return values[0];
    }
    @Override
    public void onSensorChanged(SensorEvent event){
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticFieldValues = event.values;
        }
        float degree = calculateOrientation();
        RotateAnimation ra = new RotateAnimation(currentDegree,-degree, Animation.RELATIVE_TO_SELF,0.5f,
                Animation.RELATIVE_TO_SELF,0.5f);

//        int rotation = getWindowManager().getDefaultDisplay().getRotation();
//        imgZnz.setRotate(ORIENTATIONS.get(rotation),50,50);
        ra.setDuration(200);
        imgZnz.startAnimation(ra);
        currentDegree=-degree;
    }
    @Override
    public void onAccuracyChanged(Sensor sensor,int accuracy){

    }





//    检测所有按键
@Override
public void onClick(View v)
{
    switch (v.getId())
    {
        case R.id.btn_search:
//            ToastUtil.show(Main.this,"搜索");
            Toast.makeText(Main.this, "搜索", Toast.LENGTH_SHORT).show();
            break;
        case R.id.My:
//            ToastUtil.show(Main.this,"我的");
            Toast.makeText(Main.this, "我的", Toast.LENGTH_SHORT).show();
            break;
    }
}




}
