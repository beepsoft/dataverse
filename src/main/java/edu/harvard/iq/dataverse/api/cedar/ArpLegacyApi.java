/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.api.cedar;

import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.api.auth.AuthRequired;
import edu.harvard.iq.dataverse.cedar.ExportTsvToCedarData;
import edu.harvard.iq.dataverse.cedar.ExportToCedarParams;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Legacy endpoint shim:
 *   /api/arp/*  ->  /api/cedar/*
 *
 * Keeps old clients working by delegating to the new CedarApi implementation.
 */
@Path("arp")
public class ArpLegacyApi extends AbstractApiBean {

    @Inject
    private CedarApi cedarApi;

    public ArpLegacyApi() {
    }

    /**
     * Legacy: POST /api/arp/checkCedarTemplate
     * New:    POST /api/cedar/checkCedarTemplate
     */
    @POST
    @Path("/checkCedarTemplate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkCedarTemplateLegacy(String resourceJson) {
        try {
            return cedarApi.checkCedarResourceCall(resourceJson);
        } catch (Exception e) {
            return error(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Legacy: POST /api/arp/cedarToMdb/{dvIdtf}?skipUpload=...
     * New:    POST /api/cedar/cedarToMdb/{dvIdtf}?skipUpload=...
     */
    @POST
    @Path("/cedarToMdb/{dvIdtf}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/tab-separated-values")
    @AuthRequired
    public Response cedarToMdbLegacy(
            @Context ContainerRequestContext crc,
            @PathParam("dvIdtf") String dvIdtf,
            @QueryParam("skipUpload") @DefaultValue("false") boolean skipUpload,
            String templateJson
    ) {
        try {
            return cedarApi.cedarToMdb(crc, dvIdtf, skipUpload, templateJson);
        } catch (Exception e) {
            return error(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Legacy: GET /api/arp/convertMdbToTsv/{mdbName}
     * New:    GET /api/cedar/convertMdbToTsv/{mdbName}
     */
    @GET
    @Path("/convertMdbToTsv/{mdbName}")
    @Produces("text/tab-separated-values")
    public Response convertMdbToTsvLegacy(@PathParam("mdbName") String mdbName) {
        try {
            return cedarApi.convertMdbToTsv(mdbName);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Legacy: POST /api/arp/exportMdbToCedar/{mdbName}?uuid=...
     * New:    POST /api/cedar/exportMdbToCedar/{mdbName}?uuid=...
     */
    @POST
    @Path("/exportMdbToCedar/{mdbName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @AuthRequired
    public Response exportMdbToCedarLegacy(
            @Context ContainerRequestContext crc,
            @PathParam("mdbName") String mdbName,
            @QueryParam("uuid") String cedarUuid,
            ExportToCedarParams cedarParams
    ) {
        try {
            return cedarApi.exportMdbToCedar(crc, mdbName, cedarUuid, cedarParams);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Legacy: POST /api/arp/extractTemplateElements
     * New:    POST /api/cedar/extractTemplateElements
     */
    @POST
    @Path("/extractTemplateElements")
    @Consumes(MediaType.APPLICATION_JSON)
    @AuthRequired
    public Response extractTemplateElementsLegacy(
            @Context ContainerRequestContext crc,
            String requestBody
    ) {
        try {
            return cedarApi.extractTemplateElements(crc, requestBody);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Legacy: POST /api/arp/extractTemplateFields
     * New:    POST /api/cedar/extractTemplateFields
     */
    @POST
    @Path("/extractTemplateFields")
    @Consumes(MediaType.APPLICATION_JSON)
    @AuthRequired
    public Response extractTemplateFieldsLegacy(
            @Context ContainerRequestContext crc,
            String requestBody
    ) {
        try {
            return cedarApi.extractTemplateFields(crc, requestBody);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Legacy: POST /api/arp/extractResources
     * New:    POST /api/cedar/extractResources
     */
    @POST
    @Path("/extractResources")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @AuthRequired
    public Response extractResourcesLegacy(
            @Context ContainerRequestContext crc,
            String requestBody
    ) {
        try {
            return cedarApi.extractResources(crc, requestBody);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Legacy: POST /api/arp/convertTsvToCedarTemplate
     * New:    POST /api/cedar/convertTsvToCedarTemplate
     */
    @POST
    @Path("/convertTsvToCedarTemplate")
    @Consumes("text/tab-separated-values")
    @Produces(MediaType.APPLICATION_JSON)
    @AuthRequired
    public Response convertTsvToCedarTemplateLegacy(String mdbTsv) {
        try {
            return cedarApi.convertTsvToCedarTemplate(mdbTsv);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Legacy: POST /api/arp/exportTsvToCedar/
     * New:    POST /api/cedar/exportTsvToCedar/
     */
    @POST
    @Path("/exportTsvToCedar/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportTsvToCedarLegacy(
            @Context ContainerRequestContext crc,
            ExportTsvToCedarData data
    ) {
        try {
            return cedarApi.exportTsvToCedar(crc, data);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Legacy: POST /api/arp/updateMdb/{dvIdtf}
     * New:    POST /api/cedar/updateMdb/{dvIdtf}
     */
    @POST
    @Path("/updateMdb/{dvIdtf}")
    @Consumes("text/tab-separated-values")
    public Response updateMdbLegacy(
            @PathParam("dvIdtf") String dvIdtf,
            java.io.File file
    ) {
        try {
            return cedarApi.updateMdb(dvIdtf, file);
        } catch (Exception e) {
            return error(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Legacy: GET /api/arp/cedarResourceProxy/{cedarUrl}
     * New:    GET /api/cedar/cedarResourceProxy/{cedarUrl}
     */
    @GET
    @Path("/cedarResourceProxy/{cedarUrl}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cedarResourceProxyUrlInPathLegacy(
            @PathParam("cedarUrl") String cedarUrl,
            @Context HttpHeaders incomingHeaders
    ) {
        try {
            return cedarApi.cedarResourceProxyUrlInPath(cedarUrl, incomingHeaders);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid URL").build();
        }
    }

    /**
     * Legacy: GET /api/arp/cedarResourceProxy?url=...
     * New:    GET /api/cedar/cedarResourceProxy?url=...
     */
    @GET
    @Path("/cedarResourceProxy")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cedarResourceProxyUrlInQueryLegacy(
            @QueryParam("url") String cedarUrl,
            @Context HttpHeaders incomingHeaders
    ) {
        try {
            return cedarApi.cedarResourceProxyUrlInQuery(cedarUrl, incomingHeaders);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid URL").build();
        }
    }

    /**
     * Legacy: GET /api/arp/proxy?cedarUrl=...
     * New:    GET /api/cedar/proxy?cedarUrl=...
     *
     * Note: CedarApi defines @Path("proxy") (no leading slash) and uses @QueryParam("cedarUrl").
     */
    @GET
    @Path("/proxy")
    public Response doCedarResourceProxyLegacy(
            @QueryParam("cedarUrl") String cedarUrl,
            @Context HttpHeaders incomingHeaders
    ) {
        try {
            return cedarApi.doCedarResourceProxy(cedarUrl, incomingHeaders);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid URL").build();
        }
    }

    /**
     * Legacy: GET /api/arp/apiKey
     * New:    GET /api/cedar/apiKey
     */
    @GET
    @Path("/apiKey")
    @Produces(MediaType.APPLICATION_JSON)
    @AuthRequired
    public Response getApiKeyLegacy(
            @Context ContainerRequestContext crc,
            @Context HttpHeaders incomingHeaders
    ) {
        try {
            return cedarApi.getApiKey(crc, incomingHeaders);
        } catch (Exception e) {
            return Response.status(Response.Status.OK).entity(e.getMessage()).build();
        }
    }
}
