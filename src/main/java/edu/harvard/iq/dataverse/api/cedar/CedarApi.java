/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.api.cedar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import edu.harvard.iq.dataverse.*;
import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.api.auth.AuthRequired;
import edu.harvard.iq.dataverse.cedar.*;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.util.json.NullSafeJsonBuilder;
import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.api.ApiConstants.STATUS_ERROR;
import static jakarta.ws.rs.core.Response.Status.*;

/**
 * API for CEDAR integration, including:
 * - Validating CEDAR templates for use in Dataverse
 * - Creating MetadataBlocks from CEDAR templates
 * - Exporting MetadataBlocks to CEDAR templates and uploading them to CEDAR
 * - Extracting TemplateFields and TemplateElements from CEDAR templates and uploading them to CEDAR
 * - A proxy endpoint for reading CEDAR resources without exposing the API key to clients
 */
@Path("cedar")
public class CedarApi extends AbstractApiBean {

    private static final Logger logger = Logger.getLogger(CedarApi.class.getCanonicalName());
    private static final Properties prop = new Properties();

    @EJB
    DatasetFieldServiceApiBean datasetFieldServiceApi;

    @EJB
    CedarServiceBean cedarService;

    @EJB
    CedarConfig cedarConfig;

    @Inject
    DataverseSession dataverseSession;

    public CedarApi() throws NoSuchAlgorithmException, KeyManagementException {
    }

