#!/usr/bin/env python3
"""Runiverse 백엔드 구조 규칙 검사.

문서를 읽어야 판단되는 것(스펙 ↔ 구현 정합성)은 다루지 않는다.
소스만 보고 기계적으로 판정 가능한 것만 검사한다.

사용법:
    python3 .claude/skills/spec-check/scripts/check_conventions.py [저장소_루트] [소스_범위]

출력은 확정 위반·조사 필요·휴리스틱 의심으로 나눈 사람이 읽는 리포트다.
위반이 있어도 종료 코드는 0이다(게이트가 아니라 조사 도구이므로 호출한 쪽이 결과를 해석한다).
사용법·경로·범위 오류는 검사 미실행을 뜻하므로 종료 코드 2를 반환한다.
"""

import re
import sys
from pathlib import Path

SRC = "running-service/src/main/java/com/runiverse/running_service"
BASE_PKG = "com.runiverse.running_service"

# 레이어별 금지 import (접두사 매칭). lombok은 전 레이어 허용.
FORBIDDEN = {
    "domain": [
        ("org.springframework", "스프링 의존"),
        ("jakarta.persistence", "JPA 의존"),
        ("jakarta.validation", "Bean Validation 의존"),
        ("jakarta.transaction", "트랜잭션 API 의존"),
        ("org.hibernate", "하이버네이트 의존"),
        ("com.fasterxml", "잭슨 의존"),
        (f"{BASE_PKG}.application", "바깥 레이어 참조"),
        (f"{BASE_PKG}.infrastructure", "바깥 레이어 참조"),
        (f"{BASE_PKG}.presentation", "바깥 레이어 참조"),
    ],
    "application": [
        ("jakarta.persistence", "JPA 의존 — 영속성은 어댑터 책임"),
        ("org.hibernate", "하이버네이트 의존"),
        (f"{BASE_PKG}.infrastructure", "바깥 레이어 참조"),
        (f"{BASE_PKG}.presentation", "바깥 레이어 참조"),
    ],
    "infrastructure": [
        (f"{BASE_PKG}.presentation", "presentation 참조 — 순환 의존"),
    ],
    "presentation": [
        (f"{BASE_PKG}.infrastructure", "infrastructure 참조 — port/in만 통해야 함"),
    ],
}

# 단위 접미사가 필요한 물리량 키워드 → 허용 접미사
UNIT_KEYWORDS = {
    "distance": ("Meters",),
    "pace": ("SecondsPerKm",),
    "weight": ("Kg",),
    "height": ("Cm",),
    "altitude": ("Meters",),
    "elevation": ("Meters",),
    "speed": ("MetersPerSecond",),
    "cadence": ("Spm",),
    "calorie": ("Kcal",),
    "duration": ("Seconds",),
    "heading": ("Degrees",),
    "accuracy": ("Meters",),
}

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)", re.M)
NORMAL_IMPORT_RE = re.compile(r"^\s*import\s+(?!static\s+)([\w.*]+)\s*;", re.M)
STATIC_IMPORT_RE = re.compile(r"^\s*import\s+static\s+([\w.*]+)\s*;", re.M)
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)
# 인터페이스 본문의 메서드 선언 (기본 구현 default 메서드 제외)
IFACE_METHOD_RE = re.compile(r"^\s*(?!default\b|static\b)[\w<>\[\],.?\s]+?\s+(\w+)\s*\(", re.M)

APP_ERROR_CODE = f"{BASE_PKG}.application.common.exception.ErrorCode"
EXTERNAL_CLIENT_IMPORT_PREFIXES = (
    "org.springframework.web.client",
    "org.springframework.web.reactive.function.client",
    "org.springframework.cloud.openfeign",
    "java.net.http",
    "feign",
    "okhttp3",
    "retrofit2",
)


