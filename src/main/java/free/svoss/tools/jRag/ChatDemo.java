package free.svoss.tools.jRag;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive chat demo for the RAG (Retrieval-Augmented Generation) system.
 *
 * This program allows users to:
 * 1. Query documents using natural language
 * 2. Add new documents from local files or URLs
 * 3. Select from available language models
 */
public class ChatDemo {

    /**
     * Main entry point for the chat demo application.
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        RAG rag = initializeRagSystem();
        if (rag == null) {
            System.err.println("Failed to initialize RAG system. Exiting.");
            return;
        }

        runChatSession(rag);
    }

    /**
     * Initializes the RAG system with a language model.
     *
     * @return Initialized RAG instance, or null if initialization fails
     */
    private static RAG initializeRagSystem() {
        try {
            String selectedModel = selectLanguageModel("huihui_ai/phi4-mini-abliterated:latest");
            return new RAG("./lucene-vector-index", selectedModel);
        } catch (Exception e) {
            System.err.println("Error initializing RAG system: " + e.getMessage());
            return null;
        }
    }

    /**
     * Runs the interactive chat session with the user.
     *
     * @param rag The initialized RAG system
     */
    private static void runChatSession(RAG rag) {
        System.out.println("\n");
        printWelcomeMessage();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nYou: ");
                String userInput = scanner.nextLine().trim();

                if (shouldExit(userInput)) {
                    System.out.println("Ending chat session. Goodbye!");
                    break;
                }

                handleUserInput(rag, userInput);
            }
        } catch (Exception e) {
            System.err.println("Unexpected error in chat session: " + e.getMessage());
        }
    }

    /**
     * Prints the welcome message and available commands.
     */
    private static void printWelcomeMessage() {
        System.out.println("--- Chat Started (Type 'exit' to quit)       ---");
        System.out.println("--- Add documents with 'addFile <path>'      ---");
        System.out.println("--- Add online resources with 'addUrl <url>' ---");
    }

    /**
     * Checks if the user wants to exit the chat.
     *
     * @param input User input
     * @return true if user wants to exit, false otherwise
     */
    private static boolean shouldExit(String input) {
        return "exit".equalsIgnoreCase(input);
    }

    /**
     * Processes user input and executes appropriate actions.
     *
     * @param rag The RAG system
     * @param input User input string
     */
    private static void handleUserInput(RAG rag, String input) {
        if (input.startsWith("addFile ")) {
            addLocalDocument(rag, input.substring(8).trim());
        } else if (input.startsWith("addUrl ")) {
            addOnlineDocument(rag, input.substring(7).trim());
        } else {
            processQuery(rag, input);
        }
    }

    /**
     * Adds a document from the local filesystem.
     *
     * @param rag The RAG system
     * @param filePath Path to the local file
     */
    private static void addLocalDocument(RAG rag, String filePath) {
        try {
            rag.addDocumentEmbedding(new File(filePath));
            System.out.println("Document added: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to add document: " + e.getMessage());
        }
    }

    /**
     * Adds a document from an online resource.
     *
     * @param rag The RAG system
     * @param url URL of the online resource
     */
    private static void addOnlineDocument(RAG rag, String url) {
        try {
            rag.addOnlineDocumentEmbedding(url);
            System.out.println("Document added: " + url);
        } catch (Exception e) {
            System.err.println("Failed to add URL: " + e.getMessage());
        }
    }

    /**
     * Processes a natural language query against the RAG system.
     *
     * @param rag The RAG system
     * @param query User's natural language query
     */
    private static void processQuery(RAG rag, String query) {
        try {
            // Step 1: Convert query to embedding vector
            float[] queryEmbedding = rag.getEmbedding(query);

            // Step 2: Retrieve relevant document chunks
            List<String> retrievedDocs = rag.retrieveDocs(queryEmbedding);
            String context = String.join("\n", retrievedDocs);

            System.out.println("[System] Found " + retrievedDocs.size() + " relevant document chunks.");

            // Step 3: Generate answer using context and query
            OllamaResponse response = rag.generateAnswer(context, query);

            // Step 4: Display the answer
            System.out.println("Ollama: " + response.answer());
        } catch (Exception e) {
            System.err.println("Error processing query: " + e.getMessage());
        }
    }

    /**
     * Selects a language model from available options.
     *
     * @param preferredModel The default model to use if available
     * @return Selected model name
     */
    private static String selectLanguageModel(String preferredModel) {
        OllamaClient client = new OllamaClient();

        try {
            List<String> availableModels = client.getAvailableModels();

            // Use preferred model if available
            if (availableModels != null && availableModels.contains(preferredModel)) {
                return preferredModel;
            }

            // If no models available or preferred not found, show selection menu
            if (availableModels == null || availableModels.isEmpty()) {
                System.out.println("No models available. Using default: " + preferredModel);
                return preferredModel;
            }

            return promptUserForModelSelection(availableModels, preferredModel);

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to retrieve available models: " + e.getMessage());
            return preferredModel;
        }
    }

    /**
     * Presents model selection menu to user.
     *
     * @param models List of available models
     * @param defaultModel Default model to use if selection fails
     * @return Selected model name
     */
    private static String promptUserForModelSelection(List<String> models, String defaultModel) {
        System.out.println("\nAvailable models:");
        for (int i = 0; i < models.size(); i++) {
            System.out.printf("%d. %s%n", i + 1, models.get(i));
        }

        System.out.println("\nChoose a model number (or press Enter for default):");

        try {
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return defaultModel;
            }

            int selection = Integer.parseInt(input);
            if (selection >= 1 && selection <= models.size()) {
                return models.get(selection - 1);
            }

            System.out.println("Invalid selection. Using default model.");
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Using default model.");
        }

        return defaultModel;
    }
}