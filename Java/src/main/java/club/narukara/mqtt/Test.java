package club.narukara.mqtt;

import org.eclipse.paho.client.mqttv3.MqttException;

public class Test {

    public static void main(String[] args) throws MqttException {
        String sub = "subClient", pub = "pubClient", broker = "mqtts://ip";
//        SubscribeClient subscribeClient = new SubscribeClient(broker, sub, sub, sub);
//        subscribeClient.subscribe("test", 1);
        PublishClient publishClient = new PublishClient(broker, pub, pub, pub);
        publishClient.publish("test", "hello,mqtt", 1);
        publishClient.close();
//        subscribeClient.close();
    }

}
