/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.api.cedar.CedarParams;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.cedar.CedarServiceBean;
import edu.harvard.iq.dataverse.cedar.ExportToCedarParams;
import edu.harvard.iq.dataverse.cedar.TemplateFolderCheckResult;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.PrimeFaces;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataversePageCedarUploadTest {

    @Mock
    DataverseSession session;

    @Mock
    CedarServiceBean cedarService;

    @InjectMocks
    DataversePage dataversePage;

    private static final String DIRECTORY_URL = "https://repo.arp.orgx/folders/b564c0d2-e518-422d-9a40-45ade2e8eef5";
    private static final String API_KEY = "test-api-key";
    private static final String ORIGINAL_CEDAR_UUID = "d5af206b-7d76-40b3-bf71-9deedb9f8cb3";

    @BeforeEach
    void setUp() {
        dataversePage.setCedarUploadMdbName("citation");
        dataversePage.setCedarUploadDirectoryUrl(DIRECTORY_URL);
        dataversePage.setCedarUploadApiKey(API_KEY);
    }

    private void stubOriginalCedarUuid() {
        when(cedarService.getCedarUuidForMdb("citation")).thenReturn(ORIGINAL_CEDAR_UUID);
    }

    private AuthenticatedUser superuser() {
        AuthenticatedUser user = Mockito.mock(AuthenticatedUser.class);
        when(user.isSuperuser()).thenReturn(true);
        when(session.getUser()).thenReturn(user);
        return user;
    }

    @Nested
    class Authorization {

        @Test
        void rejectsNonSuperuser() {
            AuthenticatedUser nonSuperuser = Mockito.mock(AuthenticatedUser.class);
            when(nonSuperuser.isSuperuser()).thenReturn(false);
            when(session.getUser()).thenReturn(nonSuperuser);

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(Mockito.any()), Mockito.atLeastOnce());
            }
            verify(cedarService, never()).uploadMetadataBlockToCedar(any(), any(), anyBoolean(), anyBoolean());
        }

        @Test
        void rejectsAnonymousUser() {
            when(session.getUser()).thenReturn(null);

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(Mockito.any()), Mockito.atLeastOnce());
            }
            verify(cedarService, never()).uploadMetadataBlockToCedar(any(), any(), anyBoolean(), anyBoolean());
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsEmptyDirectoryUrl() {
            superuser();
            dataversePage.setCedarUploadDirectoryUrl("");

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(
                    BundleUtil.getStringFromBundle("dataverse.cedarUpload.emptyDirectoryUrl"),
                    errorCaptor.getValue());
            verify(cedarService, never()).uploadMetadataBlockToCedar(any(), any(), anyBoolean(), anyBoolean());
        }

        @Test
        void rejectsEmptyApiKey() {
            superuser();
            dataversePage.setCedarUploadApiKey("   ");

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(
                    BundleUtil.getStringFromBundle("dataverse.cedarUpload.emptyApiKey"),
                    errorCaptor.getValue());
            verify(cedarService, never()).uploadMetadataBlockToCedar(any(), any(), anyBoolean(), anyBoolean());
        }
    }

    @Nested
    class Success {

        @Test
        void callsUploadWithCorrectParams() {
            superuser();
            stubOriginalCedarUuid();

            ArgumentCaptor<CedarParams.MdbParam> mdbCaptor = ArgumentCaptor.forClass(CedarParams.MdbParam.class);
            ArgumentCaptor<ExportToCedarParams> paramsCaptor = ArgumentCaptor.forClass(ExportToCedarParams.class);

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                verify(cedarService).uploadMetadataBlockToCedar(mdbCaptor.capture(), paramsCaptor.capture(), eq(false), eq(false));
                jsfHelper.verify(() -> JsfHelper.addSuccessMessage(Mockito.any()), Mockito.atLeastOnce());
            }

            assertEquals("citation", mdbCaptor.getValue().name);
            assertEquals(ORIGINAL_CEDAR_UUID, mdbCaptor.getValue().cedarUuid);
            assertEquals(DIRECTORY_URL, paramsCaptor.getValue().folderId);
            assertEquals(API_KEY, paramsCaptor.getValue().apiKey);
            assertEquals("arp.orgx", paramsCaptor.getValue().cedarDomain);
            assertEquals(API_KEY, dataversePage.getCedarUploadApiKey());
        }

        @Test
        void passesNullCedarUuidWhenNoStoredTemplate() {
            superuser();
            when(cedarService.getCedarUuidForMdb("citation")).thenReturn(null);

            ArgumentCaptor<CedarParams.MdbParam> mdbCaptor = ArgumentCaptor.forClass(CedarParams.MdbParam.class);

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                verify(cedarService).uploadMetadataBlockToCedar(mdbCaptor.capture(), any(), eq(false), eq(false));
            }

            assertEquals("citation", mdbCaptor.getValue().name);
            assertEquals(null, mdbCaptor.getValue().cedarUuid);
        }

        @Test
        void stripsApiKeyAuthorizationPrefix() {
            superuser();
            stubOriginalCedarUuid();
            dataversePage.setCedarUploadApiKey("apiKey " + API_KEY);

            ArgumentCaptor<ExportToCedarParams> paramsCaptor = ArgumentCaptor.forClass(ExportToCedarParams.class);

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                verify(cedarService).uploadMetadataBlockToCedar(any(), paramsCaptor.capture(), eq(false), eq(false));
            }

            assertEquals(API_KEY, paramsCaptor.getValue().apiKey);
        }

        @Test
        void showsSuccessMessageWithMdbName() {
            superuser();
            stubOriginalCedarUuid();

            ArgumentCaptor<String> successCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                jsfHelper.verify(() -> JsfHelper.addSuccessMessage(successCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(
                    BundleUtil.getStringFromBundle("dataverse.cedarUpload.successNamed", List.of("citation")),
                    successCaptor.getValue());
        }
    }

    @Nested
    class Failure {

        @Test
        void showsErrorMessageOnServiceException() {
            superuser();
            stubOriginalCedarUuid();

            doThrow(new RuntimeException("CEDAR upload error"))
                    .when(cedarService).uploadMetadataBlockToCedar(any(), any(), eq(false), eq(false));

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.uploadMdbToCedar();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertTrue(errorCaptor.getValue().contains("CEDAR upload error"));
        }
    }

    @Nested
    class FolderCheck {

        @Test
        void uploadsImmediatelyWhenSameFolder() throws Exception {
            superuser();
            stubOriginalCedarUuid();
            when(cedarService.checkTemplateFolder(any(), any()))
                    .thenReturn(new TemplateFolderCheckResult(
                            TemplateFolderCheckResult.Status.SAME_FOLDER, DIRECTORY_URL, "/Users/Admin"));

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.checkCedarUploadFolder();
                verify(cedarService).uploadMetadataBlockToCedar(any(), any(), eq(false), eq(false));
                jsfHelper.verify(() -> JsfHelper.addSuccessMessage(Mockito.any()), Mockito.atLeastOnce());
            }
        }

        @Test
        void uploadsImmediatelyWhenNotFound() throws Exception {
            superuser();
            stubOriginalCedarUuid();
            when(cedarService.checkTemplateFolder(any(), any()))
                    .thenReturn(TemplateFolderCheckResult.notFound());

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.checkCedarUploadFolder();
                verify(cedarService).uploadMetadataBlockToCedar(any(), any(), eq(false), eq(false));
            }
        }

        @Test
        void setsMismatchPathLinkAndCallbackWhenDifferentFolder() throws Exception {
            superuser();
            stubOriginalCedarUuid();
            when(cedarService.checkTemplateFolder(any(), any()))
                    .thenReturn(new TemplateFolderCheckResult(
                            TemplateFolderCheckResult.Status.DIFFERENT_FOLDER,
                            "https://repo.arp.orgx/folders/other",
                            "/Users/CEDAR Admin"));

            PrimeFaces primeFaces = Mockito.mock(PrimeFaces.class);
            PrimeFaces.Ajax ajax = Mockito.mock(PrimeFaces.Ajax.class);
            when(primeFaces.ajax()).thenReturn(ajax);

            ArgumentCaptor<CedarParams.MdbParam> mdbCaptor = ArgumentCaptor.forClass(CedarParams.MdbParam.class);

            try (MockedStatic<PrimeFaces> pf = Mockito.mockStatic(PrimeFaces.class);
                 MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                pf.when(PrimeFaces::current).thenReturn(primeFaces);
                dataversePage.checkCedarUploadFolder();
                verify(ajax).addCallbackParam("folderMismatch", true);
                verify(cedarService).checkTemplateFolder(mdbCaptor.capture(), any());
                verify(cedarService, never()).uploadMetadataBlockToCedar(any(), any(), anyBoolean(), anyBoolean());
            }

            assertEquals(ORIGINAL_CEDAR_UUID, mdbCaptor.getValue().cedarUuid);
            assertEquals("/Users/CEDAR Admin", dataversePage.getCedarUploadFolderMismatchPath());
            assertEquals(
                    "https://cedar.arp.orgx/dashboard?folderId="
                            + URLEncoder.encode("https://repo.arp.orgx/folders/other", StandardCharsets.UTF_8),
                    dataversePage.getCedarUploadFolderMismatchUrl());
        }

        @Test
        void confirmProceedsWithMismatchFlag() {
            superuser();
            stubOriginalCedarUuid();

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.confirmCedarUploadDespiteFolderMismatch();
                verify(cedarService).uploadMetadataBlockToCedar(any(), any(), eq(false), eq(true));
                jsfHelper.verify(() -> JsfHelper.addSuccessMessage(Mockito.any()), Mockito.atLeastOnce());
            }
        }
    }

    @Nested
    class Init {

        @Test
        void doesNotClearCredentialsOnInit() {
            dataversePage.setCedarUploadDirectoryUrl(DIRECTORY_URL);
            dataversePage.setCedarUploadApiKey(API_KEY);
            dataversePage.initCedarUpload(null);
            assertEquals(DIRECTORY_URL, dataversePage.getCedarUploadDirectoryUrl());
            assertEquals(API_KEY, dataversePage.getCedarUploadApiKey());
        }
    }
}
