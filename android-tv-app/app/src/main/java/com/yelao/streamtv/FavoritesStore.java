package com.yelao.streamtv;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FavoritesStore {
    private static final String PREFS = "stream_tv_preferences";
    private static final String KEY_FAVORITES = "favorites_json";
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<Channel>>() { }.getType();

    FavoritesStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized List<Channel> getAll() {
        String raw = preferences.getString(KEY_FAVORITES, "[]");
        try {
            List<Channel> result = gson.fromJson(raw, listType);
            return result == null ? new ArrayList<Channel>() : result;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    synchronized boolean contains(Channel channel) {
        if (channel == null) return false;
        String key = channel.favoriteKey();
        for (Channel favorite : getAll()) {
            if (favorite != null && key.equals(favorite.favoriteKey())) return true;
        }
        return false;
    }

    synchronized boolean toggle(Channel channel) {
        List<Channel> favorites = getAll();
        String key = channel.favoriteKey();
        boolean removed = false;
        for (int i = favorites.size() - 1; i >= 0; i--) {
            Channel favorite = favorites.get(i);
            if (favorite != null && key.equals(favorite.favoriteKey())) {
                favorites.remove(i);
                removed = true;
            }
        }
        if (!removed) favorites.add(channel);
        save(favorites);
        return !removed;
    }

    synchronized void refreshFromCatalog(List<Channel> catalog) {
        if (catalog == null || catalog.isEmpty()) return;
        Map<String, Channel> latest = new LinkedHashMap<>();
        for (Channel channel : catalog) latest.put(channel.favoriteKey(), channel);

        List<Channel> favorites = getAll();
        boolean changed = false;
        for (int i = 0; i < favorites.size(); i++) {
            Channel replacement = latest.get(favorites.get(i).favoriteKey());
            if (replacement != null) {
                favorites.set(i, replacement);
                changed = true;
            }
        }
        if (changed) save(favorites);
    }

    private void save(List<Channel> channels) {
        preferences.edit().putString(KEY_FAVORITES, gson.toJson(channels, listType)).apply();
    }
}