def strip_annotations(text: str) -> str:
    """`@Foo(...)` 를 제거한다. 어노테이션 인자 안의 쉼표·괄호·문자열 때문에
    정규식으로는 안정적으로 못 지운다 — 괄호 깊이를 세며 훑는다."""
    out, i, n = [], 0, len(text)
    while i < n:
        if text[i] != "@":
            out.append(text[i])
            i += 1
            continue
        i += 1
        while i < n and (text[i].isalnum() or text[i] in "_."):
            i += 1
        j = i
        while j < n and text[j].isspace():
            j += 1
        if j < n and text[j] == "(":
            i = skip_balanced(text, j)
    return "".join(out)


def skip_balanced(text: str, start: int) -> int:
    """`text[start]`의 여는 괄호에 대응하는 닫는 괄호 다음 위치를 반환한다."""
    depth, i, n = 0, start, len(text)
    while i < n:
        c = text[i]
        if c in "\"'":
            quote, i = c, i + 1
            while i < n and text[i] != quote:
                i += 2 if text[i] == "\\" else 1
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


def split_params(params: str):
    """최상위 쉼표로만 나눈다 (제네릭·괄호 안 쉼표는 무시)."""
    parts, depth, buf = [], 0, []
    for c in params:
        if c in "<(":
            depth += 1
        elif c in ">)":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(c)
    if "".join(buf).strip():
        parts.append("".join(buf))
    return parts


def record_fields(text: str):
    """record 헤더의 필드명 목록."""
    m = re.search(r"\brecord\s+\w+\s*(?=\()", text)
    if not m:
        return []
    open_paren = text.index("(", m.end() - 1)
    params = text[open_paren + 1 : skip_balanced(text, open_paren) - 1]
    fields = []
    for part in split_params(strip_annotations(params)):
        tokens = part.split()
        if len(tokens) >= 2:
            fields.append(tokens[-1])
    return fields


def java_files(root: Path, scope: Path = None):
    target = scope or root / SRC
    if target.is_file():
        return [target] if target.suffix == ".java" else []
    return sorted(target.rglob("*.java"))


def resolve_scope(root: Path, value: str):
    """선택 범위를 소스 루트 안의 파일이나 디렉터리로 해석한다."""
    if not value:
        return None

    source_root = (root / SRC).resolve()
    raw = Path(value)
    candidates = [raw] if raw.is_absolute() else [root / raw, source_root / raw]
    scope = next((candidate.resolve() for candidate in candidates if candidate.exists()), None)
    if scope is None:
        requested = display_requested_path(value, root)
        raise ValueError(f"검사 범위를 찾을 수 없습니다: {requested}")
    try:
        scope.relative_to(source_root)
    except ValueError as e:
        raise ValueError(
            f"검사 범위는 소스 디렉터리 안이어야 합니다: {display_path(scope, root)}"
        ) from e
    if not scope.is_dir() and scope.suffix != ".java":
        raise ValueError(
            f"검사 범위는 디렉터리나 Java 파일이어야 합니다: {display_path(scope, root)}"
        )
    return scope


def layer_of(path: Path, root: Path) -> str:
    rel = path.relative_to(root / SRC).as_posix()
    return rel.split("/")[0] if "/" in rel else ""


def rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def display_path(path: Path, root: Path) -> str:
    """저장소 안은 상대 경로로, 밖은 로컬 절대 경로 없이 표시한다."""
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return f"<저장소 밖 경로>/{path.name}" if path.name else "<저장소 밖 경로>"


def display_requested_path(value: str, root: Path) -> str:
    """존재하지 않는 범위도 절대 경로 노출 없이 표시한다."""
    requested = Path(value)
    if requested.is_absolute():
        return display_path(requested, root)
    return requested.as_posix()


def without_comments(text: str) -> str:
    """주석 속 코드를 실제 선언·참조로 오인하지 않게 제거한다."""
    return re.sub(r"//.*?$|/\*.*?\*/", "", text, flags=re.S | re.M)


