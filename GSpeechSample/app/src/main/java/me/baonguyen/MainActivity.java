package me.baonguyen;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.security.ProviderInstaller;
import com.google.cloud.speech.v1beta1.StreamingRecognizeResponse;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import io.grpc.ManagedChannel;
import me.baonguyen.utils.AudioUtils;

import static me.baonguyen.Constants.PREFS_NAME;
import static me.baonguyen.Constants.SERVER_URL;

public class MainActivity extends AppCompatActivity implements StreamingRecognizeClient.StreamingRecognizeClientListener {

    private static final String HOSTNAME = "speech.googleapis.com";

    private static final int PORT = 443;
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final String LOG_TAG = "Appla Debug";
    private static final int REQUEST_LOGIN = 0;
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
    private long mLastT;
    private Socket mSocket = null;
    private String mUsername;
    private List<Message> mMessages;
    private ArrayAdapter<String> mAdapter;
    private String accessToken;
    private SharedPreferences sharedPreferences;
    private String messageId = "";
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;

    public MainActivity() {
        mRawFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mWavFileName = mRawFileName + "/test-new.wav";
        mRawFileName += "/test.amr";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mDrawerList = (ListView) findViewById(R.id.navList);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        addDrawerItems();
        setupDrawer();

        mBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, AudioFormat
                .CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;

        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                mBufferSize);

//        mAdapter = new MessageAdapter(this, mMessages);
        initialize();

        sharedPreferences = getSharedPreferences(PREFS_NAME, 0);
        accessToken = sharedPreferences.getString("accessToken", "");
        if (accessToken.equals("")) {
            startSignIn();
        } else {
            checkSignIn();
        }
//        Log.i("accessToken", accessToken);

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

        messageId = randomString();

        mPlayRecordingBt = (Button) findViewById(R.id.play_bt);
        mPlayRecordingBt.setOnClickListener(new View.OnClickListener(){
            int count = 0;

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
//                count++;
//                JSONObject message = makeMessage(messageId, "Count is: " + count, accessToken);
//                if (mSocket!=null) {
//                    mSocket.emit("messages/send/incomplete", message);
//                }
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
//                JSONObject message = makeMessage(messageId, "FINAL!", accessToken);
//                if (mSocket!=null) {
//                    mSocket.emit("messages/send/complete", message);
//                }
//                messageId = randomString();
            }
        });

    }

    private void addDrawerItems() {
        String[] menuArray = { "Home", "All Rooms", "Sign Out" };
        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuArray);
        mDrawerList.setAdapter(mAdapter);
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(MainActivity.this, "Position= " + position + " - Id= " +id, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupDrawer() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getSupportActionBar().setTitle(R.string.navigation);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getSupportActionBar().setTitle(getTitle().toString());
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // Activate the navigation drawer toggle
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void checkSignIn() {
        try {
            JsonObject result = Ion.with(getApplicationContext())
                    .load( SERVER_URL + "/api/users/isSignedIn" )
                    .setHeader("x-access-token", accessToken)
                    .asJsonObject().get();
            boolean success = result.get("success").getAsBoolean();
            String message = result.get("message").getAsString();
            if (!success) {
                startSignIn();
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            } else {
                initSocket(accessToken);
                Toast.makeText(getApplicationContext(), "Logged in!", Toast.LENGTH_SHORT).show();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void startSignIn() {
        mUsername = null;
        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }


    private void clearTexts() {
        if (linearLayout.getChildCount() > 0) {
            linearLayout.removeAllViews();
        }
    }

    private void initSocket(String accessToken) {
        saveToken(accessToken);
        AppLa app = (AppLa) getApplication();
        app.initSocket(accessToken);
        mSocket = app.getSocket();
        mSocket.on(Socket.EVENT_CONNECT, onConnect);
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on("chat message", onNewMessage);
        mSocket.connect();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Activity.RESULT_OK != resultCode) {
            this.finish();
            return;
        }

        initSocket(data.getStringExtra("token"));
    }

    private void saveToken(String accessToken) {
        sharedPreferences = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("accessToken", accessToken);
        editor.apply();
    }


    private void stopRecording() {
        mIsRecording = false;
        mAudioRecord.stop();
        if (mStreamingClient != null) {
            mStreamingClient.finish();
        }
        try {
            AudioUtils.rawToWave(new File(mRawFileName), new File(mWavFileName));
        } catch (IOException io){
            io.printStackTrace();
        }
        mRecordingBt.setText(R.string.start_recording);
    }

    private void startRecording() {
        mLastT = System.currentTimeMillis();
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
                    if (System.currentTimeMillis() - mLastT > 55000) {
                        mStreamingClient.finish();
                        mLastT = System.currentTimeMillis();
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
        if (mSocket != null) {
            mSocket.disconnect();
            mSocket.off(Socket.EVENT_CONNECT, onConnect);
            mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        }
    }

    private static String randomString() {
        Random generator = new Random();
        StringBuilder randomStringBuilder = new StringBuilder();
        int length = 8;
        char tempChar;
        for (int i = 0; i < length; i++){
            tempChar = (char) (generator.nextInt(24) + 65);
            randomStringBuilder.append(tempChar);
        }
        return randomStringBuilder.toString();
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
                                String script = response.getResults(0).getAlternatives(0).getTranscript();
                                JSONObject message = makeMessage(messageId, script, accessToken);
                                textOutput.setText(script);

                                mSocket.emit("messages/send/incomplete", message);
                                if (response.getResults(0).getIsFinal()) {
                                    mSocket.emit("messages/send/complete", message);
                                    messageId = randomString();
                                }
                            }
                        }
                        break;
                }
            }


        });
    }

    private JSONObject makeMessage(String messageId, String script, String accessToken) {
        JSONObject message = new JSONObject();
        try {
            message.put("messageId", messageId);
            message.put("message", script);
            message.put("token", accessToken);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return message;
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

    private Emitter.Listener onNewMessage = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
//                    JSONObject data = (JSONObject) args[0];
                    Log.i("args",args.toString());
//                    String message;
//                    try {
//                        message = data.getString("message");
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                        return;
//                    }
                }
            });
        }
    };

    private boolean isConnected = false;

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isConnected) {
//                        Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_LONG).show();
                        isConnected = true;
                    }
                }
            });
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),"Connect Error!", Toast.LENGTH_LONG).show();
                }
            });
        }
    };
}
