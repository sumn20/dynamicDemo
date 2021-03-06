package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.SyncStateContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveBase;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.ttpic.baseutils.zip.ZipUtils;
import com.tencent.ugc.TXRecordCommon;
import com.tencent.ugc.TXUGCBase;
import com.tencent.ugc.TXUGCRecord;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private OkHttpClient mOkHttpClient;
    private String rtmpPushUrl;
    private String rtmpPlayUrl;
    private String flvPlayUrl;
    private String hlsPlayUrl;
    private String realtimePlayUrl;
    private TXCloudVideoView videoView;
    private SeekBar seekbar;
    private TextView tv_progress;
    private TXLivePusher txLivePusher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.SYSTEM_ALERT_WINDOW, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.WRITE_SETTINGS, Manifest.permission.READ_EXTERNAL_STORAGE});
        intiView();
        intoURL();
    }


    /**
     * ?????????view
     */
    private void intiView() {
        videoView = findViewById(R.id.videoView);
        seekbar = findViewById(R.id.seekbar);
        tv_progress = findViewById(R.id.tv_progress);
        Button btn_1 = findViewById(R.id.btn_1);
        Button btn_2 = findViewById(R.id.btn_2);
        Button btn_3 = findViewById(R.id.btn_3);
        Button btn_4 = findViewById(R.id.btn_4);
        Button btn_5 = findViewById(R.id.btn_5);
        btn_1.setOnClickListener(this);
        btn_2.setOnClickListener(this);
        btn_3.setOnClickListener(this);
        btn_4.setOnClickListener(this);
        btn_5.setOnClickListener(this);
    }

    /**
     * ??????so?????????????????????
     *
     * @return
     */
    private String getPath() {
        File file = getFilesDir();
        if (!file.exists()) {
            file.mkdir();
        }
        return file.getPath() + "/arm64-v8a";
    }

    String url = "http://xxx.com/so_files.zip";//so??????????????????
    String FILE_NAME = "so_files.zip";//??????????????????

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_1://??????
                startPusher();
                break;
            case R.id.btn_2://??????so
                DownloadUtils.getInstance().download(url, getPath(), FILE_NAME, new DownloadUtils.OnDownloadListener() {
                    @Override
                    public void onDownloadSuccess(String mSavePath) {
                        Toast.makeText(MainActivity.this, mSavePath, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onDownloading(int progress) {
                        handler.sendMessage(handler.obtainMessage(1, progress));
                    }

                    @Override
                    public void onDownloadFailed() {

                    }
                });
                break;
            case R.id.btn_3://??????so
                try {
                    readByApacheZipFile(new File(getPath(), FILE_NAME).getPath(), getPath());
                } catch (IOException e) {
                    e.printStackTrace();
                    tv_progress.setText("????????????");
                    Log.e("lin", "++++++++++ e=" + e.toString());
                }
                break;
            case R.id.btn_4:

                break;
            case R.id.btn_5://??????setLibraryPath
                String s = getPath() + "/so_files";//???????????? FILE_NAME
                Log.e("lin", "--------  s=" + s);
                TXLiveBase.setLibraryPath(s);//??????so
                handler.sendEmptyMessageDelayed(3, 5000);//5???????????? intiLicence() setLicence
                break;
            default:
        }
    }

    /**
     * ?????????licence
     */
    private void intiLicence() {
        String licenceURL = "";
        String licenceKey = "";
        TXLiveBase.getInstance().setLicence(this, licenceURL, licenceKey);
        String licenceInfo = TXLiveBase.getInstance().getLicenceInfo(this);
        Log.e("lin", "-------------------------------   licenceInfo=" + licenceInfo);
        Toast.makeText(this, "licenceInfo=" + licenceInfo, Toast.LENGTH_SHORT).show();
    }


    /**
     * ??????* ?????? org.apache.tools.zip.ZipFile ????????????????????? java ????????????
     * ??????* java.util.zip.ZipFile ???????????????????????????????????????????????????????????????
     * ??????* ?????????
     * ??????*
     * ??????* ??????apache ???????????? ZipInputStream ????????????????????????????????????ZipFile
     * ??????* ????????????????????????
     * ??????* @param archive ???????????????
     * ??????* @param decompressDir ????????????
     * ??????* @throws IOException
     * ??????* @throws FileNotFoundException
     * ??????* @throws ZipException
     */
    public void readByApacheZipFile(String archive, String decompressDir) throws IOException, FileNotFoundException, ZipException {
        BufferedInputStream bi;
        ZipFile zf = new ZipFile(archive);//????????????
        Enumeration e = zf.entries();
        File newFile = new File(decompressDir);
        if (!newFile.exists()) {
            if (!newFile.mkdirs()) {
                Toast.makeText(this, "??????????????????", Toast.LENGTH_SHORT).show();
            }
        }
        while (e.hasMoreElements()) {
            ZipEntry ze2 = (ZipEntry) e.nextElement();
            String entryName = ze2.getName();
            String path = decompressDir + "/" + entryName;
            if (ze2.isDirectory()) {
                System.out.println("???????????????????????? - " + entryName);
                File decompressDirFile = new File(path);
                if (!decompressDirFile.exists()) {
                    decompressDirFile.mkdirs();
                }
            } else {
                System.out.println("???????????????????????? - " + entryName);
                String fileDir = path.substring(0, path.lastIndexOf("/"));
                File fileDirFile = new File(fileDir);
                if (!fileDirFile.exists()) {
                    fileDirFile.mkdirs();
                }
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
                bi = new BufferedInputStream(zf.getInputStream(ze2));
                byte[] readContent = new byte[1024];
                int readCount;
                while ((readCount = bi.read(readContent)) != -1) {
                    bos.write(readContent, 0, readCount);
                }
                bos.flush();
                bos.close();
                bi.close();
            }
        }
        seekbar.setProgress(100);
        tv_progress.setText("????????????");
        zf.close();
    }


    /**
     * ??????
     */
    private void startPusher() {
        txLivePusher = new TXLivePusher(this);
        TXLivePushConfig config = new TXLivePushConfig();
        txLivePusher.startCameraPreview(videoView);
        txLivePusher.setConfig(config);
        int i = txLivePusher.startPusher(rtmpPushUrl);
        Log.e("lin", "--------  ????????????=" + i);
        txLivePusher.setPushListener(new ITXLivePushListener() {
            @Override
            public void onPushEvent(int i, Bundle bundle) {
                if (i == 1002) {
                    tv_progress.setText("????????????");
                    Toast.makeText(MainActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                }
                Log.e("lin", "onPushEvent=" + bundle.toString());
            }

            @Override
            public void onNetStatus(Bundle bundle) {
                Log.e("lin", "onNetStatus=" + bundle.toString());
            }
        });
    }

    /**
     * ??????????????????
     *
     * @param permissions ?????????????????????
     */
    private void requestPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(this, permissions, 1);
    }

    /**
     * ????????????????????????demo???????????????
     */
    private void intoURL() {
        if (mOkHttpClient == null) {
            mOkHttpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
        String reqUrl = "https://lvb.qcloud.com/weapp/utils/get_test_pushurl";
        Request request = new Request.Builder()
                .url(reqUrl)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build();
        Log.d("lin", "start fetch push url");
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "????????????????????????", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject jsonRsp;
                    try {
                        jsonRsp = new JSONObject(response.body().string());
                        rtmpPushUrl = jsonRsp.optString("url_push");            // RTMP ????????????
                        rtmpPlayUrl = jsonRsp.optString("url_play_rtmp");   // RTMP ????????????
                        // FLA  ????????????
                        flvPlayUrl = jsonRsp.optString("url_play_flv");
                        // HLS  ????????????
                        hlsPlayUrl = jsonRsp.optString("url_play_hls");
                        // RTMP ???????????????
                        realtimePlayUrl = jsonRsp.optString("url_play_acc");
//                        bitmap = createQRCodeBitmap(realtimePlayUrl, 800, 800, "UTF-8", "H", "1", Color.BLACK, Color.WHITE);
                        Log.e("lin", "------- rtmpPushUrl=" + rtmpPushUrl);
                        Log.e("lin", "------- rtmpPlayUrl=" + rtmpPlayUrl);
                        Log.e("lin", "------- flvPlayUrl=" + flvPlayUrl);
                        Log.e("lin", "------- hlsPlayUrl=" + hlsPlayUrl);
                        Log.e("lin", "------- realtimePlayUrl=" + realtimePlayUrl);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    int lastProgress = -1;


    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (message.what == 1) {
                int progress = (int) message.obj;
                if (progress != lastProgress) {
                    seekbar.setProgress(progress);
                    tv_progress.setText("???????????????" + progress + "%");
                    lastProgress = progress;
                }
            } else if (message.what == 2) {
                Toast.makeText(MainActivity.this, "????????????", Toast.LENGTH_SHORT).show();
            } else if (message.what == 3) {
                intiLicence();
            }
            return false;
        }
    });

}