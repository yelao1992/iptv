package com.yelao.streamtv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ChannelAdapter.Listener {
    private static final String PREFS = "stream_tv_preferences";
    private static final String KEY_COUNTRY_CODE = "selected_country_code";
    private static final String KEY_COUNTRY_NAME = "selected_country_name";

    private final Gson gson = new Gson();
    private PlaylistRepository repository;
    private FavoritesStore favoritesStore;
    private ChannelAdapter adapter;
    private SharedPreferences preferences;

    private RecyclerView channelGrid;
    private TextView subtitleText;
    private TextView sectionTitle;
    private TextView countText;
    private TextView emptyText;
    private TextView loadingText;
    private LinearLayout loadingPanel;
    private Button countryButton;
    private Button favoritesButton;
    private EditText searchInput;

    private final List<Channel> currentCountryChannels = new ArrayList<>();
    private String selectedCountryCode = "AR";
    private String selectedCountryName = "Argentina";
    private boolean showingFavorites = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedCountryCode = preferences.getString(KEY_COUNTRY_CODE, "AR");
        selectedCountryName = preferences.getString(KEY_COUNTRY_NAME, "Argentina");
        repository = new PlaylistRepository(this);
        favoritesStore = new FavoritesStore(this);

        bindViews();
        setupGrid();
        setupActions();
        updateCountryButton();
        loadSelectedCountry(false);
    }

    private void bindViews() {
        channelGrid = findViewById(R.id.channelGrid);
        subtitleText = findViewById(R.id.subtitleText);
        sectionTitle = findViewById(R.id.sectionTitle);
        countText = findViewById(R.id.countText);
        emptyText = findViewById(R.id.emptyText);
        loadingText = findViewById(R.id.loadingText);
        loadingPanel = findViewById(R.id.loadingPanel);
        countryButton = findViewById(R.id.countryButton);
        favoritesButton = findViewById(R.id.favoritesButton);
        searchInput = findViewById(R.id.searchInput);
    }

    private void setupGrid() {
        adapter = new ChannelAdapter(this);
        channelGrid.setLayoutManager(new GridLayoutManager(this, 5));
        channelGrid.setHasFixedSize(true);
        channelGrid.setAdapter(adapter);
    }

    private void setupActions() {
        countryButton.setOnClickListener(v -> openCountrySelector());
        favoritesButton.setOnClickListener(v -> {
            showingFavorites = !showingFavorites;
            favoritesButton.setText(showingFavorites ? "★ Todos los canales" : "★ Favoritos");
            applyFilter();
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void loadSelectedCountry(boolean forceRefresh) {
        showingFavorites = false;
        favoritesButton.setText("★ Favoritos");
        showLoading(true, "Cargando canales de " + selectedCountryName + "…");
        repository.loadChannels(selectedCountryCode, forceRefresh, new PlaylistRepository.Callback<List<Channel>>() {
            @Override
            public void onSuccess(List<Channel> value, boolean fromCache) {
                currentCountryChannels.clear();
                currentCountryChannels.addAll(value);
                favoritesStore.refreshFromCatalog(value);
                subtitleText.setText(fromCache
                        ? "Catálogo guardado · " + selectedCountryName
                        : "Catálogo oficial actualizado · " + selectedCountryName);
                showLoading(false, null);
                applyFilter();
            }

            @Override
            public void onError(String message) {
                showLoading(false, null);
                applyFilter();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("No se pudo cargar la lista")
                        .setMessage(message)
                        .setPositiveButton("Reintentar", (dialog, which) -> loadSelectedCountry(true))
                        .setNegativeButton("Ver favoritos", (dialog, which) -> {
                            showingFavorites = true;
                            favoritesButton.setText("★ Todos los canales");
                            applyFilter();
                        })
                        .show();
            }
        });
    }

    private void openCountrySelector() {
        showLoading(true, "Cargando países…");
        repository.loadCountries(new PlaylistRepository.Callback<List<Country>>() {
            @Override
            public void onSuccess(List<Country> countries, boolean fromCache) {
                showLoading(false, null);
                String[] labels = new String[countries.size()];
                int selectedIndex = -1;
                for (int i = 0; i < countries.size(); i++) {
                    Country country = countries.get(i);
                    labels[i] = country.emoji() + "  " + country.displayName();
                    if (selectedCountryCode.equalsIgnoreCase(country.code)) selectedIndex = i;
                }

                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Seleccionar país")
                        .setSingleChoiceItems(labels, selectedIndex, (currentDialog, which) -> {
                            Country country = countries.get(which);
                            selectedCountryCode = country.code.toUpperCase(Locale.ROOT);
                            selectedCountryName = country.displayName();
                            preferences.edit()
                                    .putString(KEY_COUNTRY_CODE, selectedCountryCode)
                                    .putString(KEY_COUNTRY_NAME, selectedCountryName)
                                    .apply();
                            updateCountryButton();
                            currentDialog.dismiss();
                            loadSelectedCountry(false);
                        })
                        .setNegativeButton("Cancelar", null)
                        .create();
                dialog.setOnShowListener(ignored -> dialog.getListView().requestFocus());
                dialog.show();
            }

            @Override
            public void onError(String message) {
                showLoading(false, null);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilter() {
        String query = searchInput.getText() == null
                ? ""
                : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);

        List<Channel> source = showingFavorites
                ? favoritesStore.getAll()
                : new ArrayList<>(currentCountryChannels);
        List<Channel> filtered = new ArrayList<>();
        for (Channel channel : source) {
            if (channel == null || channel.streams == null || channel.streams.isEmpty()) continue;
            String searchable = (channel.name + " " + channel.group + " " + channel.countryCode)
                    .toLowerCase(Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) filtered.add(channel);
        }

        Collections.sort(filtered, new Comparator<Channel>() {
            @Override
            public int compare(Channel left, Channel right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });

        adapter.submit(filtered);
        sectionTitle.setText(showingFavorites ? "Mis canales favoritos" : "Canales de " + selectedCountryName);
        countText.setText(filtered.size() + (filtered.size() == 1 ? " canal" : " canales"));
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        channelGrid.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);

        if (!filtered.isEmpty()) {
            channelGrid.post(() -> {
                RecyclerView.ViewHolder first = channelGrid.findViewHolderForAdapterPosition(0);
                if (first != null && !searchInput.hasFocus() && !countryButton.hasFocus() && !favoritesButton.hasFocus()) {
                    first.itemView.requestFocus();
                }
            });
        }
    }

    private void updateCountryButton() {
        Country country = new Country();
        country.code = selectedCountryCode;
        country.name = selectedCountryName;
        countryButton.setText(country.emoji() + " " + selectedCountryName);
    }

    private void showLoading(boolean visible, String message) {
        loadingPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (message != null) loadingText.setText(message);
        if (visible) loadingPanel.requestFocus();
    }

    @Override
    public void onPlay(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_JSON, gson.toJson(channel));
        startActivity(intent);
    }

    @Override
    public void onToggleFavorite(Channel channel) {
        boolean added = favoritesStore.toggle(channel);
        Toast.makeText(this, added ? "Agregado a favoritos" : "Quitado de favoritos", Toast.LENGTH_SHORT).show();
        applyFilter();
    }

    @Override
    public boolean isFavorite(Channel channel) {
        return favoritesStore.contains(channel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) applyFilter();
    }

    @Override
    public void onBackPressed() {
        if (showingFavorites) {
            showingFavorites = false;
            favoritesButton.setText("★ Favoritos");
            applyFilter();
            return;
        }
        super.onBackPressed();
    }
}
