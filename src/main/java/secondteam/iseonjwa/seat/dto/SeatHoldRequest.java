package secondteam.iseonjwa.seat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public record SeatHoldRequest(
        @JsonDeserialize(contentUsing = SeatHoldRequest.SeatIdDeserializer.class)
        @NotEmpty List<@NotNull @Positive Long> seatIds) {

    static class SeatIdDeserializer extends ValueDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Long.class, "좌석 ID는 정수여야 합니다.");
            }
            return parser.getLongValue();
        }
    }
}
