// net/BackupRequest.java
package com.example.android.notepad.net;

import java.util.List;

public class BackupRequest {
    public String deviceId;
    public List<NoteDto> notes;

    public BackupRequest(String deviceId, List<NoteDto> notes) {
        this.deviceId = deviceId;
        this.notes = notes;
    }
}