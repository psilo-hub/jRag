package free.svoss.tools.jRag;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Retrieval-Augmented Generation (RAG) system implementation.
 *
 * This class combines document retrieval with language model generation
 * to provide context-aware responses. It maintains:
 * 1. A vector store for document similarity search
 * 2. Conversation history for context continuity
 * 3. Integration with Ollama for embeddings and generation
 */
public class RAG {

    /** Default chunk size in characters for document splitting */
    private static final int DEFAULT_CHUNK_SIZE = 600;

    /** Default chunk overlap in characters for context preservation */
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    /** Default number of documents to retrieve for context */
    private static final int DEFAULT_DOCS_TO_RETRIEVE = 4;

    /** Default embedding model name */
    private static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text:latest";

    /** Default Ollama server URL */
    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";

    /** User agent string for HTTP requests when fetching online documents */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0";

    private final int chunkSize;
    private final int chunkOverlap;
    private final String dbPath;
    private final String embeddingModel;
    private final String llm;
    private final String ollamaUrl;
    private final int nrDocsToRetrieve;

    private final LuceneVectorStore vectorStore;
    private final OllamaClient ollamaClient;
    private final TikaService tikaService;

    /** Conversation history for maintaining context across interactions */
    private List<Integer> conversationHistory = null;

    /**
     * Constructs a RAG system with specified parameters.
     *
     * @param chunkSize Size of text chunks in characters
     * @param chunkOverlap Overlap between chunks in characters
     * @param dbPath File system path for the vector store index
     * @param embeddingModel Name of the model to use for embeddings
     * @param llm Name of the language model for text generation
     * @param ollamaUrl URL of the Ollama server
     * @param nrDocsToRetrieve Number of documents to retrieve for context
     * @throws IOException If vector store initialization fails
     * @throws RuntimeException If Ollama server is unreachable
     */
    public RAG(int chunkSize, int chunkOverlap, String dbPath,
               String embeddingModel, String llm, String ollamaUrl,
               int nrDocsToRetrieve) throws IOException {

        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.dbPath = dbPath;
        this.embeddingModel = embeddingModel;
        this.llm = llm;
        this.ollamaUrl = ollamaUrl;
        this.nrDocsToRetrieve = nrDocsToRetrieve;

        this.tikaService = new TikaService();
        this.vectorStore = new LuceneVectorStore(dbPath);
        this.ollamaClient = initializeOllamaClient();

        validateOllamaConnection();
    }

