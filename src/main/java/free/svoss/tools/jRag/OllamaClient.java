package free.svoss.tools.jRag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for interacting with the Ollama API.
 *
 * This class provides methods to:
 * 1. Check server availability
 * 2. Retrieve available language models
 * 3. Generate text embeddings
 * 4. Generate text completions with conversation context
 */
public class OllamaClient {

    /** Default Ollama server URL */
    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";

    /** API endpoint for listing available models */
    private static final String TAGS_ENDPOINT = "/api/tags";

    /** API endpoint for generating embeddings */
    private static final String EMBEDDINGS_ENDPOINT = "/api/embeddings";

    /** API endpoint for generating text completions */
    private static final String GENERATE_ENDPOINT = "/api/generate";

    /** HTTP timeout duration in seconds */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(300);

    /** HTTP client connection timeout in seconds */
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);

    /** JSON content type header */
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final String ollamaBaseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Constructs an OllamaClient with a custom server URL.
     *
     * @param ollamaUrl The base URL of the Ollama server
     */
    public OllamaClient(String ollamaUrl) {
        this.ollamaBaseUrl = ollamaUrl != null ? ollamaUrl : DEFAULT_OLLAMA_URL;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECTION_TIMEOUT)
                .build();
    }

    /**
     * Constructs an OllamaClient with the default localhost URL.
     */
    public OllamaClient() {
        this(DEFAULT_OLLAMA_URL);
    }

    /**
     * Checks if the Ollama server is reachable and responding.
     *
     * @return true if the server responds with HTTP 200, false otherwise
     */
    public boolean isReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl))
                    .timeout(CONNECTION_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            // Server is unreachable or connection timed out
            return false;
        }
    }

    /**
     * Retrieves all available models from the Ollama server.
     *
     * @return List of available model names
     * @throws IOException If an I/O error occurs during the HTTP request
     * @throws InterruptedException If the HTTP request is interrupted
     */
    public List<String> getAvailableModels() throws IOException, InterruptedException {
        HttpRequest request = createGetRequest(TAGS_ENDPOINT);
        HttpResponse<String> response = executeRequest(request);

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch models. HTTP " +
                    response.statusCode() + ": " + response.body());
        }

        return parseModelList(response.body());
    }

    /**
     * Generates an embedding vector for the given text using the specified model.
     *
     * @param text The text to embed
     * @param embeddingModel The model to use for embedding generation
     * @return The embedding vector as an array of floats
     * @throws IOException If an I/O error occurs during the HTTP request
     * @throws InterruptedException If the HTTP request is interrupted
     */
    public float[] getEmbedding(String text, String embeddingModel)
            throws IOException, InterruptedException {

        Map<String, String> requestBody = Map.of(
                "model", embeddingModel,
                "prompt", text
        );

        HttpRequest request = createPostRequest(EMBEDDINGS_ENDPOINT, requestBody);
        HttpResponse<String> response = executeRequest(request);

        if (response.statusCode() != 200) {
            throw new IOException("Embedding generation failed. HTTP " +
                    response.statusCode() + ": " + response.body());
        }

        return parseEmbeddingResponse(response.body());
    }

    /**
     * Generates a response from the language model using RAG context and conversation history.
     *
     * @param model The language model to use
     * @param ragContext Retrieved text chunks for context
     * @param query The user's question
     * @param conversationContext Previous conversation context (null for first turn)
     * @return An OllamaResponse containing the answer and updated context
     * @throws IOException If an I/O error occurs during the HTTP request
     * @throws InterruptedException If the HTTP request is interrupted
     */
    public OllamaResponse generateAnswer(String model, String ragContext, String query,
                                         List<Integer> conversationContext)
            throws IOException, InterruptedException {

        String prompt = buildPrompt(ragContext, query);
        Map<String, Object> requestBody = buildGenerateRequestBody(model, prompt, conversationContext);

        HttpRequest request = createPostRequest(GENERATE_ENDPOINT, requestBody);
        HttpResponse<String> response = executeRequest(request);

        if (response.statusCode() != 200) {
            throw new IOException("Text generation failed. HTTP " +
                    response.statusCode() + ": " + response.body());
        }

        return parseGenerateResponse(response.body());
    }

    /**
     * Creates an HTTP GET request to the specified endpoint.
     */
    private HttpRequest createGetRequest(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + endpoint))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
    }

    /**
     * Creates an HTTP POST request with JSON body to the specified endpoint.
     */
    private HttpRequest createPostRequest(String endpoint, Object requestBody)
            throws IOException {

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        return HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + endpoint))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    /**
     * Executes an HTTP request and returns the response.
     */
    private HttpResponse<String> executeRequest(HttpRequest request)
            throws IOException, InterruptedException {

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Parses the model list from the API response.
     */
    private List<String> parseModelList(String jsonResponse) throws IOException {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode modelsNode = root.get("models");

        List<String> modelNames = new ArrayList<>();
        if (modelsNode != null && modelsNode.isArray()) {
            for (JsonNode model : modelsNode) {
                String name = model.get("name").asText();
                if (name != null && !name.isEmpty()) {
                    modelNames.add(name);
                }
            }
        }
        return modelNames;
    }

    /**
     * Parses the embedding response to extract the vector array.
     */
    private float[] parseEmbeddingResponse(String jsonResponse) throws IOException {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode embeddingNode = root.get("embedding");

        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new IOException("Invalid embedding response: missing or malformed embedding array");
        }

        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }

    /**
     * Builds the prompt for text generation using RAG context.
     */
    private String buildPrompt(String ragContext, String query) {
        return String.format(
                "Relevant Information:\n%s\n\nUser Question: %s\n\nAnswer:",
                ragContext, query
        );
    }

    /**
     * Builds the request body for the generate API call.
     */
    private Map<String, Object> buildGenerateRequestBody(String model, String prompt,
                                                         List<Integer> conversationContext) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        if (conversationContext != null && !conversationContext.isEmpty()) {
            requestBody.put("context", conversationContext);
        }

        return requestBody;
    }

    /**
     * Parses the generate API response to create an OllamaResponse object.
     */
    private OllamaResponse parseGenerateResponse(String jsonResponse) throws IOException {
        JsonNode root = objectMapper.readTree(jsonResponse);

        String answerText = root.get("response").asText();
        List<Integer> newContextVector = parseContextVector(root.get("context"));

        return new OllamaResponse(answerText, newContextVector);
    }

    /**
     * Parses the context vector from the API response.
     */
    private List<Integer> parseContextVector(JsonNode contextNode) {
        List<Integer> contextVector = new ArrayList<>();

        if (contextNode != null && contextNode.isArray()) {
            for (JsonNode id : contextNode) {
                contextVector.add(id.asInt());
            }
        }

        return contextVector;
    }
}