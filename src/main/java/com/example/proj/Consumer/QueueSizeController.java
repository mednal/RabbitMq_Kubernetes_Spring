package com.example.proj.Consumer;

import com.rabbitmq.client.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.amqp.core.Message;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/queues")
public class QueueSizeController {

    @Value("${spring.rabbitmq.host}")
    private String rabbitMQHost;
    @Value("${spring.rabbitmq.port}")
    private int rabbitMQPort;
    @Value("${spring.rabbitmq.username}")
    private String rabbitMQUsername;
    @Value("${spring.rabbitmq.password}")
    private String rabbitMQPassword;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Value("${QUEUE_NAME}")
    private String queueName;
    @GetMapping("/size")
    public String getQueueSize() {
        QueueInformation queueInfo = rabbitTemplate.execute(channel -> {
            // Get the queue information
            AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(queueName);
            return new QueueInformation(declareOk.getMessageCount());
        });
        return "The size of the queue " + queueName + " is " + queueInfo.getMessageCount();
    }

/*
    @GetMapping("/consume")
    public String consumeFromQueue() {
        int messagesToConsume = 2; // set the number of messages to consume
        List<Message> messages = new ArrayList<>();

        for (int i = 0; i < messagesToConsume; i++) {
            Message message = rabbitTemplate.receive(queueName);
            if (message == null) {
                break; // break the loop if there are no more messages in the queue
            }
            messages.add(message);
        }

        for (Message message : messages) {
            rabbitTemplate.receiveAndReply(queueName, message1 -> {
                // process the message here
                System.out.println("Consuming message with payload: " + new String(message.getBody()));
                return null;
            });
        }


        int remainingMessages = rabbitTemplate.execute(channel -> {
            AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(queueName);
            return declareOk.getMessageCount();
        });

        return "Consumed " + messages.size() + " messages from queue " + queueName + ". " +
                remainingMessages + " messages remaining in the queue.";
    }*/
    @GetMapping("/unack")
    public String consumeFromQueue() {
        int messagesToConsume = 1; // set the number of messages to consume
        List<String> messagePayloads = new ArrayList<>();

        for (int i = 0; i < messagesToConsume; i++) {
            Message message = rabbitTemplate.receive(queueName);
            if (message == null) {
                break; // break the loop if there are no more messages in the queue
            }
            messagePayloads.add(new String(message.getBody()));
        }

        return "Consumed and processed " + messagePayloads.size() + " messages from queue " + queueName + ".";
    }

    private static class QueueInformation {
        private final long messageCount;

        public QueueInformation(long messageCount) {
            this.messageCount = messageCount;
        }

        public long getMessageCount() {
            return messageCount;
        }
    }
}
