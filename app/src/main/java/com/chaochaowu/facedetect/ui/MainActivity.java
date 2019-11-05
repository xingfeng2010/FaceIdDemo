package com.chaochaowu.facedetect.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.chaochaowu.facedetect.HorizonSigner;
import com.chaochaowu.facedetect.LiveDetectParam;
import com.chaochaowu.facedetect.ParamBean;
import com.chaochaowu.facedetect.PermissionUtils;
import com.chaochaowu.facedetect.adapter.FacesInfoAdapter;
import com.chaochaowu.facedetect.eventbus.FaceEvent;
import com.chaochaowu.facedetect.R;
import com.chaochaowu.facedetect.Utils;
import com.chaochaowu.facedetect.bean.FaceppBean;
import com.chaochaowu.facedetect.dagger.DaggerMainActivityComponent;
import com.chaochaowu.facedetect.dagger.MainPresenterModule;
import com.gc.materialdesign.views.ButtonRectangle;
import com.gc.materialdesign.views.ProgressBarCircularIndeterminate;
import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 主界面
 *
 * @author chaochaowu
 */
public class MainActivity extends AppCompatActivity implements MainContract.View, SurfaceHolder.Callback, Runnable {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final int CAMERA_REQUEST_CODE = 2;
    private static final int REQUEST_CODE = 11;

    @BindView(R.id.imageView)
    SurfaceView imageView;
    @BindView(R.id.progressBar)
    ProgressBarCircularIndeterminate progressBar;
    @BindView(R.id.button)
    ButtonRectangle button;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;


    File mTmpFile;
    Uri imageUri;
    Bitmap photo = null;

    @Inject
    MainPresenter mPresenter;

    FacesInfoAdapter mAdapter;
    private List<FaceppBean.FacesBean> faces;
    private int mCameraPreviewWidth, mCameraPreviewHeight;
    private FaceIdHandler mHandler = new FaceIdHandler();

    private Context mContext;

    class FaceIdHandler extends Handler {
        //上传图片数量限制
        private static final int PIC_NUM_LIMIT = 5;
        private int count = 0;
        private String[] array = new String[PIC_NUM_LIMIT];

        @Override
        public void handleMessage(Message msg) {
            count++;
            byte[] data = (byte[]) msg.obj;

            if (count <= PIC_NUM_LIMIT - 1) {
                Bitmap bitmap = nv21ToBitmap(data, mCameraPreviewWidth, mCameraPreviewHeight);
                String image64 = Utils.base64(bitmap);
                array[count] = image64;
                mAdapter.setPhoto(bitmap);
            }

            if (count == PIC_NUM_LIMIT - 1) {
                //mPresenter.getDetectResultFromServer(bitmap);
                String[] temparray = new String[PIC_NUM_LIMIT];
                for (int i = 0; i < temparray.length; i++) {
                    temparray[i] = array[i];
                }
//                testDipingxianApi(temparray);
                testLiveDetect();
                //调用地平线接口传arra数组。
//                count = 0;
            }
        }
    }

