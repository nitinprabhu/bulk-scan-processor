package uk.gov.hmcts.reform.bulkscanprocessor.services;

import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.ListBlobItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FileNamesExtractorTest {

    @Mock
    private CloudBlobContainer container;

    @Mock
    private ListBlobItem blob;

    @Test
    void should_extract_single_file_name() {
        // given
        given(container.listBlobs()).willReturn(singletonList(blob));
        given(blob.getUri()).willReturn(URI.create("file.zip"));

        // when
        var zipFileNames = FileNamesExtractor.getShuffledZipFileNames(container);

        // then
        assertThat(zipFileNames).containsExactly("file.zip");
    }

    @Test
    void should_handle_empty_file_name() {
        // given
        given(container.listBlobs()).willReturn(singletonList(blob));
        given(blob.getUri()).willReturn(URI.create(""));

        // when
        var zipFileNames = FileNamesExtractor.getShuffledZipFileNames(container);

        // then
        assertThat(zipFileNames).isEmpty();
    }

    @Test
    void should_shuffle_multiple_file_names() {
        // given
        var blob0 = mock(ListBlobItem.class);
        var blob1 = mock(ListBlobItem.class);
        var blob2 = mock(ListBlobItem.class);
        var blob3 = mock(ListBlobItem.class);
        var blob4 = mock(ListBlobItem.class);
        var blobs = asList(blob0, blob1, blob2, blob3, blob4);
        given(container.listBlobs()).willReturn(blobs);
        for (var i = 0; i < blobs.size(); i++) {
            given(blobs.get(i).getUri()).willReturn(URI.create("file" + i + ".zip"));
        }

        // when
        var zipFileNames = FileNamesExtractor.getShuffledZipFileNames(container);

        // then
        assertThat(zipFileNames).hasSize(blobs.size());

        String[] fileNames = new String[blobs.size()];
        for (var i = 0; i < blobs.size(); i++) {
            fileNames[i] = "file" + i + ".zip";
        }
        // ensure all file names are present in the result
        assertThat(zipFileNames).containsExactlyInAnyOrder(fileNames);

        var isShuffled = false;
        // ensure resulting file names are not in the same order as file names of original blobs
        for (var i = 0; i < blobs.size(); i++) {
            if (!zipFileNames.get(i).equals(fileNames[i])) {
                isShuffled = true;
                break;
            }
        }
        assertThat(isShuffled).isTrue();
    }
}