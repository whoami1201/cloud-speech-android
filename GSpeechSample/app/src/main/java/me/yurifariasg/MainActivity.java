package me.yurifariasg;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.security.ProviderInstaller;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.grpc.ManagedChannel;

public class MainActivity extends AppCompatActivity {

    private static final String HOSTNAME = "speech.googleapis.com";
    private static final int PORT = 443;
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final String LOG_TAG = "TAG";
    private String mFileName;

    private AudioRecord mAudioRecord = null;
    private Thread mRecordingThread = null;
    private boolean mIsRecording = false;
    private Button mRecordingBt;
    private StreamingRecognizeClient mStreamingClient;
    private int mBufferSize;
    private ManagedChannel channel;
    private LinearLayout linearLayout;
    private FileOutputStream outputStream;
    private Button mPlayRecordingBt;
    private boolean mIsPlaying = false;

    public MainActivity() {
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/test.amr";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, AudioFormat
                .CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;

        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                mBufferSize);

        initialize();

        Spinner spinner = (Spinner) findViewById(R.id.language_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.languages, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mIsRecording) {
                    stopRecording();
                }
                clearTexts();
                initialize();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.i(this.getClass().getSimpleName(),"Spinner not selected");
            }
        });


        linearLayout = (LinearLayout) findViewById(R.id.linearLayout);
        mRecordingBt = (Button) findViewById(R.id.recording_bt);
        mRecordingBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsRecording) {
                    stopRecording();
                } else {
                    if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        mRecordingBt.setText(R.string.stop_recording);
                        startRecording();
                    } else {
                        Log.i(MainActivity.this.getClass().getSimpleName(), "Not Initialized yet.");
                    }
                }
            }
        });

        mPlayRecordingBt = (Button) findViewById(R.id.play_bt);
        mPlayRecordingBt.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (mIsPlaying) {
                    stopPlayingRecord();
                } else {
                    startPlayingRecord();
                }
                mIsPlaying = !mIsPlaying;
            }
        });

        Button mClearTextBt = (Button) findViewById(R.id.clear_text_bt);
        mClearTextBt.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if (mIsRecording) {
                    stopRecording();
                }
                clearTexts();
            }
        });
    }

    private void clearTexts() {
        if (linearLayout.getChildCount() > 0) {
            linearLayout.removeAllViews();
        }
    }

    private void stopRecording() {
        mIsRecording = false;
        mAudioRecord.stop();
        if (mStreamingClient != null) {
            mStreamingClient.finish();
        }
        mRecordingBt.setText(R.string.start_recording);
    }

    private void startRecording() {
        try {
            outputStream = new FileOutputStream(mFileName);
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        }
        mAudioRecord.startRecording();
        mIsRecording = true;
        mRecordingThread = new Thread(new Runnable() {
            public void run() {
                readData();
                try {
                    outputStream.close();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
        }, "AudioRecorder Thread");
        mRecordingThread.start();

    }

    private void startPlayingRecord(){

        byte[] audioData = null;

        try {
            InputStream inputStream = new FileInputStream(mFileName);

            audioData = new byte[mBufferSize];

            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    RECORDER_SAMPLERATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    RECORDER_AUDIO_ENCODING,
                    mBufferSize,
                    AudioTrack.MODE_STREAM);

            audioTrack.play();
            int i;

            while ((i = inputStream.read(audioData)) != -1 ) {
                audioTrack.write(audioData,0,i);
            }

        } catch(FileNotFoundException fe) {
            Log.e(LOG_TAG,"File not found");
        } catch(IOException io) {
            Log.e(LOG_TAG,"IO Exception");
        }
    }

    private void stopPlayingRecord(){
        Log.i("Clicked:","STOP");
    }

    private void readData() {
        byte sData[] = new byte[mBufferSize];
        while (mIsRecording) {
            int bytesRead = mAudioRecord.read(sData, 0, mBufferSize);
            if (bytesRead > 0) {
                try {
                    if (outputStream != null) {
                        outputStream.write(sData);
                    }
                    mStreamingClient.recognizeBytes(sData, bytesRead);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Log.e(getClass().getSimpleName(), "Error while reading bytes: " + bytesRead);
            }
        }
    }

    private void initialize() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                // Required to support Android 4.x.x (patches for OpenSSL from Google-Play-Services)
                try {
                    ProviderInstaller.installIfNeeded(getApplicationContext());
                } catch (GooglePlayServicesRepairableException e) {

                    // Indicates that Google Play services is out of date, disabled, etc.
                    e.printStackTrace();
                    // Prompt the user to install/update/enable Google Play services.
                    GooglePlayServicesUtil.showErrorNotification(
                            e.getConnectionStatusCode(), getApplicationContext());
                    return;

                } catch (GooglePlayServicesNotAvailableException e) {
                    // Indicates a non-recoverable error; the ProviderInstaller is not able
                    // to install an up-to-date Provider.
                    e.printStackTrace();
                    return;
                }

                try {
                    InputStream credentials = getAssets().open("credentials.json");
                    channel = StreamingRecognizeClient.createChannel(
                            HOSTNAME, PORT, credentials);
                    mStreamingClient = new StreamingRecognizeClient(MainActivity.this, channel, RECORDER_SAMPLERATE);
                } catch (Exception e) {
                    Log.e(MainActivity.class.getSimpleName(), "Error", e);
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mStreamingClient != null) {
            try {
                mStreamingClient.shutdown();
            } catch (InterruptedException e) {
                Log.e(MainActivity.class.getSimpleName(), "Error", e);
            }
        }
    }

}
