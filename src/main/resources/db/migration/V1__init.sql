USE iseonjwa_test;

CREATE TABLE member
(
	member_id BIGINT AUTO_INCREMENT  NOT NULL ,
	email VARCHAR(255) NOT NULL ,
	name VARCHAR(100) NOT NULL ,
	phone VARCHAR(20) NULL ,
	role VARCHAR(20) NOT NULL ,
	created_at DATETIME NOT NULL ,
	password VARCHAR(255) NOT NULL ,
	 CONSTRAINT XPKMEMBER PRIMARY KEY  ( member_id ASC ),
	CONSTRAINT AK_MEMBER_EMAIL UNIQUE  (
	email ASC,
	created_at ASC )
);

CREATE TABLE performance
(
	performance_id BIGINT AUTO_INCREMENT  NOT NULL ,
	title VARCHAR(200) NOT NULL ,
	genre VARCHAR(20) NOT NULL ,
	running_time INT NOT NULL ,
	description TEXT NULL ,
	status VARCHAR(20) NOT NULL ,
	venue_name VARCHAR(200) NOT NULL ,
	max_tickets_per_member INT NOT NULL ,
	 CONSTRAINT XPKPERFORMANCE PRIMARY KEY  ( performance_id ASC )
);

CREATE TABLE schedule
(
	schedule_id BIGINT AUTO_INCREMENT  NOT NULL ,
	start_at DATETIME NOT NULL ,
	status VARCHAR(20) NOT NULL ,
	performance_id BIGINT NOT NULL ,
	 CONSTRAINT XPKSCHEDULE PRIMARY KEY  ( schedule_id ASC ),
	CONSTRAINT AK_SCHEDULE_START UNIQUE  (
	performance_id ASC,
	start_at ASC ),
	CONSTRAINT R_8 FOREIGN KEY (performance_id) REFERENCES performance (performance_id)
);

CREATE TABLE seat
(
	seat_id BIGINT AUTO_INCREMENT  NOT NULL ,
	section VARCHAR(20) NOT NULL ,
	row_num VARCHAR(10) NOT NULL ,
	seat_num INT NOT NULL ,
	seat_rank VARCHAR(20) NOT NULL ,
	status VARCHAR(20) NOT NULL ,
	held_until DATETIME NULL ,
	held_by BIGINT NULL ,
	schedule_id BIGINT NOT NULL ,
	 CONSTRAINT XPKSEAT PRIMARY KEY  ( seat_id ASC ),
	CONSTRAINT AK_SEAT_POSITION UNIQUE  (
	section ASC,
	row_num ASC,
	seat_num ASC,
	schedule_id ASC ),
	CONSTRAINT R_17 FOREIGN KEY (held_by) REFERENCES member (member_id),
	CONSTRAINT R_18 FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id)
);

CREATE TABLE reservation
(
	reservation_id BIGINT AUTO_INCREMENT  NOT NULL ,
	status VARCHAR(20) NOT NULL ,
	reserved_at DATETIME NOT NULL ,
	canceled_at DATETIME NULL ,
	member_id BIGINT NOT NULL ,
	schedule_id BIGINT NOT NULL ,
	seat_id BIGINT NOT NULL ,
	 CONSTRAINT XPKRESERVATION PRIMARY KEY  ( reservation_id ASC ),
	CONSTRAINT R_12 FOREIGN KEY (member_id) REFERENCES member (member_id),
	CONSTRAINT R_13 FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id),
	CONSTRAINT R_19 FOREIGN KEY (seat_id) REFERENCES seat (seat_id)
);

CREATE TABLE payment
(
	payment_id BIGINT AUTO_INCREMENT  NOT NULL ,
	paid_amount INT NOT NULL ,
	cancel_fee INT  DEFAULT 0 NOT NULL ,
	refund_amount INT  DEFAULT 0 NOT NULL ,
	status VARCHAR(20) NOT NULL ,
	paid_at DATETIME NULL ,
	refunded_at DATETIME NULL ,
	reservation_id BIGINT NOT NULL ,
	 CONSTRAINT XPKPAYMENT PRIMARY KEY  ( payment_id ASC ),
	CONSTRAINT AK_PAYMENT_RESERVATION UNIQUE  (
	reservation_id ASC ),
	CONSTRAINT R_16 FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id)
);

CREATE TABLE price
(
	price INT NOT NULL ,
	performance_id BIGINT NOT NULL ,
	seat_rank VARCHAR(20) NOT NULL ,
	 CONSTRAINT XPKPRICE PRIMARY KEY  ( performance_id ASC, seat_rank ASC ),
	CONSTRAINT R_6 FOREIGN KEY (performance_id) REFERENCES performance (performance_id)
);
