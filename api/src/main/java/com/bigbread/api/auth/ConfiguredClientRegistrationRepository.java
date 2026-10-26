package com.bigbread.api.auth;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/** 설정된 제공자만 담는다. 스프링 기본 구현은 비어 있으면 생성에 실패해서, 아무 제공자도 없는 환경을 위해 따로 둔다. */
class ConfiguredClientRegistrationRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();

    ConfiguredClientRegistrationRepository(List<ClientRegistration> list) {
        list.forEach(registration -> registrations.put(registration.getRegistrationId(), registration));
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }

    boolean isEmpty() {
        return registrations.isEmpty();
    }
}
