package edu.harvard.iq.dataverse.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.*;

public class CedarApiIT
{

    private static final Logger logger = Logger.getLogger(CedarApiIT.class.getCanonicalName());

    public static final String API_TOKEN_HTTP_HEADER = "X-Dataverse-key";


    @BeforeEach
    public void setUpClass() {
        RestAssured.baseURI = UtilIT.getRestAssuredBaseUri();
    }

    public String createRandomSuperUser() {
        Response createSuperuser = UtilIT.createRandomUser();
        String superuserUsername = UtilIT.getUsernameFromResponse(createSuperuser);
        String superuserApiToken = UtilIT.getApiTokenFromResponse(createSuperuser);
        Response superuser = UtilIT.makeSuperUser(superuserUsername);
        return superuserApiToken;
    }

    @Test
    public void checkTemplate_NoError() {
        Response createUser = UtilIT.createRandomUser();
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        byte[] templateContent = null;
        try {
            templateContent = Files.readAllBytes(Paths.get("src/test/resources/cedar/no-error-template.json"));
        } catch (IOException e) {
            logger.warning(e.getMessage());
            assertEquals(0,1);
        }

        Response response = checkTemplate(apiToken, templateContent);
        assertEquals(200, response.getStatusCode());
        response.then().assertThat().statusCode(OK.getStatusCode());

        String body = response.getBody().asString();
        String status = JsonPath.from(body).getString("status");
        assertEquals("OK", status);

        Map<String, String> data = JsonPath.from(body).getMap("data");
        // message and warnings is the 2
        assertEquals(2, data.size());
        String message = data.get("message");
        assertEquals("Valid Resource", message);
    }

    @Test
    public void checkTemplate_unprocessable() {
        Response createUser = UtilIT.createRandomUser();
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        byte[] templateContent = null;
        try {
            templateContent = Files.readAllBytes(Paths.get("src/test/resources/cedar/unprocessable-template.json"));
        } catch (IOException e) {
            logger.warning(e.getMessage());
            assertEquals(0,1);
        }

        Response response = checkTemplate(apiToken, templateContent);
        System.out.println("response: " + response.getBody().asString());
        assertEquals(200, response.getStatusCode());

        String body = response.getBody().asString();
        String status = JsonPath.from(body).getString("status");
        assertEquals("OK", status);

        Map<String, List<String>> data = JsonPath.from(body).getMap("data");
        assertEquals(2, data.size());
        String warning = data.get("warnings").get(0);
        assertEquals("/properties/lvl_1_element_test/lvl_2_element_test", warning);
    }

    @Test
    public void checkTemplate_invalidNames() {
        Response createUser = UtilIT.createRandomUser();
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        byte[] templateContent = null;
        try {
            templateContent = Files.readAllBytes(Paths.get("src/test/resources/cedar/invalid-names-template.json"));
        } catch (IOException e) {
            logger.warning(e.getMessage());
            assertEquals(0,1);
        }

        Response response = checkTemplate(apiToken, templateContent);
        assertEquals(500, response.getStatusCode());
        response.then().assertThat().statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

        String body = response.getBody().asString();
        String status = JsonPath.from(body).getString("status");
        assertEquals("ERROR", status);

        List<String> invalidNames = JsonPath.from(body).getList("message.invalidNames");
        assertEquals(5, invalidNames.size());
        assertTrue(invalidNames.contains("_invalid1_test_"));
        assertTrue(invalidNames.contains("invalid3_test_"));
        assertTrue(invalidNames.contains("invalid 4_test"));
        assertTrue(invalidNames.contains("invalid#_test"));
        assertTrue(invalidNames.contains("name"));
    }

