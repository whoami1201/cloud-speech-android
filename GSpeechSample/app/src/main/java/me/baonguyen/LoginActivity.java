package me.baonguyen;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.nkzawa.socketio.client.Socket;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import org.json.JSONException;
import org.json.JSONObject;

import static me.baonguyen.Constants.SERVER_URL;

/**
 * Created by bao on 01/11/2016.
 */

public class LoginActivity extends AppCompatActivity {
    private EditText mUsernameView;
    private EditText mPasswordView;

    private String mUsername;
    private String mPassword;

    private Socket mSocket;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Set up the login form.
        mUsernameView = (EditText) findViewById(R.id.username_input);
        mPasswordView = (EditText) findViewById(R.id.password_input);

        Button signInButton = (Button) findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Attempts to sign in the account specified by the login form.
     * If there are form errors (invalid username, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        // Reset errors.
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String username = mUsernameView.getText().toString().trim();
        String password = mPasswordView.getText().toString().trim();

        // Check for a valid username.
        if (TextUtils.isEmpty(username)) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            mUsernameView.setError(getString(R.string.error_field_required));
            mUsernameView.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            mPasswordView.setError(getString(R.string.error_field_required));
            mPasswordView.requestFocus();
            return;
        }

        mUsername = username;
        mPassword = password;

        Ion.with(getApplicationContext())
            .load( SERVER_URL + "/api/login")
            .setBodyParameter("username", mUsername)
            .setBodyParameter("password", mPassword)
            .asString()
            .setCallback(new FutureCallback<String>() {
                @Override
                public void onCompleted(Exception e, String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    boolean success = json.getBoolean("success");
                    if (success){
                        String token = json.getString("token");
                        Intent intent = new Intent();
                        intent.putExtra("token", token);
                        setResult(RESULT_OK, intent);
                        finish();
                    } else {
                        // Result is NOT "OK"
                        String error = json.getString("msg");
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show(); // This will show the user what went wrong with a toast

                    }
                } catch (JSONException err){
                    // This method will run if something goes wrong with the json, like a typo to the json-key or a broken JSON.
                    err.printStackTrace();
                }
                }
            });

    }
}
