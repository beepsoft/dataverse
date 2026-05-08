package edu.harvard.iq.dataverse.cedar;

public final class CedarNamespaceUriUtil {

    private static final String DEFAULT_BASE_NAMESPACE_URI = "https://dataverse.org/schema/";

    private CedarNamespaceUriUtil() {
    }

    public static String mdbNamespaceUri(String namespaceUri, String mdbName) {
        String normalized = namespaceUri;
        if (normalized == null || normalized.isBlank()) {
            String safeMdbName = mdbName == null ? "" : mdbName.trim();
            normalized = DEFAULT_BASE_NAMESPACE_URI + (safeMdbName.isEmpty() ? "" : safeMdbName + "/");
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }
}