def without_comments_and_imports(text: str) -> str:
    """직접 참조 탐색 중 주석·import 선언을 사용 지점으로 오인하지 않게 제거한다."""
    return re.sub(
        r"^\s*import\s+[^;]+;\s*$", "", without_comments(text), flags=re.M
    )


def check_layers(root: Path, scope: Path = None):
    """의존 방향 위반. 안쪽 레이어가 바깥을 참조하면 클린 아키텍처가 깨진다."""
    hits = []
    for f in java_files(root, scope):
        layer = layer_of(f, root)
        rules = FORBIDDEN.get(layer)
        if not rules:
            continue
        text = without_comments(f.read_text(encoding="utf-8"))
        for imported in IMPORT_RE.findall(text):
            for prefix, why in rules:
                if imported.startswith(prefix):
                    hits.append((rel(f, root), imported, why))
                    break
    return hits


def parse_enum_constants(path: Path):
    """enum 상수명만 뽑는다 (CONSTANT("code", "msg") 형태)."""
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8")
    body = text.split("{", 1)[-1]
    names = []
    for line in body.splitlines():
        line = line.strip()
        if line.startswith("//") or line.startswith("*") or line.startswith("/*"):
            continue
        m = re.match(r"([A-Z][A-Z0-9_]*)\s*\(", line)
        if m:
            names.append(m.group(1))
    return names


def source_fqcn(path: Path, source_root: Path) -> str:
    """Java 소스 경로를 패키지 포함 클래스명으로 바꾼다."""
    relative = path.relative_to(source_root).with_suffix("")
    return f"{BASE_PKG}." + ".".join(relative.parts)


def application_exception_index(root: Path):
    """직접 참조를 소스로 연결하기 위한 application 예외 인덱스."""
    source_root = root / SRC
    app_root = source_root / "application"
    return {
        source_fqcn(path, source_root): path
        for path in app_root.rglob("*Exception.java")
        if path.name != "BusinessException.java"
    }


def referenced_application_error_codes(text: str, known_codes):
    """domain의 동명 ErrorCode를 제외하고 application 코드 참조만 반환한다."""
    known = set(known_codes)
    source = without_comments(text)
    imports = set(NORMAL_IMPORT_RE.findall(source))
    static_imports = set(STATIC_IMPORT_RE.findall(source))
    body = without_comments_and_imports(text)
    found = set()

    package_match = PACKAGE_RE.search(source)
    package_name = package_match.group(1) if package_match else ""
    explicit_error_imports = {
        imported for imported in imports if imported.endswith(".ErrorCode")
    }
    imports_application_error = APP_ERROR_CODE in explicit_error_imports or (
        not explicit_error_imports
        and (
            f"{BASE_PKG}.application.common.exception.*" in imports
            or package_name == f"{BASE_PKG}.application.common.exception"
        )
    )
    if imports_application_error:
        found.update(re.findall(r"\bErrorCode\.([A-Z][A-Z0-9_]*)\b", body))

    found.update(
        re.findall(rf"\b{re.escape(APP_ERROR_CODE)}\.([A-Z][A-Z0-9_]*)\b", body)
    )

    static_prefix = f"{APP_ERROR_CODE}."
    for imported in static_imports:
        if not imported.startswith(static_prefix):
            continue
        member = imported[len(static_prefix):]
        if member == "*":
            found.update(code for code in known if re.search(rf"\b{re.escape(code)}\b", body))
        elif member in known and re.search(rf"\b{re.escape(member)}\b", body):
            found.add(member)

    return found & known


