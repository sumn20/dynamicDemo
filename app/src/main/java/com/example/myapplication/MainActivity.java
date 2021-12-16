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
     * 初始化view
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
     * 下载so文件到指定目录
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

    String url = "http://xxx.com/so_files.zip";//so文件下载地址
    String FILE_NAME = "so_files.zip";//保存的文件名

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_1://推流
                startPusher();
                break;
            case R.id.btn_2://下载so
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
            case R.id.btn_3://解压so
                try {
                    readByApacheZipFile(new File(getPath(), FILE_NAME).getPath(), getPath());
                } catch (IOException e) {
                    e.printStackTrace();
                    tv_progress.setText("解压失败");
                    Log.e("lin", "++++++++++ e=" + e.toString());
                }
                break;
            case R.id.btn_4:

                break;
            case R.id.btn_5://设置setLibraryPath
                String s = getPath() + "/so_files";//这里拼接 FILE_NAME
                Log.e("lin", "--------  s=" + s);
                TXLiveBase.setLibraryPath(s);//加载so
                handler.sendEmptyMessageDelayed(3, 5000);//5秒后调用 intiLicence() setLicence
                break;
            default:
        }
    }

    /**
     * 初始化licence
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
     * 　　* 使用 org.apache.tools.zip.ZipFile 解压文件，它与 java 类库中的
     * 　　* java.util.zip.ZipFile 使用方式是一新的，只不过多了设置编码方式的
     * 　　* 接口。
     * 　　*
     * 　　* 注，apache 没有提供 ZipInputStream 类，所以只能使用它提供的ZipFile
     * 　　* 来读取压缩文件。
     * 　　* @param archive 压缩包路径
     * 　　* @param decompressDir 解压路径
     * 　　* @throws IOException
     * 　　* @throws FileNotFoundException
     * 　　* @throws ZipException
     */
    public void readByApacheZipFile(String archive, String decompressDir) throws IOException, FileNotFoundException, ZipException {
        BufferedInputStream bi;
        ZipFile zf = new ZipFile(archive);//支持中文
        Enumeration e = zf.entries();
        File newFile = new File(decompressDir);
        if (!newFile.exists()) {
            if (!newFile.mkdirs()) {
                Toast.makeText(this, "无法创建路径", Toast.LENGTH_SHORT).show();
            }
        }
        while (e.hasMoreElements()) {
            ZipEntry ze2 = (ZipEntry) e.nextElement();
            String entryName = ze2.getName();
            String path = decompressDir + "/" + entryName;
            if (ze2.isDirectory()) {
                System.out.println("正在创建解压目录 - " + entryName);
                File decompressDirFile = new File(path);
                if (!decompressDirFile.exists()) {
                    decompressDirFile.mkdirs();
                }
            } else {
                System.out.println("正在创建解压文件 - " + entryName);
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
        tv_progress.setText("解压完成");
        zf.close();
    }


    /**
     * 推流
     */
    private void startPusher() {
        txLivePusher = new TXLivePusher(this);
        TXLivePushConfig config = new TXLivePushConfig();
        txLivePusher.startCameraPreview(videoView);
        txLivePusher.setConfig(config);
        int i = txLivePusher.startPusher(rtmpPushUrl);
        Log.e("lin", "--------  推流状态=" + i);
        txLivePusher.setPushListener(new ITXLivePushListener() {
            @Override
            public void onPushEvent(int i, Bundle bundle) {
                if (i == 1002) {
                    tv_progress.setText("推流成功");
                    Toast.makeText(MainActivity.this, "推流成功", Toast.LENGTH_SHORT).show();
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
     * 获取多个权限
     *
     * @param permissions 获取权限的数组
     */
    private void requestPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(this, permissions, 1);
    }

    /**
     * 获取推拉流地址（demo测试使用）
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
                        Toast.makeText(MainActivity.this, "获取推流地址失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject jsonRsp;
                    try {
                        jsonRsp = new JSONObject(response.body().string());
                        rtmpPushUrl = jsonRsp.optString("url_push");            // RTMP 推流地址
                        rtmpPlayUrl = jsonRsp.optString("url_play_rtmp");   // RTMP 播放地址
                        // FLA  播放地址
                        flvPlayUrl = jsonRsp.optString("url_play_flv");
                        // HLS  播放地址
                        hlsPlayUrl = jsonRsp.optString("url_play_hls");
                        // RTMP 加速流地址
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
                    tv_progress.setText("下载进度：" + progress + "%");
                    lastProgress = progress;
                }
            } else if (message.what == 2) {
                Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
            } else if (message.what == 3) {
                intiLicence();
            }
            return false;
        }
    });

}