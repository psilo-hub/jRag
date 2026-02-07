package free.svoss.tools.jRag;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for splitting text into overlapping chunks.
 *
 * This is used to break down large documents into smaller pieces that can be
 * processed by language models with limited context windows. Overlap between
 * chunks helps maintain context across chunk boundaries.
 */
public class TextSplitter {

    /**
     * Splits text into overlapping chunks of specified size.
     *
     * This method ensures that no chunk exceeds the maximum size while
     * maintaining continuity between chunks through overlapping text.
     *
     * @param text The complete text to split into chunks
     * @param chunkSize Maximum number of characters per chunk
     * @param chunkOverlap Number of characters to overlap between consecutive chunks
     * @return List of text chunks, or empty list if input text is null or empty
     * @throws IllegalArgumentException If parameters are invalid
     */
    public static List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        // Validate input parameters
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }

        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("Chunk overlap cannot be negative");
        }

        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "Chunk overlap must be less than chunk size. " +
                            "Overlap: " + chunkOverlap + ", Chunk size: " + chunkSize
            );
        }

        return getChunks(text, chunkSize, chunkOverlap);
    }

    private static List<String> getChunks(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        int textLength = text.length();
        int startIndex = 0;

        do {
            // Calculate end index for current chunk
            int endIndex = Math.min(startIndex + chunkSize, textLength);

            // Extract chunk
            String chunk = text.substring(startIndex, endIndex);
            chunks.add(chunk);

            // Move start index for next chunk, accounting for overlap
            startIndex += (chunkSize - chunkOverlap);

            // If we're at or beyond the end, stop processing
        } while (startIndex < textLength);
        return chunks;
    }

    /**
     * Splits text using default overlap of 20% of chunk size.
     *
     * @param text The complete text to split into chunks
     * @param chunkSize Maximum number of characters per chunk
     * @return List of text chunks
     */
    public static List<String> splitText(String text, int chunkSize) {
        int defaultOverlap = Math.max(1, chunkSize / 5); // 20% overlap by default
        return splitText(text, chunkSize, defaultOverlap);
    }

    /**
     * Splits text into sentences or logical chunks based on punctuation.
     * This is a more sophisticated alternative to fixed-size chunking.
     *
     * @param text The text to split
     * @param maxChunkSize Maximum characters per chunk
     * @return List of text chunks broken at sentence boundaries
     */
    public static List<String> splitTextBySentences(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty() || maxChunkSize <= 0) {
            return chunks;
        }

        // Simple sentence boundary detection (can be enhanced)
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            // If adding this sentence would exceed max size, start a new chunk
            if (currentChunk.length() + sentence.length() > maxChunkSize && !currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }

            if (!currentChunk.isEmpty()) {
                currentChunk.append(" ");
            }
            currentChunk.append(sentence);
        }

        // Add the last chunk if it contains text
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}