    private static final String LIVE_DETECT_URL = "https://faceid-pre.horizon.ai/faceid/v1/face/live_detect";
    private void testLiveDetect() {
        Log.i("DEBUG_TEST", "testLiveDetect call");
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/CameraKitDemo0.13/Full/_2019-11-05-132724_Full.mp4";
//        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test2.mp4";

        String base64 = file2Base64(path);

        String authString = sign(HorizonSigner.HTTP_METHOD_POST, "/faceid/v1/face/live_detect");

        HttpUrl.Builder urlBuilder = HttpUrl.parse(LIVE_DETECT_URL).newBuilder();
        urlBuilder.setQueryParameter("authorization", authString);


        LiveDetectParam paramBean = new LiveDetectParam();
        paramBean.msg_id = random.nextLong();
        paramBean.video_type = 1;
        paramBean.client_type = 1;
        paramBean.video_base64 = base64;
        paramBean.app_id = "98bea9445db9992e4b5d16da";
        paramBean.client_type = 1;
        paramBean.device_id = "test_00001";
        paramBean.group_id = "car_connect_98bea9445db9992e4b5d16da_5db999d098bea90008f7774d_";


        String param = new Gson().toJson(paramBean);
        OkHttpClient okHttpClient = new OkHttpClient();//创建OkHttpClient对象。
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), param);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(body)
                .build();


        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i("DEBUG_TEST", "00 e:" + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i("DEBUG_TEST", "00 onResponse:" + response.body().string());
            }
        });
    }


    /**
     * 文件转Base64.
     *
     * @param filePath
     * @return
     */
    public static String file2Base64(String filePath) {
        FileInputStream objFileIS = null;
        try {
            objFileIS = new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        ByteArrayOutputStream objByteArrayOS = new ByteArrayOutputStream();
        byte[] byteBufferString = new byte[1024];
        try {
            for (int readNum; (readNum = objFileIS.read(byteBufferString)) != -1; ) {
                objByteArrayOS.write(byteBufferString, 0, readNum);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String videodata = Base64.encodeToString(objByteArrayOS.toByteArray(), Base64.NO_WRAP);
        return videodata;
    }

    private static final String URL = "https://faceid-pre.horizon.ai/faceid/v1/faces/98bea9445db9992e4b5d16da/car_connect_98bea9445db9992e4b5d16da_5dba4f0bbbc3c70008a832d4_/faces";
    private Random random = new Random();

    public long nextLong(Random rng, long n) {
        // error checking and 2^x checking removed for simplicity.
        long bits, val;
        do {
            bits = (rng.nextLong() << 1) >>> 1;
            val = bits % n;
        } while (bits - val + (n - 1) < 0L);
        return val;
    }

    private void testDipingxianApi(String[] array) {
        Log.i("DEBUG_TEST", "testDipingxianApi call");
        String authString = sign(HorizonSigner.HTTP_METHOD_POST, "/faceid/v1/faces/faces");

        HttpUrl.Builder urlBuilder = HttpUrl.parse(URL).newBuilder();
        urlBuilder.setQueryParameter("authorization", authString);

        ParamBean.ImageBean[] imgArr = new ParamBean.ImageBean[array.length - 1];
        for (int i = 1; i < array.length; i++) {
            ParamBean.ImageBean bean = new ParamBean.ImageBean();
            bean.image_type = 1;
            bean.image_base64 = array[i];
            imgArr[i - 1] = bean;
        }

        ParamBean paramBean = new ParamBean();
        paramBean.msg_id = random.nextLong();
        paramBean.images = imgArr;
        paramBean.client_type = 1;
        paramBean.device_id = "test_00001";
        paramBean.distance_threshold = "1e-5";

        String param = new Gson().toJson(paramBean);
        OkHttpClient okHttpClient = new OkHttpClient();//创建OkHttpClient对象。
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), param);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(body)
                .build();


        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i("DEBUG_TEST", "11 e:" + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i("DEBUG_TEST", "11 onResponse:" + response.body().string());
            }
        });
    }


    private HorizonSigner mHorizonSigner;

    private String sign(String httpMethod, String httpApi) {
        try {
            SortedMap<String, String> headers = new TreeMap<String, String>();
            SortedMap<String, String> params = new TreeMap<String, String>();
            headers.put("content-type", "application/json");
            headers.put("host", String.valueOf(9600));
            return mHorizonSigner.Sign(httpMethod, httpApi, params, headers);
        } catch (Exception e) {
            Log.e(TAG, "sign exception:" + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        DaggerMainActivityComponent.builder()
                .mainPresenterModule(new MainPresenterModule(this))
                .build()
                .inject(this);
        faces = new ArrayList<>();
        faces.add(new FaceppBean.FacesBean());
        mAdapter = new FacesInfoAdapter(this, faces, photo);
        mAdapter.setListener(new FacesInfoAdapter.onItemClickListener() {
            @Override
            public void onItemClicked(FaceppBean.FacesBean face, TextView tvBeauty) {
                gotoDetailActivity(face, tvBeauty);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setAdapter(mAdapter);

        PermissionUtils.askPermission(this, new String[]{Manifest.permission.CAMERA, Manifest
                .permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE, this);

        imageView.getHolder().addCallback(this);

        mContext = this;

        String ak = "tIvZrJMe11Ao0DWvJYtzobHQ";
        String sk = "NoA6fSVxiknDT7YkS4xnF59GMJyHNU00";
        mHorizonSigner = new HorizonSigner(ak, sk);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        Bitmap bitmap = BitmapFactory.decodeResource(this.getResources(), R.drawable.timg);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] bytes = baos.toByteArray();
        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

        String[] array = new String[]{base64, base64};

//        testOnePicApi(array);
    }

    @OnClick(R.id.button)
    public void onButtonClicked() {
        takePhoto();
    }

    private void gotoDetailActivity(FaceppBean.FacesBean face, TextView tvBeauty) {
        if (face.getAttributes() == null) {
            return;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        android.support.v4.util.Pair<View, String> image = new android.support.v4.util.Pair(imageView, "image");
        android.support.v4.util.Pair<View, String> beauty = new android.support.v4.util.Pair(tvBeauty, "beauty");
        ActivityOptionsCompat optionsCompat =
                ActivityOptionsCompat.makeSceneTransitionAnimation(this, image, beauty);
        EventBus.getDefault().postSticky(new FaceEvent(photo, face));
        startActivity(intent, optionsCompat.toBundle());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0) {
                for (int grantResult : grantResults) {
                    if (grantResult == PackageManager.PERMISSION_DENIED) {
                        return;
                    }
                }
                takePhoto();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case CAMERA_REQUEST_CODE:
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    photo = BitmapFactory.decodeFile(mTmpFile.getAbsolutePath(), options);
                    int bitmapDegree = Utils.getBitmapDegree(mTmpFile.getAbsolutePath());
                    if (bitmapDegree != 0) {
                        photo = Utils.rotateBitmapByDegree(this.photo, bitmapDegree);
                    }
                    displayPhoto(this.photo);
                    mAdapter.setPhoto(this.photo);
                    mPresenter.getDetectResultFromServer(this.photo);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void takePhoto() {
        if (!Utils.checkAndRequestPermission(this, PERMISSIONS_REQUEST_CODE)) {
            return;
        }
        Intent intent = new Intent();
        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/img";
        if (new File(path).exists()) {
            try {
                new File(path).createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @SuppressLint("SimpleDateFormat")
        String filename = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        mTmpFile = new File(path, filename + ".jpg");
        mTmpFile.getParentFile().mkdirs();
        String authority = getPackageName() + ".provider";
        imageUri = FileProvider.getUriForFile(this, authority, mTmpFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, CAMERA_REQUEST_CODE);
    }

    @Override
    public void displayPhoto(Bitmap photo) {
        //Glide.with(this).load(photo).into(imageView);
    }

    @Override
    public void displayFaceInfo(List<FaceppBean.FacesBean> faces) {
        this.faces.clear();
        if (faces == null) {
            this.faces.add(new FaceppBean.FacesBean());
            Toast.makeText(this, "未检测到面部信息", Toast.LENGTH_LONG).show();
        } else {
            this.faces.addAll(faces);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void showProgress() {
        button.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideProgress() {
        button.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }


    private Camera mCamera;

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        try {
            Camera.Parameters parms = mCamera.getParameters();
            Camera.Size mCameraPreviewSize = parms.getPreviewSize();
            mCameraPreviewWidth = mCameraPreviewSize.width;
            mCameraPreviewHeight = mCameraPreviewSize.height;
            Log.i("DEBUG_TEST", "mCameraPreviewWidth:" + mCameraPreviewWidth);
            Log.i("DEBUG_TEST", "mCameraPreviewHeight:" + mCameraPreviewHeight);
            mCamera.setPreviewDisplay(holder);
            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewCallback(new CustomPreviewCallback());
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void run() {

    }

    public class CustomPreviewCallback implements Camera.PreviewCallback {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            // mPresenter.getDetectResultFromServer(this.photo);
            mHandler.obtainMessage(1, data).sendToTarget();
        }
    }

    private Bitmap nv21ToBitmap(byte[] nv21, int width, int height) {
        Bitmap bitmap = null;
        try {
            YuvImage image = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, width, height), 80, stream);
            bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * @param context
     * @return 获取唯一标示
     */
    public String deviceID(Context context) {
        String deviceid = "";

        int permission2 = ActivityCompat.checkSelfPermission(mContext,
                android.Manifest.permission.READ_PHONE_STATE);
        if (permission2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{}, 0x0010);
            return deviceid;
        }
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String szImei = tm.getDeviceId();
        String sn = tm.getSimSerialNumber();
        return sn + szImei;
    }

    private void testOnePicApi(String[] array) {
        String authString = sign(HorizonSigner.HTTP_METHOD_POST, "/faceid/v1/faces/faces");

        HttpUrl.Builder urlBuilder = HttpUrl.parse(URL).newBuilder();
        urlBuilder.setQueryParameter("authorization", authString);

        ParamBean.ImageBean[] imgArr = new ParamBean.ImageBean[array.length - 1];
        for (int i = 1; i < array.length; i++) {
            ParamBean.ImageBean bean = new ParamBean.ImageBean();
            bean.image_type = 1;
            bean.image_base64 = array[i];
            imgArr[i - 1] = bean;
        }

        ParamBean paramBean = new ParamBean();
        paramBean.msg_id = random.nextLong();
        paramBean.images = imgArr;
        paramBean.client_type = 1;
        paramBean.device_id = "test_00001";
        paramBean.distance_threshold = "1e-5";

        String param = new Gson().toJson(paramBean);
        OkHttpClient okHttpClient = new OkHttpClient();//创建OkHttpClient对象。
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), param);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(body)
                .build();


        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i("DEBUG_TEST", "e:" + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i("DEBUG_TEST", "onResponse:" + response.body().string());
            }
        });
    }
}

