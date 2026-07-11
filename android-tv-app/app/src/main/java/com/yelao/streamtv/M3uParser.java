package com.yelao.streamtv;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class M3uParser {
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([\\w-]+)=\\\"([^\\\"]*)\\\"");
    private static final Pattern QUALITY_PATTERN = Pattern.compile("(?i)(2160p|1080p|720p|576p|480p|360p|4K|FHD|HD|UHD)");

    List<Channel> parse(String content, String countryCode) throws Exception {
        Map<String, Channel> channels = new LinkedHashMap<>();
        BufferedReader reader = new BufferedReader(new StringReader(content == null ? "" : content));

        String pendingId = null;
        String pendingLogo = null;
        String pendingGroup = null;
        String pendingTitle = null;
        String pendingUserAgent = null;
        String pendingReferrer = null;

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || "#EXTM3U".equals(line)) continue;

            if (line.startsWith("#EXTINF")) {
                Map<String, String> attributes = parseAttributes(line);
                pendingId = attributes.get("tvg-id");
                pendingLogo = attributes.get("tvg-logo");
                pendingGroup = attributes.get("group-title");
                pendingTitle = extractTitle(line);
                pendingUserAgent = null;
                pendingReferrer = null;
                continue;
            }

            if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                pendingUserAgent = line.substring("#EXTVLCOPT:http-user-agent=".length()).trim();
                continue;
            }

            if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                pendingReferrer = line.substring("#EXTVLCOPT:http-referrer=".length()).trim();
                continue;
            }

            if (line.startsWith("#") || !(line.startsWith("http://") || line.startsWith("https://"))) {
                continue;
            }

            String title = cleanTitle(pendingTitle);
            if (title.isEmpty()) title = "Canal sin nombre";
            String fullId = pendingId == null ? "" : pendingId.trim();
            String baseId = fullId;
            int feedSeparator = baseId.indexOf('@');
            if (feedSeparator > 0) baseId = baseId.substring(0, feedSeparator);
            String key = baseId.isEmpty() ? normalize(title) : baseId;

            Channel channel = channels.get(key);
            if (channel == null) {
                channel = new Channel();
                channel.id = baseId.isEmpty() ? key : baseId;
                channel.name = title;
                channel.logoUrl = nullToEmpty(pendingLogo);
                channel.group = emptyFallback(pendingGroup, "Otros");
                channel.countryCode = countryCode == null ? "" : countryCode.toUpperCase(Locale.ROOT);
                channel.streams = new ArrayList<>();
                channels.put(key, channel);
            } else {
                if ((channel.logoUrl == null || channel.logoUrl.isEmpty()) && pendingLogo != null) {
                    channel.logoUrl = pendingLogo;
                }
                if ((channel.group == null || "Otros".equals(channel.group)) && pendingGroup != null && !pendingGroup.isEmpty()) {
                    channel.group = pendingGroup;
                }
            }

            if (!containsUrl(channel.streams, line)) {
                StreamSource source = new StreamSource();
                source.url = line;
                source.userAgent = nullToEmpty(pendingUserAgent);
                source.referrer = nullToEmpty(pendingReferrer);
                source.quality = detectQuality(fullId + " " + pendingTitle);
                channel.streams.add(source);
            }

            pendingId = null;
            pendingLogo = null;
            pendingGroup = null;
            pendingTitle = null;
            pendingUserAgent = null;
            pendingReferrer = null;
        }

        List<Channel> result = new ArrayList<>(channels.values());
        for (Channel channel : result) channel.sortStreams();
        Collections.sort(result, new Comparator<Channel>() {
            @Override
            public int compare(Channel left, Channel right) {
                String leftGroup = left.group == null ? "" : left.group;
                String rightGroup = right.group == null ? "" : right.group;
                int groupComparison = leftGroup.compareToIgnoreCase(rightGroup);
                if (groupComparison != 0) return groupComparison;
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        return result;
    }

    private Map<String, String> parseAttributes(String line) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(line);
        while (matcher.find()) attributes.put(matcher.group(1), matcher.group(2));
        return attributes;
    }

    private String extractTitle(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') inQuotes = !inQuotes;
            if (current == ',' && !inQuotes) return line.substring(i + 1).trim();
        }
        return "";
    }

    private String cleanTitle(String title) {
        if (title == null) return "";
        return title
                .replaceAll("(?i)\\s*\\[(Geo-blocked|Not 24/7|Offline|Geo Blocked)\\]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String detectQuality(String text) {
        if (text == null) return "";
        Matcher matcher = QUALITY_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private boolean containsUrl(List<StreamSource> sources, String url) {
        for (StreamSource source : sources) {
            if (source != null && url.equals(source.url)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
