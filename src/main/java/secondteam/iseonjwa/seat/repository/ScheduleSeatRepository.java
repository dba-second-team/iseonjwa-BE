package secondteam.iseonjwa.seat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import secondteam.iseonjwa.seat.entity.SeatSection;
import secondteam.iseonjwa.seat.entity.SeatStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ScheduleSeatRepository {
    private static final int ROWS_PER_SECTION = 15;
    private static final int SEATS_PER_ROW = 10;
    private static final int SEATS_PER_SCHEDULE = SeatSection.values().length * ROWS_PER_SECTION * SEATS_PER_ROW;
    private static final String INSERT_SEATS = "INSERT INTO seat (schedule_id, section, row_num, seat_num, status) VALUES "
            + String.join(", ", Collections.nCopies(SEATS_PER_SCHEDULE, "(?, ?, ?, ?, ?)"));

    private final JdbcTemplate jdbcTemplate;

    public void createForSchedule(long scheduleId) {
        List<Object> values = new ArrayList<>(SEATS_PER_SCHEDULE * 5);
        for (SeatSection section : SeatSection.values()) {
            for (int row = 1; row <= ROWS_PER_SECTION; row++) {
                String rowNumber = Integer.toString(row);
                for (int seatNumber = 1; seatNumber <= SEATS_PER_ROW; seatNumber++) {
                    values.add(scheduleId);
                    values.add(section.name());
                    values.add(rowNumber);
                    values.add(seatNumber);
                    values.add(SeatStatus.AVAILABLE.name());
                }
            }
        }

        jdbcTemplate.update(INSERT_SEATS, values.toArray());
    }
}
