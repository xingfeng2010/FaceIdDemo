package com.chaochaowu.facedetect;

import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.gson.Gson;
import com.wonderkiln.camerakit.CameraKit;
import com.wonderkiln.camerakit.CameraKitEventCallback;
import com.wonderkiln.camerakit.CameraKitVideo;
import com.wonderkiln.camerakit.CameraView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LiveDetectActivity extends AppCompatActivity {

    private static final String TAG = "LiveDetectActivity";
    CameraView cameraView;

    private Button btnStartRec, btnStopRec;

    private String recordingVideoFilePath;
    private Handler handler = new Handler();

    private Long liveDetectTime;
    private Long liveApiTime;
    private Long liveBase64Time;
    private Long picRegisterTime;
    private Long picApiTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_detect);
        cameraView = (CameraView) findViewById(R.id.camera);
        cameraView.setFacing(CameraKit.Constants.FACING_FRONT);
        cameraView.setVideoBitRate(640);
        cameraView.setVideoQuality(CameraKit.Constants.VIDEO_QUALITY_480P);

        btnStartRec = findViewById(R.id.btn_start_rec);
        btnStartRec.setOnClickListener(videoOnClickListener);

        btnStopRec = findViewById(R.id.btn_stop_rec);
        btnStopRec.setOnClickListener(stopRecOnClickListener);

        String ak = "tIvZrJMe11Ao0DWvJYtzobHQ";
        String sk = "NoA6fSVxiknDT7YkS4xnF59GMJyHNU00";
        mHorizonSigner = new HorizonSigner(ak, sk);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        cameraView.stop();
        super.onPause();
    }

    private View.OnClickListener videoOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            btnStartRec.setVisibility(View.GONE);
            btnStopRec.setVisibility(View.VISIBLE);
            File videoFile = getRecordingVideoFilePath();
            recordingVideoFilePath = videoFile.getAbsolutePath();
            cameraView.captureVideo(videoFile, new CameraKitEventCallback<CameraKitVideo>() {
                @Override
                public void callback(CameraKitVideo cameraKitVideo) {

                    Log.e("Rec Video Path", cameraKitVideo.getVideoFile().getAbsolutePath());
                }
            });

        }
    };

    private View.OnClickListener stopRecOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            cameraView.stopVideo();
            btnStartRec.setVisibility(View.VISIBLE);
            btnStopRec.setVisibility(View.GONE);

            testLiveDetect(recordingVideoFilePath);
        }
    };

    public File getRecordingVideoFilePath() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/FaceIdDemo", "/" + "Full");

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e("createFullFolder", "failed to create the directory " + mediaStorageDir);
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault()).format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator + "_" + timeStamp + "_Full" + ".mp4");
    }


    private static final String REGISTER_URL = "https://faceid-pre.horizon.ai/faceid/v1/faces/98bea9445db9992e4b5d16da/car_connect_98bea9445db9992e4b5d16da_5dba4f0bbbc3c70008a832d4_/faces";
    private Random random = new Random();
    private static final String LIVE_DETECT_URL = "https://faceid-pre.horizon.ai/faceid/v1/face/live_detect";

    private void testLiveDetect(String path) {
        liveDetectTime = System.currentTimeMillis();
        String base64 = file2Base64(path);
        liveBase64Time = System.currentTimeMillis();
        Log.i("DEBUG_TEST", "视频-->Base64时间：" + (liveBase64Time - liveDetectTime));
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


        liveApiTime = System.currentTimeMillis();
        Log.i("DEBUG_TEST", "Base64-->签名：" + (liveApiTime - liveBase64Time));
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i("DEBUG_TEST", "00 e:" + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                picRegisterTime = System.currentTimeMillis();
                Log.i("DEBUG_TEST", "活体检测接口调用-->返回时间：" + (picRegisterTime - liveApiTime));
                String plainText = response.body().string();
                try {
                    JSONObject jsonObject = new JSONObject(plainText);
                    int code = jsonObject.optInt("rsp_code");
                    if (code == 0) {
                        JSONObject live_result = jsonObject.optJSONObject("live_result");
                        String base64 = live_result.optString("best_frame_base64");
                        testDipingxianApi(new String[]{base64, base64});
                        Log.i("DEBUG_TEST", "活体检测成功！！");
                    } else {
                        Log.i("DEBUG_TEST", "活体检测失败！！");
                        showToast("活体检测失败");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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

    private void testDipingxianApi(String[] array) {
        Log.i("DEBUG_TEST", "testDipingxianApi call");
        String authString = sign(HorizonSigner.HTTP_METHOD_POST, "/faceid/v1/faces/faces");

        HttpUrl.Builder urlBuilder = HttpUrl.parse(REGISTER_URL).newBuilder();
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


        picApiTime = System.currentTimeMillis();
        Log.i("DEBUG_TEST", "签名-->注册接口时间：" + (picApiTime - picRegisterTime));
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i("DEBUG_TEST", "11 e:" + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i("DEBUG_TEST", "注册接口调用-->返回时间：" + (System.currentTimeMillis() - picApiTime));
                Log.i("DEBUG_TEST", "视频-->活检完成-->注册完成总的时间：" + (System.currentTimeMillis() - liveDetectTime));
                String plainText = response.body().string();
                Log.i("DEBUG_TEST", ": "+plainText);
                try {
                    JSONObject jsonObject = new JSONObject(plainText);
                    int rsp_code = jsonObject.optInt("rsp_code");
                    if (rsp_code == 0 || rsp_code == 4005) {
                        String person_id = jsonObject.optString("person_id");
                        Log.i("DEBUG_TEST", "注册成功！！");
                    } else {
                        Log.i("DEBUG_TEST", "注册失败！！");
                        showToast("注册失败");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void showToast(final String str) {
        LiveDetectActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LiveDetectActivity.this, str, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
