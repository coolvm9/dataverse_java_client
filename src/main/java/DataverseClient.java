import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.*;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DataverseClient {

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String orgUrl;
    private final String apiBase;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataverseClient(String tenantId, String clientId, String clientSecret, String orgUrl) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.orgUrl = orgUrl.replaceAll("/+$", "");
        this.apiBase = this.orgUrl + "/api/data/v9.2";
    }

    private String getAccessToken() throws Exception {
        IClientCredential cred = ClientCredentialFactory.createFromSecret(clientSecret);
        ConfidentialClientApplication app = ConfidentialClientApplication
                .builder(clientId, cred)
                .authority("https://login.microsoftonline.com/" + tenantId)
                .build();
        Set<String> scopes = Set.of(orgUrl + "/.default");
        ClientCredentialParameters params = ClientCredentialParameters.builder(scopes).build();
        CompletableFuture<IAuthenticationResult> fut = app.acquireToken(params);
        return fut.get().accessToken();
    }

    private String getJson(String path, Map<String, String> queryParams) throws Exception {
        String token = getAccessToken();
        URIBuilder builder = new URIBuilder(apiBase + "/" + path);
        if (queryParams != null) {
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                builder.addParameter(e.getKey(), e.getValue());
            }
        }
        URI uri = builder.build();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(uri);
            get.addHeader("Authorization", "Bearer " + token);
            get.addHeader("Accept", "application/json");
            get.addHeader("OData-Version", "4.0");
            get.addHeader("OData-MaxVersion", "4.0");

            try (var response = client.execute(get)) {
                int code = response.getCode();
                HttpEntity entity = response.getEntity();
                String body = entity != null ? EntityUtils.toString(entity) : "";
                if (code >= 200 && code < 300) {
                    return body;
                } else {
                    throw new RuntimeException("HTTP " + code + " - " + body);
                }
            }
        }
    }

    public JsonNode query(String table, String select, String filter) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        if (select != null && !select.isBlank()) params.put("$select", select);
        if (filter != null && !filter.isBlank()) params.put("$filter", filter);
        String json = getJson(table, params);
        return mapper.readTree(json);
    }

    public JsonNode getTableMetadata(String logicalName) throws Exception {
        String path = "EntityDefinitions(LogicalName='" + logicalName + "')";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("$select", "LogicalName,SchemaName,PrimaryIdAttribute,PrimaryNameAttribute");
        params.put("$expand", "Attributes($select=LogicalName,AttributeType,SchemaName,IsPrimaryId,IsPrimaryName)");
        String json = getJson(path, params);
        return mapper.readTree(json);
    }

    public Path downloadFileColumn(String table, UUID rowId, String column, Path outFile) throws Exception {
        String token = getAccessToken();
        URI uri = new URI(apiBase + "/" + table + "(" + rowId + ")/" + column + "/$value");

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(uri);
            get.addHeader("Authorization", "Bearer " + token);

            try (var response = client.execute(get)) {
                int code = response.getCode();
                if (code >= 200 && code < 300) {
                    Files.createDirectories(outFile.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
                        response.getEntity().writeTo(fos);
                    }
                    return outFile;
                } else {
                    String err = EntityUtils.toString(response.getEntity());
                    throw new RuntimeException("HTTP " + code + " - " + err);
                }
            }
        }
    }

    public List<Path> downloadNoteAttachments(UUID rowId, Path outDir) throws Exception {
        Files.createDirectories(outDir);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("$select", "annotationid,filename,documentbody");
        params.put("$filter", "_objectid_value eq " + rowId + " and isdocument eq true");
        JsonNode json = mapper.readTree(getJson("annotations", params));

        List<Path> saved = new ArrayList<>();
        if (json.has("value")) {
            for (JsonNode note : json.get("value")) {
                String filename = note.has("filename") ? note.get("filename").asText() : "attachment.bin";
                String base64 = note.has("documentbody") ? note.get("documentbody").asText() : null;
                if (base64 != null) {
                    byte[] bytes = Base64.getDecoder().decode(base64);
                    Path file = outDir.resolve(filename);
                    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                        fos.write(bytes);
                    }
                    saved.add(file);
                }
            }
        }
        return saved;
    }

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.load(DataverseClient.class.getResourceAsStream("/application.properties"));
        
        String TENANT = System.getenv("AZ_TENANT_ID");
        String CLIENT = System.getenv("AZ_CLIENT_ID");
        String SECRET = System.getenv("AZ_CLIENT_SECRET");
        String ORGURL = System.getenv("DV_ORG_URL");

        DataverseClient dv = new DataverseClient(TENANT, CLIENT, SECRET, ORGURL);

        JsonNode result = dv.query(
                "contacts",
                "fullname,emailaddress1,contactid",
                "contains(fullname,'Acme')"
        );
        System.out.println(result.toPrettyString());

        JsonNode meta = dv.getTableMetadata("contact");
        System.out.println(meta.toPrettyString());
    }
}