    @Test
    public void checkTemplate_incompatiblePairs_afterOverrideUpdate() {
        Response createUser = UtilIT.createRandomUser();
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        byte[] templateContent = null;
        try {
            templateContent = Files.readAllBytes(Paths.get("src/test/resources/cedar/incompatible-pairs-template.json"));
        } catch (IOException e) {
            logger.warning(e.getMessage());
            assertEquals(0,1);
        }

        Response response = checkTemplate(apiToken, templateContent);
        assertEquals(200, response.getStatusCode());
        response.then().assertThat().statusCode(OK.getStatusCode());

        String body = response.getBody().asString();
        String status = JsonPath.from(body).getString("status");
        assertEquals("OK", status);

        Map<String, String> data = JsonPath.from(body).getMap("data");
        // message and warnings is the 2
        assertEquals(2, data.size());
        String message = data.get("message");
        assertEquals("Valid Resource", message);

        /*
        We used to have the response below, but after adding the override generation this template no longer contains errors,
        the required fields will be overridden during the mdb creation

        Response response = checkTemplate(apiToken, templateContent);
        assertEquals(500, response.getStatusCode());
        response.then().assertThat().statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

        String body = response.getBody().asString();
        String status = JsonPath.from(body).getString("status");
        assertEquals("ERROR", status);

        Map<String, Map<Pair<String, String>, HashMap<String, String>>> data = JsonPath.from(body).getMap("message");
        Map<Pair<String, String>, HashMap<String, String>> incompatiblePairs = data.get("incompatiblePairs");
        assertEquals(4, incompatiblePairs.size());
        assertTrue(incompatiblePairs.containsValue(Map.of("authorIdentifierScheme", "http://purl.org/spar/datacite/AgentIdentifierScheme")));
        assertTrue(incompatiblePairs.containsValue(Map.of("software", "https://www.w3.org/TR/prov-o/#wasGeneratedBy")));
        assertTrue(incompatiblePairs.containsValue(Map.of("publicationIDType", "http://purl.org/spar/datacite/ResourceIdentifierScheme")));
        assertTrue(incompatiblePairs.containsValue(Map.of("alternativeURL", "https://schema.org/distribution")));
        */
    }

    @Test
    public void generateTsv_noError() {
        var apiToken = createRandomSuperUser();

        byte[] templateContent = null;
        try {
            templateContent = Files.readAllBytes(Paths.get("src/test/resources/cedar/no-error-template.json"));
        } catch (IOException e) {
            logger.warning(e.getMessage());
            assertEquals(0,1);
        }

        Response response = cedarToMdb(apiToken, templateContent);
        assertEquals(200, response.getStatusCode());
        response.then().assertThat().statusCode(OK.getStatusCode());

        String body = response.getBody().asString();

        String tsvContent = null;
        try {
            tsvContent = Files.readString(Paths.get("src/test/resources/cedar/generated-no-error.tsv"));
        } catch (IOException e) {
            logger.warning(e.getMessage());
            assertEquals(0,1);
        }

        assertEquals(tsvContent, body);
    }

