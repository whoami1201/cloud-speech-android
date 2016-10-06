package me.yurifariasg;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.security.ProviderInstaller;
import com.google.cloud.speech.v1beta1.StreamingRecognizeResponse;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.grpc.ManagedChannel;

public class MainActivity extends AppCompatActivity implements StreamingRecognizeClient.StreamingRecognizeClientListener {

    private static final String HOSTNAME = "speech.googleapis.com";
    private static final int PORT = 443;
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final String LOG_TAG = "TAG";
    private String mWavFileName;
    private String mRawFileName;

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
    private MediaPlayer mPlayer;
    private TextView textOutput;

    public MainActivity() {
        mRawFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mWavFileName = mRawFileName + "/test-new.wav";
        mRawFileName += "/test.amr";
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
                if (mIsPlaying) {
                    stopPlayingRecord();
                }
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
                if (mIsRecording) {
                    stopRecording();
                }
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
        try {
            rawToWave(new File(mRawFileName), new File(mWavFileName));
        } catch (IOException io){
            io.printStackTrace();
        }
        mRecordingBt.setText(R.string.start_recording);
    }

    private void startRecording() {
        try {
            outputStream = new FileOutputStream(mRawFileName);
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
        mPlayRecordingBt.setText(R.string.stop_playing);

        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mWavFileName);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    private void stopPlayingRecord(){
        mPlayer.release();
        mPlayer = null;
        mPlayRecordingBt.setText(R.string.start_playing);
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
                    mStreamingClient = new StreamingRecognizeClient(channel, RECORDER_SAMPLERATE, MainActivity.this);
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

    private void rawToWave(final File rawFile, final File waveFile) throws IOException {

        byte[] rawData = new byte[(int) rawFile.length()];
        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(rawFile));
            input.read(rawData);
        } finally {
            if (input != null) {
                input.close();
            }
        }

        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new FileOutputStream(waveFile));
            // WAVE header
            // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
            writeString(output, "RIFF"); // chunk id
            writeInt(output, 36 + rawData.length); // chunk size
            writeString(output, "WAVE"); // format
            writeString(output, "fmt "); // subchunk 1 id
            writeInt(output, 16); // subchunk 1 size
            writeShort(output, (short) 1); // audio format (1 = PCM)
            writeShort(output, (short) 1); // number of channels
            writeInt(output, RECORDER_SAMPLERATE); // sample rate
            writeInt(output, RECORDER_SAMPLERATE * 2); // byte rate
            writeShort(output, (short) 2); // block align
            writeShort(output, (short) 16); // bits per sample
            writeString(output, "data"); // subchunk 2 id
            writeInt(output, rawData.length); // subchunk 2 size
            // Audio data (conversion big endian -> little endian)
            short[] shorts = new short[rawData.length / 2];
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
            ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
            for (short s : shorts) {
                bytes.putShort(s);
            }

            output.write(fullyReadFileToBytes(rawFile));
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }
    byte[] fullyReadFileToBytes(File f) throws IOException {
        int size = (int) f.length();
        byte bytes[] = new byte[size];
        byte tmpBuff[] = new byte[size];
        FileInputStream fis= new FileInputStream(f);
        try {

            int read = fis.read(bytes, 0, size);
            if (read < size) {
                int remain = size - read;
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain);
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                    remain -= read;
                }
            }
        }  catch (IOException e){
            throw e;
        } finally {
            fis.close();
        }

        return bytes;
    }
    private void writeInt(final DataOutputStream output, final int value) throws IOException {
        output.write(value >> 0);
        output.write(value >> 8);
        output.write(value >> 16);
        output.write(value >> 24);
    }

    private void writeShort(final DataOutputStream output, final short value) throws IOException {
        output.write(value >> 0);
        output.write(value >> 8);
    }

    private void writeString(final DataOutputStream output, final String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            output.write(value.charAt(i));
        }
    }

    @Override
    public void onUIResponseRefresh(final StreamingRecognizeResponse response, final StreamingRecognizeResponse lastResponse) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                StreamingRecognizeResponse.EndpointerType endPointerType = response.getEndpointerType();
                switch (endPointerType){
                    case START_OF_SPEECH:
                        if (lastResponse!= null
                                && lastResponse.getEndpointerType() != StreamingRecognizeResponse.EndpointerType.START_OF_SPEECH) {
                            if (lastResponse.getResultsCount() > 0) {
                                // If the previous response final
                                if (lastResponse.getResults(0).getIsFinal()) {
                                    textOutput = createNewTextView("");
                                    linearLayout.addView(textOutput);
                                }
                            }
                        } else {
                            textOutput = createNewTextView("");
                            linearLayout.addView(textOutput);
                        }
                        break;
                    case ENDPOINTER_EVENT_UNSPECIFIED:
                        if (response.getResultsCount() > 0){
                            if (response.getResults(0).getAlternativesCount() > 0) {
                                textOutput.setText(response.getResults(0).getAlternatives(0).getTranscript());
                            }
                        }
                        break;
                }
            }
        });
    }

    @Override
    public String onUISpinnerRefresh() {
        Spinner spinner = (Spinner) findViewById(R.id.language_spinner);
        return spinner.getSelectedItem().toString();
    }

    private TextView createNewTextView(String text) {
        final LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        final TextView txt = new TextView(this);
        txt.setLayoutParams(lparams);
        txt.setText(text);
        txt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        txt.setTextColor(getResources().getColor(R.color.colorPrimaryBlack));
        return txt;
    }
}
