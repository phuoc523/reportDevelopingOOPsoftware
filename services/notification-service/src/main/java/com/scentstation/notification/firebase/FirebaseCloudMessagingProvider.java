package com.scentstation.notification.firebase;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification.firebase", name = "enabled", havingValue = "true")
public class FirebaseCloudMessagingProvider {
    private static final int MAX_BATCH_SIZE = 500;
    private final FirebaseMessaging messaging;

    public FirebaseCloudMessagingProvider(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    public FcmDeliveryResult send(FcmMessage request) {
        List<String> tokens = request.tokens().stream()
            .filter(token -> token != null && !token.isBlank())
            .map(String::trim)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        if (tokens.isEmpty()) {
            return FcmDeliveryResult.empty();
        }

        int successes = 0;
        List<String> invalidTokens = new ArrayList<>();
        try {
            for (int from = 0; from < tokens.size(); from += MAX_BATCH_SIZE) {
                List<String> batchTokens = tokens.subList(from, Math.min(from + MAX_BATCH_SIZE, tokens.size()));
                BatchResponse batch = messaging.sendEachForMulticast(message(request, batchTokens));
                successes += batch.getSuccessCount();
                collectInvalidTokens(batchTokens, batch.getResponses(), invalidTokens);
            }
        } catch (FirebaseMessagingException exception) {
            throw new FcmDeliveryException(exception);
        }
        return new FcmDeliveryResult(tokens.size(), successes, tokens.size() - successes, invalidTokens);
    }

    private MulticastMessage message(FcmMessage request, List<String> tokens) {
        MulticastMessage.Builder builder = MulticastMessage.builder()
            .setNotification(Notification.builder()
                .setTitle(request.title())
                .setBody(request.body())
                .build())
            .putAllData(request.data())
            .addAllTokens(tokens);
        return builder.build();
    }

    private void collectInvalidTokens(List<String> tokens, List<SendResponse> responses,
                                      List<String> invalidTokens) {
        for (int index = 0; index < responses.size(); index++) {
            SendResponse response = responses.get(index);
            if (!response.isSuccessful() && isInvalid(response.getException())) {
                invalidTokens.add(tokens.get(index));
            }
        }
    }

    private boolean isInvalid(FirebaseMessagingException exception) {
        return exception != null && (exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
            || exception.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT);
    }
}
