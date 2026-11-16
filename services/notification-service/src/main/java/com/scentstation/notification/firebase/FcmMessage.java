package com.scentstation.notification.firebase;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FcmMessage(
    Collection<String> tokens,
    String title,
    String body,
    Map<String, String> data
) {
    public FcmMessage {
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        title = Objects.requireNonNull(title, "title");
        body = Objects.requireNonNull(body, "body");
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
