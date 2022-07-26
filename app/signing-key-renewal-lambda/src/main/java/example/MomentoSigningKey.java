package example;

import momento.sdk.messages.CreateSigningKeyResponse;

import java.util.Date;

// POJO class that ensures a `CreateSigningKeyResponse` can be properly serialized to SecretsManager.
// It's unfortunate we have to make an extra copy of data but this ensures the generated JSON maintains its structure
public class MomentoSigningKey {
    public static MomentoSigningKey fromCreateSigningResponse(CreateSigningKeyResponse response) {
        return new MomentoSigningKey(response.getKeyId(),
                response.getEndpoint(),
                response.getKey(),
                response.getExpiresAt()
        );
    }
    private String keyId;
    private String endpoint;
    private String key;
    private Date expiresAt;

    public MomentoSigningKey(String keyId, String endpoint, String key, Date expiresAt) {
        this.keyId = keyId;
        this.endpoint = endpoint;
        this.key = key;
        this.expiresAt = expiresAt;
    }

    public String getKeyId() {
        return this.keyId;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public String getKey() {
        return this.key;
    }

    public Date getExpiresAt() {
        return this.expiresAt;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }
}
