# MariaDB + HeidiSQL setup

## 1. Cai MariaDB Server

- Cai MariaDB Server tren may.
- Ghi nho `root password` ban dat trong qua trinh cai.
- Mac dinh MariaDB chay o port `3306`.

## 2. Tao ket noi trong HeidiSQL

- Mo HeidiSQL.
- Bam `New`.
- Dat ten session, vi du: `ChatApp MariaDB`.
- `Network type`: chon `MariaDB or MySQL (TCP/IP)`.
- Dien cac truong:
  - `Hostname / IP`: `127.0.0.1`
  - `User`: `root`
  - `Password`: mat khau MariaDB cua ban
  - `Port`: `3306`
- Bam `Open`.

## 3. Tao database

Trong HeidiSQL, mo cua so query va chay:

```sql
CREATE DATABASE chatapp
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Sau do bam refresh. Ban se thay database `chatapp`.

## 4. Tao file .env cho chat app

Trong thu muc:

`Code/TCPChatApp`

tao file `.env` voi noi dung:

```env
DB_URL=jdbc:mariadb://127.0.0.1:3306/chatapp
DB_USER=root
DB_PASSWORD=your_password_here
```

Neu ban da co file `.env` cu dung `SUPABASE_DB_*` thi co the doi sang ten moi. Code van doc duoc ten cu de tranh vo app, nhung nen dung `DB_*` tu nay.

## 5. Chay app

- Khi server chat khoi dong, no se tu tao bang neu database ket noi thanh cong.
- Cac bang du kien se duoc tao:
  - `users`
  - `messages`
  - `rooms`
  - `room_members`
  - `private_conversations`

## 6. Kiem tra nhanh trong HeidiSQL

Sau khi mo server:

```sql
SHOW TABLES;
SELECT * FROM users;
SELECT * FROM messages;
```

## 7. Loi hay gap

- `Access denied for user`: sai user hoac password.
- `Unknown database 'chatapp'`: chua tao database.
- `Connection refused`: MariaDB Server chua chay.
- App van chay nhung khong luu du lieu: kiem tra log server, neu thay canh bao `Database config is not fully configured` thi file `.env` chua dung.
