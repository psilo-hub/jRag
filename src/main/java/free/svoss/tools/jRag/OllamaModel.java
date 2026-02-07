package free.svoss.tools.jRag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a model available in the Ollama system.
 *
 * This class is used for JSON deserialization of model information
 * returned by the Ollama API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaModel {

    /** The name of the model (e.g., "llama3:latest") */
    private String name;

    /** The last modification timestamp of the model */
    @JsonProperty("modified_at")
    private String modifiedAt;

    /** The size of the model in bytes */
    private long size;

    /**
     * Default constructor for JSON deserialization.
     */
    public OllamaModel() {
        // Default constructor required for Jackson deserialization
    }

    /**
     * Gets the model name.
     *
     * @return The model name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the model name.
     *
     * @param name The model name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the modification timestamp of the model.
     *
     * @return The modification timestamp
     */
    public String getModifiedAt() {
        return modifiedAt;
    }

    /**
     * Sets the modification timestamp of the model.
     *
     * @param modifiedAt The modification timestamp to set
     */
    public void setModifiedAt(String modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    /**
     * Gets the size of the model in bytes.
     *
     * @return The model size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets the size of the model in bytes.
     *
     * @param size The model size in bytes to set
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Calculates the model size in gigabytes.
     *
     * @return The model size in gigabytes formatted to two decimal places
     */
    public double getSizeInGB() {
        return size / (1024.0 * 1024.0 * 1024.0);
    }

    /**
     * Returns a formatted string representation of the model.
     *
     * @return A string containing the model name and size in gigabytes
     */
    @Override
    public String toString() {
        return String.format("%-30s | Size: %.2f GB", name, getSizeInGB());
    }
}