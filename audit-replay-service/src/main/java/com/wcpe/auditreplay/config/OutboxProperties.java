package com.wcpe.auditreplay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcpe.audit.outbox")
public class OutboxProperties {

    private final Publisher publisher = new Publisher();
    private final Kafka kafka = new Kafka();
    private final Encryption encryption = new Encryption();

    public Publisher getPublisher() {
        return publisher;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public Encryption getEncryption() {
        return encryption;
    }

    public static class Publisher {
        private boolean enabled = true;
        private long pollIntervalMs = 1000;
        private int batchSize = 50;
        private int maxAttempts = 5;
        private long initialBackoffMs = 1000;
        private long maxBackoffMs = 60000;
        private double backoffMultiplier = 2.0;
        private double jitterFactor = 0.1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialBackoffMs() {
            return initialBackoffMs;
        }

        public void setInitialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
        }

        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }

        public void setMaxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public double getJitterFactor() {
            return jitterFactor;
        }

        public void setJitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
        }
    }

    public static class Kafka {
        private String topic = "pricing.audit.events.v1";
        private String dlqTopic = "pricing.audit.events.dlq.v1";
        private String clientId = "audit-replay-publisher";

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getDlqTopic() {
            return dlqTopic;
        }

        public void setDlqTopic(String dlqTopic) {
            this.dlqTopic = dlqTopic;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }

    public static class Encryption {
        private boolean enabled = false;
        private String algorithm = "AES-256-GCM";
        private String keyRef;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public String getKeyRef() {
            return keyRef;
        }

        public void setKeyRef(String keyRef) {
            this.keyRef = keyRef;
        }
    }
}
