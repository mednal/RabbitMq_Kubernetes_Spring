package com.example.proj.Consumer;

import com.rabbitmq.client.AMQP;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/queues")
public class QueueSizeController {
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
