package com.yelao.streamtv;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.List;

final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelHolder> {
    interface Listener {
        void onPlay(Channel channel);
        void onToggleFavorite(Channel channel);
        boolean isFavorite(Channel channel);
    }

    private final Listener listener;
    private final List<Channel> items = new ArrayList<>();

    ChannelAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    void submit(List<Channel> channels) {
        items.clear();
        if (channels != null) items.addAll(channels);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).favoriteKey().hashCode();
    }

    @NonNull
    @Override
    public ChannelHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new ChannelHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelHolder holder, int position) {
        Channel channel = items.get(position);
        holder.name.setText(channel.name);
        String meta = (channel.group == null || channel.group.trim().isEmpty() ? "Otros" : channel.group)
                + " · " + channel.streams.size() + (channel.streams.size() == 1 ? " fuente" : " fuentes");
        holder.meta.setText(meta);
        holder.initials.setText(channel.initials());
        holder.initials.setVisibility(View.VISIBLE);
        holder.favorite.setVisibility(listener.isFavorite(channel) ? View.VISIBLE : View.GONE);

        Glide.with(holder.logo).clear(holder.logo);
        holder.logo.setImageDrawable(null);
        if (channel.logoUrl != null && !channel.logoUrl.trim().isEmpty()) {
            Glide.with(holder.logo)
                    .load(channel.logoUrl)
                    .fitCenter()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> target, boolean isFirstResource) {
                            holder.initials.setVisibility(View.VISIBLE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                       Target<Drawable> target, @NonNull DataSource dataSource,
                                                       boolean isFirstResource) {
                            holder.initials.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.logo);
        }

        holder.root.setOnClickListener(v -> listener.onPlay(channel));
        holder.root.setOnLongClickListener(v -> {
            listener.onToggleFavorite(channel);
            return true;
        });
        holder.root.setOnFocusChangeListener((view, hasFocus) -> {
            view.animate()
                    .scaleX(hasFocus ? 1.07f : 1f)
                    .scaleY(hasFocus ? 1.07f : 1f)
                    .setDuration(130)
                    .start();
            view.setElevation(hasFocus ? 18f : 2f);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ChannelHolder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView logo;
        final TextView initials;
        final TextView favorite;
        final TextView name;
        final TextView meta;

        ChannelHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.cardRoot);
            logo = itemView.findViewById(R.id.logoImage);
            initials = itemView.findViewById(R.id.initialsText);
            favorite = itemView.findViewById(R.id.favoriteBadge);
            name = itemView.findViewById(R.id.channelName);
            meta = itemView.findViewById(R.id.channelMeta);
        }
    }
}
