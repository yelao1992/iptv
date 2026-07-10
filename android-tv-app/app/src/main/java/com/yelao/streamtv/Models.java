package com.yelao.streamtv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class Country {
    String name;
    String code;
    List<String> languages;
    String flag;

    String displayName() {
        if (code == null || code.length() != 2) {
            return name == null ? "País" : name;
        }
        String localized = new Locale("", code).getDisplayCountry(new Locale("es", "AR"));
        if (localized == null || localized.trim().isEmpty()) {
            localized = name;
        }
        return localized == null ? code : localized;
    }

    String emoji() {
        if (code == null || code.length() != 2) return "🌐";
        String upper = code.toUpperCase(Locale.ROOT);
        int first = Character.codePointAt(upper, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(upper, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }
}

final class StreamSource {
    String url;
    String userAgent;
    String referrer;
    String quality;

    int priorityScore() {
        int score = 0;
        if (url != null && url.startsWith("https://")) score += 100;
        String q = quality == null ? "" : quality.toLowerCase(Locale.ROOT);
        if (q.contains("4k") || q.contains("2160")) score += 60;
        else if (q.contains("1080") || q.contains("fhd")) score += 50;
        else if (q.contains("720") || q.equals("hd")) score += 40;
        else if (q.contains("576")) score += 30;
        else if (q.contains("480")) score += 20;
        if (url != null && url.toLowerCase(Locale.ROOT).contains("m3u8")) score += 10;
        return score;
    }
}

final class Channel {
    String id;
    String name;
    String logoUrl;
    String group;
    String countryCode;
    List<StreamSource> streams = new ArrayList<>();

    String favoriteKey() {
        String stable = id == null || id.trim().isEmpty() ? normalizedName() : id;
        return (countryCode == null ? "" : countryCode) + "|" + stable;
    }

    String normalizedName() {
        return name == null ? "canal" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    String initials() {
        if (name == null || name.trim().isEmpty()) return "TV";
        String[] parts = name.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0))) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (result.length() == 2) break;
            }
        }
        return result.length() == 0 ? "TV" : result.toString();
    }

    void sortStreams() {
        if (streams == null) streams = new ArrayList<>();
        Collections.sort(streams, new Comparator<StreamSource>() {
            @Override
            public int compare(StreamSource left, StreamSource right) {
                return Integer.compare(right.priorityScore(), left.priorityScore());
            }
        });
    }
}
