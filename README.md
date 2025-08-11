# Dataverse Java Client

A Java client for connecting to Microsoft Dataverse (formerly Common Data Service) using OAuth2 authentication with Azure AD.

## Features

- OAuth2 authentication with Azure AD using client credentials flow
- Query Dataverse tables with OData filters
- Retrieve table metadata
- Download file columns
- Download note attachments

## Prerequisites

- Java 11 or higher
- Gradle 7+
- Azure AD app registration with appropriate Dataverse permissions

## Setup

### 1. Azure AD App Registration

1. Register an application in Azure AD
2. Grant the following permissions:
   - `Dynamics CRM` → `user_impersonation`
   - Or specific Dataverse permissions as needed
3. Create a client secret
4. Note down:
   - Tenant ID
   - Application (Client) ID
   - Client Secret
   - Dataverse Organization URL

### 2. Environment Configuration

#### Option A: IntelliJ IDEA Run Configuration (Recommended for Development)

1. Go to `Run` → `Edit Configurations`
2. Select your main class configuration (or create new)
3. In `Environment variables` field, add:
   ```
   AZ_TENANT_ID=your-tenant-id-here;AZ_CLIENT_ID=your-client-id-here;AZ_CLIENT_SECRET=your-client-secret-here;DV_ORG_URL=https://yourorg.crm.dynamics.com
   ```

#### Option B: Terminal Environment Variables

```bash
export AZ_TENANT_ID="your-tenant-id-here"
export AZ_CLIENT_ID="your-client-id-here"
export AZ_CLIENT_SECRET="your-client-secret-here"
export DV_ORG_URL="https://yourorg.crm.dynamics.com"
```

#### Option C: Environment Script (Recommended for Team Development)

Create `setenv.sh` (add to `.gitignore`):
```bash
#!/bin/bash
export AZ_TENANT_ID="your-tenant-id-here"
export AZ_CLIENT_ID="your-client-id-here"
export AZ_CLIENT_SECRET="your-client-secret-here"
export DV_ORG_URL="https://yourorg.crm.dynamics.com"
```

Run with: `source setenv.sh && ./gradlew run`

#### Option D: Pass at Runtime

```bash
AZ_TENANT_ID="your-tenant-id" AZ_CLIENT_ID="your-client-id" AZ_CLIENT_SECRET="your-secret" DV_ORG_URL="https://yourorg.crm.dynamics.com" ./gradlew run
```

## Usage

### Basic Usage

```java
DataverseClient client = new DataverseClient(tenantId, clientId, clientSecret, orgUrl);

// Query contacts
JsonNode contacts = client.query(
    "contacts", 
    "fullname,emailaddress1,contactid", 
    "contains(fullname,'Acme')"
);

// Get table metadata
JsonNode metadata = client.getTableMetadata("contact");

// Download file column
Path file = client.downloadFileColumn("table", rowId, "columnname", Paths.get("output.bin"));

// Download note attachments
List<Path> attachments = client.downloadNoteAttachments(rowId, Paths.get("attachments/"));
```

### Running the Example

The main method includes example usage:

```bash
./gradlew run
```

Or from IntelliJ: Run the `DataverseClient` main class.

## Security Notes

- **Never commit sensitive credentials to source control**
- Environment variables are excluded from Git via `.gitignore`
- Copilot ignores sensitive files via `.copilotignore`
- Use Azure Key Vault or similar for production environments

## Dependencies

- Apache HttpClient 5.3.1 - HTTP client
- MSAL4J 1.14.3 - Microsoft Authentication Library
- Jackson 2.17.2 - JSON processing

## License

This project is for demonstration purposes.