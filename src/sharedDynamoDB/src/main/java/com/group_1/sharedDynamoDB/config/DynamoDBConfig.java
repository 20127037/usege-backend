package com.group_1.sharedDynamoDB.config;

import com.group_1.sharedAws.config.AwsClientConfig;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
@AllArgsConstructor
public class DynamoDBConfig {
    private final AwsClientConfig awsClientConfig;
    @Bean
    public DynamoDbEnhancedClient dynamoDbClient() {
        DynamoDbClientBuilder dbClientBuilder = DynamoDbClient.builder()
                .region(awsClientConfig.region())
                .credentialsProvider(awsClientConfig.credentialsProvider());
        URI overrideUri = awsClientConfig.overrideUri();
        if (overrideUri != null)
            dbClientBuilder.endpointOverride(overrideUri);
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dbClientBuilder.build())
                .build();
    }
}