def referenced_application_exceptions(text: str, current: Path, index):
    """생성·생성자 참조·상속으로 직접 연결된 application 예외을 찾는다."""
    source = without_comments(text)
    imports = set(NORMAL_IMPORT_RE.findall(source))
    explicit = {
        imported.rsplit(".", 1)[-1]: imported
        for imported in imports
        if not imported.endswith(".*")
    }
    wildcard_packages = [imported[:-2] for imported in imports if imported.endswith(".*")]
    package_match = PACKAGE_RE.search(source)
    package_name = package_match.group(1) if package_match else ""
    body = without_comments_and_imports(text)

    simple_names = set(re.findall(r"\bnew\s+([A-Z]\w*Exception)\b", body))
    simple_names.update(re.findall(r"\b([A-Z]\w*Exception)\s*::\s*new\b", body))
    simple_names.update(re.findall(r"\bextends\s+([A-Z]\w*Exception)\b", body))
    simple_names.update(re.findall(r"\b([A-Z]\w*Exception)\s*\.", body))

    resolved, unresolved = set(), set()
    for name in simple_names:
        if name == "BusinessException":
            continue
        fqcn = explicit.get(name)
        if fqcn is None and package_name:
            same_package = f"{package_name}.{name}"
            if same_package in index:
                fqcn = same_package
        if fqcn is None:
            fqcn = next(
                (f"{package}.{name}" for package in wildcard_packages
                 if f"{package}.{name}" in index),
                None,
            )
        if fqcn is None or not fqcn.startswith(f"{BASE_PKG}.application."):
            continue
        target = index.get(fqcn)
        if target is None:
            unresolved.add(fqcn)
        elif target.resolve() != current.resolve():
            resolved.add(target)

    fqcn_pattern = rf"\b({re.escape(BASE_PKG)}\.application(?:\.\w+)+\.[A-Z]\w*Exception)\b"
    for fqcn in re.findall(fqcn_pattern, body):
        target = index.get(fqcn)
        if target is None:
            unresolved.add(fqcn)
        elif target.resolve() != current.resolve():
            resolved.add(target)

    return resolved, unresolved


def trace_scoped_application_codes(root: Path, scope: Path, known_codes):
    """범위 파일의 직접 코드 참조와 직접 참조 예외 체인을 추적한다."""
    index = application_exception_index(root)
    scoped_files = java_files(root, scope)
    pending = list(scoped_files)
    visited, followed, unresolved, codes = set(), set(), set(), set()

    while pending:
        path = pending.pop()
        resolved = path.resolve()
        if resolved in visited:
            continue
        visited.add(resolved)
        text = path.read_text(encoding="utf-8")
        codes.update(referenced_application_error_codes(text, known_codes))
        references, missing = referenced_application_exceptions(text, path, index)
        unresolved.update(missing)
        for target in references:
            if target.resolve() not in visited:
                followed.add(target)
                pending.append(target)

    return codes, followed, unresolved


def check_error_exposure_details(root: Path, scope: Path = None):
    """ErrorCode ↔ toStatus ↔ 공개 경로 대조와 범위 추적 메타데이터."""
    base = root / SRC
    all_codes = parse_enum_constants(base / "application/common/exception/ErrorCode.java")
    selected_codes = set(all_codes)
    followed, unresolved = set(), set()
    complete_claim = scope is None

    if scope is not None:
        scoped_files = java_files(root, scope)
        global_files = {
            (base / "application/common/exception/ErrorCode.java").resolve(),
            (base / "presentation/common/exception/GlobalExceptionHandler.java").resolve(),
            (base / "presentation/common/exception/ErrorExposurePolicy.java").resolve(),
        }
        complete_claim = any(path.resolve() in global_files for path in scoped_files)
        if not complete_claim:
            selected_codes, followed, unresolved = trace_scoped_application_codes(
                root, scope, all_codes
            )

    handler = base / "presentation/common/exception/GlobalExceptionHandler.java"
    policy = base / "presentation/common/exception/ErrorExposurePolicy.java"
    handler_text = handler.read_text(encoding="utf-8") if handler.exists() else ""
    policy_text = policy.read_text(encoding="utf-8") if policy.exists() else ""
    handler_code = without_comments_and_imports(handler_text)
    policy_code = without_comments_and_imports(policy_text)

    # toStatus 스위치 본문만 잘라낸다
    match = re.search(r"toStatus\s*\([^)]*\)\s*\{(.*?)\n\s*\}", handler_code, re.S)
    switch_body = match.group(1) if match else ""
    exposed = set(re.findall(r"ErrorCode\.(\w+)\.getCode\(\)", policy_code))
    bad_request_auto_exposed = re.search(
        r"\bstatus\s*==\s*HttpStatus\.BAD_REQUEST\b|"
        r"\bHttpStatus\.BAD_REQUEST\s*==\s*status\b",
        policy_code,
    ) is not None
    status_by_code = {}
    for case_match in re.finditer(
        r"\bcase\s+(.*?)\s*->\s*HttpStatus\.([A-Z_]+)", switch_body, re.S
    ):
        case_body, status = case_match.groups()
        for code in re.findall(r"\b[A-Z][A-Z0-9_]*\b", case_body):
            status_by_code[code] = status

    rows = []
    for code in all_codes:
        if code not in selected_codes:
            continue
        in_switch = re.search(rf"\b{re.escape(code)}\b", switch_body) is not None
        has_public_path = (
            bad_request_auto_exposed and status_by_code.get(code) == "BAD_REQUEST"
        ) or code in exposed
        rows.append((code, in_switch, has_public_path))
    metadata = {
        "complete_claim": complete_claim,
        "followed": sorted(rel(path, root) for path in followed),
        "unresolved": sorted(unresolved),
    }
    return rows, metadata


