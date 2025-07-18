package com.example.iplan.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "security.aes")
public class AES256Encryptor {

    private String key;
    private String iv;

    private SecretKeySpec keySpec;
    private IvParameterSpec ivSpec;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
    }

    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decoded = Base64.getDecoder().decode(encryptedText);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // Config 주입용 Setter
    public void setKey(String key) {
        this.key = key;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    /**
     * 전체 JSON 구조를 암호화
     * @param data
     * @return
     * @throws Exception
     */
    public <T> String encryptJsonObject(T data) throws Exception {
        String json = objectMapper.writeValueAsString(data);
        return encrypt(json);
    }

    /**
     * 암호화된 JSON 문자열을 복호화해서 변환
     * @param encryptedJson
     * @return
     * @throws Exception
     */
    public <T> T decryptJsonObject(String encryptedJson, TypeReference<T> typeRef) throws Exception {
        String json = decrypt(encryptedJson);
        return objectMapper.readValue(json, typeRef);
    }
}
