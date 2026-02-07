package free.svoss.tools.jRag;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Vector store implementation using Apache Lucene for similarity search.
 *
 * This class provides functionality to store and retrieve document vectors
 * using cosine similarity for nearest neighbor search.
 */
public class LuceneVectorStore implements AutoCloseable {

    /** Field name for storing document text content */
    private static final String CONTENT_FIELD = "content";

    /** Field name for storing vector embeddings */
    private static final String VECTOR_FIELD = "vector";

    private final String indexPath;
    private final Directory directory;
    private final StandardAnalyzer analyzer;

    /**
     * Constructs a LuceneVectorStore with the specified index path.
     *
     * @param indexPath The file system path where the Lucene index will be stored
     * @throws IOException If the directory cannot be opened
     */
    public LuceneVectorStore(String indexPath) throws IOException {
        this.indexPath = indexPath;
        this.directory = FSDirectory.open(Paths.get(indexPath));
        this.analyzer = new StandardAnalyzer();
    }

    /**
     * Adds a document with its vector embedding to the index.
     *
     * Note: This method creates a new IndexWriter for each document addition,
     * which may not be optimal for bulk operations. Consider batching
     * multiple documents for better performance.
     *
     * @param text The document text content
     * @param vector The vector embedding of the document
     * @throws IOException If an I/O error occurs during indexing
     */
    public void addDocument(String text, float[] vector) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            Document doc = createDocument(text, vector);
            writer.addDocument(doc);
            writer.commit();
        }
    }

    /**
     * Creates a Lucene document from text and vector data.
     *
     * @param text The document content
     * @param vector The vector embedding
     * @return A Lucene Document containing both fields
     */
    private Document createDocument(String text, float[] vector) {
        Document doc = new Document();

        // Store the text content for retrieval
        doc.add(new StoredField(CONTENT_FIELD, text));

        // Store the vector for similarity search using cosine similarity
        doc.add(new KnnFloatVectorField(VECTOR_FIELD, vector,
                VectorSimilarityFunction.COSINE));

        return doc;
    }

    /**
     * Searches for the k most similar documents to the query vector.
     *
     * @param queryVector The query vector to compare against stored documents
     * @param k The number of nearest neighbors to return
     * @return List of document contents sorted by similarity (most similar first)
     * @throws IOException If an I/O error occurs during search
     */
    public List<String> search(float[] queryVector, int k) throws IOException {
        List<String> results = new ArrayList<>();

        if (!DirectoryReader.indexExists(directory)) {
            System.out.println("Warning: No index found at " + indexPath);
            return results;
        }

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Query query = new KnnFloatVectorQuery(VECTOR_FIELD, queryVector, k);
            TopDocs topDocs = searcher.search(query, k);

            extractSearchResults(results, searcher, topDocs);
        }

        return results;
    }

    /**
     * Extracts document contents from search results.
     *
     * @param results List to populate with document contents
     * @param searcher The Lucene IndexSearcher
     * @param topDocs Search results containing score documents
     * @throws IOException If an I/O error occurs during document retrieval
     */
    private void extractSearchResults(List<String> results, IndexSearcher searcher,
                                      TopDocs topDocs) throws IOException {
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            String content = doc.get(CONTENT_FIELD);
            if (content != null) {
                results.add(content);
            }
        }
    }

    /**
     * Closes the Lucene directory and releases associated resources.
     *
     * @throws IOException If an I/O error occurs during closing
     */
    @Override
    public void close() throws IOException {
        directory.close();
    }
}