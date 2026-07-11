package com.yelao.streamtv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class PlaylistRepository {
    interface Callback<T> {
        void onSuccess(T value, boolean fromCache);
        void onError(String message);
    }

    private static final String COUNTRIES_URL = "https://iptv-org.github.io/api/countries.json";
    private static final String PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/%s.m3u";
    private static final String FORK_URL = "https://raw.githubusercontent.com/yelao1992/iptv/master/streams/%s.m3u";
    private static final long CACHE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(6);

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    private final Gson gson = new Gson();
    private final M3uParser parser = new M3uParser();

    PlaylistRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    void loadCountries(Callback<List<Country>> callback) {
        executor.execute(() -> {
            File cache = new File(context.getFilesDir(), "countries.json");
            try {
                String json;
                boolean fromCache = cache.exists() && isFresh(cache);
                if (fromCache) {
                    json = readFile(cache);
                } else {
                    try {
                        json = download(COUNTRIES_URL);
                        writeFile(cache, json);
                    } catch (Exception networkError) {
                        if (cache.exists()) {
                            json = readFile(cache);
                            fromCache = true;
                        } else {
                            deliverSuccess(callback, fallbackCountries(), true);
                            return;
                        }
                    }
                }

                Type type = new TypeToken<List<Country>>() { }.getType();
                List<Country> countries = gson.fromJson(json, type);
                if (countries == null || countries.isEmpty()) countries = fallbackCountries();
                sortCountries(countries);
                deliverSuccess(callback, countries, fromCache);
            } catch (Exception error) {
                deliverSuccess(callback, fallbackCountries(), true);
            }
        });
    }

    void loadChannels(String countryCode, boolean forceRefresh, Callback<List<Channel>> callback) {
        final String code = countryCode == null ? "AR" : countryCode.toUpperCase(Locale.ROOT);
        executor.execute(() -> {
            File cache = new File(context.getFilesDir(), "playlist_" + code + ".m3u");
            try {
                String playlist;
                boolean fromCache = false;
                if (!forceRefresh && cache.exists() && isFresh(cache)) {
                    playlist = readFile(cache);
                    fromCache = true;
                } else {
                    try {
                        playlist = download(String.format(Locale.ROOT, PLAYLIST_URL, code.toLowerCase(Locale.ROOT)));
                        writeFile(cache, playlist);
                    } catch (Exception officialError) {
                        try {
                            playlist = download(String.format(Locale.ROOT, FORK_URL, code.toLowerCase(Locale.ROOT)));
                            if (!playlist.trim().isEmpty()) writeFile(cache, playlist);
                        } catch (Exception forkError) {
                            if (cache.exists()) {
                                playlist = readFile(cache);
                                fromCache = true;
                            } else {
                                throw officialError;
                            }
                        }
                    }
                }

                List<Channel> channels = parser.parse(playlist, code);
                if (channels.isEmpty()) {
                    throw new IllegalStateException("La lista no contiene transmisiones reproducibles.");
                }
                deliverSuccess(callback, channels, fromCache);
            } catch (Exception error) {
                String detail = error.getMessage();
                if (detail == null || detail.trim().isEmpty()) detail = error.getClass().getSimpleName();
                deliverError(callback, "No se pudo cargar la lista de " + code + ": " + detail);
            }
        });
    }

    private String download(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "StreamTVTest/0.1 AndroidTV")
                .header("Accept", "application/json, application/x-mpegURL, text/plain, */*")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private boolean isFresh(File file) {
        return System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MS;
    }

    private String readFile(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private void writeFile(File file, String content) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void sortCountries(List<Country> countries) {
        Collections.sort(countries, new Comparator<Country>() {
            @Override
            public int compare(Country left, Country right) {
                if ("AR".equals(left.code)) return -1;
                if ("AR".equals(right.code)) return 1;
                return left.displayName().compareToIgnoreCase(right.displayName());
            }
        });
    }

    private List<Country> fallbackCountries() {
        String[][] values = {
                {"Argentina", "AR"}, {"Brasil", "BR"}, {"Chile", "CL"},
                {"Uruguay", "UY"}, {"Paraguay", "PY"}, {"Bolivia", "BO"},
                {"Perú", "PE"}, {"Colombia", "CO"}, {"México", "MX"},
                {"España", "ES"}, {"Estados Unidos", "US"}, {"Reino Unido", "UK"}
        };
        List<Country> result = new ArrayList<>();
        for (String[] value : values) {
            Country country = new Country();
            country.name = value[0];
            country.code = value[1];
            country.languages = new ArrayList<>();
            result.add(country);
        }
        return result;
    }

    private <T> void deliverSuccess(Callback<T> callback, T value, boolean fromCache) {
        mainHandler.post(() -> callback.onSuccess(value, fromCache));
    }

    private <T> void deliverError(Callback<T> callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }
}
