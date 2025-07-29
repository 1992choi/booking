# Stock

## 1. DB
- MySQL 설정
  - 이미지 다운로드
    - docker pull mysql:8.0.38
  - 이미지 조회
    - docker images
  - 실행
    - docker run --name stock-mysql -e MYSQL_ROOT_PASSWORD=root -d -p 3306:3306 mysql:8.0.38
  - 컨테이너 접속
    - docker exec -it stock-mysql bash
  - 데이터베이스 접속
    - mysql -u root -p
      - 위 명령어 입력한 이후 패스워드로 'root' 입력
