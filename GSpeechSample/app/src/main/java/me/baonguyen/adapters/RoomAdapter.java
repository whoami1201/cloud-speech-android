package me.baonguyen.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import me.baonguyen.R;
import me.baonguyen.RoomFragment.OnListFragmentInteractionListener;
import me.baonguyen.models.Room;

import java.util.List;

import static me.baonguyen.utils.Utils.getDateFromTimeStamp;


public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.ViewHolder> {

    private final List<Room> mRooms;
    private final OnListFragmentInteractionListener mListener;

    public RoomAdapter(List<Room> rooms, OnListFragmentInteractionListener listener) {
        mRooms = rooms;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final Room room = mRooms.get(position);
        holder.setRoomName(room.getRoomName());
        holder.setDescription(room.getDescription());
        holder.setTimeStamp(room.getTimeStamp());

        holder.mRoomContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    mListener.onRoomSelected(room.getSlug());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mRooms.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mRoomNameView;
        private TextView mDescriptionView;
        private TextView mTimeStampView;
        private LinearLayout mRoomContainer;

        public ViewHolder(View itemView) {
            super(itemView);
            mRoomNameView = (TextView) itemView.findViewById(R.id.room_name);
            mDescriptionView = (TextView) itemView.findViewById(R.id.description);
            mTimeStampView = (TextView) itemView.findViewById(R.id.room_time_stamp);
            mRoomContainer = (LinearLayout) itemView.findViewById(R.id.item_room_container);
        }

        private void setRoomName(String fullName) {
            if (null == mRoomNameView) return;
            mRoomNameView.setText(fullName);
        }

        public void setDescription(String message) {
            if (null == mDescriptionView) return;
            mDescriptionView.setText(message);
        }

        private void setTimeStamp(int timeStamp) {
            if (null == mTimeStampView) return;
            String dateString = getDateFromTimeStamp(timeStamp);
            mTimeStampView.setText(dateString);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mRoomNameView.getText() + "'";
        }
    }
}
