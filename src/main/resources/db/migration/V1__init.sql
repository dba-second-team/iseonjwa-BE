
CREATE TABLE member
(
	member_id BIGINT AUTO_INCREMENT  NOT NULL ,
	email VARCHAR(255) NOT NULL ,
	name VARCHAR(100) NOT NULL ,
	phone VARCHAR(20) NULL ,
	role VARCHAR(20) NOT NULL ,
	created_at DATETIME NOT NULL ,
	password VARCHAR(255) NOT NULL ,
	 CONSTRAINT XPKMEMBER PRIMARY KEY  ( member_id ASC )
);

ALTER TABLE member
	ADD CONSTRAINT AK_MEMBER_EMAIL UNIQUE  (
	email ASC );

CREATE TABLE payment
(
	payment_id BIGINT AUTO_INCREMENT  NOT NULL ,
	payment_method VARCHAR(20) NOT NULL ,
	paid_amount INT NOT NULL ,
	cancel_fee INT  DEFAULT 0 NOT NULL ,
	refund_amount INT  DEFAULT 0 NOT NULL ,
	status VARCHAR(20) NOT NULL ,
	paid_at DATETIME NOT NULL ,
	refunded_at DATETIME NULL ,
	reservation_id BIGINT NOT NULL ,
	 CONSTRAINT XPKPAYMENT PRIMARY KEY  ( payment_id ASC )
);

ALTER TABLE payment
	ADD CONSTRAINT AK_PAYMENT_RESERVATION UNIQUE  (
	reservation_id ASC );

CREATE TABLE performance
(
	performance_id BIGINT AUTO_INCREMENT  NOT NULL ,
	title VARCHAR(200) NOT NULL ,
	genre VARCHAR(20) NOT NULL ,
	running_time INT NOT NULL ,
	description TEXT NULL ,
	status VARCHAR(20) NOT NULL ,
	venue_id BIGINT NOT NULL ,
	 CONSTRAINT XPKPERFORMANCE PRIMARY KEY  ( performance_id ASC )
);

CREATE TABLE performance_seat
(
	performance_seat_id BIGINT AUTO_INCREMENT  NOT NULL ,
	performance_id BIGINT NOT NULL ,
	seat_id BIGINT NOT NULL ,
	seat_rank_id BIGINT NOT NULL ,
	 CONSTRAINT XPKPERFORMANCE_SEAT PRIMARY KEY  ( performance_seat_id ASC )
);

ALTER TABLE performance_seat
	ADD CONSTRAINT AK_PERFORMANCE_SEAT UNIQUE  (
	performance_id ASC,
	seat_id ASC );

CREATE TABLE price
(
	price INT NOT NULL ,
	performance_id BIGINT NOT NULL ,
	seat_rank_id BIGINT NOT NULL ,
	 CONSTRAINT XPKPRICE PRIMARY KEY  ( performance_id ASC, seat_rank_id ASC )
);

CREATE TABLE reservation
(
	reservation_id BIGINT AUTO_INCREMENT  NOT NULL ,
	status VARCHAR(20) NOT NULL ,
	reserved_at DATETIME NOT NULL ,
	canceled_at DATETIME NULL ,
	member_id BIGINT NOT NULL ,
	schedule_id BIGINT NOT NULL ,
	 CONSTRAINT XPKRESERVATION PRIMARY KEY  ( reservation_id ASC )
);

CREATE TABLE reservation_seat
(
	reservation_seat_id BIGINT AUTO_INCREMENT  NOT NULL ,
	price INT NOT NULL ,
	reservation_id BIGINT NOT NULL ,
	scheduled_seat_id BIGINT NOT NULL ,
	 CONSTRAINT XPKRESERVATION_SEAT PRIMARY KEY  ( reservation_seat_id ASC )
);

CREATE TABLE schedule
(
	schedule_id BIGINT AUTO_INCREMENT  NOT NULL ,
	start_at DATETIME NOT NULL ,
	status VARCHAR(20) NOT NULL ,
	performance_id BIGINT NOT NULL ,
	 CONSTRAINT XPKSCHEDULE PRIMARY KEY  ( schedule_id ASC )
);

