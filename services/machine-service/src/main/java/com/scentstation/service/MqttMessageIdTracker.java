package com.scentstation.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MqttMessageIdTracker {
    private static final int MAX_ENTRIES = 10_000;
    private static final Duration RETENTION = Duration.ofHours(24);
    private final LinkedHashMap<String, Instant> processedMessages = new LinkedHashMap<>(128, 0.75f, true);

    public synchronized boolean markIfNew(String deviceIdentity, String messageId, Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = processedMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isBefore(now.minus(RETENTION))) {
                iterator.remove();
            }
        }

        String key = deviceIdentity.toLowerCase(Locale.ROOT) + ":" + messageId;
        if (processedMessages.containsKey(key)) {
            return false;
        }
        processedMessages.put(key, now);
        while (processedMessages.size() > MAX_ENTRIES) {
            processedMessages.remove(processedMessages.keySet().iterator().next());
        }
        return true;
    }
}
