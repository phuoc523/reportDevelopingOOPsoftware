package com.scentstation.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "notification.firebase", name = "enabled", havingValue = "true")
public class FirebaseConfiguration {
    @Bean(destroyMethod = "delete")
    FirebaseApp firebaseApp(
            @Value("${notification.firebase.credentials-path:}") String credentialsPath,
            @Value("${notification.firebase.project-id:}") String projectId
    ) throws IOException {
        GoogleCredentials credentials;
        if (credentialsPath == null || credentialsPath.isBlank()) {
            credentials = GoogleCredentials.getApplicationDefault();
        } else {
            try (InputStream input = Files.newInputStream(Path.of(credentialsPath.trim()))) {
                credentials = GoogleCredentials.fromStream(input);
            }
        }

        FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
        if (projectId != null && !projectId.isBlank()) {
            options.setProjectId(projectId.trim());
        }
        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }
}
