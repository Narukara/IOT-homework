package club.narukara.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class SubscribeClient {
    private final MqttClient client;

    public SubscribeClient(String broker, String clientID, String userName, String password) throws MqttException {
        client = new MqttClient(broker, clientID, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setUserName(userName);
        options.setPassword(password.toCharArray());
        client.setCallback(new MqttCallback() {

            public void connectionLost(Throwable cause) {
                System.out.println("---connection lost---");
            }

            public void messageArrived(String topic, MqttMessage message) {
                System.out.println("---message arrived---");
                System.out.println("topic:" + topic);
                System.out.println("Qos:" + message.getQos());
                System.out.println("message content:" + new String(message.getPayload()));
            }

            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("---deliveryComplete---\n" + token.isComplete());
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
