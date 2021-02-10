package com.narukara.mqttsapp;

import android.widget.TextView;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.SocketFactory;

public class SubscribeClient {
    private final MqttClient client;

    public SubscribeClient(String broker, String clientID, String userName, String password, SocketFactory socketFactory, final TextView textView) throws MqttException {
        client = new MqttClient(broker, clientID, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setUserName(userName);
        options.setPassword(password.toCharArray());
        options.setSocketFactory(socketFactory);
        client.setCallback(new MqttCallback() {

            public void connectionLost(final Throwable cause) {
                textView.post(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(cause.getMessage());
                    }
                });
            }

            public void messageArrived(final String topic, final MqttMessage message) {
                textView.post(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(topic + "\n" + new String(message.getPayload()));
                    }
                });
            }

            public void deliveryComplete(IMqttDeliveryToken token) {
            }

        });
        client.connect(options);
    }

    public void subscribe(String topic, int qos) throws MqttException {
        client.subscribe(topic, qos);
    }

    public void close() throws MqttException {
        client.unsubscribe("#");
        client.disconnect();
        client.close();
    }
}
