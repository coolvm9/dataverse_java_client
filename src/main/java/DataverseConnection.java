import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class DataverseConnection {
    
    private final DataverseClient client;
    private final Properties config;
    
    public DataverseConnection() throws Exception {
        this.config = loadConfiguration();
        this.client = createClient();
    }
    
    public DataverseConnection(String tenantId, String clientId, String clientSecret, String orgUrl) {
        this.config = null;
        this.client = new DataverseClient(tenantId, clientId, clientSecret, orgUrl);
    }
    
    private Properties loadConfiguration() throws Exception {
        Properties props = new Properties();
        props.load(getClass().getResourceAsStream("/application.properties"));
        return props;
    }
    
    private String resolveProperty(String key) {
        if (config == null) return null;
        String value = config.getProperty(key);
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String envVar = value.substring(2, value.length() - 1);
            return System.getenv(envVar);
        }
        return value;
    }
    
    private DataverseClient createClient() {
        String tenantId = resolveProperty("azure.tenant.id");
        String clientId = resolveProperty("azure.client.id");
        String clientSecret = resolveProperty("azure.client.secret");
        String orgUrl = resolveProperty("dataverse.org.url");
        
        if (tenantId == null || clientId == null || clientSecret == null || orgUrl == null) {
            throw new IllegalStateException("Missing required configuration. Check environment variables.");
        }
        
        return new DataverseClient(tenantId, clientId, clientSecret, orgUrl);
    }
    
    public JsonNode queryTable(String tableName) throws Exception {
        return client.query(tableName, null, null);
    }
    
    public JsonNode queryTable(String tableName, String select) throws Exception {
        return client.query(tableName, select, null);
    }
    
    public JsonNode queryTable(String tableName, String select, String filter) throws Exception {
        return client.query(tableName, select, filter);
    }
    
    public JsonNode getTableMetadata(String tableName) throws Exception {
        return client.getTableMetadata(tableName);
    }
    
    public Path downloadFile(String tableName, UUID recordId, String columnName, String outputPath) throws Exception {
        Path outFile = Paths.get(outputPath);
        return client.downloadFileColumn(tableName, recordId, columnName, outFile);
    }
    
    public List<Path> downloadAttachments(UUID recordId, String outputDirectory) throws Exception {
        Path outDir = Paths.get(outputDirectory);
        return client.downloadNoteAttachments(recordId, outDir);
    }
    
    public void testConnection() throws Exception {
        JsonNode result = client.query("systemusers", "fullname", "isdisabled eq false");
        System.out.println("Connection successful. Found " + result.get("value").size() + " active users.");
    }
    
    public static void main(String[] args) {
        try {
            DataverseConnection connection = new DataverseConnection();
            
            // Test the connection
            connection.testConnection();
            
            // Example usage
            JsonNode contacts = connection.queryTable(
                "contacts", 
                "fullname,emailaddress1,contactid", 
                "contains(fullname,'Test')"
            );
            
            System.out.println("Contacts found: " + contacts.get("value").size());
            System.out.println(contacts.toPrettyString());
            
            // Get table metadata
            JsonNode metadata = connection.getTableMetadata("contact");
            System.out.println("Contact table metadata retrieved");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}