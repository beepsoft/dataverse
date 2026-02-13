/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

//TODO: use these and remove the others from CedarTemplateToDvMdbConverter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"name", "dataverseAlias", "displayName", "blockURI"})
public class DataverseMetadataBlock
{
    private String name;
    private String dataverseAlias;
    // schema:name
    private String displayName;
    // @id
    private String blockURI;

    public DataverseMetadataBlock()
    {
    }

    public DataverseMetadataBlock(String name, String dataverseAlias, String displayName, String blockUri)
    {
        this.name = name;
        this.dataverseAlias = dataverseAlias;
        this.displayName = displayName;
        this.blockURI = blockUri;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDataverseAlias()
    {
        return dataverseAlias;
    }

    public void setDataverseAlias(String dataverseAlias)
    {
        this.dataverseAlias = dataverseAlias;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getBlockURI()
    {
        return blockURI;
    }

    public void setBlockURI(String blockUri)
    {
        this.blockURI = blockUri;
    }

}
