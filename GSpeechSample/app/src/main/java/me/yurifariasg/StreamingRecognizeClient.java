package me.yurifariasg;

import android.app.Activity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1beta1.RecognitionConfig;
import com.google.cloud.speech.v1beta1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1beta1.SpeechGrpc;
import com.google.cloud.speech.v1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1beta1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1beta1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.okhttp.OkHttpChannelProvider;
import io.grpc.stub.StreamObserver;

/**
 * Client that sends streaming audio to Speech.Recognize and returns streaming transcript.
 */
public class StreamingRecognizeClient implements StreamObserver<StreamingRecognizeResponse>  {

    private final int mSamplingRate;

    private final ManagedChannel mChannel;

    private final SpeechGrpc.SpeechStub mSpeechClient;
    private final Activity mActivity;
    private final LinearLayout linearLayout;

    private boolean mIsInitialized = false;

    private static final List<String> OAUTH2_SCOPES =
            Arrays.asList("https://www.googleapis.com/auth/cloud-platform");
    private StreamingRecognizeResponse.EndpointerType lastEndPointer;


    /**
     * Construct client connecting to Cloud Speech server at {@code host:port}.
     */
    public StreamingRecognizeClient(Activity activity, ManagedChannel channel, int samplingRate)
            throws IOException {
        this.mSamplingRate = samplingRate;
        this.mChannel = channel;
        this.mActivity = activity;
        this.linearLayout = (LinearLayout) mActivity.findViewById(R.id.linearLayout);


        mSpeechClient = SpeechGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        mChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }


    StreamObserver<StreamingRecognizeRequest> requestObserver;

    private void initializeRecognition() throws InterruptedException, IOException {
        requestObserver = mSpeechClient.streamingRecognize(this);
        RecognitionConfig config =
                RecognitionConfig.newBuilder()
                        .setEncoding(AudioEncoding.LINEAR16)
                        .setSampleRate(mSamplingRate)
                        .build();
        StreamingRecognitionConfig streamingConfig =
                StreamingRecognitionConfig.newBuilder()
                        .setConfig(config)
                        .setInterimResults(true)
                        .setSingleUtterance(false)
                        .build();

        StreamingRecognizeRequest initial =
                StreamingRecognizeRequest.newBuilder().setStreamingConfig(streamingConfig).build();
        requestObserver.onNext(initial);
    }

    @Override
    public void onNext(final StreamingRecognizeResponse response) {
        mActivity.runOnUiThread(new Runnable() {
            String stringResponse = "";
            @Override
            public void run() {
                Log.i("-----------------","-------------------");
//                if (response.getResultsCount() > 0){
//                    if (response.getResults(0).getAlternativesCount() > 0) {
//                        stringResponse = ;
////                        TextView txtOutput = (TextView) mActivity.findViewById(R.id.txtOutput);
////                        txtOutput.setText(response.getResults(0).getAlternatives(0).getTranscript());
//                    }
//                }

                StreamingRecognizeResponse.EndpointerType endPointerType = response.getEndpointerType();

                Log.i("endPointerType", response.getEndpointerType().name());
                Log.i("lastEndPointer is null","" + (lastEndPointer == null));
                if (lastEndPointer != null) {
                    Log.i("lastEndPointer", lastEndPointer.name());
                }
                Log.i("stringResponse", stringResponse);

                if (endPointerType.equals(StreamingRecognizeResponse.EndpointerType.ENDPOINTER_EVENT_UNSPECIFIED)) {
                    Log.i(getClass().getSimpleName(), "Received response: " + TextFormat.printToString(response));
                    Log.i("Result Count:", "" + response.getResultsCount());
                    if (response.getResultsCount() > 0){
                        if (response.getResults(0).getAlternativesCount() > 0) {
                            if (response.getResults(0).getIsFinal()) {
                                linearLayout.addView(createNewTextView(response.getResults(0).getAlternatives(0).getTranscript()));
                            }
//                            TextView txtOutput = (TextView) mActivity.findViewById(R.id.txtOutput);
//                            txtOutput.setText(response.getResults(0).getAlternatives(0).getTranscript());
                        }
                    }
                }

                lastEndPointer = endPointerType;
            }
        });
    }

    private TextView createNewTextView(String text) {
        Log.i("createNewTextView",text);
        final LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        final TextView textView = new TextView(mActivity);
        textView.setLayoutParams(lparams);
        textView.setText(text);
        return textView;
    }

    @Override
    public void onError(Throwable error) {
        Status status = Status.fromThrowable(error);
        Log.w(getClass().getSimpleName(), "recognize failed: {0}: " + status);
        Log.e(StreamingRecognizeClient.this.getClass().getSimpleName(), "Error to" +
                " Recognize.", error);
    }

    @Override
    public void onCompleted() {
        Log.i(getClass().getSimpleName(), "recognize completed.");
    }

    public void recognizeBytes(byte[] audioBytes, int size) throws IOException,
            InterruptedException {
        if (!mIsInitialized) {
            initializeRecognition();
            mIsInitialized = true;
        }
        try {
            StreamingRecognizeRequest request =
                    StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(ByteString.copyFrom(audioBytes, 0, size))
                            .build();
            requestObserver.onNext(request);
        } catch (RuntimeException e) {
            Log.e(StreamingRecognizeClient.this.getClass().getSimpleName(), "Error stopping.", e);
            requestObserver.onError(e);
            throw e;
        }
    }

    public void finish() {
        Log.i(StreamingRecognizeClient.this.getClass().getSimpleName(), "onComplete.");
        requestObserver.onCompleted();
        mIsInitialized = false;
    }

    public static ManagedChannel createChannel(String host, int port, InputStream credentials)
            throws IOException {
        GoogleCredentials creds = GoogleCredentials.fromStream(credentials);
        creds = creds.createScoped(OAUTH2_SCOPES);
        OkHttpChannelProvider provider = new OkHttpChannelProvider();
        OkHttpChannelBuilder builder = provider.builderForAddress(host, port);
        ManagedChannel channel =  builder.intercept(new ClientAuthInterceptor(creds, Executors
                .newSingleThreadExecutor
                ()))
                .build();

        credentials.close();
        return channel;
    }


}