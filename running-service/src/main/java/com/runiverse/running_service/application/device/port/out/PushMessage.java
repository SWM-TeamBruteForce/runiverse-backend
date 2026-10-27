package com.runiverse.running_service.application.device.port.out;

import java.util.Map;

// data는 눌렀을 때 갈 화면을 앱이 정하는 값이다
public record PushMessage(String title, String body, Map<String, String> data) {

}