    /**
     * Constructs a RAG system with default parameters.
     *
     * @param dbPath File system path for the vector store index
     * @param llm Name of the language model for text generation
     * @throws IOException If vector store initialization fails
     */
    public RAG(String dbPath, String llm) throws IOException {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, dbPath,
                DEFAULT_EMBEDDING_MODEL, llm, DEFAULT_OLLAMA_URL,
                DEFAULT_DOCS_TO_RETRIEVE);
    }

    /**
     * Initializes the Ollama client based on configured URL.
     *
     * @return Initialized OllamaClient instance
     */
    private OllamaClient initializeOllamaClient() {
        return ollamaUrl != null ? new OllamaClient(ollamaUrl) : new OllamaClient();
    }

    /**
     * Validates that the Ollama server is reachable.
     *
     * @throws RuntimeException If Ollama server is not reachable
     */
    private void validateOllamaConnection() {
        if (!ollamaClient.isReachable()) {
            throw new RuntimeException("Ollama server is not reachable at: " + ollamaUrl);
        }
        // TODO: Add model availability check here
    }

    /**
     * Clears the current conversation history.
     * This resets the context for the next interaction.
     */
    public void resetConversationHistory() {
        conversationHistory = null;
    }

    /**
     * Deletes all documents from the vector store.
     *
     * Warning: This operation is irreversible and removes all indexed documents.
     */
    public void emptyVectorStore() {
        try {
            Path rootPath = Path.of(dbPath);
            if (Files.exists(rootPath)) {
                Files.walk(rootPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to empty vector store: " + e.getMessage());
        }
    }

    /**
     * Adds an online document to the vector store by downloading and processing it.
     *
     * @param urlString URL of the document to add
     */
    public void addOnlineDocumentEmbedding(String urlString) {
        try {
            System.out.println("Fetching content from: " + urlString);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            Path tempFile = Files.createTempFile("rag-download-", ".tmp");

            HttpResponse<Path> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofFile(tempFile));

            if (response.statusCode() == 200) {
                addDocumentEmbedding(tempFile.toFile());
                Files.deleteIfExists(tempFile);
                System.out.println("Successfully added document from URL: " + urlString);
            } else {
                System.err.println("Failed to fetch URL. Status code: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Error processing URL '" + urlString + "': " + e.getMessage());
        }
    }

    /**
     * Adds a local file to the vector store.
     *
     * @param file The file to add (must be readable)
     * @throws RuntimeException If the file cannot be read or processed
     */
    public void addDocumentEmbedding(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("File is not readable or does not exist: " + file);
        }

        try {
            String documentText = tikaService.getText(file)
                    .replaceAll("\\n\\s*\\n", "\n")  // Normalize multiple newlines
                    .trim();

            if (documentText.isEmpty()) {
                System.err.println("Warning: Document is empty: " + file.getName());
                return;
            }

            System.out.println("Document length: " + documentText.length() + " characters");
            addDocumentEmbedding(documentText);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Processes and embeds document text into the vector store.
     * Long documents are automatically split into chunks with overlap.
     *
     * @param documentText The text content to embed
     */
    private void addDocumentEmbedding(String documentText) {
        if (documentText == null || documentText.isEmpty()) {
            System.err.println("Cannot embed empty document");
            return;
        }

        try {
            if (documentText.length() <= chunkSize) {
                float[] embedding = ollamaClient.getEmbedding(documentText, embeddingModel);
                vectorStore.addDocument(documentText, embedding);
            } else {
                System.out.println("Splitting document into chunks...");
                List<String> chunks = TextSplitter.splitText(documentText, chunkSize, chunkOverlap);
                System.out.println("Created " + chunks.size() + " chunks");

                for (String chunk : chunks) {
                    float[] embedding = ollamaClient.getEmbedding(chunk, embeddingModel);
                    vectorStore.addDocument(chunk, embedding);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to embed document: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generates an embedding vector for the given text.
     *
     * @param text The text to embed
     * @return The embedding vector
     * @throws IOException If embedding generation fails
     * @throws InterruptedException If the request is interrupted
     */
    public float[] getEmbedding(String text) throws IOException, InterruptedException {
        return ollamaClient.getEmbedding(text, embeddingModel);
    }

    /**
     * Retrieves relevant documents for a query embedding.
     *
     * @param queryEmbedding The query embedding vector
     * @param numberOfDocs Number of documents to retrieve (if null, uses default)
     * @return List of relevant document texts
     * @throws IOException If vector store search fails
     */
    public List<String> retrieveDocs(float[] queryEmbedding, Integer numberOfDocs) throws IOException {
        int docsToRetrieve = (numberOfDocs != null && numberOfDocs > 0)
                ? numberOfDocs : nrDocsToRetrieve;
        return vectorStore.search(queryEmbedding, docsToRetrieve);
    }

    /**
     * Retrieves documents using the default number of documents.
     *
     * @param queryEmbedding The query embedding vector
     * @return List of relevant document texts
     * @throws IOException If vector store search fails
     */
    public List<String> retrieveDocs(float[] queryEmbedding) throws IOException {
        return retrieveDocs(queryEmbedding, null);
    }

    /**
     * Generates an answer using retrieved context and conversation history.
     * Updates conversation history with the new context vector.
     *
     * @param contextBlock Retrieved document context
     * @param query User's query
     * @return Response containing answer and updated conversation context
     * @throws IOException If generation fails
     * @throws InterruptedException If the request is interrupted
     */
    public OllamaResponse generateAnswer(String contextBlock, String query)
            throws IOException, InterruptedException {

        OllamaResponse response = ollamaClient.generateAnswer(llm, contextBlock, query, conversationHistory);
        conversationHistory = response.contextVector();
        return response;
    }
}