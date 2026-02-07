package free.svoss.tools.jRag;

import java.util.List;

/**
 * Represents a response from the Ollama language model API.
 *
 * This record encapsulates both the generated answer and the context vector
 * that can be used to maintain conversation state across multiple turns.
 *
 * @param answer The generated text response from the language model
 * @param contextVector The context vector representing the conversation state,
 *                      which can be passed back to the model for subsequent
 *                      requests to maintain conversation continuity
 */
public record OllamaResponse(String answer, List<Integer> contextVector) {

    /**
     * Constructs an OllamaResponse with the given answer and context vector.
     *
     * @param answer The generated text response
     * @param contextVector The context vector for conversation continuity
     */
    public OllamaResponse {
        // Record constructor body allows for validation if needed
        // For now, we'll just document the parameters
    }

    /**
     * Returns a string representation of the response for debugging purposes.
     * Shows the answer and whether a context vector is present.
     *
     * @return A formatted string representation
     */
    @Override
    public String toString() {
        int contextSize = (contextVector != null) ? contextVector.size() : 0;
        String answerPreview = (answer != null && answer.length() > 50)
                ? answer.substring(0, 50) + "..."
                : answer;

        return String.format("OllamaResponse[answer='%s', contextSize=%d]",
                answerPreview, contextSize);
    }
}