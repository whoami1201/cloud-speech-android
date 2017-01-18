package me.baonguyen;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;

import java.util.concurrent.ExecutionException;

import static me.baonguyen.Constants.PREFS_NAME;
import static me.baonguyen.Constants.SERVER_URL;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_LOGIN = 0;

    private ArrayAdapter<String> mAdapter;
    private String accessToken;
    private SharedPreferences sharedPreferences;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;


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
            startSignIn();
        } else {
            checkSignIn();
        }

    }

    private void addDrawerItems() {
        String[] menuArray = { "Home", "All Rooms", "Log Out" };
        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuArray);
        mDrawerList.setAdapter(mAdapter);
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectDrawerItem(position);
            }

            private void selectDrawerItem(int position) {
                switch (position) {
                    case 2: // Sign Out
                        saveToken("");
                        startSignIn();
                }
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
        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void initSocket(String accessToken) {
        saveToken(accessToken);
        AppLa app = (AppLa) getApplication();
        app.initSocket(accessToken);
        ChatFragment fragment = (ChatFragment) getFragmentManager().findFragmentById(R.id.fragment_chat);
        fragment.initSocket();
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


    @Override
    protected void onDestroy() {
        super.onDestroy();

    }



}
