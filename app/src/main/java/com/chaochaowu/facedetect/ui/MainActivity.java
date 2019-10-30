package com.chaochaowu.facedetect.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
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

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

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

    class FaceIdHandler extends Handler {
        //上传图片数量限制
        private static final int PIC_NUM_LIMIT = 15;
        private int count = 0;
        private String[] array = new String[PIC_NUM_LIMIT];

        @Override
        public void handleMessage(Message msg) {
            count ++;
            if (count >= PIC_NUM_LIMIT) {
                byte[] data = (byte[]) msg.obj;
                Bitmap bitmap = nv21ToBitmap(data, mCameraPreviewWidth, mCameraPreviewHeight);
                mAdapter.setPhoto(bitmap);
                mPresenter.getDetectResultFromServer(bitmap);
                String image64 = Utils.base64(bitmap);
                array[count] = image64;
                //调用地平线接口传arra数组。
                count = 0;
            }
        }
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
}

