package com.example.proj.Services;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQService {

    private final RabbitAdmin rabbitAdmin;

    @Autowired
    public RabbitMQService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    public int getQueueSize(String queueName) {
        return rabbitAdmin.getQueueProperties(queueName).size();
    }
}
