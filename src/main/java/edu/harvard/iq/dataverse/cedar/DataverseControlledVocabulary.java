/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"DatasetField", "Value", "identifier", "displayOrder"})
public class DataverseControlledVocabulary
{
    @JsonProperty("DatasetField")
    private String datasetField;
    @JsonProperty("Value")
    private String value;
    private String identifier;
    private int displayOrder;

    public DataverseControlledVocabulary()
    {
    }

    @JsonProperty("DatasetField")
    public String getDatasetField()
    {
        return datasetField;
    }

    @JsonProperty("DatasetField")
    public void setDatasetField(String DatasetField)
    {
        this.datasetField = DatasetField;
    }

    @JsonProperty("Value")
    public String getValue()
    {
        return value;
    }

    @JsonProperty("Value")
    public void setValue(String value)
    {
        this.value = value;
    }

    public String getIdentifier()
    {
        return identifier;
    }

    public void setIdentifier(String identifier)
    {
        this.identifier = identifier;
    }

    public int getDisplayOrder()
    {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder)
    {
        this.displayOrder = displayOrder;
    }
}
