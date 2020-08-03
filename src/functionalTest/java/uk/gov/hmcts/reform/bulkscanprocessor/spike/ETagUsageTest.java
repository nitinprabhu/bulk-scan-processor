package uk.gov.hmcts.reform.bulkscanprocessor.spike;

import com.microsoft.azure.storage.AccessCondition;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobInputStream;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import io.vavr.Tuple2;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.bulkscanprocessor.controllers.BaseFunctionalTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ETagUsageTest extends BaseFunctionalTest {

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    void should_reject_to_download_when_etag_not_match() throws Exception {
        var blobName = uploadBlobAndGetName();
        var blockBlobClient = rejectedContainer.getBlockBlobReference(blobName);
        var accessCondition = AccessCondition.generateIfMatchCondition("NONE");
        var requestOptions = new BlobRequestOptions();
        requestOptions.setSkipEtagLocking(false);

        assertThatCode(() -> {
            try (BlobInputStream blobInputStream = blockBlobClient.openInputStream(accessCondition, requestOptions, null)) {
                // do smth with blob
                var actualBlobContents = new String(blobInputStream.readAllBytes());
                assertThat(actualBlobContents).isEqualTo(blobName + "-contents");
            }
        })
            .asInstanceOf(InstanceOfAssertFactories.type(StorageException.class))
            .satisfies(exception -> {
                assertThat(exception.getErrorCode()).isEqualTo("CONDITION_FAILED");
                assertThat(exception.getHttpStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED.value());
            });
    }

    @Test
    void should_download_the_blob_when_etag_is_correct() throws Exception {
        var blobName = uploadBlobAndGetName();
        var blockBlobClient = rejectedContainer.getBlockBlobReference(blobName);
        var accessCondition = AccessCondition.generateIfMatchCondition(blockBlobClient.getProperties().getEtag());
        var requestOptions = new BlobRequestOptions();
        requestOptions.setSkipEtagLocking(false);

        try (BlobInputStream blobInputStream = blockBlobClient.openInputStream(accessCondition, requestOptions, null)) {
            // do smth with blob
            var actualBlobContents = new String(blobInputStream.readAllBytes());
            assertThat(actualBlobContents).isEqualTo(blobName + "-contents");
        }
    }

    /*
SIZE: 10
INIT: 10
INIT: 8
INIT: 1
INIT: 2
INIT: 6
INIT: 4
METADATA UPDATED: 2
INIT: 3
     */
    @Test
    void should_update_metadata() throws Exception {
        var blobName = uploadBlobAndGetName();

        var blockBlobClients = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> {
                try {
                    return new AbstractMap.SimpleEntry<>(i, rejectedContainer.getBlockBlobReference(blobName));
                } catch (StorageException | URISyntaxException exception) {
                    throw new RuntimeException(exception);
                }
            })
            .collect(Collectors.toMap(
                AbstractMap.SimpleEntry::getKey,
                entry -> {
                    try {
                        var blobClient = entry.getValue();
                        blobClient.downloadAttributes();
                        return new Tuple2<>(blobClient, blobClient.getProperties().getEtag());
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }
            ));

        System.out.println("SIZE: " + blockBlobClients.size());

        // assert all clients are unique
        assertThat(blockBlobClients
                       .values()
                       .stream()
                       .map(tuple -> tuple._1.hashCode())
                       .collect(Collectors.toSet())
        ).hasSize(blockBlobClients.size());
        // assert all etags are the same
        assertThat(blockBlobClients
                       .values()
                       .stream()
                       .map(Tuple2::_2)
                       .collect(Collectors.toSet())
        ).hasSize(1);

        blockBlobClients
            .entrySet()
            .parallelStream()
            .forEach(entry -> {
                try {
                    System.out.println("INIT: " + entry.getKey());
                    CloudBlockBlob blockBlobClient = entry.getValue()._1;
                    String originalETag = entry.getValue()._2;
                    var accessCondition = AccessCondition.generateIfMatchCondition(originalETag);
                    var requestOptions = new BlobRequestOptions();
                    requestOptions.setSkipEtagLocking(false);

                    HashMap<String, String> map = new HashMap<>();
                    map.put("one", Integer.toString(entry.getKey()));
                    blockBlobClient.setMetadata(map);
                    blockBlobClient.uploadMetadata(accessCondition, requestOptions, null);
                    blockBlobClient.downloadAttributes();
                    String newEtag = blockBlobClient.getProperties().getEtag();

                    System.out.println("METADATA UPDATED: " + entry.getKey());

                    assertThat(newEtag).isNotEqualTo(originalETag);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        var lastClient = rejectedContainer.getBlockBlobReference(blobName);
        lastClient.downloadAttributes();
        lastClient.getMetadata().forEach(
            (key, value) -> System.out.println("FINISH METADATA: key=" + key + "; value=" + value)
        );
    }

/*
SIZE: 10
INIT: 1
INIT: 6
INIT: 8
INIT: 10
INIT: 2
INIT: 4
METADATA UPDATED: 8
 */
    @Test
    void should_update_metadata_v2() throws Exception {
        var blobName = uploadBlobAndGetName();

        var blockBlobClients = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> {
                try {
                    return new AbstractMap.SimpleEntry<>(i, rejectedContainer.getBlockBlobReference(blobName));
                } catch (StorageException | URISyntaxException exception) {
                    throw new RuntimeException(exception);
                }
            })
            .collect(Collectors.toMap(
                AbstractMap.SimpleEntry::getKey,
                AbstractMap.SimpleEntry::getValue
            ));

        System.out.println("SIZE: " + blockBlobClients.size());

        // assert all clients are unique
        assertThat(blockBlobClients
                       .values()
                       .stream()
                       .map(CloudBlockBlob::hashCode)
                       .collect(Collectors.toSet())
        ).hasSize(blockBlobClients.size());

        blockBlobClients
            .entrySet()
            .parallelStream()
            .forEach(entry -> {
                try {
                    System.out.println("INIT: " + entry.getKey());
                    CloudBlockBlob blockBlobClient = entry.getValue();
                    blockBlobClient.downloadAttributes();
                    String originalETag = blockBlobClient.getProperties().getEtag();
                    var accessCondition = AccessCondition.generateIfMatchCondition(originalETag);
                    var requestOptions = new BlobRequestOptions();
                    requestOptions.setSkipEtagLocking(false);

                    HashMap<String, String> map = new HashMap<>();
                    map.put("one", Integer.toString(entry.getKey()));
                    blockBlobClient.setMetadata(map);
                    blockBlobClient.uploadMetadata(accessCondition, requestOptions, null);
                    blockBlobClient.downloadAttributes();
                    String newEtag = blockBlobClient.getProperties().getEtag();

                    System.out.println("METADATA UPDATED: " + entry.getKey());

                    assertThat(newEtag).isNotEqualTo(originalETag);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        var lastClient = rejectedContainer.getBlockBlobReference(blobName);
        lastClient.downloadAttributes();
        lastClient.getMetadata().forEach(
            (key, value) -> System.out.println("FINISH METADATA: key=" + key + "; value=" + value)
        );
    }

    /*
    FIRST RUN:
SIZE: 10
INIT: 1
INIT: 8
INIT: 2
INIT: 10
INIT: 6
INIT: 4
DOWNLOADED: 4
DOWNLOADED: 8
DOWNLOADED: 10
DOWNLOADED: 2
DOWNLOADED: 6
DOWNLOADED: 1
INIT: 5
INIT: 9
INIT: 7
INIT: 3
DOWNLOADED: 9
DOWNLOADED: 5
DOWNLOADED: 7
DOWNLOADED: 3
     */
    @Test
    void should_download_the_blob_when_etag_is_correct_parallel_case() throws Exception {
        var blobName = uploadBlobAndGetName();

        var blockBlobClients = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> {
                try {
                    return new AbstractMap.SimpleEntry<>(i, rejectedContainer.getBlockBlobReference(blobName));
                } catch (StorageException | URISyntaxException exception) {
                    throw new RuntimeException(exception);
                }
            })
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        System.out.println("SIZE: " + blockBlobClients.size());

        assertThat(blockBlobClients
            .values()
            .stream()
            .map(CloudBlockBlob::hashCode)
            .collect(Collectors.toSet())
        ).hasSize(blockBlobClients.size());

        blockBlobClients
            .entrySet()
            .parallelStream()
            .forEach(entry -> {
                try {
                    System.out.println("INIT: " + entry.getKey());
                    rejectedContainer.getBlockBlobReference(blobName);
                    String originalETag = entry.getValue().getProperties().getEtag();
                    var accessCondition = AccessCondition.generateIfMatchCondition(originalETag);
                    var requestOptions = new BlobRequestOptions();
                    requestOptions.setSkipEtagLocking(false);

                    try (BlobInputStream blobInputStream = entry.getValue().openInputStream(
                        accessCondition,
                        requestOptions,
                        null
                    )) {
                        System.out.println("DOWNLOADED: " + entry.getKey());
                        // do smth with blob
                        var actualBlobContents = new String(blobInputStream.readAllBytes());
                        assertThat(actualBlobContents).isEqualTo(blobName + "-contents");
                    }
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
    }

    /*
    FIRST RUN:
SIZE: 10
INIT: 2
INIT: 1
INIT: 8
INIT: 4
INIT: 10
INIT: 6
DOWNLOADED: 10
DOWNLOADED: 2
DOWNLOADED: 8
DOWNLOADED: 4
DOWNLOADED: 6
DOWNLOADED: 1
UPDATED: 10
UPDATED: 8
INIT: 9
UPDATED: 4
INIT: 5
UPDATED: 1
UPDATED: 2
INIT: 3
UPDATED: 6
INIT: 7
DOWNLOADED: 9


Expecting:
 <"spike-use-etag-%d1595444498073-contents-updated">
to be equal to:
 <"spike-use-etag-%d1595444498073-contents">
but was not.
org.opentest4j.AssertionFailedError:
Expecting:
 <"spike-use-etag-%d1595444498073-contents-updated">
to be equal to:
 <"spike-use-etag-%d1595444498073-contents">
but was not.
     */
    @Test
    void should_reject_to_download_when_etag_not_match_parallel_case() throws Exception {
        var blobName = uploadBlobAndGetName();

        var blockBlobClients = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> {
                try {
                    return new AbstractMap.SimpleEntry<>(i, rejectedContainer.getBlockBlobReference(blobName));
                } catch (StorageException | URISyntaxException exception) {
                    throw new RuntimeException(exception);
                }
            })
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        System.out.println("SIZE: " + blockBlobClients.size());

        blockBlobClients
            .entrySet()
            .parallelStream()
            .forEach(entry -> {
                try {
                    System.out.println("INIT: " + entry.getKey());
                    rejectedContainer.getBlockBlobReference(blobName);
                    String originalETag = entry.getValue().getProperties().getEtag();
                    var accessCondition = AccessCondition.generateIfMatchCondition(originalETag);
                    var requestOptions = new BlobRequestOptions();
                    requestOptions.setSkipEtagLocking(false);

                    try (BlobInputStream blobInputStream = entry.getValue().openInputStream(
                        accessCondition,
                        requestOptions,
                        null
                    )) {
                        System.out.println("DOWNLOADED: " + entry.getKey());
                        // do smth with blob
                        var actualBlobContents = new String(blobInputStream.readAllBytes());
                        assertThat(actualBlobContents).isEqualTo(blobName + "-contents");
                        // update
                        entry.getValue().uploadFromByteArray(
                            (actualBlobContents + "-updated").getBytes(), 0, actualBlobContents.length() + 8, accessCondition, requestOptions, operationContext
                        );
                        System.out.println("UPDATED: " + entry.getKey());
                    }
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
    }

    private String uploadBlobAndGetName() throws URISyntaxException, StorageException, IOException {
        var blobName = "spike-use-etag-%d" + System.currentTimeMillis();
        var blobContents = (blobName + "-contents").getBytes();

        rejectedContainer
            .getBlockBlobReference(blobName)
            .uploadFromByteArray(
                blobContents,
                0,
                blobContents.length,
                null,
                null,
                operationContext
            );

        return blobName;
    }
}
