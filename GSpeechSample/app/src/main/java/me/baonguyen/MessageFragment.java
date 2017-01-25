package me.baonguyen;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.test.ActivityTestCase;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.security.ProviderInstaller;
import com.google.cloud.speech.v1beta1.StreamingRecognizeResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.grpc.ManagedChannel;
import me.baonguyen.models.Message;
import me.baonguyen.adapters.MessageAdapter;

import static me.baonguyen.Constants.PREFS_NAME;
import static me.baonguyen.utils.Utils.randomString;


/**
 * Created by bao on 17/01/2017.
 */

public class MessageFragment extends Fragment implements StreamingRecognizeClient.StreamingRecognizeClientListener {

    private static final String HOSTNAME = "speech.googleapis.com";

    private static final int PORT = 443;
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private int mBufferSize;
    private AudioRecord mAudioRecord;
    private RecyclerView.Adapter mAdapter;
    private List<Message> mMessages = new ArrayList<Message>();
    private StreamingRecognizeClient mStreamingClient;
    private ManagedChannel channel;
    private boolean mIsRecording;
    private Button mRecordingBtn;
    private long mLastT;
    private FileOutputStream outputStream;
    private Thread mRecordingThread;
    private boolean mIsPlaying;
    private Socket mSocket;
    private RecyclerView mMessagesView;
    private SharedPreferences sharedPreferences;
    private String accessToken;
    private String messageId;
    private TextView emptyView;
    private TextView roomInfoView;

    public MessageFragment() {
        super();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Activity activity = context instanceof Activity ? (Activity) context : null;
        mAdapter = new MessageAdapter(activity, mMessages);
//        if (mSocket!=null) return;
        initSocket();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        clear();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mIsRecording = false;
        messageId = randomString();

        mBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, AudioFormat
                .CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;

        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                mBufferSize);

        initStreamClient();

        sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, 0);
        accessToken = sharedPreferences.getString("accessToken", "");
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_message, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mRecordingBtn = (Button) view.findViewById(R.id.recording_bt);
        Spinner spinner = (Spinner) view.findViewById(R.id.language_spinner);

        emptyView = (TextView) view.findViewById(R.id.empty_view);
        roomInfoView = (TextView) view.findViewById(R.id.room_text);

        String roomName = getArguments().getString("roomName");
        String firstName = getArguments().getString("firstName");

        roomInfoView.setText("Hi " + firstName + "! You're in room: " + roomName);

        if (mMessages.isEmpty()) {
            mMessagesView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        }
        else {
            mMessagesView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity().getApplicationContext(),
                R.array.languages, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mIsRecording) {
                    stopRecording();
                }
                initStreamClient();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.i(this.getClass().getSimpleName(),"Spinner not selected");
            }
        });


        mRecordingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if (mIsPlaying) {
//                    stopPlayingRecord();
//                }
                if (mIsRecording) {
                    stopRecording();
                } else {
                    if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        mRecordingBtn.setText(R.string.stop_recording);
                        startRecording();

                    } else {
                        Log.i(getActivity().getClass().getSimpleName(), "Not Initialized yet. Please check permissions.");
                    }
                }
            }
        });


    }

    public void initSocket() {
        if (getActivity() == null) return;
        AppLa app = (AppLa) getActivity().getApplication();
        mSocket = app.getSocket();
        if (mSocket!=null) {
            mSocket.on("messages/received", onNewMessage);
            mSocket.connect();
        }

    }

    private void readData() {
        byte sData[] = new byte[mBufferSize];
        while (mIsRecording) {

            if (getActivity() == null) {
                return;
            }

            getActivity().runOnUiThread(new Runnable(){
                @Override
                public void run() {
                    if (mMessagesView.getVisibility() == View.GONE) {
                        if (!mMessages.isEmpty()) {
                            mMessagesView.setVisibility(View.VISIBLE);
                            emptyView.setVisibility(View.GONE);
                        }
                    }
                }
            });


            int bytesRead = mAudioRecord.read(sData, 0, mBufferSize);
            if (bytesRead > 0) {
                try {
//                    if (outputStream != null) {
//                        outputStream.write(sData);
//                    }
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


    private void startRecording() {
        mLastT = System.currentTimeMillis();
//        try {
//            outputStream = new FileOutputStream(mRawFileName);
//        } catch(FileNotFoundException e) {
//            e.printStackTrace();
//        }
        mAudioRecord.startRecording();
        mIsRecording = true;
        mRecordingThread = new Thread(new Runnable() {
            public void run() {
                readData();


//                try {
//                    outputStream.close();
//                } catch (IOException io) {
//                    io.printStackTrace();
//                }
            }
        }, "AudioRecorder Thread");
        mRecordingThread.start();
    }



    private void stopRecording() {
        mIsRecording = false;
        mAudioRecord.stop();
        if (mStreamingClient != null) {
            mStreamingClient.finish();
        }
//        try {
//            AudioUtils.rawToWave(new File(mRawFileName), new File(mWavFileName));
//        } catch (IOException io){
//            io.printStackTrace();
//        }
        mRecordingBtn.setText(R.string.start_recording);
    }

    private void initStreamClient() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                // Required to support Android 4.x.x (patches for OpenSSL from Google-Play-Services)
                try {
                    ProviderInstaller.installIfNeeded(getActivity().getApplicationContext());
                } catch (GooglePlayServicesRepairableException e) {

                    // Indicates that Google Play services is out of date, disabled, etc.
                    e.printStackTrace();
                    // Prompt the user to install/update/enable Google Play services.
                    GooglePlayServicesUtil.showErrorNotification(
                            e.getConnectionStatusCode(), getActivity().getApplicationContext());
                    return;

                } catch (GooglePlayServicesNotAvailableException e) {
                    // Indicates a non-recoverable error; the ProviderInstaller is not able
                    // to install an up-to-date Provider.
                    e.printStackTrace();
                    return;
                }

                try {
                    InputStream credentials = getActivity().getAssets().open("credentials.json");
                    channel = StreamingRecognizeClient.createChannel(
                            HOSTNAME, PORT, credentials);
                    mStreamingClient = new StreamingRecognizeClient(channel, RECORDER_SAMPLERATE, MessageFragment.this);
                } catch (Exception e) {
                    Log.e(MainActivity.class.getSimpleName(), "Error", e);
                }
            }
        }).start();
    }

    @Override
    public void onUIResponseRefresh(final StreamingRecognizeResponse response) {

        if (getActivity() == null) {
            Log.i(getClass().getSimpleName(), "onUIResponseRefresh getActivity() returns null");
            return;
        }

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSocket==null) {
                    Log.i(getClass().getSimpleName(),"mSocket is null. Init socket.");
                    initSocket();
                }
                StreamingRecognizeResponse.EndpointerType endPointerType = response.getEndpointerType();
                switch (endPointerType){
                    case ENDPOINTER_EVENT_UNSPECIFIED:
                        if (response.getResultsCount() > 0){
                            if (response.getResults(0).getAlternativesCount() > 0) {
                                String script = response.getResults(0).getAlternatives(0).getTranscript();
                                JSONObject message = makeMessage(messageId, script, accessToken);

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


    @Override
    public String onUISpinnerRefresh() {
        Spinner spinner = (Spinner) getView().findViewById(R.id.language_spinner);
        return spinner.getSelectedItem().toString();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mSocket!=null) {
            mSocket.disconnect();
            mSocket.off(Socket.EVENT_CONNECT, onConnect);
            mSocket.off("messages/received", onNewMessage);
            mSocket = null;
        }
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

    private void addMessage(String firstName, String lastName, String messageId, int timeStamp, String message) {
        Message newMessage = new Message.Builder()
                .setFirstName(firstName)
                .setLastName(lastName)
                .setMessageId(messageId)
                .timeStamp(timeStamp)
                .setMessage(message).build();
        int index = 0;
        boolean messageFinal = true;
        for (int i = 0; i<mMessages.size(); i++) {
            if (mMessages.get(i).getMessageId().equals(messageId)) {
                messageFinal = false;
                index = i;
            }
        }

        if (messageFinal) {
            mMessages.add(newMessage);
            mAdapter.notifyItemInserted(mMessages.size() - 1);
            scrollToBottom();
        } else {
            mMessages.set(index, newMessage);
            mAdapter.notifyDataSetChanged();
        }
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }


    private Emitter.Listener onNewMessage = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {

            if (getActivity() == null) {
                Log.i(getClass().getSimpleName(), "onNewMessage getActivity() returns null");
                return;
            }

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String firstName;
                    String lastName;
                    String messageId;
                    String message;
                    int timeStamp;
                    try {
                        JSONObject user = data.getJSONObject("owner_info");
                        firstName = user.getString("first_name");
                        lastName = user.getString("last_name");
                        messageId = data.getString("messageId");
                        message = data.getString("message");
                        timeStamp = data.getInt("created_at");
                    } catch (JSONException e) {
                        Log.e("onNewMessage", e.getMessage());
                        return;
                    }

                    addMessage(firstName, lastName, messageId, timeStamp, message);
                }
            });
        }
    };

    private boolean isConnected = false;

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {

        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {

        }
    };

    public void clear() {

        mMessages.clear();
        mAdapter.notifyDataSetChanged();
        if (mSocket!=null) {
            mSocket.disconnect();
            mSocket.off("messages/received", onNewMessage);
            mSocket = null;
        }

    }
}
