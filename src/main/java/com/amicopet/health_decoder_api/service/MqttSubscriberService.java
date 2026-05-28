package com.amicopet.health_decoder_api.service;

import com.amicopet.health_decoder_api.dto.DecodeResponse;
import com.amicopet.health_decoder_api.dto.MqttDecodeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MQTT 訂閱服務：應用程式啟動時自動連線 HiveMQ，訂閱裝置 topic。
 *
 * 啟用條件：application.properties 的 mqtt.enabled=true
 * 關閉方式：改為 mqtt.enabled=false，HTTP POST /api/health/decode-mqtt 不受影響。
 *
 * 未來換成自建 MQTT Server（EMQX Webhook）：
 *   1. 將 mqtt.enabled 改為 false
 *   2. EMQX Rule Engine 設定 Webhook → POST /api/health/decode-mqtt
 *   3. 不需要修改任何程式碼
 */
@Service
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttSubscriberService implements MqttCallback {

    @Autowired
    private HealthDecoderService decoderService;

    @Autowired
    private MqttProcessingService processingService;

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.topic}")
    private String topic;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.qos:1}")
    private int qos;

    private MqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 標記服務是否應繼續運行（PreDestroy 時設為 false，阻止重連執行緒繼續） */
    private volatile boolean running = true;

    // ─────────────────────────────────────────────────────────────
    // 啟動 & 停止
    // ─────────────────────────────────────────────────────────────

    @PostConstruct
    public void start() {
        System.out.println("🔌 MQTT 訂閱服務啟動中: broker=" + brokerUrl + " topic=" + topic);
        scheduleConnect();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                System.out.println("🔌 MQTT 已正常斷線");
            } catch (MqttException e) {
                System.out.println("⚠️  MQTT 斷線時發生例外: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 連線邏輯
    // ─────────────────────────────────────────────────────────────

    private void scheduleConnect() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    doConnect();
                    return; // 連線成功，離開重試迴圈
                } catch (MqttException e) {
                    System.out.println("⚠️  MQTT 連線失敗，5 秒後重試: " + e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "mqtt-connect");
        t.setDaemon(true);
        t.start();
    }

    private void doConnect() throws MqttException {
        mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        mqttClient.setCallback(this);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setKeepAliveInterval(60); // 必須 < Azure App Service idle timeout（240s）
        options.setAutomaticReconnect(false); // 手動管理重連，避免與 connectionLost 衝突

        mqttClient.connect(options);
        mqttClient.subscribe(topic, qos);
        System.out.println("✅ MQTT 訂閱成功: broker=" + brokerUrl + " topic=" + topic + " qos=" + qos);
    }

    // ─────────────────────────────────────────────────────────────
    // MqttCallback
    // ─────────────────────────────────────────────────────────────

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("⚠️  MQTT 連線中斷: " + cause.getMessage() + "，5 秒後重連...");
        scheduleConnect();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());

        // 無論格式為何，完整印出原始訊息
        System.out.println("📨 MQTT 原始訊息:");
        System.out.println("  topic: " + topic);
        System.out.println("  長度: " + payload.length() + " bytes");
        System.out.println("  內容: " + payload);

        MqttDecodeRequest request;
        try {
            request = objectMapper.readValue(payload, MqttDecodeRequest.class);
        } catch (Exception e) {
            System.out.println("⚠️  格式不符，略過（非本系統訊息）");
            return;
        }

        // 基本驗證
        if (request.getMacAddress() == null || request.getMacAddress().isBlank()) {
            System.out.println("⚠️  MQTT 訊息缺少 mac_address，略過");
            return;
        }
        if (request.getDataPayload() == null || request.getDataPayload().isEmpty()) {
            System.out.println("⚠️  MQTT 訊息缺少 data_payload，略過");
            return;
        }

        // 計算總封包數
        int totalPackets = request.getDataPayload().stream()
                .mapToInt(p -> p.getPackets() == null ? 0 : p.getPackets().size())
                .sum();
        System.out.println("✅ 格式正確，開始解碼");
        System.out.println("  mac: " + request.getMacAddress());
        System.out.println("  packets: " + totalPackets + " 筆");

        try {
            // 解碼（共用 MqttProcessingService 的 animalTypeCache）
            int animalType = processingService.resolveAnimalType(request.getMacAddress());
            DecodeResponse.HealthData healthData = decoderService.decodeMqtt(
                    request.getMacAddress(), request.getDataPayload(), animalType);

            // 轉送至 .NET WebAPI
            processingService.forwardToNetApi(
                    request.getMacAddress(), request.getDataPayload(), healthData);

        } catch (RuntimeException e) {
            System.out.println("❌ MQTT 訊息解碼失敗: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("❌ MQTT 訊息處理例外: " + e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 訂閱方不發布訊息，不需處理
    }
}
