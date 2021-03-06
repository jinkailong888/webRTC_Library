//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.webrtc.voiceengine;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioAttributes.Builder;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

import org.webrtc.Logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class WebRtcAudioTrack {
    private static final boolean DEBUG = false;
    private static final String TAG = "WebRtcAudioTrack";
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;
    private static final int BUFFERS_PER_SECOND = 100;
    private final Context context;
    private final long nativeAudioTrack;
    private final AudioManager audioManager;
    private ByteBuffer byteBuffer;
    private AudioTrack audioTrack = null;
    private WebRtcAudioTrack.AudioTrackThread audioThread = null;
    private static volatile boolean speakerMute = false;
    private byte[] emptyBytes;

    WebRtcAudioTrack(Context context, long nativeAudioTrack) {
        Logging.d("WebRtcAudioTrack", "ctor" + WebRtcAudioUtils.getThreadInfo());
        this.context = context;
        this.nativeAudioTrack = nativeAudioTrack;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    private boolean initPlayout(int sampleRate, int channels) {
        Logging.d("WebRtcAudioTrack", "initPlayout(sampleRate=" + sampleRate + ", channels=" + channels + ")");
        int bytesPerFrame = channels * 2;
        ByteBuffer var10001 = this.byteBuffer;
        this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (sampleRate / 100));
        Logging.d("WebRtcAudioTrack", "byteBuffer.capacity: " + this.byteBuffer.capacity());
        this.emptyBytes = new byte[this.byteBuffer.capacity()];
        this.nativeCacheDirectBufferAddress(this.byteBuffer, this.nativeAudioTrack);
        int channelConfig = this.channelCountToConfiguration(channels);
        int minBufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRate, channelConfig, 2);
        Logging.d("WebRtcAudioTrack", "AudioTrack.getMinBufferSize: " + minBufferSizeInBytes);
        if (minBufferSizeInBytes < this.byteBuffer.capacity()) {
            Logging.e("WebRtcAudioTrack", "AudioTrack.getMinBufferSize returns an invalid value.");
            return false;
        } else if (this.audioTrack != null) {
            Logging.e("WebRtcAudioTrack", "Conflict with existing AudioTrack.");
            return false;
        } else {
            try {
                if (WebRtcAudioUtils.runningOnLollipopOrHigher()) {
                    this.audioTrack = this.createAudioTrackOnLollipopOrHigher(sampleRate, channelConfig, minBufferSizeInBytes);
                } else {
                    this.audioTrack = new AudioTrack(0, sampleRate, channelConfig, 2, minBufferSizeInBytes, 1);
                }
            } catch (IllegalArgumentException var7) {
                Logging.d("WebRtcAudioTrack", var7.getMessage());
                this.releaseAudioResources();
                return false;
            }

            if (this.audioTrack != null && this.audioTrack.getState() == 1) {
                this.logMainParameters();
                this.logMainParametersExtended();
                return true;
            } else {
                Logging.e("WebRtcAudioTrack", "Initialization of audio track failed.");
                this.releaseAudioResources();
                return false;
            }
        }
    }

    private boolean startPlayout() {
        Logging.d("WebRtcAudioTrack", "startPlayout");
        assertTrue(this.audioTrack != null);
        assertTrue(this.audioThread == null);
        if (this.audioTrack.getState() != 1) {
            Logging.e("WebRtcAudioTrack", "AudioTrack instance is not successfully initialized.");
            return false;
        } else {
            this.audioThread = new WebRtcAudioTrack.AudioTrackThread("AudioTrackJavaThread");
            this.audioThread.start();
            return true;
        }
    }

    private boolean stopPlayout() {
        Logging.d("WebRtcAudioTrack", "stopPlayout");
        assertTrue(this.audioThread != null);
        this.logUnderrunCount();
        this.audioThread.joinThread();
        this.audioThread = null;
        this.releaseAudioResources();
        return true;
    }

    private int getStreamMaxVolume() {
        Logging.d("WebRtcAudioTrack", "getStreamMaxVolume");
        assertTrue(this.audioManager != null);
        return this.audioManager.getStreamMaxVolume(0);
    }

    private boolean setStreamVolume(int volume) {
        Logging.d("WebRtcAudioTrack", "setStreamVolume(" + volume + ")");
        assertTrue(this.audioManager != null);
        if (this.isVolumeFixed()) {
            Logging.e("WebRtcAudioTrack", "The device implements a fixed volume policy.");
            return false;
        } else {
            this.audioManager.setStreamVolume(0, volume, 0);
            return true;
        }
    }

    private boolean isVolumeFixed() {
        return !WebRtcAudioUtils.runningOnLollipopOrHigher() ? false : this.audioManager.isVolumeFixed();
    }

    private int getStreamVolume() {
        Logging.d("WebRtcAudioTrack", "getStreamVolume");
        assertTrue(this.audioManager != null);
        return this.audioManager.getStreamVolume(0);
    }

    private void logMainParameters() {
        StringBuilder var10001 = (new StringBuilder()).append("AudioTrack: session ID: ").append(this.audioTrack.getAudioSessionId()).append(", channels: ").append(this.audioTrack.getChannelCount()).append(", sample rate: ").append(this.audioTrack.getSampleRate()).append(", max gain: ");
        AudioTrack var10002 = this.audioTrack;
        Logging.d("WebRtcAudioTrack", var10001.append(AudioTrack.getMaxVolume()).toString());
    }

    @TargetApi(21)
    private AudioTrack createAudioTrackOnLollipopOrHigher(int sampleRateInHz, int channelConfig, int bufferSizeInBytes) {
        Logging.d("WebRtcAudioTrack", "createAudioTrackOnLollipopOrHigher");
        int nativeOutputSampleRate = AudioTrack.getNativeOutputSampleRate(0);
        Logging.d("WebRtcAudioTrack", "nativeOutputSampleRate: " + nativeOutputSampleRate);
        if (sampleRateInHz != nativeOutputSampleRate) {
            Logging.w("WebRtcAudioTrack", "Unable to use fast mode since requested sample rate is not native");
        }

        return new AudioTrack((new Builder()).setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(), (new android.media.AudioFormat.Builder()).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRateInHz).setChannelMask(channelConfig).build(), bufferSizeInBytes, 1, 0);
    }

    @TargetApi(24)
    private void logMainParametersExtended() {
        if (WebRtcAudioUtils.runningOnMarshmallowOrHigher()) {
            //      Logging.d("WebRtcAudioTrack", "AudioTrack: buffer size in frames: " + this.audioTrack.getBufferSizeInFrames());
        }

        if (WebRtcAudioUtils.runningOnNougatOrHigher()) {
            Logging.d("WebRtcAudioTrack", "AudioTrack: buffer capacity in frames: "
                    //              + this.audioTrack.getBufferCapacityInFrames()
            );
        }

    }

    @TargetApi(24)
    private void logUnderrunCount() {
        if (WebRtcAudioUtils.runningOnNougatOrHigher()) {
            Logging.d("WebRtcAudioTrack", "underrun count: "
                    //              + this.audioTrack.getUnderrunCount()
            );
        }

    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private int channelCountToConfiguration(int channels) {
        return channels == 1 ? 4 : 12;
    }

    private native void nativeCacheDirectBufferAddress(ByteBuffer var1, long var2);

    private native void nativeGetPlayoutData(int var1, long var2);

    public static void setSpeakerMute(boolean mute) {
        Logging.w("WebRtcAudioTrack", "setSpeakerMute(" + mute + ")");
        speakerMute = mute;
    }

    private void releaseAudioResources() {
        if (this.audioTrack != null) {
            this.audioTrack.release();
            this.audioTrack = null;
        }

    }

    private class AudioTrackThread extends Thread {
        private volatile boolean keepAlive = true;

        public AudioTrackThread(String name){
            super(name);
        }

        public void run() {
            Process.setThreadPriority(-19);
            Logging.d("WebRtcAudioTrack", "AudioTrackThread" + WebRtcAudioUtils.getThreadInfo());

            try {
                WebRtcAudioTrack.this.audioTrack.play();
                WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 3);
            } catch (IllegalStateException var4) {
                Logging.e("WebRtcAudioTrack", "AudioTrack.play failed: " + var4.getMessage());
                WebRtcAudioTrack.this.releaseAudioResources();
                return;
            }

            for (int sizeInBytes = WebRtcAudioTrack.this.byteBuffer.capacity(); this.keepAlive; WebRtcAudioTrack.this.byteBuffer.rewind()) {
                WebRtcAudioTrack.this.nativeGetPlayoutData(sizeInBytes, WebRtcAudioTrack.this.nativeAudioTrack);
                WebRtcAudioTrack.assertTrue(sizeInBytes <= WebRtcAudioTrack.this.byteBuffer.remaining());
                if (WebRtcAudioTrack.speakerMute) {
                    WebRtcAudioTrack.this.byteBuffer.clear();
                    WebRtcAudioTrack.this.byteBuffer.put(WebRtcAudioTrack.this.emptyBytes);
                    WebRtcAudioTrack.this.byteBuffer.position(0);
                }

                boolean e = false;
                int e1;
                if (WebRtcAudioUtils.runningOnLollipopOrHigher()) {
                    e1 = this.writeOnLollipop(WebRtcAudioTrack.this.audioTrack, WebRtcAudioTrack.this.byteBuffer, sizeInBytes);
                } else {
                    e1 = this.writePreLollipop(WebRtcAudioTrack.this.audioTrack, WebRtcAudioTrack.this.byteBuffer, sizeInBytes);
                }

                if (e1 != sizeInBytes) {
                    Logging.e("WebRtcAudioTrack", "AudioTrack.write failed: " + e1);
                    if (e1 == -3) {
                        this.keepAlive = false;
                    }
                }
            }

            try {
                WebRtcAudioTrack.this.audioTrack.stop();
            } catch (IllegalStateException var3) {
                Logging.e("WebRtcAudioTrack", "AudioTrack.stop failed: " + var3.getMessage());
            }

            WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 1);
            WebRtcAudioTrack.this.audioTrack.flush();
        }

        @TargetApi(21)
        private int writeOnLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
            return audioTrack.write(byteBuffer, sizeInBytes, AudioTrack.WRITE_BLOCKING);
        }

        private int writePreLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
            //save_pcm(byteBuffer.array());//音频数据
            return audioTrack.write(byteBuffer.array(), byteBuffer.arrayOffset(), sizeInBytes);
        }

        File temp_pcm = null;
        private void save_pcm(byte[] mp3SoundByteArray) {
            try {
                if(temp_pcm == null) {
                    temp_pcm = File.createTempFile("china", ".pcm", Environment.getExternalStorageDirectory());
                }
                FileOutputStream fos = new FileOutputStream(temp_pcm,true);
                fos.write(mp3SoundByteArray);
            } catch (IOException ex) {
                String s = ex.toString();
                ex.printStackTrace();
                Log.e("111","写入引起的异常");
            }
        }

        public void joinThread() {
            this.keepAlive = false;

            while (this.isAlive()) {
                try {
                    this.join();
                } catch (InterruptedException var2) {
                    ;
                }
            }

        }
    }
}
