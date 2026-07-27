package com.demo.upimesh.crypto;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Holds the server's RSA keypair.
 *
 * Supports persistent Base64 RSA keys passed via environment variables (e.g. RSA_PRIVATE_KEY_BASE64)
 * or properties (`upi.mesh.rsa.private-key`, `upi.mesh.rsa.public-key`).
 * Falls back to auto-generating a fresh 2048-bit RSA keypair on startup for local dev & testing.
 */
@Component
public class ServerKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(ServerKeyHolder.class);

    @Value("${upi.mesh.rsa.private-key:}")
    private String configuredPrivateKeyBase64;

    @Value("${upi.mesh.rsa.public-key:}")
    private String configuredPublicKeyBase64;

    private KeyPair keyPair;

    @PostConstruct
    public void init() throws Exception {
        if (configuredPrivateKeyBase64 != null && !configuredPrivateKeyBase64.isBlank() &&
                configuredPublicKeyBase64 != null && !configuredPublicKeyBase64.isBlank()) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                byte[] privateBytes = Base64.getDecoder().decode(cleanKeyPem(configuredPrivateKeyBase64));
                PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateBytes);
                PrivateKey privateKey = keyFactory.generatePrivate(privateSpec);

                byte[] publicBytes = Base64.getDecoder().decode(cleanKeyPem(configuredPublicKeyBase64));
                X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicBytes);
                PublicKey publicKey = keyFactory.generatePublic(publicSpec);

                this.keyPair = new KeyPair(publicKey, privateKey);
                log.info("Server RSA keypair loaded successfully from environment/configuration");
                return;
            } catch (Exception e) {
                log.error("Failed to parse configured RSA keypair from environment: {}. Falling back to generated keypair.", e.getMessage());
            }
        }

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
        log.info("Server RSA keypair generated (2048-bit). Public key fingerprint: {}",
                getPublicKeyBase64().substring(0, 32) + "...");
    }

    private String cleanKeyPem(String key) {
        return key.replaceAll("-----\\w+ PRIVATE KEY-----", "")
                  .replaceAll("-----\\w+ PUBLIC KEY-----", "")
                  .replaceAll("\\s+", "");
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
