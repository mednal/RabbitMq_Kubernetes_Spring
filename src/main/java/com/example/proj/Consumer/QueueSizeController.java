package com.example.proj.Consumer;
import com.example.proj.Configuration.RabbitMQConfig;
import com.example.proj.Entity.DatasetEntry;
import com.example.proj.Entity.WekaRequestData;
import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.Environment;
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
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import okhttp3.*;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.amqp.core.Message;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;

import javax.servlet.http.HttpServletRequest;
import io.prometheus.client.Gauge;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Date;

import weka.classifiers.evaluation.NumericPrediction;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.timeseries.WekaForecaster;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;


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


    @Value("${content}")
    private String content;

    @RequestMapping("/forecast")
    public ArrayList<ArrayList<String>> forecast(@RequestParam String algorithme, @RequestParam int nombre) {
        ArrayList<ArrayList<String>> input = new ArrayList<>();

        try {
            String pathToWineData = "C:\\Users\\HP\\Desktop\\vermeg\\models\\dataset.arff";
            Instances wine = new Instances(new BufferedReader(new FileReader(pathToWineData)));

            // Convert the timestamps from seconds to milliseconds
            Attribute dateAttribute = wine.attribute("date");
            for (Instance instance : wine) {
                instance.setValue(dateAttribute, instance.value(dateAttribute) * 1000);
            }

            // Sort the instances based on the timestamp field
            wine.sort(dateAttribute);

            WekaForecaster forecaster = new WekaForecaster();
            forecaster.setFieldsToForecast("useful");

            if (algorithme.equalsIgnoreCase("Gaussian")) {
                forecaster.setBaseForecaster(new GaussianProcesses());
            } else if (algorithme.equalsIgnoreCase("Linear")) {
                forecaster.setBaseForecaster(new LinearRegression());
            } else if (algorithme.equalsIgnoreCase("Multilayer")) {
                forecaster.setBaseForecaster(new MultilayerPerceptron());
            }

            forecaster.getTSLagMaker().setTimeStampField("date");
            forecaster.buildForecaster(wine, System.out);
            forecaster.primeForecaster(wine);

            // Find the timestamp of the last instance in the dataset
            long startTime = (long) wine.lastInstance().value(dateAttribute);

            // Set the forecast range to the desired number of time steps (nombre)
            int forecastRange = nombre;
            long timeStepMillis = (long) ((startTime - wine.firstInstance().value(dateAttribute)) / forecastRange);

            // Call the forecast method to get the predictions
            List<List<NumericPrediction>> forecast = forecaster.forecast(forecastRange, System.out);

            for (int i = 0; i < forecastRange; i++) {
                List<NumericPrediction> predsAtStep = forecast.get(i);
                for (int j = 0; j < 1; j++) {
                    NumericPrediction predForTarget = predsAtStep.get(j);
                    System.out.print("" + predForTarget.predicted() + " ");
                    ArrayList<String> aaaa = new ArrayList<String>();
                    long currentTimeMillis = startTime + timeStepMillis * (i + 1);
                    Date d = new Date(currentTimeMillis);

                    // Create a SimpleDateFormat object to format the date
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    // Format the date and add it to the ArrayList
                    String formattedDate = sdf.format(d);
                    aaaa.add(formattedDate);
                    aaaa.add(String.valueOf(predForTarget.predicted()));
                    input.add(aaaa);
                }
                System.out.println();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return input;
    }


    Gauge podRunningDuration = Gauge.build()
            .name("pod_running_duration_seconds")
            .help("Running duration of pods in a deployment")
            .labelNames("deployment", "pod", "creation_time")
            .register();
    @GetMapping("/GetPodRunningTime")
    public static void GetPodRunningTime(String[] args) throws IOException {
        List<DatasetEntry> dataset = new ArrayList<>();
        String targetPodIP = "file-write-service";  // Update with the service name or IP
        int targetPodPort = 8082;                   // Update with the service port
        String filePath = "/home/dataset.arff";     // Update with the desired file path in the target pod

        // Connect to the target pod
        Socket socket = new Socket(targetPodIP, targetPodPort);

        OkHttpClient client = new OkHttpClient();
        String prometheusQuery = "last_over_time(timestamp(kube_pod_status_phase{phase=\"Succeeded\",pod=~\"consumer-deployment.*\"})[1h:]) - ignoring(phase) group_right() last_over_time((kube_pod_created{pod=~\"consumer-deployment.*\"})[1h:])";
        String prometheusURL = "http://prometheus-server/api/v1/query";

        HttpUrl.Builder urlBuilder = HttpUrl.parse(prometheusURL).newBuilder();
        urlBuilder.addQueryParameter("query", prometheusQuery);

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                if (body != null) {
                    JSONObject jsonObject = new JSONObject(body.string());
                    JSONArray resultArray = jsonObject.getJSONObject("data").getJSONArray("result");

                    for (int i = 0; i < resultArray.length(); i++) {
                        JSONObject resultObject = resultArray.getJSONObject(i);
                        String pod = resultObject.getJSONObject("metric").getString("pod");
                        double runningTime = resultObject.getJSONArray("value").getDouble(1);

                        // Fetch creation date from Prometheus using kube_pod_created metric
                        String creationDateQuery = "last_over_time((kube_pod_created{pod=\"" + pod + "\"})[1h:])";
                        double creationTimestamp = queryPrometheus(prometheusURL, creationDateQuery);
                        LocalDateTime creationDateTime = LocalDateTime.ofInstant(
                                Instant.ofEpochSecond((long) creationTimestamp), ZoneId.systemDefault());

                        System.out.println("Pod: " + pod +
                                ", Running Time: " + runningTime + " seconds" +
                                ", Creation Date: " + creationDateTime);

                        // Add entry to the dataset
                        DatasetEntry entry = new DatasetEntry(creationDateTime, runningTime);
                        dataset.add(entry);
                    }
                }
            } else {
                System.out.println("Request failed with response code: " + response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        writeDatasetToFile(dataset, filePath);
    }

    public static double queryPrometheus(String url, String query) throws IOException {
        OkHttpClient client = new OkHttpClient();

        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        urlBuilder.addQueryParameter("query", query);

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                if (body != null) {
                    JSONObject jsonObject = new JSONObject(body.string());
                    JSONArray resultArray = jsonObject.getJSONObject("data").getJSONArray("result");

                    if (resultArray.length() > 0) {
                        double value = resultArray.getJSONObject(0).getJSONArray("value").getDouble(1);
                        return value;
                    }
                }
            } else {
                System.out.println("Request failed with response code: " + response.code());
            }
        }

        return 0.0;
    }



    private static void writeDatasetToFile(List<DatasetEntry> dataset, String filePath) {
        // Append ".arff" extension if it doesn't exist
        if (!filePath.endsWith(".arff")) {
            filePath = filePath + ".arff";
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
            // Write ARFF header if the file is empty
            Collections.sort(dataset, Comparator.comparing(DatasetEntry::getCreationDateTime));

            File file = new File(filePath);
            if (file.length() == 0) {
                writer.println("@RELATION dataset\n");
                writer.println("@ATTRIBUTE date NUMERIC");
                writer.println("@ATTRIBUTE duration NUMERIC");
                writer.println("@ATTRIBUTE useful NUMERIC\n");
                writer.println("@DATA");
            }

            // Write dataset entries
            for (DatasetEntry entry : dataset) {
                LocalDateTime creationDateTime = entry.getCreationDateTime();
                double runningDuration = entry.getRunningDuration();
                int usefulValue = (runningDuration > 900) ? 1 : 0;

                long unixTimestamp = creationDateTime.toEpochSecond(ZoneOffset.UTC);
                writer.println(
                        unixTimestamp + "," +
                                runningDuration + "," +
                                usefulValue
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void writeARFFHeader(PrintWriter writer) {
        // Write ARFF header
        writer.println("@relation autoscaling");
        writer.println();
        writer.println("@attribute month {January, February, March, April, May, June, July, August, September, October, November, December}");
        writer.println("@attribute day_of_week {Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday}");
        writer.println("@attribute time_of_day {1,2,3,4,5,6,7,8,9,10,11,12}");
        writer.println("@attribute action {scale_up}");
        writer.println("@attribute duration numeric");
        writer.println("@attribute useful {0,1}");
        writer.println();
        writer.println("@data");
    }
    @GetMapping("/size")
    public String getQueueSize() {
        QueueInformation queueInfo;
        try {
            queueInfo = rabbitTemplate.execute(channel -> {
                // Get the queue information
                AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(queueName);
                return new QueueInformation(declareOk != null ? declareOk.getMessageCount() : 0);
            });
        } catch (Exception e) {
            // Handle the exception, log an error, or perform any necessary actions
            // You can also provide a default value for queueInfo if needed
            queueInfo = new QueueInformation(0);
        }

        return "The size of the queue " + queueName + " is " + (queueInfo != null ? queueInfo.getMessageCount() : 0);
    }


    private static int lastPodCount = 0; // initial pod count





    private final Map<String, LocalDateTime> scaleUpTimes = new HashMap<>();





    @GetMapping("/consume/single")
    public String consumeSingleMessageFromQueue() {
        Message message = rabbitTemplate.receive(queueName);
        if (message != null) {
            // process the message
            String messageBody = new String(message.getBody(), StandardCharsets.UTF_8);
            System.out.println("Received message: "  + messageBody);

            // Call handleMessage to process the message
            handleMessage(messageBody);

            return "Consumed message with payload: " + messageBody;
        } else {
            return "No messages found in queue " + queueName;
        }
    }
    @GetMapping("/consume/singles")
    public String consumeSingleMessageFromQueues() {
        Message message = rabbitTemplate.receive(queueName);
        if (message != null) {
            // process the message
            String messageBody = new String(message.getBody(), StandardCharsets.UTF_8);
            System.out.println("Received message: "  + messageBody);


            return "Consumed message with payload: " + messageBody;
        } else {
            return "No messages found in queue " + queueName;
        }
    }

    @GetMapping("/getheallthresult")  // 2 hours in milliseconds
    public void healthCheckRequest() {
        RestTemplate restTemplate = new RestTemplate();
        String app2Url = "http://localhost:8089/monitoring/ishealthy";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

        ResponseEntity<String> result = restTemplate.exchange(app2Url, HttpMethod.GET, entity, String.class);

        System.out.println(result.getBody());
    }


    public void performCpuIntensiveTask() {
        long start = System.currentTimeMillis();
        int iterations = 100_000_000;
        int repeats = 30; // Repeating the calculations n times
        double sum = 0;

        for (int repeat = 0; repeat < repeats; repeat++) {
            for (int i = 1; i <= iterations; i++) {
                sum += Math.sqrt(i);
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("CPU-intensive task completed in " + (end - start) + " ms, result: " + sum);
    }



    private final AtomicInteger messagesBeingProcessed = new AtomicInteger(0);

    public void handleMessage(String message) {
        messagesBeingProcessed.incrementAndGet();
        try {
            System.out.println("Starting to process message: " + message);

            // Perform a CPU-intensive task
            performCpuIntensiveTask();

            System.out.println("Message processing completed: " + message);
        } catch (Exception e) {
            System.out.println("Error while processing the message: " + e.getMessage());
        } finally {
            messagesBeingProcessed.decrementAndGet();
        }
    }




    @PostMapping("/publish")
    public String publishMessage(@RequestBody String messagePayload, @RequestParam("queueName") String queueName) {
        rabbitTemplate.convertAndSend(queueName, messagePayload);
        return "Message published to queue " + queueName + ": " + messagePayload;
    }

    @GetMapping("/delayweka/{seconds}")
    public ResponseEntity<String> testDelay(@PathVariable int seconds) throws InterruptedException {
        // Simulate a long-running request by sleeping for the specified number of seconds
        System.out.println("Starting long-running request for " + seconds + " seconds");
        Thread.sleep(seconds * 1000);
        System.out.println("Long-running request completed");

        return ResponseEntity.ok("Request completed after " + seconds + " seconds.");
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

            // Wait for all messages to be processed
            System.out.println("Shutdown signal received. Waiting for message processing to complete.");
            while (messagesBeingProcessed.get() > 0) {
                try {
                    Thread.sleep(1000); // wait for 1 second before checking again
                } catch (InterruptedException e) {
                    System.out.println("Shutdown waiting interrupted: " + e.getMessage());
                }
            }
            System.out.println("All messages have been processed. Proceeding with the shutdown.");
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