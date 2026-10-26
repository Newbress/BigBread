package com.bigbread.api.auth.local;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용: 보낼 메일을 보내지 않고 모아 둔다. */
class CapturingAuthMailer implements AuthMailer {

    record Verification(String email, String nickname, String link, Duration validFor) {
        String token() {
            return link.substring(link.indexOf("token=") + "token=".length());
        }
    }

    final List<Verification> verifications = new CopyOnWriteArrayList<>();
    final List<String> alreadyRegistered = new CopyOnWriteArrayList<>();

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void sendSignupVerification(String email, String nickname, String link, Duration validFor) {
        verifications.add(new Verification(email, nickname, link, validFor));
    }

    @Override
    public void sendAlreadyRegistered(String email) {
        alreadyRegistered.add(email);
    }

    Verification last() {
        List<Verification> copy = new ArrayList<>(verifications);
        return copy.get(copy.size() - 1);
    }

    void clear() {
        verifications.clear();
        alreadyRegistered.clear();
    }
}
