package example;

import momento.sdk.messages.CreateSigningKeyResponse;

import java.util.Map;

// Helper data class to store results and make retrieval easier
public class MomentoSigningKey {
    private final CreateSigningKeyResponse createSigningKeyResponse;
    private final Map<String, String> jsonMap;

    public MomentoSigningKey(CreateSigningKeyResponse createSigningKeyResponse, Map<String, String> jsonMap) {
        this.createSigningKeyResponse = createSigningKeyResponse;
        this.jsonMap = jsonMap;
    }

    public CreateSigningKeyResponse getCreateSigningKeyResponse() {
        return createSigningKeyResponse;
    }

    public Map<String, String> getJsonMap() {
        return jsonMap;
    }
}
