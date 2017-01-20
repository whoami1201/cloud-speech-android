package me.baonguyen;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


import me.baonguyen.models.Room;
import me.baonguyen.adapters.RoomAdapter;

import static me.baonguyen.Constants.SERVER_URL;


/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class RoomFragment extends Fragment {

    private RecyclerView.Adapter mAdapter;
    private List<Room> mRooms = new ArrayList<>();

    // TODO: Customize parameter argument names
    public static final String ACCESS_TOKEN = "accessToken";
    // TODO: Customize parameters
    private OnListFragmentInteractionListener mListener;
    private String mAccessToken;
    private RecyclerView mRoomView;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public RoomFragment() {
    }

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static RoomFragment newInstance(String accessToken) {
        RoomFragment fragment = new RoomFragment();
        Bundle args = new Bundle();
        args.putString(ACCESS_TOKEN, accessToken);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mAccessToken = getArguments().getString(ACCESS_TOKEN);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_room_list, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            mRoomView = (RecyclerView) view;
            mRoomView.setLayoutManager(new LinearLayoutManager(context));
            try {
                JsonObject roomsResult = Ion.with(getContext())
                        .load( SERVER_URL + "/api/rooms" )
                        .setHeader("x-access-token", mAccessToken)
                        .asJsonObject().get();
                boolean success = roomsResult.get("success").getAsBoolean();

                if (success) {
                    JsonArray rooms = roomsResult.get("rooms").getAsJsonArray();
                    for (int i = 0; i < rooms.size(); i++) {
                        JsonObject room = rooms.get(i).getAsJsonObject();
                        String roomName = room.get("room_name").getAsString();
                        String roomDescription = room.get("description").getAsString();
                        String roomSlug = room.get("slug").getAsString();
                        int timeStamp = room.get("created_at").getAsInt();
                        Room newRoom = new Room.Builder()
                                .setRoomName(roomName)
                                .setDescription(roomDescription)
                                .setSlug(roomSlug)
                                .setTimeStamp(timeStamp)
                                .build();
                        mRooms.add(newRoom);
                    }
                }
                mAdapter = new RoomAdapter(mRooms, mListener);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            mRoomView.setAdapter(mAdapter);
        }
        return view;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        mRooms.clear();
        mAdapter.notifyDataSetChanged();
    }

    public interface OnListFragmentInteractionListener {
        void onRoomSelected(String roomSlug);
    }
}
