package me.lake.librestreaming.sample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import java.util.Timer;
import java.util.TimerTask;

import me.lake.librestreaming.core.listener.RESConnectionListener;
import me.lake.librestreaming.display.SurfaceFactory;
import me.lake.librestreaming.display.surface.MediaCodecSurface;
import me.lake.librestreaming.model.RESCoreParameters;
import me.lake.librestreaming.rtmp.RESRtmpSender;
import me.lake.librestreaming.tools.LogTools;

public class RecordActivity extends AppCompatActivity implements RESConnectionListener{

    private MediaProjectionManager mPrMgr;
    private SurfaceFactory factory;
    private int width = 480;
    private int height = 720;
    private MediaProjection mPr;
    private Button mBtn;
    private int mPrCode;
    private Intent mPrBundle;
    String url = "rtmp://120.55.160.65:1935/live/test";
    RESRtmpSender rtmpSender;
    private Timer timer;
    private TextView mCountText;
    private TextView count_sendframerate;
    EditText et_url;
    long startTime;
    Handler mainHander;
    RESCoreParameters coreParameters;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.record_main);
        LogTools.setEnableLog(true);
        //先请求permission
        int writePm = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int InternetPm = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);
        if (writePm != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        }

        coreParameters = new RESCoreParameters();
        coreParameters.senderQueueLength = 150;
        coreParameters.mediacodecAVCFrameRate = 20;
        coreParameters.videoWidth = 480;
        coreParameters.videoHeight = 720;
        coreParameters.videoBufferQueueNum = 5;
        coreParameters.videoFPS = 20;
        coreParameters.videoGOP = 1;
        coreParameters.mediacodecAVCIFrameInterval = 1;
        coreParameters.mediacdoecAVCBitRate = 1000*1024;
        coreParameters.printDetailMsg = true;

        mBtn = (Button) findViewById(R.id.btn);
        et_url = (EditText) findViewById(R.id.et_url);

        mBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPr == null && mPrCode == 0 && mPrBundle == null) {
                    rtmpSender = new RESRtmpSender();
                    rtmpSender.prepare(coreParameters);
                    rtmpSender.setConnectionListener(RecordActivity.this);
                    if (!TextUtils.isEmpty(et_url.getText().toString())) {
                       url = et_url.getText().toString() ;
                    }
                    rtmpSender.start(url);
                    mainHander.sendEmptyMessage(0);

                    mPrMgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                    Intent screenCaptureIntent = mPrMgr.createScreenCaptureIntent();
                    startActivityForResult(screenCaptureIntent, 1);
                } else {
                    if (mPr != null) {
                        releaseProjection();
                        releaseTimer();
                    }
                }
            }
        });

        factory =  new MediaCodecSurface();
        ((MediaCodecSurface)factory).setParams(coreParameters);

        mCountText = (TextView) findViewById(R.id.count_);
        count_sendframerate = (TextView) findViewById(R.id.count_sendframerate);
        mCountText.setVisibility(View.GONE);

        mainHander = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                count_sendframerate.setText("byteSpeed=" + (rtmpSender.getTotalSpeed() / 1024) + ";sendFPS=" + rtmpSender.getSendFrameRate() + ";sendbufferfreepercent=" + rtmpSender.getSendBufferFreePercent());
                sendEmptyMessageDelayed(0, 3000);
                if (rtmpSender.getSendBufferFreePercent() <= 0.05) {
                    Toast.makeText(RecordActivity.this, "sendbuffer is full,netspeed is low!", Toast.LENGTH_SHORT).show();
                }
            }
        };

    }

    private void startTimer() {
        timer = new Timer();
        startTime= System.currentTimeMillis();
        mCountText.setVisibility(View.VISIBLE);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long curTime = System.currentTimeMillis();
                        long l = (curTime - startTime) / 1000;
                        mCountText.setText("" + l);
                    }
                });
            }
        }, 0, 1000);
    }

    private void releaseTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
            mCountText.setVisibility(View.GONE);
        }
    }

    private void releaseProjection() {
        mPr.stop();
        mPr = null;
        mPrCode = 0;
        mPrBundle = null;
        factory.stop();
        mBtn.setText("start");
        rtmpSender.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission not granted!!", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        this.mPrCode = resultCode;
        this.mPrBundle = data;
        mPr = mPrMgr.getMediaProjection(this.mPrCode, this.mPrBundle);
        if (mPr != null) {
            Surface surface = factory.createSurface(coreParameters.videoWidth, coreParameters.videoHeight);
            if (surface == null) {
                releaseProjection();
                releaseTimer();
                Toast.makeText(this, "Can not create surface", Toast.LENGTH_SHORT).show();
            } else {
                ((MediaCodecSurface)factory).setRtmpSender(rtmpSender);
                startTimer();
                mPr.createVirtualDisplay("display-", width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null);
                mBtn.setText("stop");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        rtmpSender.stop();
        releaseTimer();

    }

    @Override
    public void onOpenConnectionResult(int result) {
        if (result == 0) {
            Log.e("kael", "server IP = " );
        }else {
            Toast.makeText(this, "startfailed", Toast.LENGTH_SHORT).show();
        }
        /**
         * result==0 success
         * result!=0 failed
         */
    }

    @Override
    public void onWriteError(int errno) {

    }

    @Override
    public void onCloseConnectionResult(int result) {

    }
}
