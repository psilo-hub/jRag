package free.svoss.tools.jRag;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.AccessChecker;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Service for extracting text content from various file formats using Apache Tika.
 *
 * This class provides functionality to parse documents (PDF, Word, HTML, etc.)
 * and extract plain text while handling common issues like PDF security restrictions
 * and multi-column layouts.
 */
public class TikaService {

    /** Size limit for the Tika content handler (-1 for unlimited) */
    private static final int CONTENT_HANDLER_SIZE_LIMIT = -1;

    /** Whether to bypass PDF access restrictions */
    private static final boolean BYPASS_PDF_ACCESS_CHECKS = true;

    /** Whether to sort PDF content by position (useful for multi-column layouts) */
    private static final boolean SORT_PDF_BY_POSITION = true;

    /** Debug mode flag - set to false for production */
    private static final boolean DEBUG_MODE = false;

    /**
     * Extracts text content from a file using Apache Tika's auto-detection.
     *
     * This method can handle various file formats including:
     * - PDF (with special handling for security and layout)
     * - Microsoft Office documents (Word, Excel, PowerPoint)
     * - HTML, XML, plain text
     * - And many other formats supported by Tika
     *
     * @param file The file to extract text from
     * @return The extracted text content, trimmed and normalized
     * @throws Exception If the file cannot be read or parsed
     */
    public String getText(File file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        if (!file.exists() || !file.canRead()) {
            throw new IllegalArgumentException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }

        BodyContentHandler handler = new BodyContentHandler(CONTENT_HANDLER_SIZE_LIMIT);
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();

        configurePdfParsing(context);

        try (InputStream stream = new FileInputStream(file)) {
            parser.parse(stream, handler, metadata, context);
        }

        String extractedText = handler.toString();
        String normalizedText = normalizeExtractedText(extractedText);

        validateExtractedContent(normalizedText, file, metadata);

        if (DEBUG_MODE) {
            logDebugInfo(file, metadata, normalizedText);
        }

        return normalizedText;
    }

    /**
     * Configures PDF parsing options for better text extraction.
     *
     * @param context The ParseContext to configure with PDF settings
     */
    private void configurePdfParsing(ParseContext context) {
        PDFParserConfig pdfConfig = new PDFParserConfig();

        // Bypass PDF security/accessibility restrictions if configured
        if (BYPASS_PDF_ACCESS_CHECKS) {
            pdfConfig.setAccessChecker(new AccessChecker(false));
        }

        // Sort content by position (useful for multi-column documents like resumes)
        if (SORT_PDF_BY_POSITION) {
            pdfConfig.setSortByPosition(SORT_PDF_BY_POSITION);
        }

        context.set(PDFParserConfig.class, pdfConfig);
    }

    /**
     * Normalizes extracted text by removing excessive whitespace.
     *
     * @param text The raw extracted text
     * @return Normalized text with consistent line breaks and trimmed
     */
    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }

        // Replace multiple newlines with a single newline
        // This preserves paragraph breaks while removing excessive whitespace
        String normalized = text.replaceAll("\\n\\s*\\n+", "\n\n");

        // Trim leading/trailing whitespace
        return normalized.trim();
    }

    /**
     * Validates that content was successfully extracted from the file.
     *
     * @param extractedText The extracted text content
     * @param file The source file
     * @param metadata The Tika metadata for the file
     */
    private void validateExtractedContent(String extractedText, File file, Metadata metadata) {
        if (extractedText == null || extractedText.isEmpty()) {
            System.err.printf(
                    "Warning: No text content extracted from file: %s (Content-Type: %s)%n",
                    file.getName(),
                    metadata.get("Content-Type")
            );
        } else if (extractedText.length() < 10) {
            // Very short content might indicate extraction issues
            System.err.printf(
                    "Warning: Minimal text extracted (%d characters) from file: %s%n",
                    extractedText.length(),
                    file.getName()
            );
        }
    }

    /**
     * Logs debug information about the extraction process.
     *
     * @param file The processed file
     * @param metadata Tika metadata from the file
     * @param extractedText The extracted text content
     */
    private void logDebugInfo(File file, Metadata metadata, String extractedText) {
        System.out.println("=== Tika Text Extraction Debug ===");
        System.out.println("File: " + file.getAbsolutePath());
        System.out.println("Content-Type: " + metadata.get("Content-Type"));
        System.out.println("Extracted Length: " + extractedText.length());
        System.out.println("First 200 chars: " +
                (extractedText.length() > 200 ?
                        extractedText.substring(0, 200) + "..." :
                        extractedText));
        System.out.println("================================");
    }

    /**
     * Gets the MIME type of a file using Tika's metadata.
     *
     * @param file The file to analyze
     * @return The detected MIME type, or "application/octet-stream" if unknown
     * @throws Exception If the file cannot be read
     */
    public String getMimeType(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File does not exist or cannot be read");
        }

        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();

        // We only need metadata, not the full content
        try (InputStream stream = new FileInputStream(file)) {
            parser.parse(stream, new BodyContentHandler(0), metadata, context);
        }

        String mimeType = metadata.get("Content-Type");
        return mimeType != null ? mimeType : "application/octet-stream";
    }
}