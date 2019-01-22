package uk.gov.hmcts.reform.bulkscanprocessor.validation;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import uk.gov.hmcts.reform.bulkscanprocessor.exceptions.ContainerJurisdictionMismatchException;
import uk.gov.hmcts.reform.bulkscanprocessor.exceptions.FileNameIrregularitiesException;
import uk.gov.hmcts.reform.bulkscanprocessor.exceptions.OcrDataNotFoundException;
import uk.gov.hmcts.reform.bulkscanprocessor.model.blob.InputDocumentType;
import uk.gov.hmcts.reform.bulkscanprocessor.model.blob.InputEnvelope;
import uk.gov.hmcts.reform.bulkscanprocessor.model.common.Classification;
import uk.gov.hmcts.reform.bulkscanprocessor.services.document.output.Pdf;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static uk.gov.hmcts.reform.bulkscanprocessor.helper.InputEnvelopeCreator.inputEnvelope;
import static uk.gov.hmcts.reform.bulkscanprocessor.helper.InputEnvelopeCreator.scannableItem;

@SuppressWarnings("checkstyle:LineLength")
public class EnvelopeProcessorValidationTest {

    @Test
    public void should_throw_exception_when_zip_file_contains_fewer_pdfs() throws Exception {
        // given
        InputEnvelope envelope = inputEnvelope(
            "BULKSCAN",
            Classification.EXCEPTION,
            asList(
                scannableItem("hello.pdf"),
                scannableItem("world.pdf")
            )
        );
        List<Pdf> pdfs = singletonList(new Pdf("hello.pdf", null));

        // when
        Throwable throwable = catchThrowable(() -> EnvelopeValidator.assertEnvelopeHasPdfs(envelope, pdfs));

        // then
        assertThat(throwable)
            .isInstanceOf(FileNameIrregularitiesException.class)
            .hasMessageMatching("Missing PDFs: world.pdf");
    }

    @Test
    public void should_throw_exception_when_zip_file_contains_more_pdfs() throws Exception {
        // given
        InputEnvelope envelope = inputEnvelope(
            "BULKSCAN",
            Classification.EXCEPTION,
            asList(
                scannableItem("aaa.pdf"),
                scannableItem("bbb.pdf")
            )
        );
        List<Pdf> pdfs = asList(
            new Pdf("aaa.pdf", null),
            new Pdf("bbb.pdf", null),
            new Pdf("extra.pdf", null)
        );

        // when
        Throwable throwable = catchThrowable(() -> EnvelopeValidator.assertEnvelopeHasPdfs(envelope, pdfs));

        // then
        assertThat(throwable)
            .isInstanceOf(FileNameIrregularitiesException.class)
            .hasMessageMatching("Not declared PDFs: extra.pdf");
    }

    @Test
    public void should_throw_exception_when_zip_file_has_mismatching_pdf() throws Exception {
        // given
        InputEnvelope envelope = inputEnvelope(
            "BULKSCAN",
            Classification.EXCEPTION,
            asList(
                scannableItem("xxx.pdf"),
                scannableItem("yyy.pdf"),
                scannableItem("zzz.pdf")
            )
        );
        List<Pdf> pdfs = asList(
            new Pdf("xxx.pdf", null),
            new Pdf("yyy.pdf", null),
            new Pdf("something_not_declared.pdf", null)
        );

        // when
        Throwable throwable = catchThrowable(() -> EnvelopeValidator.assertEnvelopeHasPdfs(envelope, pdfs));

        // then
        assertThat(throwable)
            .isInstanceOf(FileNameIrregularitiesException.class)
            .hasMessageContaining("Not declared PDFs: something_not_declared.pdf")
            .hasMessageContaining("Missing PDFs: zzz.pdf");
    }

