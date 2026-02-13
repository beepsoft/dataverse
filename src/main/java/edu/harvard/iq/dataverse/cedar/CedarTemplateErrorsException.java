/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.cedar;

public class CedarTemplateErrorsException extends Exception
{
    CedarTemplateErrors errors;

    public CedarTemplateErrorsException(String message, CedarTemplateErrors errors)
    {
        super(message);
        this.errors = errors;
    }

    public CedarTemplateErrorsException(CedarTemplateErrors errors)
    {
        super(errors.toJson().toString());
        this.errors = errors;
    }


    public CedarTemplateErrors getErrors()
    {
        return errors;
    }
}
