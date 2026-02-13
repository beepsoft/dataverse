/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;

import jakarta.ejb.Stateless;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;

@Stateless
@Named
public class CedarAuthenticationServiceBean
{
    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

     public AuthenticatedUserCedar findUserForCedarToken(String cedarToken) {
        Query query = em.createNamedQuery("AuthenticatedUserCedar.findOneForCedarToken");
        query.setParameter("cedarToken", cedarToken);
        List<AuthenticatedUserCedar> res = query.getResultList();
        if (res.isEmpty()) {
            return null;
        }
        return res.get(0);
    }

    public AuthenticatedUser lookupUser(String cedarToken)
    {
        AuthenticatedUserCedar user = findUserForCedarToken(cedarToken);
        if (user == null) {
            return null;
        }
        return user.getUser();
    }

    public AuthenticatedUserCedar findAuthenticatedUserCedarById(Long id) {
         return em.find(AuthenticatedUserCedar.class, id);
    }

    public AuthenticatedUserCedar createNewAuthenticatedUserCedar() {
         return em.merge(new AuthenticatedUserCedar());
    }
}
