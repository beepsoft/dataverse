/**
 * This work was implemented and sponsored by the Hungarian national research data project
 * HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.api.auth;

import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.cedar.CedarAuthenticationServiceBean;
import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;

import java.util.logging.Logger;

/**
 * AuthMechanism to allow using "X-Dataverse-key" header or "key" query param with a CEDAR provided
 * token as associated with a Dataverse user via AuthenticatedUserArp.
 *
 * Note: while this is not an annotated EJB, this will be injected as an EJB into CompoundAuthMechanism
 * and so we can use @EJB and @Context inside it.
 */
public class CedarApiKeyAuthMechanism implements AuthMechanism
{
    private static final Logger logger = Logger.getLogger(CedarApiKeyAuthMechanism.class.getCanonicalName());

    @EJB
    protected CedarAuthenticationServiceBean cedarAuthSvc;

    @Context
    protected HttpServletRequest httpRequest;

    @Override
    public User findUserFromRequest(ContainerRequestContext crc) throws WrappedAuthErrorResponse
    {
        return cedarAuthSvc.lookupUser(getRequestApiKey(crc));
    }

    private String getRequestApiKey(ContainerRequestContext containerRequestContext) {
        String headerParamApiKey = containerRequestContext.getHeaderString(ApiKeyAuthMechanism.DATAVERSE_API_KEY_REQUEST_HEADER_NAME);
        String queryParamApiKey = containerRequestContext.getUriInfo().getQueryParameters().getFirst(ApiKeyAuthMechanism.DATAVERSE_API_KEY_REQUEST_PARAM_NAME);

        return headerParamApiKey != null ? headerParamApiKey : queryParamApiKey;
    }
}