def check_error_exposure(root: Path, scope: Path = None):
    """기존 호출 호환을 위해 대조 행만 반환한다."""
    rows, _ = check_error_exposure_details(root, scope)
    return rows


def check_dead_exceptions(root: Path, scope: Path = None):
    """어디서도 생성되지 않는 예외 클래스. 리팩토링 잔재일 가능성이 높다."""
    files = java_files(root)
    candidates = java_files(root, scope)
    corpus = {f: f.read_text(encoding="utf-8") for f in files}
    dead = []
    for f in candidates:
        if not f.name.endswith("Exception.java"):
            continue
        name = f.stem
        # 추상 기반 클래스는 직접 생성되지 않는 게 정상이다
        if re.search(rf"\babstract\s+class\s+{re.escape(name)}\b", corpus[f]):
            continue
        used = any(
            other is not f and (f"new {name}(" in text or f"{name}::new" in text)
            for other, text in corpus.items()
        )
        if not used:
            dead.append(rel(f, root))
    return dead


def check_ports(root: Path, scope: Path = None):
    """port/out 규칙: `*Port`는 인터페이스여야 한다.

    port/out에는 인터페이스뿐 아니라 전용 입출력 모델(record)도 둘 수 있으므로
    (architecture.md '아웃바운드 인터페이스 · 전용 입출력 모델'), 이름이 `*Port`인
    것만 인터페이스 여부를 따진다.

    메서드 수는 확정 위반이 아니다 — architecture.md는 '작고 응집된 인터페이스'를
    요구할 뿐이라, 여러 메서드가 하나의 역할로 묶이면 정상이다. 사람이 판단하도록
    휴리스틱으로만 보고한다.
    """
    multi_method, non_iface = [], []
    for f in java_files(root, scope):
        parts = f.relative_to(root / SRC).as_posix().split("/")
        if "port" not in parts:
            continue
        text = f.read_text(encoding="utf-8")
        kind = parts[parts.index("port") + 1] if parts.index("port") + 1 < len(parts) else ""
        if kind != "out":
            continue

        if not re.search(r"\binterface\s+" + re.escape(f.stem) + r"\b", text):
            if f.stem.endswith("Port"):
                non_iface.append((rel(f, root), "이름이 *Port인데 인터페이스가 아님"))
            continue

        body = text.split("{", 1)[-1]
        # 주석 제거 후 메서드 선언 수를 센다
        body = re.sub(r"//.*?$|/\*.*?\*/", "", body, flags=re.S | re.M)
        methods = IFACE_METHOD_RE.findall(body)
        if len(methods) > 1:
            multi_method.append((rel(f, root), methods))
    return multi_method, non_iface


