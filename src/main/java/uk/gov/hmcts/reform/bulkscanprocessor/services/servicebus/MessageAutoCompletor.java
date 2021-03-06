package uk.gov.hmcts.reform.bulkscanprocessor.services.servicebus;

import com.microsoft.azure.servicebus.IQueueClient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MessageAutoCompletor {

    private final IQueueClient queueClient;

    public MessageAutoCompletor(IQueueClient queueClient) {
        this.queueClient = queueClient;
    }

    public CompletableFuture<Void> completeAsync(UUID lockToken) {
        return queueClient.completeAsync(lockToken);
    }

    public CompletableFuture<Void> deadLetterAsync(UUID lockToken, String reason, String description) {
        return queueClient.deadLetterAsync(lockToken, reason, description);
    }
}
