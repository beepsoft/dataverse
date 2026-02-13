/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.api.cedar;

import edu.harvard.iq.dataverse.cedar.ExportToCedarParams;

import java.util.List;

/**
 *  Parameters for  /api/admon/cedar/syncMdbsWithCedar endpoint invocation.
 */
public class CedarParams
{
    public static class MdbParam
    {
        // Name of the MDB to sync. Either name or id must be provided
        public String name;

        // ID of the MDB to sync. Either name or id must be provided.
        public Long id;

        // An optional namespace URI to set for the MDB in Dataverse
        public String namespaceUri;

        // Explicit CEDAR UUID to use when creating the template. If not provided one is automatically generated based
        // on the name of the MDB.
        public String cedarUuid;
    }

    public List<MdbParam> mdbParams;
    public ExportToCedarParams cedarParams;

    public CedarParams()
    {
    }

    public List<MdbParam> getMdbParams()
    {
        return mdbParams;
    }

    public void setMdbParams(List<MdbParam> mdbParams)
    {
        this.mdbParams = mdbParams;
    }

    public ExportToCedarParams getCedarParams()
    {
        return cedarParams;
    }

    public void setCedarParams(ExportToCedarParams cedarParams)
    {
        this.cedarParams = cedarParams;
    }
}
