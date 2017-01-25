package me.baonguyen;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;


import static me.baonguyen.Constants.PREFS_NAME;
import static me.baonguyen.Constants.SERVER_URL;
import static me.baonguyen.RoomFragment.ACCESS_TOKEN;

public class MainActivity extends AppCompatActivity
    implements RoomFragment.OnListFragmentInteractionListener {

    private static final int REQUEST_LOGIN = 0;
    private static final String FRAGMENT_HOME_MESSAGE_TAG = "fragment_message";
    private static final String FRAGMENT_ROOM_TAG = "fragment_room";
    private static final String FRAGMENT_ROOM_MESSAGE_TAG = "fragment_room_message";

    private ArrayAdapter<String> mAdapter;
    private String accessToken;
    private SharedPreferences sharedPreferences;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private MessageFragment messageFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_main);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mDrawerList = (ListView) findViewById(R.id.navList);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        addDrawerItems();
        setupDrawer();

        sharedPreferences = getSharedPreferences(PREFS_NAME, 0);
        accessToken = sharedPreferences.getString("accessToken", "");
        if (accessToken.equals("")) {
            Log.d("Main Activity onCreate", "accessToken is empty");
            startSignIn();
        } else {
            checkSignIn();
        }


        if (findViewById(R.id.drawer_layout) != null) {
            if (savedInstanceState != null) {
                return;
            }
            Log.d("Main Activity onCreate", "Add messageFragment");
            addMessageFragment();
        }

    }

    private void addMessageFragment() {
        messageFragment = new MessageFragment();
        String firstName = sharedPreferences.getString("firstName", "");
        Bundle bundle = new Bundle();
        bundle.putString("firstName", firstName);
        bundle.putString("roomName", "HOME");
        messageFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.drawer_layout, messageFragment, FRAGMENT_HOME_MESSAGE_TAG)
                .commit();
    }

    private void addDrawerItems() {
        String[] menuArray = { "Home", "Rooms", "Sign Out" };
        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuArray);
        mDrawerList.setAdapter(mAdapter);
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectDrawerItem(position);
            }

            private void selectDrawerItem(int position) {
                Fragment fragment = null;
                Class fragmentClass = null;
                String TAG = "";
                Bundle args = new Bundle();
                switch (position) {
                    case 0:
                        initSocket(accessToken, false);
                        args.putString("firstName", sharedPreferences.getString("firstName",""));
                        args.putString("roomName", "HOME");
                        fragmentClass = MessageFragment.class;
                        TAG = FRAGMENT_HOME_MESSAGE_TAG;
                        break;
                    case 1:
                        initSocket(accessToken, true);
                        args.putString(ACCESS_TOKEN, accessToken);
//                        Fragment homeMessageFragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_HOME_MESSAGE_TAG);
//                        if (homeMessageFragment!=null) {
//                            getSupportFragmentManager().beginTransaction().remove(homeMessageFragment).commit();
//                        }
                        fragmentClass = RoomFragment.class;
                        TAG = FRAGMENT_ROOM_TAG;
                        break;
                    case 2: // Sign Out
                        removeHomeMessageFragment();
                        saveToken("");
                        startSignIn();
                        break;
                }
                try {
                    if (fragmentClass == null)
                        return;
                    fragment = (Fragment) fragmentClass.newInstance();
                    fragment.setArguments(args);

                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.drawer_layout, fragment, TAG);
//                transaction.addToBackStack(null);
                transaction.disallowAddToBackStack();
                // Commit the transaction
                transaction.commit();

                mDrawerLayout.closeDrawers();

            }


        });
    }

    private void removeHomeMessageFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_HOME_MESSAGE_TAG);
        if (fragment!=null) {
            getSupportFragmentManager().beginTransaction().remove(fragment).commit();
        }
    }

    private void setupDrawer() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                mDrawerList.bringToFront();
                mDrawerLayout.requestLayout();
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
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

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
                initSocket(accessToken, false);
                getCurrentUser(accessToken);
                Toast.makeText(getApplicationContext(), "Logged in!", Toast.LENGTH_SHORT).show();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void startSignIn() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void initSocket(String accessToken, boolean isRoom) {
        saveToken(accessToken);
        AppLa app = (AppLa) getApplication();
        app.initSocket(accessToken, isRoom);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            this.finish();
            return;
        }
        accessToken = data.getStringExtra("token");
        initSocket(accessToken, false);
        getCurrentUser(accessToken);

        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_HOME_MESSAGE_TAG);
        if (fragment==null)
            addMessageFragment();
    }

    private void getCurrentUser(String accessToken) {
        try {
            JsonObject result = Ion.with(getApplicationContext())
                    .load( SERVER_URL + "/api/users/getCurrentUser" )
                    .setHeader("x-access-token", accessToken)
                    .asJsonObject().get();

            if (result.get("success").getAsBoolean()) {
                JsonObject userJson = result.getAsJsonObject("user");
                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putString("userId", userJson.get("user_id").getAsString());
                editor.putString("firstName", userJson.get("first_name").getAsString());
                editor.putString("lastName", userJson.get("last_name").getAsString());
                editor.putString("username", userJson.get("username").getAsString());
                editor.apply();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void saveToken(String accessToken) {
        sharedPreferences = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("accessToken", accessToken);
        editor.apply();
    }


    @Override

    protected void onDestroy() {
        super.onDestroy();

    }

    public void onRoomSelected(String roomSlug) {
        AppLa app = (AppLa) getApplication();
        app.initSocket(accessToken, true);
        Socket socket = app.getSocket();
        socket.on("rooms/joined", onRoomJoined);
        socket.connect();

        JSONObject data = new JSONObject();

        try {
            data.put("userId", sharedPreferences.getString("userId", ""));
            data.put("roomSlug", roomSlug);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("rooms/join", data);

    }

    private Emitter.Listener onRoomJoined = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            removeHomeMessageFragment();
            JSONObject data = (JSONObject) args[0];
            String roomName = "";
            try {
                roomName = data.getString("room_name");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            messageFragment = new MessageFragment();

            String firstName = sharedPreferences.getString("firstName", "");

            Bundle bundle = new Bundle();
            bundle.putString("roomName", roomName);
            bundle.putString("firstName", firstName);
            messageFragment.setArguments(bundle);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.drawer_layout, messageFragment, FRAGMENT_ROOM_MESSAGE_TAG);
            transaction.disallowAddToBackStack();
            transaction.commit();

        }
    };

}
