package me.baonguyen;

import android.util.Log;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1beta1.RecognitionConfig;
import com.google.cloud.speech.v1beta1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1beta1.SpeechGrpc;
import com.google.cloud.speech.v1beta1.StreamingRecognitionConfig;
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
    private final StreamingRecognizeClientListener listener;

    private StreamingRecognizeResponse lastResponse;

    private boolean mIsInitialized = false;

    private static final List<String> OAUTH2_SCOPES =
            Arrays.asList("https://www.googleapis.com/auth/cloud-platform");


    /**
     * Construct client connecting to Cloud Speech server at {@code host:port}.
     */
    public StreamingRecognizeClient(ManagedChannel channel, int samplingRate, StreamingRecognizeClientListener listener)
            throws IOException {
        this.mSamplingRate = samplingRate;
        this.mChannel = channel;
        this.listener = listener;

        mSpeechClient = SpeechGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        mChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }


    StreamObserver<StreamingRecognizeRequest> requestObserver;

    private void initializeRecognition() throws InterruptedException, IOException {
        requestObserver = mSpeechClient.streamingRecognize(this);
        String languageCode = "en";

        if (listener!=null) {
            languageCode = listener.onUISpinnerRefresh();
        }

        RecognitionConfig config =
                RecognitionConfig.newBuilder()
                        .setEncoding(AudioEncoding.LINEAR16)
                        .setSampleRate(mSamplingRate)
                        .setLanguageCode(languageCode)
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
        Log.i(getClass().getSimpleName(), "Received response: " + TextFormat.printToString(response));
        if(listener!=null) {
            listener.onUIResponseRefresh(response);
        }
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
        if (requestObserver!=null) {
            requestObserver.onCompleted();
        }
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

 public interface StreamingRecognizeClientListener {
     void onUIResponseRefresh(StreamingRecognizeResponse response);
     String onUISpinnerRefresh();
 }

}