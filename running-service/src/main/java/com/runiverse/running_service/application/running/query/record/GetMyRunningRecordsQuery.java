package com.runiverse.running_service.application.running.query.record;

import java.time.LocalDate;
import java.util.UUID;

// from·to는 양 끝을 포함하는 달력 날짜다
public record GetMyRunningRecordsQuery(UUID userId, LocalDate from, LocalDate to) {

}
