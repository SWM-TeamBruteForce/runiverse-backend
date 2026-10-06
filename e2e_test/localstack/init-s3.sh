#!/usr/bin/env sh
# LocalStack이 준비된 직후 한 번 실행된다(ready.d).
# 러닝 종료가 GPS 원본 트랙을 PutObject 하고, 프로필 사진 반영이 HeadObject를 친다 —
# 버킷이 없으면 둘 다 실패하므로 여기서 미리 만든다.
set -eu

create_bucket() {
  # 서울 리전은 LocationConstraint 없이는 버킷을 만들 수 없다
  awslocal s3api create-bucket \
    --bucket "$1" \
    --create-bucket-configuration LocationConstraint="${AWS_DEFAULT_REGION}" >/dev/null
  echo "버킷 생성 완료 — $1"
}

create_bucket "${E2E_GPS_TRACK_BUCKET}"
create_bucket "${E2E_USER_ASSET_BUCKET}"
