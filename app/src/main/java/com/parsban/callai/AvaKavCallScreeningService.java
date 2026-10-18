package com.parsban.callai;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

public class AvaKavCallScreeningService extends CallScreeningService {
    @Override
    public void onScreenCall(Call.Details details) {
        boolean incoming = details.getCallDirection() == Call.Details.DIRECTION_INCOMING;

        if (incoming) {
            respondToCall(details, new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build());
        }

        Uri handle = details.getHandle();
        if (handle == null || !"tel".equals(handle.getScheme())) return;

        String phone = handle.getSchemeSpecificPart();
        if (phone == null) return;
        phone = phone.trim();
        if (phone.isEmpty() || phone.contains("*") || phone.contains("#")) return;
        if (PhoneUtil.digits(phone).length() < 5) return;

        CallSession s = new CallSession();
        s.phone = phone;
        s.direction = incoming ? "Incoming" : "Outgoing";
        s.createdAt = System.currentTimeMillis();
        s.status = incoming ? "Ringing" : "Dialing";
        s.contactName = ContactResolver.nameFor(this, phone);

        AppDb db = new AppDb(getApplicationContext());
        s.id = db.insert(s);
        AppPrefs.setActiveSession(this, s.id);

        NotificationHelper.showIncoming(this, s.id, phone, s.contactName);
    }
}
