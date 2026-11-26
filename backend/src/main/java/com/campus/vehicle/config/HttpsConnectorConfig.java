package com.campus.vehicle.config;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 可选的 HTTPS 连接器：HTTP 端口继续保留，额外开一个 HTTPS 端口。
 * 手机浏览器只有 HTTPS 才允许 getUserMedia 实时预览。
 *
 * 通过环境变量开启：
 *   CAMPUS_HTTPS_ENABLED=true
 *   CAMPUS_HTTPS_PORT=8443
 *   CAMPUS_HTTPS_KEY_STORE=C:/campus-vehicle/keystore.p12
 *   CAMPUS_HTTPS_KEY_STORE_PASSWORD=...
 */
@Configuration
public class HttpsConnectorConfig {

    @Value("${campus.https.enabled:false}")
    private boolean enabled;

    @Value("${campus.https.port:8443}")
    private int port;

    @Value("${campus.https.key-store:}")
    private String keyStore;

    @Value("${campus.https.key-store-password:}")
    private String keyStorePassword;

    @Value("${campus.https.key-store-type:PKCS12}")
    private String keyStoreType;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpsConnectorCustomizer() {
        return factory -> {
            if (!enabled) {
                return;
            }
            if (!StringUtils.hasText(keyStore) || !StringUtils.hasText(keyStorePassword)) {
                throw new IllegalStateException(
                        "HTTPS 已开启，但未配置 campus.https.key-store 或 key-store-password");
            }

            SSLHostConfig sslHostConfig = new SSLHostConfig();
            SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(
                    sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
            certificate.setCertificateKeystoreFile(keyStore);
            certificate.setCertificateKeystorePassword(keyStorePassword);
            certificate.setCertificateKeystoreType(keyStoreType);
            sslHostConfig.addCertificate(certificate);

            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(port);
            connector.setSecure(true);
            connector.setScheme("https");
            connector.setProperty("SSLEnabled", "true");
            connector.setProperty("sslProtocol", "TLS");
            connector.addSslHostConfig(sslHostConfig);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
