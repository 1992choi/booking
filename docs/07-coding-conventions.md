# 코딩 컨벤션

## 개요

이 프로젝트의 코드 가독성을 위한 포매팅 규칙입니다.
**"관심사(concern)가 달라지는 경계에 빈 줄 하나"** 가 핵심 원칙입니다.

---

## 규칙 1: 클래스 닫는 `}` 앞 빈 줄

클래스 선언부는 열리는 `{` 아래에 빈 줄이 있으므로, 닫히는 `}` 위에도 빈 줄을 둡니다.

```java
// ✓ 올바른 예
public class FooService {

    private final FooRepository fooRepository;

    public Foo getById(Long id) {
        return fooRepository.findById(id).orElseThrow(...);
    }

}

// ✗ 잘못된 예 — 마지막 메서드 `}` 와 클래스 `}` 가 붙어있음
public class FooService {

    public Foo getById(Long id) {
        return fooRepository.findById(id).orElseThrow(...);
    }
}
```

---

## 규칙 2: `return` 앞 빈 줄

`return` 은 메서드 마무리에서 결과를 반환하는 독립된 관심사입니다.
앞에 다른 코드가 있으면 반드시 빈 줄로 분리합니다.

```java
// ✓ 올바른 예
public UserResponse getMe(Long userId) {
    User user = userService.getById(userId);

    return UserResponse.from(user);
}

// ✓ 메서드 전체가 단일 return 인 경우 — 빈 줄 불필요
public UserResponse getMe(Long userId) {
    return UserResponse.from(userService.getById(userId));
}

// ✗ 잘못된 예
public UserResponse getMe(Long userId) {
    User user = userService.getById(userId);
    return UserResponse.from(user);
}
```

---

## 규칙 3: 관심사 경계마다 빈 줄

메서드 바디 안에서 **관심사(concern)가 바뀌는 순간** 빈 줄을 하나 추가합니다.

### 전형적인 관심사 순서

| 관심사 | 예시 코드 |
|--------|----------|
| **조회 / 로드** | `repo.findById(...)`, `service.getById(...)` |
| **검증 / 가드** | `if (...) throw ...`, 권한 체크 |
| **핵심 로직 수행** | `entity.update(...)`, `repo.save(...)`, `service.process(...)` |
| **이벤트 발행 / 로그** | `eventPublisher.publishEvent(...)`, `kafkaTemplate.send(...)`, `log.info(...)` |
| **반환** | `return ...` |

### 예시: Service 메서드

```java
// ✓
public Merchant update(Long userId, Long merchantId, MerchantUpdateRequest request) {
    Merchant merchant = merchantRepository.findById(merchantId)
            .orElseThrow(() -> new BusinessException(ReservationErrorCode.MERCHANT_NOT_FOUND));
    if (!merchant.getUserId().equals(userId)) {
        throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    merchant.update(request.name(), request.phone(), request.type());
    log.info("업체 수정 merchantId={}, userId={}", merchantId, userId);

    return merchant;
}
```

### 예시: Controller 메서드 (두 단계가 필요할 때)

```java
// ✓
public ResourceResponse register(AuthPrincipal principal, Long merchantId, ResourceCreateRequest request) {
    Resource resource = resourceService.register(principal.userId(), merchantId, request);

    return ResourceResponse.from(resource);
}
```

### 예시: Kafka Consumer

Kafka 메시지를 처리할 때 **역직렬화**와 **서비스 호출**을 반드시 분리합니다.

```java
// ✓
public void onUserCreated(String message) {
    try {
        UserCreatedKafkaEvent event = objectMapper.readValue(message, UserCreatedKafkaEvent.class);

        userSyncRepository.save(UserSync.builder()...build());
        log.info("유저 동기화 완료 userId={}", event.userId());
    } catch (Exception e) {
        log.error("user.created 처리 실패: {}", message, e);
    }
}
```

### 예시: 이벤트 퍼블리셔 (try-catch 후 후속 작업)

```java
// ✓
public void onReservationCreated(ReservationCreatedDomainEvent event) {
    try {
        // 슬롯 상태 변경 (부가 작업)
        ...
    } catch (Exception e) {
        log.error(...);
    }

    kafkaTemplate.send("reservation.created", ...);
    log.info("reservation.created 발행 ...");
}
```

---

## 단일 관심사는 묶어 둔다

한 관심사 안에 속하는 여러 줄은 빈 줄 없이 붙여 씁니다.
빈 줄은 "여기서 무언가가 바뀐다"는 시각적 신호이므로, 남발하면 의미를 잃습니다.

```java
// ✓ — update 는 한 관심사
public void update(String name, String phone) {
    this.name = name;
    this.phone = phone;
}

// ✗ — 같은 관심사를 불필요하게 쪼갬
public void update(String name, String phone) {
    this.name = name;

    this.phone = phone;
}
```