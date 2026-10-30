package com.scentstation.dto.request;

import jakarta.validation.constraints.Size;

public class AlertActionRequest {
    @Size(max = 100)
    private String actorId;

    @Size(max = 100)
    private String assigneeId;

    @Size(max = 1000)
    private String notes;

    public AlertActionRequest() {
    }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