    @Test
    public void checkMdbOverrideCreation() throws Exception {
        int metadataBlockMaxId = getMaxIdFromTable("metadatablock");
        int datasetFieldTypeMaxId = getMaxIdFromTable("datasetfieldtype");
        int datasetFieldTypeOverrideMaxId = getMaxIdFromTable("datasetfieldtypeoverride");
        int datasetFieldTypeArpMaxId = getMaxIdFromTable("datasetfieldtypecedar");
        int metadataBlockArpMaxId = getMaxIdFromTable("metadatablockcedar");
        logger.info("metadataBlockIdMax " + metadataBlockMaxId);
        logger.info("datasetFieldTypeMaxId " + datasetFieldTypeMaxId);
        logger.info("datasetFieldTypeOverrideMaxId " + datasetFieldTypeOverrideMaxId);
        logger.info("datasetFieldTypeArpMaxId " + datasetFieldTypeArpMaxId);
        logger.info("metadataBlockArpMaxId " + metadataBlockArpMaxId);

        var apiToken = createRandomSuperUser();

        byte[] mdbToOverride = Files.readAllBytes(Paths.get("src/test/resources/cedar/mdb-to-override.json"));
        Response response = cedarToMdbWithUpload(apiToken, mdbToOverride);
        assertEquals(200, response.getStatusCode());
        response.then().assertThat().statusCode(OK.getStatusCode());

//        byte[] mdbContainingOverride = Files.readAllBytes(Paths.get("src/test/resources/cedar/mdb-containing-override.json"));
        byte[] mdbContainingOverride = Files.readAllBytes(Paths.get("src/test/resources/cedar/mdb-containing-override-and-onemore.json"));
        Response response2 = cedarToMdbWithUpload(apiToken, mdbContainingOverride);
        assertEquals(200, response2.getStatusCode());
        response2.then().assertThat().statusCode(OK.getStatusCode());

        Connection connection = connectToDatabase();
        if (connection == null) {
            throw new Exception("Can not connect to database");
        }
        connection.setAutoCommit(false);
        Statement stmt = connection.createStatement();

        ResultSet metadatablockTable = stmt.executeQuery( "SELECT * from metadatablock where id > " + metadataBlockMaxId);
        metadatablockTable.next();
        assertEquals("MDB to override", metadatablockTable.getString("displayname"));
        int mdbToOverrideId = metadatablockTable.getInt("id");
        metadatablockTable.next();
        assertEquals("MDB containing override", metadatablockTable.getString("displayname"));
        int mdbContainingOverrideId = metadatablockTable.getInt("id");
        metadatablockTable.close();

        ResultSet datasetfieldtypeTable = stmt.executeQuery("SELECT * from datasetfieldtype where id > " + datasetFieldTypeMaxId);
        datasetfieldtypeTable.next();
        assertEquals("datasetfieldtype_to_override", datasetfieldtypeTable.getString("name"));
        assertEquals("Datasetfieldtype that will be overridden", datasetfieldtypeTable.getString("title"));
        assertEquals(mdbToOverrideId, datasetfieldtypeTable.getInt("metadatablock_id"));
        datasetfieldtypeTable.close();

        ResultSet datasetfieldtypeoverrideTable = stmt.executeQuery("SELECT * from datasetfieldtypeoverride where id > " + datasetFieldTypeOverrideMaxId);
        datasetfieldtypeoverrideTable.next();
        assertEquals("Datasetfieldtype that will overridde", datasetfieldtypeoverrideTable.getString("title"));
        assertEquals("datasetfieldtype_that_override", datasetfieldtypeoverrideTable.getString("localname"));
        assertEquals(mdbContainingOverrideId, datasetfieldtypeoverrideTable.getInt("metadatablock_id"));
        datasetfieldtypeoverrideTable.close();
        stmt.close();
        connection.close();

        deleteTestDataFromTable(datasetFieldTypeOverrideMaxId, "datasetfieldtypeoverride", "id");
        deleteTestDataFromTable(datasetFieldTypeArpMaxId, "datasetfieldtypecedar", "id");
        deleteTestDataFromTable(metadataBlockArpMaxId, "metadatablockcedar", "id");
        deleteTestDataFromTable(datasetFieldTypeMaxId, "datasetfieldtype", "id");
        deleteTestDataFromTable(metadataBlockMaxId, "dataverse_metadatablock", "metadatablocks_id");
        deleteTestDataFromTable(metadataBlockMaxId, "metadatablock", "id");
    }

