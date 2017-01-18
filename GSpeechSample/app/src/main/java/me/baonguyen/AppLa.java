package me.baonguyen;

import android.app.Application;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;


/**
 * Created by bao on 01/11/2016.
 */

public class AppLa extends Application {
    private Socket mSocket = null;

    public void initSocket(String token) {
        {
            try {
                IO.Options options = new IO.Options();
                options.query = "token="+token;
                mSocket = IO.socket(Constants.SERVER_URL, options);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    public Socket getSocket() {
        return mSocket;
    }
}
