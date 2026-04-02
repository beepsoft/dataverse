/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.cedar.CedarServiceBean;
import edu.harvard.iq.dataverse.cedar.CedarTemplateErrors;
import edu.harvard.iq.dataverse.cedar.CedarTemplateErrorsException;
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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataversePageCedarImportTest {

    @Mock
    DataverseSession session;

    @Mock
    DataverseServiceBean dataverseService;

    @Mock
    CedarServiceBean cedarService;

    @InjectMocks
    DataversePage dataversePage;

    private Dataverse dataverse;
    private static final String VALID_TEMPLATE_PATH = "src/test/resources/cedar/no-error-template.json";

    @BeforeEach
    void setUp() {
        dataverse = new Dataverse();
        dataverse.setId(1L);
        dataverse.setAlias("root");
        dataverse.setOwner(null);
        dataversePage.setDataverse(dataverse);

        lenient().when(dataverseService.findSystemMetadataBlocks()).thenReturn(Collections.emptyList());
        lenient().when(dataverseService.findMetadataBlocksByDataverseId(anyLong())).thenReturn(Collections.emptyList());
        lenient().when(dataverseService.find(1L)).thenReturn(dataverse);
    }

    @Nested
    class Authorization {

        @Test
        void rejectsNonSuperuser() throws Exception {
            AuthenticatedUser nonSuperuser = Mockito.mock(AuthenticatedUser.class);
            when(nonSuperuser.isSuperuser()).thenReturn(false);
            when(session.getUser()).thenReturn(nonSuperuser);

            dataversePage.setCedarTemplateJson("{\"schema:identifier\":\"test\"}");
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(Mockito.any()), Mockito.atLeastOnce());
            }
            verify(cedarService, never()).createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean());
        }

        @Test
        void rejectsAnonymousUser() throws Exception {
            when(session.getUser()).thenReturn(null);

            dataversePage.setCedarTemplateJson("{\"schema:identifier\":\"test\"}");
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(Mockito.any()), Mockito.atLeastOnce());
            }
            verify(cedarService, never()).createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean());
        }

        @Test
        void acceptsSuperuser() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            String templateJson = Files.readString(Paths.get(VALID_TEMPLATE_PATH));
            dataversePage.setCedarTemplateJson(templateJson);

            ArgumentCaptor<String> successCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                verify(cedarService).createOrUpdateMdbFromCedarTemplate(eq("root"), eq(templateJson), eq(false));
                // Success! – CEDAR template imported: arp_test
                jsfHelper.verify(() -> JsfHelper.addSuccessMessage(successCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(BundleUtil.getStringFromBundle("dataverse.cedarImport.successNamed", List.of("arp_test")), 
                    successCaptor.getValue());
        }
    }

    @Nested
    class DataverseValidation {

        @Test
        void rejectsNullDataverse() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            dataversePage.setDataverse(null);
            dataversePage.setCedarTemplateJson("{\"schema:identifier\":\"test\"}");

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(BundleUtil.getStringFromBundle("dataverse.cedarImport.noDataverse"), errorCaptor.getValue());
            verify(cedarService, never()).createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean());
        }

        @Test
        void rejectsDataverseWithoutId() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            Dataverse dvNoId = new Dataverse();
            dvNoId.setId(null);
            dvNoId.setAlias("root");
            dataversePage.setDataverse(dvNoId);
            dataversePage.setCedarTemplateJson("{\"schema:identifier\":\"test\"}");

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(BundleUtil.getStringFromBundle("dataverse.cedarImport.noDataverse"), errorCaptor.getValue());
            verify(cedarService, never()).createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean());
        }

        @Test
        void rejectsDataverseWithoutAlias() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            Dataverse dvNoAlias = new Dataverse();
            dvNoAlias.setId(1L);
            dvNoAlias.setAlias(null);
            dataversePage.setDataverse(dvNoAlias);
            dataversePage.setCedarTemplateJson("{\"schema:identifier\":\"test\"}");

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(BundleUtil.getStringFromBundle("dataverse.cedarImport.noDataverse"), errorCaptor.getValue());
            verify(cedarService, never()).createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean());
        }
    }

    @Nested
    class EmptyJson {

        @Test
        void rejectsNullJson() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            dataversePage.setCedarTemplateJson(null);

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(BundleUtil.getStringFromBundle("dataverse.cedarImport.empty"), errorCaptor.getValue());
        }

        @Test
        void rejectsEmptyString() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            dataversePage.setCedarTemplateJson("");

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(BundleUtil.getStringFromBundle("dataverse.cedarImport.empty"), errorCaptor.getValue());
        }

        @Test
        void rejectsWhitespaceOnly() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            dataversePage.setCedarTemplateJson("   ");

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertEquals(BundleUtil.getStringFromBundle("dataverse.cedarImport.empty"), errorCaptor.getValue());
        }
    }

    @Nested
    class SuccessPath {

        @Test
        void importsSuccessfullyWithValidTemplate() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            String templateJson = Files.readString(Paths.get(VALID_TEMPLATE_PATH));
            dataversePage.setCedarTemplateJson(templateJson);

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();

                verify(cedarService).createOrUpdateMdbFromCedarTemplate(eq("root"), eq(templateJson), eq(false));
                verify(cedarService).updateMetadataBlockInNewTransaction(eq("root"), eq("arp_test"));
                verify(dataverseService).find(1L);
                jsfHelper.verify(() -> JsfHelper.addSuccessMessage(Mockito.any()), Mockito.atLeastOnce());
            }
        }

        @Test
        void clearsStateAfterSuccessfulImport() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            String templateJson = Files.readString(Paths.get(VALID_TEMPLATE_PATH));
            dataversePage.setCedarTemplateJson(templateJson);

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
            }

            assertTrue(dataversePage.getCedarTemplateJson() == null || dataversePage.getCedarTemplateJson().isEmpty());
            assertNull(dataversePage.getCedarTemplateName());
            assertNull(dataversePage.getCedarTemplateIdentifier());
            assertNull(dataversePage.getCedarTemplateDescription());
            assertNull(dataversePage.getCedarTemplateParseError());
        }

        @Test
        void usesCedarTemplateNameInSuccessMessage() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            String templateJson = "{\"schema:name\":\"My Custom Template\",\"schema:identifier\":\"test_template\"}";
            dataversePage.setCedarTemplateJson(templateJson);

            ArgumentCaptor<String> successCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addSuccessMessage(successCaptor.capture()), Mockito.atLeastOnce());
            }
            assertTrue(successCaptor.getValue().contains("My Custom Template"));
        }

        @Test
        void usesIdentifierAsDisplayNameWhenNameEmpty() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            String templateJson = "{\"schema:identifier\":\"arp_test\"}";
            dataversePage.setCedarTemplateJson(templateJson);

            ArgumentCaptor<String> successCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addSuccessMessage(successCaptor.capture()), Mockito.atLeastOnce());
            }
            assertTrue(successCaptor.getValue().contains("arp_test"));
        }
    }

    @Nested
    class ExceptionHandling {

        @Test
        void handlesCedarTemplateErrorsException() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            String templateJson = Files.readString(Paths.get(VALID_TEMPLATE_PATH));
            dataversePage.setCedarTemplateJson(templateJson);

            CedarTemplateErrors errors = new CedarTemplateErrors();
            errors.errors.add("validation error");
            when(cedarService.createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean()))
                    .thenThrow(new CedarTemplateErrorsException(errors));

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(Mockito.any()), Mockito.atLeastOnce());
            }
            verify(cedarService, never()).updateMetadataBlockInNewTransaction(any(), any());
        }

        @Test
        void handlesGenericExceptionWithMessage() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            String templateJson = Files.readString(Paths.get(VALID_TEMPLATE_PATH));
            dataversePage.setCedarTemplateJson(templateJson);

            when(cedarService.createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean()))
                    .thenThrow(new RuntimeException("custom error message"));

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            assertTrue(errorCaptor.getValue().contains("custom error message"));
        }

        @Test
        void handlesGenericExceptionWithoutMessage() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            String templateJson = Files.readString(Paths.get(VALID_TEMPLATE_PATH));
            dataversePage.setCedarTemplateJson(templateJson);

            when(cedarService.createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean()))
                    .thenThrow(new RuntimeException());

            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(Mockito.any()), Mockito.atLeastOnce());
            }
        }
    }

    @Nested
    class ParseError {

        @Test
        void showsErrorWhenParseFailed() throws Exception {
            AuthenticatedUser superuser = Mockito.mock(AuthenticatedUser.class);
            when(superuser.isSuperuser()).thenReturn(true);
            when(session.getUser()).thenReturn(superuser);

            dataversePage.setCedarTemplateJson("invalid json {{{");

            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            try (MockedStatic<JsfHelper> jsfHelper = Mockito.mockStatic(JsfHelper.class)) {
                dataversePage.importCedarTemplate();
                jsfHelper.verify(() -> JsfHelper.addErrorMessage(errorCaptor.capture()), Mockito.atLeastOnce());
            }
            verify(cedarService, never()).createOrUpdateMdbFromCedarTemplate(any(), any(), anyBoolean());
            assertEquals(
                    BundleUtil.getStringFromBundle("dataverse.cedarImport.parseError"),
                    errorCaptor.getValue());
        }
    }
}
