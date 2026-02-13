/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

import edu.harvard.iq.dataverse.api.cedar.CedarTemplateToDvMdbConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class CedarTemplateToDvMdbConverterTest {

    static CedarTemplateToDvMdbConverter cedarTemplateToDvMdbConverter;

    @BeforeAll
    public static void setUp() {
        // Create a mock CedarServiceBean
        CedarServiceBean mockCedarService = Mockito.mock(CedarServiceBean.class);

        // Mock the hasExternalValues method to return false by default
        when(mockCedarService.hasExternalValues(Mockito.any())).thenReturn(false);

        // Initialize the converter with the mock
        cedarTemplateToDvMdbConverter = new CedarTemplateToDvMdbConverter(mockCedarService);
    }

    @Test
    public void testCitationModifiedCedarValuesForAuthorName() throws IOException {
        String originalSchema = Files.readString(Paths.get("src/test/resources/cedar/citation.json"));
        String originalTsv = Files.readString(Paths.get("src/test/resources/cedar/citation.tsv"));
        String generatedMdbTsv = cedarTemplateToDvMdbConverter.processCedarTemplate(originalSchema, new HashSet<>());
        assertEquals(originalTsv.toLowerCase(), generatedMdbTsv.toLowerCase().trim());

        // Modify the "_cedar" and "_valueConstraints" values of the authorName and datasetContactEmail properties as if 
        // these values were edited in CEDAR
        String modifiedSchema = Files.readString(Paths.get("src/test/resources/cedar/citation_modified_cedar_values.json"));
        String modifiedTsv = Files.readString(Paths.get("src/test/resources/cedar/citation_modified_cedar_values.tsv"));
        String generatedModifiedMdbTsv = cedarTemplateToDvMdbConverter.processCedarTemplate(modifiedSchema, new HashSet<>());
        assertEquals(modifiedTsv.toLowerCase(), generatedModifiedMdbTsv.toLowerCase().trim());
    }
}
