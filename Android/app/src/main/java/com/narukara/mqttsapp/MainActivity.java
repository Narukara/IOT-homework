package com.narukara.mqttsapp;

import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.paho.client.mqttv3.MqttException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClick(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
//                    PublishClient publishClient = new PublishClient("ssl://", "", "", "", getSSLSocketFactory());
//                    publishClient.publish("test", "hello", 0);
//                    publishClient.close();
                    SubscribeClient subscribeClient = new SubscribeClient("ssl://", "", "", "", getSSLSocketFactory(), (TextView) findViewById(R.id.textView));
                    subscribeClient.subscribe("/esp32/pub", 0);
                } catch (Exception e) {
                    Snackbar.make(findViewById(R.id.bg), e.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            }
        }).start();
    }

    public static SSLSocketFactory getSingleSocketFactory(InputStream interMediateCrtFileInputStream) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
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

    public SSLSocketFactory getSSLSocketFactory() {
        try (InputStream inputStream = getResources().getAssets().open("ca.crt")) {
            return getSingleSocketFactory(inputStream);
        } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            Snackbar.make(findViewById(R.id.bg), e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
        return null;
    }

}