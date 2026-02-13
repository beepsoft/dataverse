/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

import edu.harvard.iq.dataverse.settings.JvmSettings;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;

import javax.annotation.PostConstruct;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
@Named
public class CedarConfig
{
    @EJB
    SettingsServiceBean settingsService;

    private static final Logger logger = Logger.getLogger(CedarConfig.class.getCanonicalName());

    private static final Properties defaultProperties = new Properties();

    /**
        Static singleton instance to be accessed outside the EJB infrastructure, eg. {@link edu.kit.datamanager.ro_crate.preview.PreviewGenerator}
     */
    public static CedarConfig instance;

    static {
        try (InputStream input = CedarConfig.class.getClassLoader().getResourceAsStream("cedar/default.properties")) {
            if (input == null) {
                logger.log(Level.SEVERE, "CedarConfigBean was unable to load default.properties");
            }
            defaultProperties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void ensureStaticInstance() {
        if (CedarConfig.instance == null) {
            var lookup = "java:global/dataverse/CedarConfig";
            var inst = getCedarConfig(lookup);
            if (inst == null) {
                logger.info("ArpConfig with lookup '"+lookup+"' is not available.");
                String appVersion = JvmSettings.VERSION.lookup();
                var lookup2 = "java:global/dataverse-" + appVersion + "/ArpConfig";
                logger.info("Trying to get ArpConfig with lookup '"+lookup2+"'.");
                inst = getCedarConfig(lookup2);
                if (inst == null) {
                    logger.severe("ArpConfig is not neither via '"+lookup+"' not '"+lookup2+"'");
                }
            }
        }
    }

    private static CedarConfig getCedarConfig(String lookup) {
        try {
            InitialContext ic = new InitialContext();
            CedarConfig cedarConfig = (CedarConfig) ic.lookup(lookup);
            cedarConfig.init();
            System.out.println("cedarConfig: " + CedarConfig.instance);
            return CedarConfig.instance;
        } catch (NamingException e) {
            // ignore for now
        }
        return null;
    }

    @PostConstruct
    public void init() {
        instance = this;
    }

    /**
     * Returns the configuration value for the given key.
     *
     * The property value is read in the following order from various sources:
     * 1. admin config properties (http://localhost:8080/api/admin/settings)
     * 2. System.getProperty - command line props via -D or ./asadmin create-jvm-options "-Ddataverse.fqdn=dataverse.example.com"
     * 3. OS environment variables
     * 4. default.properties from the classpath
     *
     * The key should have a dot notation starting with "arp", for example "arp.SolrUpdaterAddress". If the value is
     * set via admin config it should be the same string, but in uppercase, for example
     * arp.SolrUpdaterAddress --> ARP_SOLR_UPDATER_ADDRESS
     *
     * To override te default value at runtime, eg:
     *     curl -X PUT -d http://some.other.host/aroma http://localhost:8080/api/admin/settings/arp.aroma.address
     * Remove runtime setting and fall back to default:
     *     curl -X DELETE http://localhost:8080/api/admin/settings/arp.aroma.address
     *
     * @param key
     * @return value for key, or null if nothing is set
     */
    public String get(String key) {
        // 1. admin config properties ( http://localhost:8080/api/admin/settings)
        var value = settingsService.get(key);

        // 2. System.getProperty - command line props via -D or ./asadmin create-jvm-options "-Ddataverse.fqdn=dataverse.example.com"
        if (value == null) {
            value = System.getProperty(key);
        }
        // 3. OS environment variables.
        if (value == null) {
            // arp.SolrUpdaterAddress --> ARP_SOLR_UPDATER_ADDRESS
            value = System.getenv(dotNotationToEnvVar(key));
        }
        // 4. defaultProperties
        if (value == null) {
            value = defaultProperties.getProperty(key);
        }

        return value;
    }

    private  String dotNotationToEnvVar(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] words = input.split("\\.");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (result.length() > 0) {
                result.append("_");
            }
            result.append(word.toUpperCase());
        }

        return result.toString();
    }

}
