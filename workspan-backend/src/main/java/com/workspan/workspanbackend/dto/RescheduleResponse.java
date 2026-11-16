package com.workspan.workspanbackend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RescheduleResponse(int movedTasks, List<Move> moves) {
    public record Move(UUID taskId, String name, LocalDate previousStart, LocalDate previousEnd, LocalDate newStart, LocalDate newEnd) {}
}