def check_application_structure(root: Path, scope: Path = None):
    """Command/Handler 세트와 트랜잭션 경계를 검사한다.

    `Result`는 반환값이 있을 때만 두므로 필수가 아니다. 트랜잭션도 경계가 Handler가
    아닐 수 있고(내부 컴포넌트) Redis 전용 유스케이스는 아예 불필요하므로,
    기능 패키지 전체에 `@Transactional`이 하나도 없을 때만 휴리스틱으로 보고한다.
    """
    source_root = root / SRC
    feature_dirs = set()
    for f in java_files(root, scope):
        parts = f.relative_to(source_root).parts
        if len(parts) >= 4 and parts[0] == "application" and parts[2] == "command":
            feature_dirs.add(source_root.joinpath(*parts[:4]))

    missing_sets, handler_issues, no_tx = [], [], []
    for feature_dir in sorted(feature_dirs):
        files = sorted(feature_dir.glob("*.java"))
        stems = [f.stem for f in files]
        missing = [suffix for suffix in ("Command", "Handler")
                   if not any(stem.endswith(suffix) for stem in stems)]
        if missing:
            missing_sets.append((rel(feature_dir, root), missing))

        texts = {
            f: re.sub(r"//.*?$|/\*.*?\*/", "", f.read_text(encoding="utf-8"), flags=re.S | re.M)
            for f in files
        }
        if files and not any("@Transactional" in t for t in texts.values()):
            no_tx.append(rel(feature_dir, root))

        for handler in (f for f in files if f.stem.endswith("Handler")):
            declaration = re.search(
                r"\bclass\s+\w+Handler\b[^\{]*\bimplements\b[^\{]*\b\w+Usecase\b",
                texts[handler],
                re.S,
            )
            if not declaration:
                handler_issues.append((rel(handler, root), ["*Usecase 구현 누락"]))
    return missing_sets, handler_issues, no_tx


OUTBOUND_SUFFIXES = ("Adapter", "Client", "Router", "Registry")


def check_outbound_names(root: Path, scope: Path = None):
    """port/out 구현체 네이밍: 포트 구현은 *Adapter, 외부 API는 *Client, 선택은 *Router, 상태 보관은 *Registry."""
    hits = []
    for f in java_files(root, scope):
        if layer_of(f, root) != "infrastructure":
            continue
        text = re.sub(r"//.*?$|/\*.*?\*/", "", f.read_text(encoding="utf-8"), flags=re.S | re.M)
        declaration = re.search(r"\bclass\s+(\w+)\b[^\{]*\bimplements\b([^\{]+)\{", text, re.S)
        if not declaration:
            continue
        class_name, interfaces = declaration.groups()
        if re.search(r"\b\w+Port\b", interfaces) and not class_name.endswith(OUTBOUND_SUFFIXES):
            hits.append(rel(f, root))
    return hits


