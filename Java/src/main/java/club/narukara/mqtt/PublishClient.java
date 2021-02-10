package club.narukara.mqtt;

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

    public PublishClient(String broker, String clientID, String userName, String password) throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        client = new MqttClient(broker, clientID, persistence);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setUserName(userName);
        options.setPassword(password.toCharArray());
        options.setSocketFactory(SSLSocketFactory.getDefault());
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

    public static SSLSocketFactory getSingleSocketFactory(InputStream interMediateCrtFileInputStream) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        X509Certificate caCert = null;

        BufferedInputStream bis = new BufferedInputStream(interMediateCrtFileInputStream);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        while (bis.available() > 0) {
            caCert = (X509Certificate) cf.generateCertificate(bis);
        }
        KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
        caKs.load(null, null);
        caKs.setCertificateEntry("ca-certificate", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(caKs);
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext.getSocketFactory();
    }
}