ALTER TABLE schedule
	ADD CONSTRAINT AK_SCHEDULE_START UNIQUE  (
	performance_id ASC,
	start_at ASC );

CREATE TABLE scheduled_seat
(
	scheduled_seat_id BIGINT AUTO_INCREMENT  NOT NULL ,
	status VARCHAR(20) NOT NULL ,
	held_until DATETIME NULL ,
	schedule_id BIGINT NOT NULL ,
	performance_seat_id BIGINT NOT NULL ,
	held_by BIGINT NULL ,
	 CONSTRAINT XPKSCHEDULED_SEAT PRIMARY KEY  ( scheduled_seat_id ASC )
);

ALTER TABLE scheduled_seat
	ADD CONSTRAINT AK_SCHEDULED_SEAT UNIQUE  (
	schedule_id ASC,
	performance_seat_id ASC );

CREATE TABLE seat
(
	seat_id BIGINT AUTO_INCREMENT  NOT NULL ,
	section VARCHAR(20) NOT NULL ,
	row_num VARCHAR(10) NOT NULL ,
	seat_num INT NOT NULL ,
	venue_id BIGINT NOT NULL ,
	 CONSTRAINT XPKSEAT PRIMARY KEY  ( seat_id ASC )
);

ALTER TABLE seat
	ADD CONSTRAINT AK_SEAT_POSITION UNIQUE  (
	venue_id ASC,
	section ASC,
	row_num ASC,
	seat_num ASC );

CREATE TABLE seat_rank
(
	seat_rank_id BIGINT AUTO_INCREMENT  NOT NULL ,
	grade VARCHAR(20) NOT NULL ,
	sort_order INT NOT NULL ,
	 CONSTRAINT XPKSEAT_RANK PRIMARY KEY  ( seat_rank_id ASC )
);

ALTER TABLE seat_rank
	ADD CONSTRAINT AK_SEAT_RANK_GRADE UNIQUE  (
	grade ASC );

CREATE TABLE venue
(
	venue_id BIGINT AUTO_INCREMENT  NOT NULL ,
	name VARCHAR(200) NOT NULL ,
	address VARCHAR(500) NOT NULL ,
	 CONSTRAINT XPKVENUE PRIMARY KEY  ( venue_id ASC )
);

ALTER TABLE payment
	ADD  CONSTRAINT R_16 FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE performance
	ADD  CONSTRAINT R_2 FOREIGN KEY (venue_id) REFERENCES venue (venue_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE performance_seat
	ADD  CONSTRAINT R_3 FOREIGN KEY (performance_id) REFERENCES performance (performance_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE performance_seat
	ADD  CONSTRAINT R_4 FOREIGN KEY (seat_id) REFERENCES seat (seat_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE performance_seat
	ADD  CONSTRAINT R_5 FOREIGN KEY (seat_rank_id) REFERENCES seat_rank (seat_rank_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE price
	ADD  CONSTRAINT R_6 FOREIGN KEY (performance_id) REFERENCES performance (performance_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE price
	ADD  CONSTRAINT R_7 FOREIGN KEY (seat_rank_id) REFERENCES seat_rank (seat_rank_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE reservation
	ADD  CONSTRAINT R_12 FOREIGN KEY (member_id) REFERENCES member (member_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE reservation
	ADD  CONSTRAINT R_13 FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE reservation_seat
	ADD  CONSTRAINT R_14 FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE reservation_seat
	ADD  CONSTRAINT R_15 FOREIGN KEY (scheduled_seat_id) REFERENCES scheduled_seat (scheduled_seat_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE schedule
	ADD  CONSTRAINT R_8 FOREIGN KEY (performance_id) REFERENCES performance (performance_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE scheduled_seat
	ADD  CONSTRAINT R_9 FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE scheduled_seat
	ADD  CONSTRAINT R_10 FOREIGN KEY (performance_seat_id) REFERENCES performance_seat (performance_seat_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE scheduled_seat
	ADD  CONSTRAINT R_11 FOREIGN KEY (held_by) REFERENCES member (member_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;

ALTER TABLE seat
	ADD  CONSTRAINT R_1 FOREIGN KEY (venue_id) REFERENCES venue (venue_id)
		ON DELETE NO ACTION 
		ON UPDATE NO ACTION ;
