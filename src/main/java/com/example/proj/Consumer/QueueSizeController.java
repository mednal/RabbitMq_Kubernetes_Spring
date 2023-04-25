package com.example.proj.Consumer;

import com.example.proj.Configuration.RabbitMQConfig;
import com.rabbitmq.client.*;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.amqp.core.Message;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
    private String hostname;




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

    @GetMapping("/delay/{seconds}")
    public ResponseEntity<String> testDelay(@PathVariable int seconds) throws InterruptedException {
        // Simulate a long-running request by sleeping for the specified number of seconds
        System.out.println("Starting long-running request for " + seconds + " seconds");
        Thread.sleep(seconds * 1000);
        System.out.println("Long-running request completed");

        return ResponseEntity.ok("Request completed after " + seconds + " seconds.");
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "unknown";
        }
    }


    private final AtomicBoolean longRunningRequestInProgress = new AtomicBoolean(false);

    private HttpServletRequest currentRequest;

    @PostConstruct
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            HttpServletRequest currentRequest = RequestInterceptor.getCurrentRequest();
            if (currentRequest != null) {
                System.out.println("Request in progress. Waiting for it to complete before shutting down.");
                waitForRequestToComplete(currentRequest);
                System.out.println("Request completed. Shutting down.");
            }
        }));
    }

    public void waitForRequestToComplete(HttpServletRequest request) {
        while (RequestInterceptor.getCurrentRequest() != null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted while waiting for request to complete");
            }
        }
    }


    public boolean isLongRunningRequestInProgress() {
        return longRunningRequestInProgress.get();
    }

    public void waitForLongRunningRequestToComplete() {
        while (isLongRunningRequestInProgress()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted while waiting for long-running request to complete");
            }
        }
    }


    @PostMapping("/api/cpu-stress/{podName}/{durationInSeconds}/{cpuPercentage}")
    public String increaseCpuUsage(@PathVariable String podName, @PathVariable int durationInSeconds, @PathVariable int cpuPercentage) throws IOException, ApiException, InterruptedException {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        CoreV1Api api = new CoreV1Api();

        // Replace "default" with your target namespace, if needed
        V1Pod pod = api.readNamespacedPod(podName, "default", null, null, null);

        if (pod == null) {
            return "Pod not found";
        }

        String stressCommand = String.format("stress-ng --cpu 1 --cpu-load %d --timeout %ds", cpuPercentage, durationInSeconds);

        // Executing the stress command in the first container of the specified pod
        String[] command = new String[]{"/bin/sh", "-c", stressCommand};
        KubernetesClient k8sClient = new DefaultKubernetesClient();

        // Create a CountDownLatch to wait for the stress command to complete
        CountDownLatch latch = new CountDownLatch(1);

        ExecWatch watch = k8sClient.pods()
                .inNamespace("default")
                .withName(podName)
                .inContainer(pod.getSpec().getContainers().get(0).getName())
                .readingInput(null)
                .writingOutput(System.out)
                .writingError(System.err)
                .withTTY()
                .usingListener(new SimpleListener(latch))
                .exec(command);

        // Wait for the stress command to complete
        latch.await();

        // Close the WebSocket connection
        watch.close();

        return "Increased CPU usage for pod: " + podName;
    }

    private static class SimpleListener implements ExecListener {
        private final CountDownLatch latch;

        public SimpleListener(CountDownLatch latch) {
            this.latch = latch;
        }



        @Override
        public void onFailure(Throwable throwable, Response response) {
            System.out.println("Failed");
            latch.countDown();
        }

        @Override
        public void onClose(int code, String reason) {
            System.out.println("Finished");
            latch.countDown();
        }
    }
    private List<String> activeSessions = new ArrayList<>();

    @GetMapping("/active-users")
    public ResponseEntity<String> getActiveUsers() {
        if (activeSessions.isEmpty()) {
            return ResponseEntity.ok("No active users");
        } else {
            return ResponseEntity.ok("Active users: " + activeSessions.size());
        }
    }
    @PostMapping("/add-user")
    public void addSession(@RequestBody String sessionId) {
        activeSessions.add(sessionId);
    }
    @PostMapping("/remove-user")
    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
    }

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
