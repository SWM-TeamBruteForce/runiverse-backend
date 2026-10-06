package com.runiverse.running_service.unit_test.running.domain.record;

import com.runiverse.running_service.domain.running.record.exception.GpsTrackKeyRequiredException;
import com.runiverse.running_service.domain.running.record.exception.GpsTrackKeyTooLongException;
import com.runiverse.running_service.domain.running.record.exception.InvalidRouteRangeException;
import com.runiverse.running_service.domain.running.record.exception.InvalidSplitNumberException;
import com.runiverse.running_service.domain.running.record.exception.RoutePolylineRequiredException;
import com.runiverse.running_service.domain.running.record.vo.GpsTrackKey;
import com.runiverse.running_service.domain.running.record.vo.RoutePolyline;
import com.runiverse.running_service.domain.running.record.vo.RouteRange;
import com.runiverse.running_service.domain.running.record.vo.SplitNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningRouteVoTest {

    @Nested
    @DisplayName("구간 번호 테스트")
    class SplitNumberTest {

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 42})
        @DisplayName("1부터 구간 번호를 만들 수 있다")
        void createSplitNumberSuccess(int value) {
            // when & then
            assertThat(new SplitNumber(value).value()).isEqualTo(value);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        @DisplayName("1보다 작으면 예외가 발생한다")
        void createSplitNumberBelowMinFails(int value) {
            // when & then -> 구간은 1부터 시작한다(0-based가 아니다)
            assertThatThrownBy(() -> new SplitNumber(value))
                    .isInstanceOf(InvalidSplitNumberException.class);
        }

        @Test
        @DisplayName("다음 구간 번호를 얻을 수 있다")
        void nextReturnsIncrementedNumber() {
            // given
            SplitNumber first = new SplitNumber(1);

            // when
            SplitNumber second = first.next();

            // then -> 기록이 구간이 빠짐없이 이어지는지 확인할 때 쓴다
            assertThat(second.value()).isEqualTo(2);
            assertThat(first.value()).isEqualTo(1);
        }

        @Test
        @DisplayName("첫 구간인지 판별할 수 있다")
        void isFirstDetectsFirstSplit() {
            // when & then
            assertThat(new SplitNumber(1).isFirst()).isTrue();
            assertThat(new SplitNumber(2).isFirst()).isFalse();
        }
    }

    @Nested
    @DisplayName("경로 폴리라인 테스트")
    class RoutePolylineTest {

        @Test
        @DisplayName("인코딩된 경로 문자열로 만들 수 있다")
        void createRoutePolylineSuccess() {
            // when
            RoutePolyline polyline = new RoutePolyline("_p~iF~ps|U_ulLnnqC");

            // then
            assertThat(polyline.value()).isEqualTo("_p~iF~ps|U_ulLnnqC");
        }

        @Test
        @DisplayName("앞뒤 공백은 제거된다")
        void routePolylineIsTrimmed() {
            // when & then
            assertThat(new RoutePolyline("  _p~iF  ").value()).isEqualTo("_p~iF");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("null이거나 공백뿐이면 예외가 발생한다")
        void createBlankRoutePolylineFails(String value) {
            // when & then -> 경로가 비면 지도에 그릴 것이 없다
            assertThatThrownBy(() -> new RoutePolyline(value))
                    .isInstanceOf(RoutePolylineRequiredException.class);
        }

        @Test
        @DisplayName("긴 경로도 길이 제한 없이 만들 수 있다")
        void longRoutePolylineIsAllowed() {
            // given -> text 컬럼이라 상한을 두지 않았다
            String longValue = "_p~iF".repeat(10_000);

            // when & then
            assertThat(new RoutePolyline(longValue).value()).hasSize(50_000);
        }
    }

    @Nested
    @DisplayName("GPS 트랙 키 테스트")
    class GpsTrackKeyTest {

        @Test
        @DisplayName("S3 key로 만들 수 있다")
        void createGpsTrackKeySuccess() {
            // when
            GpsTrackKey key = new GpsTrackKey("gps-tracks/2026/08/19/125-abc.json");

            // then
            assertThat(key.value()).isEqualTo("gps-tracks/2026/08/19/125-abc.json");
        }

        @Test
        @DisplayName("앞뒤 공백은 제거된다")
        void gpsTrackKeyIsTrimmed() {
            // when & then
            assertThat(new GpsTrackKey("  gps-tracks/a.json  ").value())
                    .isEqualTo("gps-tracks/a.json");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("null이거나 공백뿐이면 예외가 발생한다")
        void createBlankGpsTrackKeyFails(String value) {
            // when & then
            assertThatThrownBy(() -> new GpsTrackKey(value))
                    .isInstanceOf(GpsTrackKeyRequiredException.class);
        }

        @Test
        @DisplayName("255자는 허용되고 256자는 예외가 발생한다")
        void gpsTrackKeyBoundaryLength() {
            // when & then -> varchar(255)를 넘기면 저장 단계에서 잘린다
            assertThat(new GpsTrackKey("a".repeat(255)).value()).hasSize(255);
            assertThatThrownBy(() -> new GpsTrackKey("a".repeat(256)))
                    .isInstanceOf(GpsTrackKeyTooLongException.class);
        }

        @Test
        @DisplayName("길이는 공백을 제거한 뒤에 잰다")
        void gpsTrackKeyLengthIsMeasuredAfterTrim() {
            // given -> 앞뒤 공백까지 세면 정상 key가 막힌다
            String padded = "  " + "a".repeat(255) + "  ";

            // when & then
            assertThat(new GpsTrackKey(padded).value()).hasSize(255);
        }
    }

    @Nested
    @DisplayName("구간 경로 범위 테스트")
    class RouteRangeTest {

        @ParameterizedTest
        @CsvSource({"0,0", "0,120", "120,245", "480,481"})
        @DisplayName("시작이 끝보다 크지 않으면 만들 수 있다")
        void createRouteRangeSuccess(int startIndex, int endIndex) {
            // when
            RouteRange range = new RouteRange(startIndex, endIndex);

            // then
            assertThat(range.startIndex()).isEqualTo(startIndex);
            assertThat(range.endIndex()).isEqualTo(endIndex);
        }

        @Test
        @DisplayName("시작과 끝이 같은 한 점짜리 범위도 허용된다")
        void singlePointRangeIsAllowed() {
            // when & then -> 다운샘플 후 마지막 짧은 구간은 포인트 하나만 받을 수 있다
            assertThat(new RouteRange(300, 300).endIndex()).isEqualTo(300);
        }

        @ParameterizedTest
        @CsvSource({"-1,10", "-5,-1", "10,9", "245,120"})
        @DisplayName("시작이 음수이거나 끝보다 뒤면 예외가 발생한다")
        void createInvalidRouteRangeFails(int startIndex, int endIndex) {
            // when & then -> 뒤집힌 범위는 경로를 잘라낼 때 빈 구간이 된다
            assertThatThrownBy(() -> new RouteRange(startIndex, endIndex))
                    .isInstanceOf(InvalidRouteRangeException.class);
        }

        @Test
        @DisplayName("다음 구간이 이 구간의 끝점에서 시작하면 이어진다")
        void connectsToAdjacentRange() {
            // given
            RouteRange first = new RouteRange(0, 120);
            RouteRange second = new RouteRange(120, 245);

            // when & then -> 경계가 겹쳐야 구간을 이어 그릴 때 선이 끊기지 않는다
            assertThat(first.connectsTo(second)).isTrue();
        }

        @Test
        @DisplayName("끝점이 겹치지 않으면 이어지지 않는다")
        void doesNotConnectWhenIndexesDoNotOverlap() {
            // given
            RouteRange first = new RouteRange(0, 120);

            // when & then -> 121부터 시작하면 120~121 사이 한 칸이 비어 선이 끊긴다
            assertThat(first.connectsTo(new RouteRange(121, 245))).isFalse();
            assertThat(first.connectsTo(new RouteRange(119, 245))).isFalse();
        }

        @Test
        @DisplayName("이어짐 판정은 방향이 있다")
        void connectsToIsDirectional() {
            // given
            RouteRange first = new RouteRange(0, 120);
            RouteRange second = new RouteRange(120, 245);

            // when & then -> 앞 구간에서 뒤 구간으로만 성립한다
            assertThat(first.connectsTo(second)).isTrue();
            assertThat(second.connectsTo(first)).isFalse();
        }
    }
}
