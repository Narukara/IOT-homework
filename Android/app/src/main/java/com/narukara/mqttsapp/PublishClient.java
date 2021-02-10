package com.narukara.mqttsapp;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.security.Security;

public class PublishClient {
    private final MqttClient client;

    public PublishClient(String broker, String clientID, String userName, String password, SSLSocketFactory sslSocketFactory) throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        client = new MqttClient(broker, clientID, persistence);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setUserName(userName);
        options.setPassword(password.toCharArray());
        options.setSocketFactory(sslSocketFactory);
        client.connect(options);
    }

    public void publish(String topic, String content, int qos) throws MqttException {
        MqttMessage message = new MqttMessage(content.getBytes());
        message.setQos(qos);
        client.publish(topic, message);
    }

    public void close() throws MqttException {
        client.disconnect();
        client.close();
    }


}
