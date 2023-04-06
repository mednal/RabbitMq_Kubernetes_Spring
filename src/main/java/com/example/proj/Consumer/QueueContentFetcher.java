package com.example.proj.Consumer;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class QueueContentFetcher {
    @Value("${spring.rabbitmq.host}")
    private String rabbitMQHost;
    @Value("${spring.rabbitmq.port}")
    private int rabbitMQPort;
    @Value("${spring.rabbitmq.username}")
    private String rabbitMQUsername;
    @Value("${spring.rabbitmq.password}")
    private String rabbitMQPassword;
    @Value("${QUEUE_NAME}")
    private String queueName;

    public List<String> fetchQueueContent() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQHost);
        factory.setPort(rabbitMQPort);
        factory.setUsername(rabbitMQUsername);
        factory.setPassword(rabbitMQPassword);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        List<String> messages = new ArrayList<>();
        try {
            channel.queueDeclare(queueName, true, false, false, null);
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                messages.add(message);
            };
            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
        } finally {
            channel.close();
            connection.close();
        }
        return messages;
    }
}
