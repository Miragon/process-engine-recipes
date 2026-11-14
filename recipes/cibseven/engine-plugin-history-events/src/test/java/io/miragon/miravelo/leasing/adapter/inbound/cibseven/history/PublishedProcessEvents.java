package io.miragon.miravelo.leasing.adapter.inbound.cibseven.history;

import java.util.ArrayList;
import java.util.List;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class PublishedProcessEvents {

    private static final String DESTINATION = "process-events";
    private static final long RECEIVE_TIMEOUT_MILLIS = 200;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record ReceivedEvent(String type, String activityId, String businessKey) {
    }

    static ReceivedEvent event(String type, String activityId, String businessKey) {
        return new ReceivedEvent(type, activityId, businessKey);
    }

    static List<ReceivedEvent> receiveAll(OutputDestination output) {
        List<ReceivedEvent> received = new ArrayList<>();
        Message<byte[]> message;
        while ((message = output.receive(RECEIVE_TIMEOUT_MILLIS, DESTINATION)) != null) {
            received.add(toEvent(JSON.readTree(message.getPayload())));
        }
        return received;
    }

    private static ReceivedEvent toEvent(JsonNode json) {
        return event(text(json, "type"), text(json, "activityId"), text(json, "businessKey"));
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private PublishedProcessEvents() {
    }
}
