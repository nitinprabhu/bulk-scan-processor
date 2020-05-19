package uk.gov.hmcts.reform.bulkscanprocessor.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscanprocessor.entity.Status;
import uk.gov.hmcts.reform.bulkscanprocessor.model.common.Classification;
import uk.gov.hmcts.reform.bulkscanprocessor.services.reports.models.ZipFileSummaryResponse;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static uk.gov.hmcts.reform.bulkscanprocessor.model.common.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscanprocessor.model.common.Event.DOC_UPLOADED;

public class CsvWriterTest {

    @Test
    public void should_return_csv_file_with_headers_and_csv_records() throws IOException {
        LocalDate date = LocalDate.now();
        LocalTime time = LocalTime.now();

        //given
        ZipFileSummaryResponse zip1 = new ZipFileSummaryResponse(
            "test1.zip",
            date.plusDays(1),
            time.plusHours(1),
            date.minusDays(1),
            time.minusHours(1),
            "container-1",
            DOC_UPLOADED.toString(),
            Status.UPLOADED.toString(),
            SUPPLEMENTARY_EVIDENCE.name(),
            null,
            null
        );
        ZipFileSummaryResponse zip2 = new ZipFileSummaryResponse(
            "test2.zip",
            date.plusDays(2),
            time.plusHours(2),
            date.minusDays(2),
            time.minusHours(2),
            "container-2",
            DOC_UPLOADED.toString(),
            Status.COMPLETED.toString(),
            Classification.EXCEPTION.name(),
            "ccd-id-2",
            "ccd-action-2"
        );

        //when
        File csv = CsvWriter.writeZipFilesSummaryToCsv(asList(zip1, zip2));

        //then
        List<CSVRecord> csvRecordList = readCsv(csv);

        assertThat(csvRecordList)
            .isNotEmpty()
            .hasSize(3)
            .extracting(record -> toTuple(record))
            .containsExactly(
                tuple(
                    "Container",
                    "Zip File Name",
                    "Date Received",
                    "Time Received",
                    "Date Processed",
                    "Time Processed",
                    "Status",
                    "Classification"
                ),
                tuple(
                    zip1.container,
                    zip1.fileName,
                    zip1.dateReceived.toString(),
                    zip1.timeReceived.toString(),
                    zip1.dateProcessed.toString(),
                    zip1.timeProcessed.toString(),
                    zip1.lastEventStatus,
                    zip1.classification
                ),
                tuple(
                    zip2.container,
                    zip2.fileName,
                    zip2.dateReceived.toString(),
                    zip2.timeReceived.toString(),
                    zip2.dateProcessed.toString(),
                    zip2.timeProcessed.toString(),
                    zip2.lastEventStatus,
                    zip2.classification
                )
            );
    }

    @Test
    public void should_return_csv_file_with_only_headers_when_the_data_is_null() throws IOException {
        //when
        File summaryToCsv = CsvWriter.writeZipFilesSummaryToCsv(null);

        //then
        List<CSVRecord> csvRecordList = readCsv(summaryToCsv);

        CSVRecord firstRecord = csvRecordList.get(0);
        assertSoftly(softly -> {
            for (int i = 0; i < CsvWriter.ZIP_FILES_SUMMARY_CSV_HEADERS.length; i++) {
                softly.assertThat(firstRecord.get(i)).isEqualTo(CsvWriter.ZIP_FILES_SUMMARY_CSV_HEADERS[i]);
            }
        });

    }

    private List<CSVRecord> readCsv(File summaryToCsv) throws IOException {
        return CSVFormat.DEFAULT.parse(new FileReader(summaryToCsv)).getRecords();
    }

    private Tuple toTuple(CSVRecord r) {
        return tuple(
            r.get(0), r.get(1), r.get(2), r.get(3), r.get(4), r.get(5), r.get(6), r.get(7)
        );
    }
}
