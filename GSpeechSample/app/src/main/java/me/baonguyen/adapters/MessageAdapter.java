package me.baonguyen.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import me.baonguyen.R;
import me.baonguyen.models.Message;

import static me.baonguyen.utils.Utils.getTimeFromTimeStamp;

/**
 * Created by bao on 01/11/2016.
 */

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private List<Message> mMessages;
    private int[] mNameColors;

    public MessageAdapter(Context context, List<Message> messages) {
        mMessages = messages;
        mNameColors = context.getResources().getIntArray(R.array.name_colors);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layout = R.layout.item_message;
        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(layout, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        Message message = mMessages.get(position);
        viewHolder.setMessage(message.getMessage());
        viewHolder.setFullName(message.getFullName());
        viewHolder.setTimeStamp(message.getTimeStamp());

    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mFullNameView;
        private TextView mMessageView;
        private TextView mTimeStampView;

        private ViewHolder(View itemView) {
            super(itemView);

            mFullNameView = (TextView) itemView.findViewById(R.id.fullName);
            mMessageView = (TextView) itemView.findViewById(R.id.message);
            mTimeStampView = (TextView) itemView.findViewById(R.id.timeStamp);
        }

        private void setFullName(String fullName) {
            if (null == mFullNameView) return;
            mFullNameView.setText(fullName);
            mFullNameView.setTextColor(getUsernameColor(fullName));
        }

        public void setMessage(String message) {
            if (null == mMessageView) return;
            mMessageView.setText(message);
        }

        private void setTimeStamp(int timeStamp) {
            if (null == mTimeStampView) return;

            String dateString = getTimeFromTimeStamp(timeStamp);
            mTimeStampView.setText(dateString);
        }

        private int getUsernameColor(String username) {
            int hash = 7;
            for (int i = 0, len = username.length(); i < len; i++) {
                hash = username.codePointAt(i) + (hash << 5) - hash;
            }
            int index = Math.abs(hash % mNameColors.length);
            return mNameColors[index];
        }
    }
}
