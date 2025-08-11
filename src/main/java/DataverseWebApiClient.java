import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Map;
import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DataverseWebApiClient {

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String dataverseUrl;
    private final String tokenEndpoint;
    private final Properties config;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataverseWebApiClient() throws Exception {
        this.config = loadConfiguration();
        this.tenantId = resolveProperty("azure.tenant.id");
        this.clientId = resolveProperty("azure.client.id");
        this.clientSecret = resolveProperty("azure.client.secret");
        this.dataverseUrl = resolveProperty("dataverse.org.url");
        
        if (tenantId == null || clientId == null || clientSecret == null || dataverseUrl == null) {
            throw new IllegalStateException("Missing required configuration. Check environment variables.");
        }
        
        this.tokenEndpoint = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
    }

    public DataverseWebApiClient(String tenantId, String clientId, String clientSecret, String dataverseUrl) {
        this.config = null;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.dataverseUrl = dataverseUrl;
        this.tokenEndpoint = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
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

    private String getAccessToken() throws Exception {
        String params = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8.name()) +
                        "&scope=" + URLEncoder.encode(dataverseUrl + "/.default", StandardCharsets.UTF_8.name()) +
                        "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8.name()) +
                        "&grant_type=client_credentials";

        byte[] postData = params.getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(tokenEndpoint).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", Integer.toString(postData.length));

        try(OutputStream os = conn.getOutputStream()) {
            os.write(postData);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            
            // Simple JSON token extraction
            String json = response.toString();
            String token = json.split("\"access_token\":\"")[1].split("\"")[0];
            return token;
        } else {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            throw new RuntimeException("Failed to get access token: HTTP " + responseCode + " - " + errorResponse.toString());
        }
    }

    public String executeGet(String endpoint) throws Exception {
        String accessToken = getAccessToken();
        String requestUrl = dataverseUrl + "/api/data/v9.2/" + endpoint;

        HttpURLConnection conn = (HttpURLConnection) new URL(requestUrl).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("OData-Version", "4.0");
        conn.setRequestProperty("OData-MaxVersion", "4.0");
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } else {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            throw new RuntimeException("HTTP " + responseCode + " - " + errorResponse.toString());
        }
    }

    public String executePost(String endpoint, String jsonBody) throws Exception {
        String accessToken = getAccessToken();
        String requestUrl = dataverseUrl + "/api/data/v9.2/" + endpoint;

        HttpURLConnection conn = (HttpURLConnection) new URL(requestUrl).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("OData-Version", "4.0");
        conn.setRequestProperty("OData-MaxVersion", "4.0");

        if (jsonBody != null) {
            byte[] postData = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
            
            try(OutputStream os = conn.getOutputStream()) {
                os.write(postData);
            }
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } else {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            throw new RuntimeException("HTTP " + responseCode + " - " + errorResponse.toString());
        }
    }

    public boolean testConnection() {
        try {
            String response = executeGet("WhoAmI()");
            System.out.println("Connection test successful!");
            System.out.println("WhoAmI response: " + response);
            return true;
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    public String getContacts(int top) throws Exception {
        return executeGet("contacts?$top=" + top + "&$select=fullname,emailaddress1,contactid");
    }

    public String getAccounts(int top) throws Exception {
        return executeGet("accounts?$top=" + top + "&$select=name,accountid");
    }

    public String getTableMetadata(String logicalName) throws Exception {
        return executeGet("EntityDefinitions(LogicalName='" + logicalName + "')?$select=LogicalName,SchemaName,PrimaryIdAttribute,PrimaryNameAttribute&$expand=Attributes($select=LogicalName,AttributeType,SchemaName,IsPrimaryId,IsPrimaryName)");
    }

    private String buildQueryUrl(String table, Map<String, String> params) throws Exception {
        StringBuilder url = new StringBuilder(table);
        if (params != null && !params.isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) url.append("&");
                url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
                url.append("=");
                url.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
                first = false;
            }
        }
        return url.toString();
    }

    public JsonNode query(String table, String select, String filter) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        if (select != null && !select.isBlank()) params.put("$select", select);
        if (filter != null && !filter.isBlank()) params.put("$filter", filter);
        String queryUrl = buildQueryUrl(table, params);
        String json = executeGet(queryUrl);
        return mapper.readTree(json);
    }

    public void performBasicOperations() {
        try {
            System.out.println("=== Testing Dataverse Web API Connection ===");
            
            // Test connection
            if (!testConnection()) {
                System.err.println("Connection test failed, aborting operations");
                return;
            }
            
            System.out.println("\n=== Getting Top 5 Accounts ===");
            String accounts = getAccounts(5);
            System.out.println("Accounts: " + accounts);
            
            System.out.println("\n=== Getting Top 5 Contacts ===");
            String contacts = getContacts(5);
            System.out.println("Contacts: " + contacts);
            
            System.out.println("\n=== Getting Contact Table Metadata ===");
            String metadata = getTableMetadata("contact");
            System.out.println("Contact metadata: " + metadata);
            
        } catch (Exception e) {
            System.err.println("Error performing operations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            DataverseWebApiClient client = new DataverseWebApiClient();
            
            // Test connection first
            if (!client.testConnection()) {
                System.err.println("Connection failed, exiting");
                return;
            }
            
            // Check if table name is provided as command line argument
            String tableName = "contacts"; // default
            if (args.length > 0) {
                tableName = args[0];
            }
            
            System.out.println("\n=== Querying table: " + tableName + " ===");
            
            // Query the specified table
            JsonNode result = client.query(tableName, null, null);
            System.out.println("Query result:");
            System.out.println(result.toPrettyString());
            
            // Show record count
            if (result.has("value")) {
                System.out.println("\nFound " + result.get("value").size() + " records");
            }
            
            // Example with select and filter
            if (tableName.equals("contacts")) {
                System.out.println("\n=== Example: Query contacts with select and filter ===");
                JsonNode filteredResult = client.query(
                    "contacts", 
                    "fullname,emailaddress1,contactid", 
                    "statecode eq 0"
                );
                System.out.println("Filtered contacts:");
                System.out.println(filteredResult.toPrettyString());
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}