    private HttpClient createHttpClient(ExecutorService executorService) throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Install the all-trusting trust manager
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        // Create a HttpClient with the custom SSLContext and ExecutorService
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .executor(executorService)
                .build();
    }
    
    /**
     * Checks whether a CEDAR resource is valid for use in or use as a Metadatablock.
     *
     * Requires no authentication.
     *
     * @param resourceJson
     * @return
     */
    @POST
    @Path("/checkCedarTemplate")
    @Consumes("application/json")
    public Response checkCedarResourceCall(String resourceJson) {
        CedarTemplateErrors errors;
        try {
            errors = cedarService.validateCedarResource(resourceJson, true, false);
        } catch (Exception e) {
            e.printStackTrace();
            return error(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        if (!(errors.invalidNames.isEmpty() && errors.unprocessableElements.isEmpty() && errors.errors.isEmpty())) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity( NullSafeJsonBuilder.jsonObjectBuilder()
                            .add("status", STATUS_ERROR)
                            .add( "message", errors.toJson() ).build()
                    ).type(MediaType.APPLICATION_JSON_TYPE).build();
        }

        return ok(NullSafeJsonBuilder.jsonObjectBuilder()
                .add("message", "Valid Resource")
                .add("warnings", errors.warningsAsJson())
                .build());
    }

    /**
     * Crates a MetadataBlock in the dataverse identified by `dvIdtf` from a CEDAR template.
     *
     * Requires superuser authentication.
     *
     * @param dvIdtf Dataverse identifier to create the MetadataBlock in
     * @param skipUpload if true, the MDB will be created but not actually uploaded to the
     *                   dataverse. Practically just convert CEDAR template to TSV.
     *                   Defaults to false.
     * @param templateJson CEDAR template JSON as string
     * @return The created MetadataBlock in TSV format, or error messages if the CEDAR template is
     *         invalid or the upload fails.
     * @throws JsonProcessingException
     */
    //TODO: remove added headers
    @POST
    @Path("/cedarToMdb/{dvIdtf}")
    @Consumes("application/json")
    @Produces("text/tab-separated-values")
    @AuthRequired
    public Response cedarToMdb(
            @Context ContainerRequestContext crc,
            @PathParam("dvIdtf") String dvIdtf,
            @QueryParam("skipUpload") @DefaultValue("false") boolean skipUpload,
            String templateJson
    ) throws JsonProcessingException
    {
        try {
            AuthenticatedUser user = getRequestAuthenticatedUserOrDie(crc);
            if (!user.isSuperuser()) {
                return error(Response.Status.FORBIDDEN, "Superusers only.");
            }
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(Response.Status.FORBIDDEN, "Superusers only.");
        }

        String mdbTsv;

        try {
            mdbTsv = cedarService.createOrUpdateMdbFromCedarTemplate(dvIdtf, templateJson, skipUpload);
            if (!skipUpload) {
                String metadataBlockName = new ObjectMapper().readTree(templateJson).get("schema:identifier").textValue();
                cedarService.updateMetadataBlockInNewTransaction(dvIdtf, metadataBlockName);
            }
        } catch (CedarTemplateErrorsException cte) {
            cte.printStackTrace();
            logger.log(Level.SEVERE, "CEDAR template upload failed:"+cte.getErrors().toJson());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity( NullSafeJsonBuilder.jsonObjectBuilder()
                            .add("status", STATUS_ERROR)
                            .add( "message", cte.getErrors().toJson() ).build()
                    ).type(MediaType.APPLICATION_JSON_TYPE).header("Access-Control-Allow-Origin", "*").build();

        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "CEDAR template upload failed", e);
            return Response.serverError().entity(e.getMessage()).header("Access-Control-Allow-Origin", "*").build();
        }

        //TODO: check why is the origin duplicated if the header is not added here as well as in the ApiBlockingFilter
        //TODO: maybe the cors filter?
        return Response.ok(mdbTsv).header("Access-Control-Allow-Origin", "*").build();
    }

    /**
     * Returns a MetadatabLock in TSV format.
     *
     * Requires no authentication.
     *
     * @param mdbName
     * @return
     */
    @GET
    @Path("/convertMdbToTsv/{mdbName}")
    @Produces("text/tab-separated-values")
    public Response convertMdbToTsv(
            @PathParam("mdbName") String mdbName
    )
    {
        String mdbTsv;

        try {
            mdbTsv = cedarService.exportMdbAsTsv(mdbName);
        } catch (JsonProcessingException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok(mdbTsv).build();
    }

    /**
     * Exports a MetadataBlock to CEDAR.
     *
     * Requires superuser authentication
     *
     * @param mdbName name of MetadataBlock to export
     * @param cedarUuid an optional explicit UUID to use for the CEDAR template. If not provided,
     *                  a UUID will be generated based on the name of the MDB.
     * @param cedarParams CEDAr connection parameters and options
     * @return
     */
    @POST
    @Path("/exportMdbToCedar/{mdbName}")
    @Consumes("application/json")
    @Produces("application/json")
    @AuthRequired
    public Response exportMdbToCedar(
            @Context ContainerRequestContext crc,
            @PathParam("mdbName") String mdbName,
            @QueryParam("uuid") String cedarUuid,
            ExportToCedarParams cedarParams)
    {
        try {
            AuthenticatedUser user = getRequestAuthenticatedUserOrDie(crc);
            if (!user.isSuperuser()) {
                return error(Response.Status.FORBIDDEN, "Superusers only.");
            }
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(Response.Status.FORBIDDEN, "Superusers only.");
        }

        String res = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String cedarDomain = cedarParams.cedarDomain;

            if (cedarDomain == null || cedarDomain.isBlank()){
                cedarDomain = cedarConfig.get("CedarDomain");
            }
            cedarParams.cedarDomain = cedarDomain;

            JsonObject existingTemplate = cedarService.getCedarTemplateForMdb(mdbName);
            var actualUuid = cedarUuid != null ? cedarUuid : CedarServiceBean.generateNamedUuid(mdbName);
            JsonNode cedarTemplate = mapper.readTree(cedarService.tsvToCedarTemplate(cedarService.exportMdbAsTsv(mdbName), existingTemplate).toString());
            res = cedarService.exportTemplateToCedar(cedarTemplate, actualUuid, cedarParams);
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(FORBIDDEN, "Authorized users only.");
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok(res).build();
    }

    /**
     * Extracts TemplateElements from a CEDAR Template and uploads them to CEDAR.
     * Requires superuser authentication
     *
     * @param requestBody containing the CEDAR Parameters and the CEDAR Template
     * @return the extracted TemplateElements
     */
    @POST
    @Path("/extractTemplateElements")
    @Consumes("application/json")
    @AuthRequired
    public Response extractTemplateElements(
            @Context ContainerRequestContext crc,
            String requestBody)
    {
        List<JsonNode> extractedElements;
        var mapper = new ObjectMapper();
        String extractedElementsJson = null;
        try {
            AuthenticatedUser user = getRequestAuthenticatedUserOrDie(crc);
            if (!user.isSuperuser()) {
                return error(Response.Status.FORBIDDEN, "Superusers only.");
            }
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(Response.Status.FORBIDDEN, "Superusers only.");
        }

        try {
            JsonNode extractParams = new ObjectMapper().readTree(requestBody);

            JsonNode cedarResource;
            if (extractParams.has("cedarResource") && !extractParams.get("cedarResource").isNull()) {
                cedarResource = extractParams.get("cedarResource");
            }  else {
                return error(Response.Status.BAD_REQUEST, "cedarResource is required");
            }

            ExportToCedarParams exportToCedarParams = cedarService.getExtractParams(extractParams);
            extractedElements = cedarService.extractTemplateElements(cedarResource, exportToCedarParams);
            extractedElementsJson = mapper.writeValueAsString(extractedElements);
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(FORBIDDEN, "Authorized users only.");
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok(extractedElementsJson).build();
    }

    /**
     * Extracts TemplateFields from a CEDAR Resource and uploads them to CEDAR.
     * Requires superuser authentication
     *
     * @param requestBody containing the CEDAR Parameters and the CEDAR Resource
     * @return the extracted TemplateFields
     */
    @POST
    @Path("/extractTemplateFields")
    @Consumes("application/json")
    @AuthRequired
    public Response extractTemplateFields(
            @Context ContainerRequestContext crc,
            String requestBody)
    {
        List<JsonNode> extractedFields;
        var mapper = new ObjectMapper();
        String extractedFieldsJson = null;
        try {
            AuthenticatedUser user = getRequestAuthenticatedUserOrDie(crc);
            if (!user.isSuperuser()) {
                return error(Response.Status.FORBIDDEN, "Superusers only.");
            }
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(Response.Status.FORBIDDEN, "Superusers only.");
        }

        try {
            JsonNode extractParams = new ObjectMapper().readTree(requestBody);
            
            JsonNode cedarResource;
            if (extractParams.has("cedarResource") && !extractParams.get("cedarResource").isNull()) {
                cedarResource = extractParams.get("cedarResource");
            }  else {
                return error(Response.Status.BAD_REQUEST, "cedarResource is required");
            }
            
            ExportToCedarParams exportToCedarParams = cedarService.getExtractParams(extractParams);
            extractedFields = cedarService.extractTemplateFields(cedarResource, exportToCedarParams);
            extractedFieldsJson = mapper.writeValueAsString(extractedFields);
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(FORBIDDEN, "Authorized users only.");
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok(extractedFieldsJson).build();
    }

    /**
     * Extracts TemplateFields and TemplateElements from a CEDAR Resource and uploads them to CEDAR.
     * Requires superuser authentication
     *
     * @param requestBody containing the CEDAR Parameters and the CEDAR Resource
     * @return
     */
    @POST
    @Path("/extractResources")
    @Consumes("application/json")
    @Produces("application/json")
    @AuthRequired
    public Response extractResources(
            @Context ContainerRequestContext crc,
            String requestBody)
    {
        List<JsonNode> extractedResources;
        var mapper = new ObjectMapper();
        String extractedResourcesJson = null;
        try {
            AuthenticatedUser user = getRequestAuthenticatedUserOrDie(crc);
            if (!user.isSuperuser()) {
                return error(Response.Status.FORBIDDEN, "Superusers only.");
            }
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(Response.Status.FORBIDDEN, "Superusers only.");
        }

        try {
            JsonNode extractParams = mapper.readTree(requestBody);

            JsonNode cedarResource;
            if (extractParams.has("cedarResource") && !extractParams.get("cedarResource").isNull()) {
                cedarResource = extractParams.get("cedarResource");
            }  else {
                return error(Response.Status.BAD_REQUEST, "cedarResource is required");
            }

            ExportToCedarParams exportToCedarParams = cedarService.getExtractParams(extractParams);
            extractedResources = cedarService.extractResources(cedarResource, exportToCedarParams);
            extractedResourcesJson = mapper.writeValueAsString(extractedResources);
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(FORBIDDEN, "Authorized users only.");
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok(extractedResourcesJson).build();
    }
    
    /**
     * Converts a MetadataBlock TSV to a CEDAR template and returns it.
     *
     * Requires no authentication.
     *
     * @param mdbTsv the MetadataBlock in TSV format
     * @return
     */
    @POST
    @Path("/convertTsvToCedarTemplate")
    @Consumes("text/tab-separated-values")
    @Produces("application/json")
    public Response convertTsvToCedarTemplate(String mdbTsv)
    {
        String cedarTemplate;

        try {
            cedarTemplate = cedarService.tsvToCedarTemplate(mdbTsv, null).toString();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok(cedarTemplate).build();
    }

    /**
     * Exports a MetadataBlock given as TSV to CEDAR.
     *
     * cedarData
     * @param data export parameters
     * @return
     */
    @POST
    @Path("/exportTsvToCedar/")
    @Consumes("application/json")
    @Produces("application/json")
    @AuthRequired
    public Response exportTsvToCedar(
            @Context ContainerRequestContext crc,
            ExportTsvToCedarData data
    )
    {
        try {
            AuthenticatedUser user = getRequestAuthenticatedUserOrDie(crc);
            if (!user.isSuperuser()) {
                return error(Response.Status.FORBIDDEN, "Superusers only.");
            }
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(Response.Status.FORBIDDEN, "Superusers only.");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            ExportToCedarParams cedarParams = data.cedarParams;
            String cedarTsv = data.tsv;

            String cedarDomain = cedarParams.cedarDomain;

            if (cedarDomain == null || cedarDomain.isBlank()){
                cedarDomain = cedarConfig.get("CedarDomain");
            }
            cedarParams.cedarDomain = cedarDomain;

            JsonNode cedarTemplate = mapper.readTree(
                    cedarService.tsvToCedarTemplate(cedarTsv, null).toString());

            // Use the explicitly provided UUID or create one based on the name in the TSV, ie. "schema:identifier"
            // ine the CEDAR template.
            var actualUuid = data.cedarUuid;
            if (actualUuid == null || actualUuid.isBlank()) {
                actualUuid = CedarServiceBean.generateNamedUuid(cedarTemplate.get("schema:identifier").textValue());
            }

            cedarService.exportTemplateToCedar(cedarTemplate, actualUuid, cedarParams);
        } catch (WrappedResponse ex) {
            ex.printStackTrace();
            return error(FORBIDDEN, "Authorized users only.");
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok().build();
    }

    /**
     * Updates MDB from an uploaded TSV file.
     *
     * @param dvIdtf dataverse identifier of the MDB to update
     * @param file the TSV file to update the MDB with
     * @return
     */
    @POST
    @Consumes("text/tab-separated-values")
    @Path("/updateMdb/{dvIdtf}")
    public Response updateMdb(@PathParam("dvIdtf") String dvIdtf, File file) {
        String metadataBlockName;

        try {
            Response response = datasetFieldServiceApi.loadDatasetFields(file);
            if (!response.getStatusInfo().toEnum().equals(Response.Status.OK)) {
                throw new Exception("Failed to load dataset fields");
            }
            metadataBlockName = ((jakarta.json.JsonObject) response.getEntity()).getJsonObject("data").getJsonArray("added").getJsonObject(0).getString("name");
            cedarService.updateMetadataBlock(dvIdtf, metadataBlockName);
        } catch (Exception e) {
            e.printStackTrace();
            return error(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return Response.ok("Metadata block of dataverse with name: " + metadataBlockName + " updated").build();
    }

    @GET
    @Path("/cedarResourceProxy/{cedarUrl}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cedarResourceProxyUrlInPath(
            @PathParam("cedarUrl") String cedarUrl,
            @Context HttpHeaders incomingHeaders
    )
    {
        return doCedarResourceProxy(cedarUrl, incomingHeaders);
    }

    @GET
    @Path("/cedarResourceProxy")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cedarResourceProxyUrlInQuery(
            @QueryParam("url") String cedarUrl,
            @Context HttpHeaders incomingHeaders
    )
    {
        return doCedarResourceProxy(cedarUrl, incomingHeaders);
    }

    /**
     * Proxies requests to the cedar resource server for reading public schemas. Since we cannot have a CEDAR user which
     * only has read right, therefore we cannot have the api key in AROMA, otherwise malicious users may make modifications
     * in the name of that user. Instead we have this poxy, which aonly allows GET access to schemas and also
     * adds the necessary api key authentication to request. For this to work the CedarDomain and
     * CedarProxyApiKey configurations must be set.
     *
     * @param cedarUrl        the URL to download
     * @param incomingHeaders the request headers
     * @return contents of cedarUrl
     */
    @GET
    @Path("proxy")
    public Response doCedarResourceProxy(
            @QueryParam("cedarUrl") String cedarUrl,
            @Context HttpHeaders incomingHeaders
    ) {
        String subdomain = "resource." + cedarConfig.get("CedarDomain");
        String apiKey = cedarConfig.get("CedarProxyApiKey");

        if (cedarUrl == null || cedarUrl.isBlank()) {
            logger.severe("/cedarResourceProxy: URL path parameter is missing");
            return Response.status(Response.Status.BAD_REQUEST).entity("URL parameter is required").build();
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            HttpClient proxyClient = createHttpClient(executorService);

            URI uri = new URI(cedarUrl);
            if (!uri.getHost().endsWith(subdomain)) {
                logger.severe("/cedarResourceProxy: Invalid URL: " + uri);
                return Response.status(Response.Status.BAD_REQUEST).entity("Invalid URL").build();
            }
            logger.info("/cedarResourceProxy: proxying URL: " + uri);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET();

            // Set authorization and accept headers explicitly
            requestBuilder.header("Authorization", "apiKey " + apiKey);
            requestBuilder.header("Accept", "application/json");

            // Forward the request asynchronously
            CompletableFuture<HttpResponse<byte[]>> responseFuture = proxyClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

            HttpResponse<byte[]> proxiedResponse = responseFuture.get(); // Blocking wait for the response

            // Begin building the response to the client
            Response.ResponseBuilder responseBuilder = Response
                    .status(proxiedResponse.statusCode())
                    .entity(proxiedResponse.body());

            // Filter and set response headers
            java.net.http.HttpHeaders responseHeaders = proxiedResponse.headers();
            responseHeaders.map().forEach((key, values) -> {
                if (!List.of("Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE", "Trailer", "Transfer-Encoding", "Upgrade", "Content-Encoding", "Content-Length").contains(key)) {
                    responseBuilder.header(key, String.join(",", values));
                }
            });

            // Add CORS headers to the response
            responseBuilder.header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET")
                    .header("Access-Control-Allow-Headers", "*");

            return responseBuilder.build();
        } catch (Exception e) {
            logger.severe("/cedarResourceProxy: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid URL").build();
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(1, TimeUnit.SECONDS))
                        System.err.println("ExecutorService did not terminate");
                }
            } catch (InterruptedException ie) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Get the api key (api token) for a user authenticated via JSESSIONID
     * @param incomingHeaders
     * @return
     */
    @GET
    @Path("/apiKey")
    @Produces(MediaType.APPLICATION_JSON)
    @AuthRequired
    public Response getApiKey(
            @Context ContainerRequestContext crc,
            @Context HttpHeaders incomingHeaders
    )
    {
        try {
            var apiKey = cedarService.getCurrentUserApiKey(dataverseSession);
            Map<String, String> map = null;
            if (apiKey == null) {
                map = Map.of();
            }
            else {
                map = Map.of("apiKey", apiKey);
            }
            return Response.status(OK).entity(map).build();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return Response.status(OK).entity(ex.getMessage()).build();
        }

//        try {
//            // Check auth. Even without this, we would just return no apiKey, but rather return an appropriate error
//            AuthenticatedUser u = getRequestAuthenticatedUserOrDie(crc);
//        } catch (WrappedResponse e) {
//            String error = ConstraintViolationUtil.getErrorStringForConstraintViolations(e.getCause());
//            if (!error.isEmpty()) {
//                logger.log(Level.INFO, error);
//                return e.refineResponse(error);
//            }
//            return e.getResponse();
//        }
    }


}
