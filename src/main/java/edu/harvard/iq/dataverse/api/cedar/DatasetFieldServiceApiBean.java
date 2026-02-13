/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.api.cedar;

import edu.harvard.iq.dataverse.api.DatasetFieldServiceApi;
import jakarta.ejb.Stateless;
import jakarta.inject.Named;

/**
 * DatasetFieldServiceApiBean allows injecting DatasetFieldServiceApi as a bean, so that it can be
 * used by the CedarApi implementation.
 */
@Named
@Stateless
public class DatasetFieldServiceApiBean extends DatasetFieldServiceApi {
}