    @Test
    public void generateTsv_unprocessable() {
        String apiToken = createRandomSuperUser();

        byte[] templateContent = null;
        try {
            templateContent = Files.readAllBytes(Paths.get("src/test/resources/cedar/unprocessable-template.json"));
        } catch (IOException e) {
            logger.warning(e.getMessage());
            assertEquals(0,1);
        }

        Response response = cedarToMdb(apiToken, templateContent);
        assertEquals(500, response.getStatusCode());
        response.then().assertThat().statusCode(INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    public void citationToTsv() {
        checkGeneratedTsv("citation", "src/test/resources/cedar/citation.tsv");
    }

    @Test
    public void geospatialToTsv() {
        checkGeneratedTsv("geospatial", "src/test/resources/cedar/geospatial.tsv");
    }

    @Test
    public void socialscienceToTsv() {
        checkGeneratedTsv("socialscience", "src/test/resources/cedar/socialscience.tsv");
    }

    @Test
    public void astrophysicsToTsv() {
        checkGeneratedTsv("astrophysics", "src/test/resources/cedar/astrophysics.tsv");
    }

    @Test
    public void biomedicalToTsv() {
        checkGeneratedTsv("biomedical", "src/test/resources/cedar/biomedical.tsv");
    }

    @Test
    public void journalToTsv() {
        checkGeneratedTsv("journal", "src/test/resources/cedar/journal.tsv");
    }

    static Response checkTemplate(String apiToken, byte[] body) {
        return given()
                .header(API_TOKEN_HTTP_HEADER, apiToken)
                .contentType("application/json; charset=utf-8")
                .body(body)
                .post("/api/cedar/checkCedarTemplate");
    }

    static Response cedarToMdb(String apiToken, byte[] body) {
        return given()
                .header(API_TOKEN_HTTP_HEADER, apiToken)
                .contentType("application/json; charset=utf-8")
                .body(body)
                .queryParam("skipUpload", "true")
                .post("/api/cedar/cedarToMdb/root");
    }

    static Response exportMdbAsTsv(String apiToken, String mdbIdtf) {
        return given()
                .header(API_TOKEN_HTTP_HEADER, apiToken)
                .contentType("application/json; charset=utf-8")
                .get("/api/cedar/convertMdbToTsv/" + mdbIdtf);
    }

    static void checkGeneratedTsv(String mdbIdtf, String tsvName) {
        Response createUser = UtilIT.createRandomUser();
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        Response response = exportMdbAsTsv(apiToken, mdbIdtf);
        assertEquals(200, response.getStatusCode());
        response.then().assertThat().statusCode(OK.getStatusCode());

        String body = response.getBody().asString();

        String tsvContent = null;
        try {
            tsvContent = Files.readString(Paths.get(tsvName));
        } catch (IOException e) {
            logger.warning(e.getMessage());
            assertEquals(0,1);
        }

        assertEquals(tsvContent.trim(), body.trim());
    }

    static Response cedarToMdbWithUpload(String apiToken, byte[] body) {
        return given()
                .header(API_TOKEN_HTTP_HEADER, apiToken)
                .contentType("application/json; charset=utf-8")
                .body(body)
                .post("/api/cedar/cedarToMdb/root");
    }

    // Opens the connection to the database.
    // Uses the credentials supplied via JVM options
    private static Connection connectToDatabase() {
        Connection c;

        String host = System.getProperty("db.serverName") != null ? System.getProperty("db.serverName") : "localhost";
        String port = System.getProperty("db.portNumber") != null ? System.getProperty("db.portNumber") : "5432";
        String database = System.getProperty("db.databaseName") != null ? System.getProperty("db.databaseName") : "dataverse";
        String pguser = System.getProperty("db.user") != null ? System.getProperty("db.user") : "dataverse";
        String pgpasswd = System.getProperty("db.password") != null ? System.getProperty("db.password") : "secret";

        try {
            Class.forName("org.postgresql.Driver");
            c = DriverManager
                    .getConnection("jdbc:postgresql://" + host + ":" + port + "/" + database,
                            pguser,
                            pgpasswd);
        } catch (Exception e) {
            return null;
        }
        return c;
    }

    public static int getMaxIdFromTable(String tableName) throws Exception {
        int maxId;

        Connection connection = connectToDatabase();
        if (connection == null) {
            throw new Exception("Can not connect to database");
        }
        connection.setAutoCommit(false);
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery( "SELECT MAX(id) as max_id from " + tableName);
        rs.next();
        maxId = rs.getInt("max_id");
        rs.close();
        stmt.close();
        connection.close();

        return maxId;
    }

    public static void deleteTestDataFromTable(int originalMaxId, String tableName, String idName) throws Exception {
        Connection connection = connectToDatabase();
        if (connection == null) {
            throw new Exception("Can not connect to database");
        }
        connection.setAutoCommit(false);
        Statement stmt = connection.createStatement();
        stmt.execute( "DELETE FROM " + tableName + " WHERE " + idName + " > " + originalMaxId + ";");
        connection.commit();
        stmt.close();
        connection.close();
    }
}
