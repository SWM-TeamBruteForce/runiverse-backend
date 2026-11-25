package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardViewUrlPort;

import java.util.ArrayList;
import java.util.List;

// 사진·음악 보기 URL — 버킷을 URL에 드러내 어느 버킷으로 서명했는지 검증할 수 있게 한다
public class FakeRecordCardViewUrlGenerator implements GenerateRecordCardViewUrlPort {

    private final List<String> issued = new ArrayList<>();

    @Override
    public String imageUrl(String imageKey) {
        issued.add(imageKey);
        return imageUrlOf(imageKey);
    }

    @Override
    public String musicUrl(String musicKey) {
        issued.add(musicKey);
        return musicUrlOf(musicKey);
    }

    // 아래는 검증 전용
    public String imageUrlOf(String imageKey) {
        return "https://record-cards.fake/" + imageKey + "?signed";
    }

    public String musicUrlOf(String musicKey) {
        return "https://musics.fake/" + musicKey + "?signed";
    }

    public boolean isEmpty() {
        return issued.isEmpty();
    }
}
