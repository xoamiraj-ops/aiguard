package com.parsban.callai;

public final class CallSession {
    public long id;
    public String phone = "";
    public String direction = "Incoming";
    public long createdAt;
    public long connectedAt;
    public long endedAt;
    public String status = "Ringing";
    public String contactName = "";
    public String recordingUri = "";
    public String summary = "";
    public String highlights = "[]";
    public String actionItems = "[]";
    public String transcript = "";
    public String aiModel = "";
    public String error = "";

    public long durationMs() {
        if (connectedAt <= 0 || endedAt <= connectedAt) return 0L;
        return endedAt - connectedAt;
    }
}
