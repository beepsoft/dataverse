/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

public class ExportToCedarParams
{
    // The domain where CEDAR is deployed
    public String cedarDomain;

    // CEDAR (admin) API key for authentication
    public String apiKey;

    // Folder in CEDAR to generate the template into
    public String folderId;
}
