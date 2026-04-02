/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.api;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static edu.harvard.iq.dataverse.api.UtilIT.API_TOKEN_HTTP_HEADER;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the CEDAR metadata block import flow used from {@code DataversePage}
 * (superuser uploads a CEDAR template into a dataverse). The UI delegates to
 * {@code CedarServiceBean#createOrUpdateMdbFromCedarTemplate} and {@code #updateMetadataBlockInNewTransaction},
 * which the API exposes as {@code POST /api/cedar/cedarToMdb/{dvIdtf}} with {@code skipUpload=false}.
 *
 * <p>Setup and cleanup follow the same style as other API ITs (random user, dataverse, publish via SWORD); request
 * shapes and fixtures align with {@link CedarApiIT}.
 *
 * <p>After a successful import, {@link #deleteCedarMdbTestDataCreatedAfter} removes CEDAR-created rows from the
 * database. Deleting the dataverse via the API does not remove metadata block definitions (see
 * {@code DeleteDataverseCommand}), so SQL cleanup mirrors {@link CedarApiIT#checkMdbOverrideCreation}.
 */
public class DataversePageCedarImportIT {

    private static final String NO_ERROR_TEMPLATE = "src/test/resources/cedar/no-error-template.json";
    private static final String INVALID_NAMES_TEMPLATE = "src/test/resources/cedar/invalid-names-template.json";
    private static final String EXPECTED_TSV = "src/test/resources/cedar/generated-no-error.tsv";

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = UtilIT.getRestAssuredBaseUri();
    }

    /**
     * Same endpoint as the Dataverse page import: upload template and attach the metadata block to the dataverse.
     */
    static Response cedarToMdbUpload(String apiToken, String dataverseAlias, byte[] templateBytes) {
        return given()
                .header(API_TOKEN_HTTP_HEADER, apiToken)
                .contentType("application/json; charset=utf-8")
                .body(templateBytes)
                .post("/api/cedar/cedarToMdb/" + dataverseAlias);
    }

    static class ImportTestSetup {
        String username;
        String apiToken;
        String dataverseAlias;
    }

    /**
     * Creates a superuser, a random dataverse, and publishes it.
     */
    static ImportTestSetup createSuperuserAndPublishedDataverse() {
        ImportTestSetup setup = new ImportTestSetup();

        Response createUserResponse = UtilIT.createRandomUser();
        createUserResponse.then().assertThat().statusCode(OK.getStatusCode());
        setup.username = UtilIT.getUsernameFromResponse(createUserResponse);
        setup.apiToken = UtilIT.getApiTokenFromResponse(createUserResponse);

        Response superuserResponse = UtilIT.setSuperuserStatus(setup.username, true);
        superuserResponse.then().assertThat().statusCode(OK.getStatusCode());

        Response createDataverseResponse = UtilIT.createRandomDataverse(setup.apiToken);
        createDataverseResponse.then().assertThat().statusCode(CREATED.getStatusCode());
        setup.dataverseAlias = UtilIT.getAliasFromResponse(createDataverseResponse);

        Response publishDataverseResponse = UtilIT.publishDataverseViaSword(setup.dataverseAlias, setup.apiToken);
        publishDataverseResponse.then().assertThat().statusCode(OK.getStatusCode());

        return setup;
    }

    static void cleanupUserAndDataverse(ImportTestSetup setup) {
        Response deleteDataverseResponse = UtilIT.deleteDataverse(setup.dataverseAlias, setup.apiToken);
        deleteDataverseResponse.then().assertThat().statusCode(OK.getStatusCode());

        Response deleteUserResponse = UtilIT.deleteUser(setup.username);
        deleteUserResponse.then().assertThat().statusCode(OK.getStatusCode());
    }

    /**
     * Max primary keys for CEDAR-related tables before an import, used with {@link #deleteCedarMdbTestDataCreatedAfter}.
     */
    static final class CedarTableMaxIds {
        final int metadatablock;
        final int datasetfieldtype;
        final int datasetfieldtypeoverride;
        final int datasetfieldtypecedar;
        final int metadatablockcedar;
        final int controlledvocabularyvalue;

        CedarTableMaxIds(int metadatablock, int datasetfieldtype, int datasetfieldtypeoverride,
                int datasetfieldtypecedar, int metadatablockcedar, int controlledvocabularyvalue) {
            this.metadatablock = metadatablock;
            this.datasetfieldtype = datasetfieldtype;
            this.datasetfieldtypeoverride = datasetfieldtypeoverride;
            this.datasetfieldtypecedar = datasetfieldtypecedar;
            this.metadatablockcedar = metadatablockcedar;
            this.controlledvocabularyvalue = controlledvocabularyvalue;
        }
    }

    static CedarTableMaxIds captureCedarTableMaxIds() throws Exception {
        return new CedarTableMaxIds(
                CedarApiIT.getMaxIdFromTable("metadatablock"),
                CedarApiIT.getMaxIdFromTable("datasetfieldtype"),
                CedarApiIT.getMaxIdFromTable("datasetfieldtypeoverride"),
                CedarApiIT.getMaxIdFromTable("datasetfieldtypecedar"),
                CedarApiIT.getMaxIdFromTable("metadatablockcedar"),
                CedarApiIT.getMaxIdFromTable("controlledvocabularyvalue"));
    }

    /**
     * Deletes rows inserted after the snapshot; same order as {@link CedarApiIT#checkMdbOverrideCreation}.
     */
    static void deleteCedarMdbTestDataCreatedAfter(CedarTableMaxIds before) throws Exception {
        CedarApiIT.deleteTestDataFromTable(before.datasetfieldtypeoverride, "datasetfieldtypeoverride", "id");
        CedarApiIT.deleteTestDataFromTable(before.datasetfieldtypecedar, "datasetfieldtypecedar", "id");
        CedarApiIT.deleteTestDataFromTable(before.metadatablockcedar, "metadatablockcedar", "id");
        CedarApiIT.deleteTestDataFromTable(before.controlledvocabularyvalue, "controlledvocabularyvalue", "id");
        CedarApiIT.deleteTestDataFromTable(before.datasetfieldtype, "datasetfieldtype", "id");
        CedarApiIT.deleteTestDataFromTable(before.metadatablock, "dataverse_metadatablock", "metadatablocks_id");
        CedarApiIT.deleteTestDataFromTable(before.metadatablock, "metadatablock", "id");
    }

    /**
     * Removes the dataverse and user, then strips CEDAR MDB rows left in the DB (API delete does not drop metadatablocks).
     */
    static void cleanupUserDataverseAndCedarDb(ImportTestSetup setup, CedarTableMaxIds idsBeforeImport) throws Exception {
        try {
            if (setup != null) {
                cleanupUserAndDataverse(setup);
            }
        } finally {
            if (idsBeforeImport != null) {
                deleteCedarMdbTestDataCreatedAfter(idsBeforeImport);
            }
        }
    }

    @Test
    public void cedarImport_nonSuperuserForbidden() throws IOException {
        Response createUserResponse = UtilIT.createRandomUser();
        createUserResponse.then().assertThat().statusCode(OK.getStatusCode());
        String username = UtilIT.getUsernameFromResponse(createUserResponse);
        String apiToken = UtilIT.getApiTokenFromResponse(createUserResponse);

        Response createDataverseResponse = UtilIT.createRandomDataverse(apiToken);
        createDataverseResponse.then().assertThat().statusCode(CREATED.getStatusCode());
        String dataverseAlias = UtilIT.getAliasFromResponse(createDataverseResponse);

        byte[] templateContent = Files.readAllBytes(Paths.get(NO_ERROR_TEMPLATE));
        Response importResponse = cedarToMdbUpload(apiToken, dataverseAlias, templateContent);
        importResponse.then().assertThat().statusCode(FORBIDDEN.getStatusCode());

        UtilIT.deleteDataverse(dataverseAlias, apiToken).then().assertThat().statusCode(OK.getStatusCode());
        UtilIT.deleteUser(username).then().assertThat().statusCode(OK.getStatusCode());
    }

    /**
     * End-to-end: superuser POSTs a valid CEDAR template to a dataverse; response TSV matches the known-good export
     * and the block {@code arp_test} is attached (see {@link CedarApiIT#generateTsv_noError}).
     */
    @Test
    public void cedarImport_superuserUploadsTemplate_attachesMetadataBlock() throws Exception {
        ImportTestSetup setup = null;
        CedarTableMaxIds idsBeforeImport = null;
        try {
            setup = createSuperuserAndPublishedDataverse();
            idsBeforeImport = captureCedarTableMaxIds();

            byte[] templateContent = Files.readAllBytes(Paths.get(NO_ERROR_TEMPLATE));
            Response response = cedarToMdbUpload(setup.apiToken, setup.dataverseAlias, templateContent);
            assertEquals(OK.getStatusCode(), response.getStatusCode());
            response.then().assertThat().statusCode(OK.getStatusCode());

            String expectedTsv = Files.readString(Paths.get(EXPECTED_TSV));
            assertEquals(expectedTsv, response.getBody().asString());

            Response listBlocks = UtilIT.listMetadataBlocks(setup.dataverseAlias, false, false, setup.apiToken);
            listBlocks.then().assertThat().statusCode(OK.getStatusCode());
            List<String> blockNames = JsonPath.from(listBlocks.getBody().asString()).getList("data.name");
            assertTrue(blockNames.contains("arp_test"), "Expected metadata block arp_test after CEDAR import: " + blockNames);
        } finally {
            cleanupUserDataverseAndCedarDb(setup, idsBeforeImport);
        }
    }

    @Test
    public void cedarImport_invalidNamesReturnsError() throws IOException {
        ImportTestSetup setup = null;
        try {
            setup = createSuperuserAndPublishedDataverse();

            byte[] templateContent = Files.readAllBytes(Paths.get(INVALID_NAMES_TEMPLATE));
            Response response = cedarToMdbUpload(setup.apiToken, setup.dataverseAlias, templateContent);
            assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatusCode());
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
        } finally {
            if (setup != null) {
                cleanupUserAndDataverse(setup);
            }
        }
    }

    @Test
    public void cedarImport_unprocessableTemplateFails() throws IOException {
        ImportTestSetup setup = null;
        try {
            setup = createSuperuserAndPublishedDataverse();

            byte[] templateContent = Files.readAllBytes(Paths.get("src/test/resources/cedar/unprocessable-template.json"));
            Response response = cedarToMdbUpload(setup.apiToken, setup.dataverseAlias, templateContent);
            response.then().assertThat().statusCode(INTERNAL_SERVER_ERROR.getStatusCode());
        } finally {
            if (setup != null) {
                cleanupUserAndDataverse(setup);
            }
        }
    }
}
