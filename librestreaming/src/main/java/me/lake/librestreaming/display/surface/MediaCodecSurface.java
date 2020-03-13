package me.lake.librestreaming.display.surface;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import me.lake.librestreaming.core.MediaCodecHelper;
import me.lake.librestreaming.core.VideoSenderThread;
import me.lake.librestreaming.display.SurfaceFactory;
import me.lake.librestreaming.model.RESCoreParameters;
import me.lake.librestreaming.rtmp.RESFlvData;
import me.lake.librestreaming.rtmp.RESFlvDataCollecter;
import me.lake.librestreaming.rtmp.RESRtmpSender;

public class MediaCodecSurface implements SurfaceFactory {

    private Surface mInputSurface;
    private MediaCodec mEncoder;
    private Handler workHanlder;
    VideoSenderThread videoSenderThread;

    RESRtmpSender rtmpSender;
    RESCoreParameters parameters;
    public MediaCodecSurface() {
        createEncoderThread();
    }

    private void createEncoderThread() {
        HandlerThread encoder = new HandlerThread("Encoder");
        encoder.start();
        Looper looper = encoder.getLooper();
        workHanlder = new Handler(looper);
    }

    public void setRtmpSender(RESRtmpSender rtmpSender){
        this.rtmpSender = rtmpSender;
    }

    public void setParams(RESCoreParameters params){
        this.parameters = params;
    }




    @Override
    public Surface createSurface(int width, int height) {
        try {
            MediaFormat mediaFormat = new MediaFormat();
            mEncoder = MediaCodecHelper.createHardVideoMediaCodec(parameters,mediaFormat);
            mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mEncoder.createInputSurface();
            mEncoder.start();
            RESFlvDataCollecter collecter = new RESFlvDataCollecter() {
                @Override
                public void collect(RESFlvData flvData, int type) {
                    rtmpSender.feed(flvData,type);
                }
            };

            videoSenderThread = new VideoSenderThread("VideoSenderThread", mEncoder, collecter);
            videoSenderThread.start();
            return mInputSurface;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void stop() {
        videoSenderThread.quit();
        workHanlder.post(new Runnable() {
            @Override
            public void run() {
                mEncoder.signalEndOfInputStream();
                mEncoder.stop();
            }
        });

    }

}
