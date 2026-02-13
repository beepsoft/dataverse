/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Additional CEDAR related values associated with an AuthenticatedUser.
 *
 * For now, we just add the CEDAR api key/token to the AuthenticatedUser so that a user may be
 * authenticated using either its DV API token or CEDAR key.
 *
 * @see CedarAuthenticationServiceBean
 * @see edu.harvard.iq.dataverse.api.auth.CedarApiKeyAuthMechanism
 * @see edu.harvard.iq.dataverse.api.auth.CompoundAuthMechanism
 */
@NamedQueries({
        // This should give a single result
        @NamedQuery(name = "AuthenticatedUserCedar.findOneForCedarToken",
                query = "SELECT o FROM AuthenticatedUserCedar o WHERE o.cedarToken=:cedarToken ORDER BY o.id"),
})
@Entity
@Table(indexes = {@Index(columnList="cedar_token")})
public class AuthenticatedUserCedar implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private AuthenticatedUser user;

    private String cedarToken;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public AuthenticatedUser getUser()
    {
        return user;
    }

    public void setUser(AuthenticatedUser user)
    {
        this.user = user;
    }

    public String getCedarToken()
    {
        return cedarToken;
    }

    public void setCedarToken(String cedarToken)
    {
        this.cedarToken = cedarToken;
    }
}
