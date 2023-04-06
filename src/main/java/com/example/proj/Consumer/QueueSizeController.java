package com.example.proj.Consumer;

import ch.qos.logback.classic.pattern.MessageConverter;
import com.example.proj.Configuration.RabbitMQConfig;
import com.rabbitmq.client.*;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.converter.JsonbMessageConverter;
import org.springframework.web.bind.annotation.*;
import org.springframework.amqp.core.Message;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
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


    @Value("${QUEUE_NAME}")
    private String queueName;
    @Autowired
    private RabbitTemplate rabbitTemplate;


    @GetMapping("/size")
    public String getQueueSize() {
        QueueInformation queueInfo = rabbitTemplate.execute(channel -> {
            // Get the queue information
            AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(queueName);
            return new QueueInformation(declareOk.getMessageCount());
        });
        return "The size of the queue " + queueName + " is " + queueInfo.getMessageCount();
    }




    @GetMapping("/consume/single")
    public String consumeSingleMessageFromQueue() {
        Message message = rabbitTemplate.receive(queueName);
        if (message != null) {
            // process the message
            System.out.println("Received message: " + new String(message.getBody()));
            return "Consumed message with payload: " + new String(message.getBody());
        } else {
            return "No messages found in queue " + queueName;
        }
    }
    @PostMapping("/publish")
    public String publishMessage(@RequestBody String messagePayload, @RequestParam("queueName") String queueName) {
        rabbitTemplate.convertAndSend(queueName, messagePayload);
        return "Message published to queue " + queueName + ": " + messagePayload;
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
 /*   @GetMapping("/unack")
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
*/
    @Autowired
    private RabbitMQConfig rabbitMQConfig;


    @Autowired
    private QueueContentFetcher queueContentFetcher;

    @GetMapping("/content")
    public List<String> queuecontent() throws IOException, TimeoutException {
        return queueContentFetcher.fetchQueueContent();
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
