/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.api.cedar;

/**
 * Parameters for the /api/admin/cedar/setCedarKey endpoint.
 */
public class SetCedarKeyParams
{
    public String cedarKey;
    public String userIdentifier;
}
