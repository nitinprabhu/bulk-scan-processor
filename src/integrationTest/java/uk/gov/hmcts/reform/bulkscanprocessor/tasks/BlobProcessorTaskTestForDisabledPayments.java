package uk.gov.hmcts.reform.bulkscanprocessor.tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscanprocessor.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscanprocessor.entity.Envelope;
import uk.gov.hmcts.reform.bulkscanprocessor.model.common.Event;
import uk.gov.hmcts.reform.bulkscanprocessor.model.out.msg.ErrorCode;
import uk.gov.hmcts.reform.bulkscanprocessor.model.out.msg.ErrorMsg;
import uk.gov.hmcts.reform.bulkscanprocessor.services.document.output.Pdf;

import java.util.List;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscanprocessor.helper.DirectoryZipper.zipDir;
import static uk.gov.hmcts.reform.bulkscanprocessor.model.common.Event.FILE_VALIDATION_FAILURE;
import static uk.gov.hmcts.reform.bulkscanprocessor.model.common.Event.ZIPFILE_PROCESSING_STARTED;

@IntegrationTest
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {
    "containers.mappings[0].container=bulkscan",
    "containers.mappings[0].jurisdiction=BULKSCAN",
    "containers.mappings[0].poBox=BULKSCANPO",
    "containers.mappings[0].paymentsEnabled=false"
})
public class BlobProcessorTaskTestForDisabledPayments extends ProcessorTestSuite<BlobProcessorTask> {

    @Before
    public void setUp() throws Exception {
        super.setUp(BlobProcessorTask::new);
    }

    @Test
    public void should_reject_file_with_payments_when_payments_are_disabled() throws Exception {
        // given
        byte[] zipBytes = zipDir("zipcontents/disabled_payments");

        uploadToBlobStorage(SAMPLE_ZIP_FILE_NAME, zipBytes);

        // and
        Pdf pdf = new Pdf(
            "1111002.pdf",
            toByteArray(getResource("zipcontents/disabled_payments/1111002.pdf"))
        );

        given(documentManagementService.uploadDocuments(ImmutableList.of(pdf)))
            .willReturn(ImmutableMap.of(
                "1111002.pdf", DOCUMENT_URL2
            ));

        // when
        processor.processBlobs();

        // then
        envelopeWasNotCreated();
        eventsWereCreated(ZIPFILE_PROCESSING_STARTED, FILE_VALIDATION_FAILURE);
        fileWasDeleted(SAMPLE_ZIP_FILE_NAME);
        errorWasSent(SAMPLE_ZIP_FILE_NAME, ErrorCode.ERR_PAYMENTS_DISABLED);
    }

    private void eventsWereCreated(Event event1, Event event2) {
        assertThat(processEventRepository.findAll())
            .hasSize(2)
            .extracting(e -> tuple(e.getContainer(), e.getEvent()))
            .containsExactlyInAnyOrder(
                tuple(testContainer.getName(), event1),
                tuple(testContainer.getName(), event2)
            );
    }

    private void errorWasSent(String zipFileName, ErrorCode code) {
        ArgumentCaptor<ErrorMsg> argument = ArgumentCaptor.forClass(ErrorMsg.class);
        verify(serviceBusHelper).sendMessage(argument.capture());

        ErrorMsg sentMsg = argument.getValue();

        assertThat(sentMsg.zipFileName).isEqualTo(zipFileName);
        assertThat(sentMsg.jurisdiction).isEqualTo(CONTAINER_NAME);
        assertThat(sentMsg.errorCode).isEqualTo(code);
    }

    private void envelopeWasNotCreated() {
        List<Envelope> envelopesInDb = envelopeRepository.findAll();
        assertThat(envelopesInDb).isEmpty();
    }

    private void fileWasDeleted(String fileName) throws Exception {
        CloudBlockBlob blob = testContainer.getBlockBlobReference(fileName);
        await("file should be deleted").timeout(2, SECONDS).until(blob::exists, is(false));
    }
}