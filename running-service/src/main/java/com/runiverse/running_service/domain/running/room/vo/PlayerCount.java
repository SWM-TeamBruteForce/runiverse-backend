package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.InvalidPlayerCountException;
import com.runiverse.running_service.domain.running.room.exception.RoomIsEmptyException;
import com.runiverse.running_service.domain.running.room.exception.RoomIsFullException;

public record PlayerCount(int current, int max) {

    // 현재 서비스 최대 정원
    public static final int MAX_PLAYER = 4;

    // 최대 인원수가 1보다 작을 수는 없고 현재 인원수가 0보다 작을 수는 없다 4보다
    public PlayerCount {
        if (max < 1 || current < 0 || current > max || max > MAX_PLAYER) {
            throw new InvalidPlayerCountException();
        }
    }

    public static PlayerCount solo() {
        return new PlayerCount(1, 1);
    }

    public static PlayerCount openWith(int max) {
        return new PlayerCount(1, max); // 방은 언제나 1인으로 태어남
    }

    public boolean isFull() {
        return current == max; // 현재가 전체이면 꽉참
    }

    public boolean canJoin() {
        return current < max; // 가입 가능
    }

    public PlayerCount join() {
        if (isFull()) {
            throw new RoomIsFullException();
        }
        return new PlayerCount(current + 1, max);
    }

    public PlayerCount leave() {
        if (current == 0) {
            throw new RoomIsEmptyException();
        }
        return new PlayerCount(current - 1, max);
    }

}
