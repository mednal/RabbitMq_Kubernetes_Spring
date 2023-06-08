package com.example.proj.Consumer;

import com.example.proj.Entity.YamlFileConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/keda")
public class KedaConfigController {
    @PostMapping("/generate_yaml")
    public ResponseEntity<String> generateYaml(@RequestBody YamlFileConfiguration queueConfig) {
        Map<String, Object> config = new HashMap<>();
        config.put("apiVersion", "keda.sh/v1alpha1");
        config.put("kind", "ScaledObject");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", queueConfig.getQueueName());
        config.put("metadata", metadata);

        Map<String, Object> scaleTargetRef = new HashMap<>();
        scaleTargetRef.put("name", queueConfig.getDeploymentName());

        Map<String, Object> spec = new HashMap<>();
        spec.put("scaleTargetRef", scaleTargetRef);
        spec.put("minReplicaCount", queueConfig.getMinReplicas());
        spec.put("maxReplicaCount", queueConfig.getMaxReplicas());

        Map<String, Object> trigger = new HashMap<>();
        trigger.put("type", "queue");

        spec.put("triggers", List.of(trigger));
        config.put("spec", spec);

        Yaml yaml = new Yaml();
        String yamlStr = yaml.dump(config);
        return ResponseEntity.ok(yamlStr);
    }
}