def check_outbound_role_ambiguities(root: Path, scope: Path = None):
    """*Client·*Router가 이름에 맞는 역할인지 소스로 확정할 수 없으면 조사로 남긴다."""
    hits = []
    for f in java_files(root, scope):
        if layer_of(f, root) != "infrastructure":
            continue
        raw_text = f.read_text(encoding="utf-8")
        text = without_comments(raw_text)
        declaration = re.search(r"\bclass\s+(\w+)\b([^\{]*)\{", text, re.S)
        if not declaration:
            continue
        class_name, class_header = declaration.groups()
        implements = re.search(r"\bimplements\b(.+)$", class_header, re.S)
        interfaces = implements.group(1) if implements else ""
        implements_port = re.search(r"\b\w+Port\b", interfaces) is not None

        if class_name.endswith("Client"):
            imports = IMPORT_RE.findall(text)
            external_client = any(
                imported.startswith(prefix)
                for imported in imports
                for prefix in EXTERNAL_CLIENT_IMPORT_PREFIXES
            ) or re.search(r"\b(?:RestClient|WebClient|HttpClient|OkHttpClient)\b", text)
            if not external_client:
                hits.append((rel(f, root), "*Client이지만 외부 API 호출 근거를 기계적으로 확인할 수 없음"))

        if class_name.endswith("Router"):
            if not implements_port:
                hits.append((rel(f, root), "*Router이지만 application 포트를 구현하지 않음"))
            client_collection = re.search(
                r"\b(?:Map|List|Set|Collection|Iterable)\s*<"
                r"[^;{}]*\b\w+Client\s*>\s+(\w+)\b",
                text,
                re.S,
            )
            if not client_collection:
                hits.append((rel(f, root), "*Router이지만 클라이언트 선택 근거를 기계적으로 확인할 수 없음"))
            else:
                collection_name = client_collection.group(1)
                selects_client = re.search(
                    rf"\b{re.escape(collection_name)}\s*\.\s*(?:get|stream)\s*\(",
                    text,
                ) is not None
                if not selects_client:
                    hits.append((
                        rel(f, root),
                        "*Router의 클라이언트 컬렉션은 있으나 선택 동작을 확인할 수 없음",
                    ))
    return hits


def check_unit_suffix(root: Path, scope: Path = None):
    """요청·응답 DTO 필드의 단위 접미사 (api-convention.md '예외 0' 규칙).

    휴리스틱이다 — 이름에 물리량 키워드가 있는데 접미사가 없는 필드를 모은다.
    """
    hits = []
    for f in java_files(root, scope):
        p = f.relative_to(root / SRC).as_posix()
        if not (p.startswith("presentation/") and ("/request/" in p or "/response/" in p)):
            continue
        for field in record_fields(f.read_text(encoding="utf-8")):
            low = field.lower()
            for kw, suffixes in UNIT_KEYWORDS.items():
                if kw in low and not field.endswith(suffixes):
                    hits.append((rel(f, root), field, suffixes[0]))
                    break
    return hits


