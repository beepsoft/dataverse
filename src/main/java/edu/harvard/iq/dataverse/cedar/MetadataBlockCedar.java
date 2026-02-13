/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

import edu.harvard.iq.dataverse.MetadataBlock;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * ARP specific additional values associated with MetadataBlocks.
 */
@NamedQueries({
        // This should give a single result
        @NamedQuery(name = "MetadataBlockCedar.findOneForMetadataBlock",
                query = "SELECT o FROM MetadataBlockCedar o WHERE o.metadataBlock=:metadataBlock ORDER BY o.id"),
        @NamedQuery(name = "MetadataBlockCedar.findOneForMetadataBlockById",
                query = "SELECT o FROM MetadataBlockCedar o WHERE o.metadataBlock.id=:metadataBlockId ORDER BY o.id"),
        @NamedQuery(name = "MetadataBlockCedar.findByRoCrateConformsToId",
                query = "SELECT mdbArp FROM MetadataBlockCedar mdbArp WHERE mdbArp.roCrateConformsToId=:roCrateConformsToId")
})
@Entity
@Table(indexes = {@Index(columnList="field_type_id")})
public class MetadataBlockCedar implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private MetadataBlock metadataBlock;

    private String roCrateConformsToId;

    @Column(columnDefinition="TEXT")
    private String cedarDefinition;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public MetadataBlock getMetadataBlock()
    {
        return metadataBlock;
    }

    public void setMetadataBlock(MetadataBlock metadataBlock)
    {
        this.metadataBlock = metadataBlock;
    }

    public String getRoCrateConformsToId()
    {
        return roCrateConformsToId;
    }

    public void setRoCrateConformsToId(String roCrateConformsToId)
    {
        this.roCrateConformsToId = roCrateConformsToId;
    }

    public String getCedarDefinition()
    {
        return cedarDefinition;
    }

    public void setCedarDefinition(String cedarDefinition)
    {
        this.cedarDefinition = cedarDefinition;
    }
}
