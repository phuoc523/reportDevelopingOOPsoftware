package com.scentstation.notification.firebase;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseCloudMessagingProviderTest {
    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final FirebaseCloudMessagingProvider provider = new FirebaseCloudMessagingProvider(messaging);

    @Test
    void sendsNotificationDataAndTracksPartialResults() throws Exception {
        SendResponse sent = response(true, null);
        FirebaseMessagingException invalid = mock(FirebaseMessagingException.class);
        when(invalid.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        SendResponse rejected = response(false, invalid);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getSuccessCount()).thenReturn(1);
        when(batch.getResponses()).thenReturn(List.of(sent, rejected));
        when(messaging.sendEachForMulticast(any())).thenReturn(batch);

        FcmDeliveryResult result = provider.send(new FcmMessage(
            List.of("token-1", "token-2", "token-1"), "Alert", "Machine offline", Map.of("id", "12")));

        assertThat(result).isEqualTo(new FcmDeliveryResult(2, 1, 1, List.of("token-2")));
        verify(messaging).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void splitsMoreThanFirebaseMaximumIntoBatches() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int index = 0; index < 501; index++) tokens.add("token-" + index);
        BatchResponse first = batch(500);
        BatchResponse second = batch(1);
        when(messaging.sendEachForMulticast(any())).thenReturn(first, second);

        FcmDeliveryResult result = provider.send(new FcmMessage(tokens, "Title", "Body", Map.of()));

        assertThat(result.successCount()).isEqualTo(501);
        verify(messaging, times(2)).sendEachForMulticast(any());
    }

    @Test
    void skipsEmptyTargetsWithoutCallingFirebase() {
        assertThat(provider.send(new FcmMessage(List.of(" "), "Title", "Body", Map.of())))
            .isEqualTo(FcmDeliveryResult.empty());
        verifyNoInteractions(messaging);
    }

    @Test
    void wrapsTotalFirebaseFailureWithoutLeakingTargets() throws Exception {
        FirebaseMessagingException cause = mock(FirebaseMessagingException.class);
        when(messaging.sendEachForMulticast(any())).thenThrow(cause);

        assertThatThrownBy(() -> provider.send(
            new FcmMessage(List.of("secret-token"), "Title", "Body", Map.of())))
            .isInstanceOf(FcmDeliveryException.class)
            .hasMessage("Firebase Cloud Messaging delivery failed")
            .hasCause(cause);
    }

    private BatchResponse batch(int successes) {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getSuccessCount()).thenReturn(successes);
        List<SendResponse> responses = new ArrayList<>();
        for (int index = 0; index < successes; index++) responses.add(response(true, null));
        when(batch.getResponses()).thenReturn(responses);
        return batch;
    }

    private SendResponse response(boolean successful, FirebaseMessagingException exception) {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(successful);
        when(response.getException()).thenReturn(exception);
        return response;
    }
}