    @Test
    public void should_throw_exception_when_required_documents_are_missing() throws Exception {
        InputEnvelope envelope = inputEnvelope(
            "SSCS",
            Classification.NEW_APPLICATION,
            asList(
                scannableItem(InputDocumentType.OTHER, emptyMap()), // no 'SSCS1' documents
                scannableItem(InputDocumentType.CHERISHED, emptyMap())
            )
        );

        Throwable throwable = catchThrowable(() ->
            EnvelopeValidator.assertEnvelopeContainsOcrDataIfRequired(envelope)
        );

        assertThat(throwable).isInstanceOf(OcrDataNotFoundException.class)
            .hasMessageContaining("No documents");
    }

    @Test
    public void should_throw_exception_when_document_type_for_new_applications_is_not_configured_for_given_jurisdiction() throws Exception {
        String invalidJurisdictionForNewApplications = "some_name";

        InputEnvelope envelope = inputEnvelope(
            invalidJurisdictionForNewApplications,
            Classification.NEW_APPLICATION,
            asList(
                scannableItem(InputDocumentType.OTHER, emptyMap()),
                scannableItem(InputDocumentType.CHERISHED, emptyMap())
            )
        );

        Throwable throwable = catchThrowable(() ->
            EnvelopeValidator.assertEnvelopeContainsOcrDataIfRequired(envelope)
        );

        assertThat(throwable).isInstanceOf(OcrDataNotFoundException.class)
            .hasMessageContaining(invalidJurisdictionForNewApplications)
            .hasMessageContaining("not configured");
    }

    @Test
    public void should_throw_exception_when_required_documents_dont_have_ocr() throws Exception {
        InputEnvelope envelope = inputEnvelope(
            "SSCS",
            Classification.NEW_APPLICATION,
            asList(
                scannableItem(InputDocumentType.SSCS1, emptyMap()),
                scannableItem(InputDocumentType.SSCS1, emptyMap())
            )
        );

        Throwable throwable = catchThrowable(() ->
            EnvelopeValidator.assertEnvelopeContainsOcrDataIfRequired(envelope)
        );

        assertThat(throwable).isInstanceOf(OcrDataNotFoundException.class)
            .hasMessageContaining("Missing OCR");
    }

    @Test
    public void should_not_throw_exception_when_ocr_data_is_not_required() throws Exception {
        InputEnvelope envelope = inputEnvelope(
            "SSCS",
            Classification.EXCEPTION, // not NEW_APPLICATION
            asList(
                scannableItem(InputDocumentType.OTHER, emptyMap()), // on OCR data
                scannableItem(InputDocumentType.CHERISHED, emptyMap())
            )
        );

        Throwable throwable = catchThrowable(() ->
            EnvelopeValidator.assertEnvelopeContainsOcrDataIfRequired(envelope)
        );

        assertThat(throwable).isNull();
    }

    @Test
    public void should_not_throw_exception_when_ocr_data_is_not_missing() throws Exception {
        InputEnvelope envelope = inputEnvelope(
            "SSCS",
            Classification.NEW_APPLICATION,
            asList(
                scannableItem(InputDocumentType.SSCS1, ImmutableMap.of("key", "value"))
            )
        );

        Throwable throwable = catchThrowable(() ->
            EnvelopeValidator.assertEnvelopeContainsOcrDataIfRequired(envelope)
        );

        assertThat(throwable).isNull();
    }

    @Test
    public void should_throw_an_exception_when_jurisdiction_and_container_dont_match() {
        // given
        InputEnvelope envelope = inputEnvelope("test_jurisdiction");
        String container = "container_not_matching_jurisdiction";

        // when
        Throwable err = catchThrowable(() -> EnvelopeValidator.assertContainerMatchesJurisdiction(envelope, container));

        // then
        assertThat(err)
            .isInstanceOf(ContainerJurisdictionMismatchException.class)
            .hasMessageContaining(envelope.jurisdiction)
            .hasMessageContaining(container);
    }

    @Test
    public void should_not_throw_an_exception_when_jurisdiction_and_container_match() {
        // given
        InputEnvelope envelope = inputEnvelope("Aaa");
        String container = "AaA";

        // when
        Throwable err = catchThrowable(() -> EnvelopeValidator.assertContainerMatchesJurisdiction(envelope, container));

        // then
        assertThat(err).isNull();
    }

}