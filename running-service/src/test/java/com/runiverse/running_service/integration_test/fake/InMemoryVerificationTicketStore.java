package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.ConsumeVerificationTicketPort;
import com.runiverse.running_service.application.auth.port.out.SaveVerificationTicketPort;

import java.util.HashMap;
import java.util.Map;

public class InMemoryVerificationTicketStore
        implements SaveVerificationTicketPort, ConsumeVerificationTicketPort {

    private final Map<String, String> tickets = new HashMap<>();

    @Override
    public void save(String hashedTicket, String email) {
        tickets.put(hashedTicket, email);
    }

    // Redis GETDEL과 같다. 조회와 동시에 지워져 티켓은 한 번만 쓰인다
    @Override
    public String consume(String hashedTicket) {
        return tickets.remove(hashedTicket);
    }

    // 검증 전용
    public int size() {
        return tickets.size();
    }
}