def main():
    if len(sys.argv) > 3:
        print("인자가 너무 많습니다.", file=sys.stderr)
        print(
            "사용법: python3 .claude/skills/spec-check/scripts/check_conventions.py "
            "[저장소_루트] [소스_범위]",
            file=sys.stderr,
        )
        return 2

    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    if not (root / SRC).is_dir():
        print(f"소스 디렉터리를 찾을 수 없습니다: {SRC}", file=sys.stderr)
        return 2
    try:
        scope = resolve_scope(root, sys.argv[2] if len(sys.argv) > 2 else None)
    except ValueError as e:
        print(e, file=sys.stderr)
        return 2
    if scope is not None and not java_files(root, scope):
        print(f"검사 범위에 Java 파일이 없습니다: {display_path(scope, root)}", file=sys.stderr)
        return 2

    confirmed = 0
    investigate = 0

    print("## 1. 확정 위반")
    print("\n### 레이어 의존 방향")
    layers = check_layers(root, scope)
    if layers:
        confirmed += len(layers)
        for path, imported, why in layers:
            print(f"  - {path}\n      {imported}  ({why})")
    else:
        print("  위반 없음")

    print("\n### 포트 구조")
    multi_method, non_iface = check_ports(root, scope)
    if non_iface:
        confirmed += len(non_iface)
        for path, why in non_iface:
            print(f"  - {path}: {why}")
    else:
        print("  위반 없음")

    print("\n### 애플리케이션 구성")
    missing_sets, handler_issues, no_tx = check_application_structure(root, scope)
    if missing_sets or handler_issues:
        confirmed += len(missing_sets) + len(handler_issues)
        for path, missing in missing_sets:
            print(f"  - {path}: {', '.join(missing)} 누락")
        for path, issues in handler_issues:
            print(f"  - {path}: {', '.join(issues)}")
    else:
        print("  위반 없음")

    print("\n### 아웃바운드 구현체 네이밍")
    outbound_names = check_outbound_names(root, scope)
    if outbound_names:
        confirmed += len(outbound_names)
        for path in outbound_names:
            print(f"  - {path}: port/out 구현체는 *Adapter·*Client·*Router·*Registry 중 하나로 명명")
    else:
        print("  위반 없음")

    print("\n## 2. 조사 필요")
    print("\n### 에러 코드 상태·노출 후보 (ErrorCode / toStatus / EXPOSED_CODES)")
    rows, error_metadata = check_error_exposure_details(root, scope)
    missing = [(code, in_switch, has_public_path)
               for code, in_switch, has_public_path in rows
               if not (in_switch and has_public_path)]
    if missing:
        investigate += len(missing)
        for code, in_switch, has_public_path in missing:
            flags = []
            if not in_switch:
                flags.append("toStatus 누락")
            if not has_public_path:
                flags.append(
                    "공개 경로 없음 — 비-400 상태는 500 마스킹 대상; API 계약 확인 필요"
                )
            print(f"  - {code}: {', '.join(flags)}")
        print("  ※ 의도적 비노출일 수 있다 — 현재 API 계약과 직접 관련된 이력을 함께 확인할 것")
    else:
        if error_metadata["complete_claim"]:
            print(f"  {len(rows)}개 application 코드에 상태·공개 경로가 있음")
        elif rows:
            print(f"  범위에서 추적된 {len(rows)}개 코드에 상태·공개 경로가 있음")
        else:
            print("  범위에서 추적된 application ErrorCode 없음")

    if error_metadata["followed"]:
        print(f"  ※ 직접 참조한 application 예외 {len(error_metadata['followed'])}개의 ErrorCode를 추적함")
    if error_metadata["unresolved"]:
        investigate += len(error_metadata["unresolved"])
        for exception_name in error_metadata["unresolved"]:
            print(f"  - {exception_name}: application 예외 소스를 찾지 못해 ErrorCode 확인 불가")
    if not error_metadata["complete_claim"]:
        print(
            "  ※ 범위 검사 한계: 직접 application ErrorCode 참조와 직접 참조한 "
            "application 예외만 추적한다. 공통 변환·동적 생성 경로는 수동 확인이 필요하다."
        )

    print("\n### 아웃바운드 *Client·*Router 역할")
    outbound_ambiguities = check_outbound_role_ambiguities(root, scope)
    if outbound_ambiguities:
        investigate += len(outbound_ambiguities)
        for path, why in outbound_ambiguities:
            print(f"  - {path}: {why}")
    else:
        print("  조사 필요 없음")

    print("\n### 사용되지 않는 예외 클래스")
    dead = check_dead_exceptions(root, scope)
    if dead:
        investigate += len(dead)
        for path in dead:
            print(f"  - {path}")
    else:
        print("  없음")

    print("\n## 3. 휴리스틱 의심")
    print("\n### port/out 메서드 2개 이상")
    if multi_method:
        for path, methods in multi_method:
            print(f"  - {path}: {len(methods)}개 — {', '.join(methods)}")
        print("  ※ 하나의 역할로 응집됐으면 정상 — 변경 이유가 다르면 분리 검토")
    else:
        print("  없음")

    print("\n### 트랜잭션 경계가 없는 기능 패키지")
    if no_tx:
        for path in no_tx:
            print(f"  - {path}")
        print("  ※ Redis 전용 등 DB를 쓰지 않으면 정상")
    else:
        print("  없음")

    print("\n### DTO 단위 접미사")
    units = check_unit_suffix(root, scope)
    if units:
        for path, field, expected in units:
            print(f"  - {path}: `{field}` → `...{expected}` 필요?")
    else:
        print("  의심 필드 없음")

    print(
        f"\n---\n확정 위반 {confirmed}건 / 조사 필요 {investigate}건 / "
        f"휴리스틱 의심 {len(multi_method) + len(no_tx) + len(units)}건"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
