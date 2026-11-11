-- 초기 스키마 (#284)
-- 2026-11-07 dev DB(growingpots_dev) 구조를 mysqldump --no-data로 덤프해 만들었다. dev는 이 버전에서 시작하는 것으로
-- 표시(baseline)하고, 빈 DB(local, CI, 새로 만드는 prod)에서는 이 파일부터 실행한다.
-- 이후 스키마 변경은 이 파일을 고치지 말고 V2부터 새 마이그레이션으로 추가한다.

-- 테이블을 이름순으로 만들면서 외래 키도 같이 걸어서, 아직 없는 테이블을 참조하는 동안만 검사를 끈다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `cert_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cert_type` enum('ENGLISH','GRADUATION_CERT','SW','THESIS','TOPIK') NOT NULL,
  `result` enum('EXEMPT','FAIL','NONE','PASS') NOT NULL,
  `source` enum('MANUAL','PDF') NOT NULL,
  `student_major_id` bigint NOT NULL,
  `student_profile_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhtewr5v461shlp90i87w7werc` (`student_major_id`),
  KEY `FK36e5xm2392tfe6erkelycpf6r` (`student_profile_id`),
  CONSTRAINT `FK36e5xm2392tfe6erkelycpf6r` FOREIGN KEY (`student_profile_id`) REFERENCES `student_profile` (`id`),
  CONSTRAINT `FKhtewr5v461shlp90i87w7werc` FOREIGN KEY (`student_major_id`) REFERENCES `student_major` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `course_code` varchar(255) NOT NULL,
  `credit` int NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `is_english` bit(1) NOT NULL,
  `is_sw` bit(1) NOT NULL,
  `name` varchar(255) NOT NULL,
  `opened_semester` enum('BOTH','FIRST','SECOND') DEFAULT NULL,
  `recommended_year_high` int DEFAULT NULL,
  `recommended_year_low` int DEFAULT NULL,
  `default_division_id` bigint DEFAULT NULL,
  `ge_area_id` bigint DEFAULT NULL,
  `offering_department_id` bigint DEFAULT NULL,
  `school_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKn4v83ybx42b8da5jccq5ipfrn` (`default_division_id`),
  KEY `FKmahljfr5uwqr1g39bysmp9jfe` (`ge_area_id`),
  KEY `FKj11o8sfeepfnhxx6bi1v2kigb` (`offering_department_id`),
  KEY `FKqv2rlwmnu9rsh1siurb2f6923` (`school_id`),
  CONSTRAINT `FKj11o8sfeepfnhxx6bi1v2kigb` FOREIGN KEY (`offering_department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FKmahljfr5uwqr1g39bysmp9jfe` FOREIGN KEY (`ge_area_id`) REFERENCES `ge_area` (`id`),
  CONSTRAINT `FKn4v83ybx42b8da5jccq5ipfrn` FOREIGN KEY (`default_division_id`) REFERENCES `division` (`id`),
  CONSTRAINT `FKqv2rlwmnu9rsh1siurb2f6923` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `course_prerequisite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `prerequisite_type` enum('RECOMMENDED','REQUIRED') NOT NULL,
  `course_id` bigint NOT NULL,
  `department_id` bigint DEFAULT NULL,
  `required_course_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK45vm6hef5h141uiu8qeod3wcg` (`course_id`),
  KEY `FKq16rcxnkf52w8gdw1wm08du1d` (`department_id`),
  KEY `FKq57wfhs4ocj7ie3jxv7i1jebn` (`required_course_id`),
  CONSTRAINT `FK45vm6hef5h141uiu8qeod3wcg` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `FKq16rcxnkf52w8gdw1wm08du1d` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FKq57wfhs4ocj7ie3jxv7i1jebn` FOREIGN KEY (`required_course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cross_major_recognized_course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `course_id` bigint NOT NULL,
  `recognized_division_id` bigint NOT NULL,
  `target_department_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdowx1if70ofspt1f8soel9535` (`course_id`),
  KEY `FKk0jal6rfp00kwlqlpixabekph` (`recognized_division_id`),
  KEY `FKsfysykpdyidxccq5e0mdjhtx6` (`target_department_id`),
  CONSTRAINT `FKdowx1if70ofspt1f8soel9535` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `FKk0jal6rfp00kwlqlpixabekph` FOREIGN KEY (`recognized_division_id`) REFERENCES `division` (`id`),
  CONSTRAINT `FKsfysykpdyidxccq5e0mdjhtx6` FOREIGN KEY (`target_department_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `college` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `school_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkdr2ypoepvujtbttl2xqnajls` (`school_id`),
  CONSTRAINT `FKkdr2ypoepvujtbttl2xqnajls` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `division` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `category` enum('DISTRIBUTED_GE','FREE_GE','GENERAL_ELECTIVE','MAJOR_BASIC','MAJOR_ELECTIVE','MAJOR_REQUIRED','REQUIRED_GE') NOT NULL,
  `code` varchar(255) NOT NULL,
  `school_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlqacqby64xemmmuo2uk8gfsve` (`school_id`),
  CONSTRAINT `FKlqacqby64xemmmuo2uk8gfsve` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ge_area` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `code` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `school_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKchr07vahw4luh1x3bd0luasrl` (`school_id`),
  CONSTRAINT `FKchr07vahw4luh1x3bd0luasrl` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `graduation_analysis_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `distributed_ge_current` int DEFAULT NULL,
  `distributed_ge_required` int DEFAULT NULL,
  `english_current` int NOT NULL,
  `english_required` int NOT NULL,
  `free_ge_current` int DEFAULT NULL,
  `free_ge_required` int DEFAULT NULL,
  `general_elective_current` int NOT NULL,
  `gpa_current` decimal(5,3) DEFAULT NULL,
  `gpa_required` decimal(5,3) DEFAULT NULL,
  `major_basic_current` int NOT NULL,
  `major_basic_required` int NOT NULL,
  `major_elective_current` int NOT NULL,
  `major_elective_required` int NOT NULL,
  `major_required_current` int NOT NULL,
  `major_required_required` int NOT NULL,
  `required_ge_current` int DEFAULT NULL,
  `required_ge_required` int DEFAULT NULL,
  `required_plus_elective_current` int NOT NULL,
  `required_plus_elective_required` int NOT NULL,
  `sw_cert_current` int DEFAULT NULL,
  `sw_cert_required` int DEFAULT NULL,
  `total_credit_current` int NOT NULL,
  `total_credit_required` int NOT NULL,
  `student_major_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlh0hsg4ej0mbyin9j4lcwylku` (`student_major_id`),
  CONSTRAINT `FKsawxdgvx1acpqlcuy4qghd5jf` FOREIGN KEY (`student_major_id`) REFERENCES `student_major` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `nickname` varchar(255) NOT NULL,
  `oauth_id` varchar(255) NOT NULL,
  `oauth_provider` enum('KAKAO') NOT NULL,
  `refresh_token` varchar(512) DEFAULT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKq04eskgongimwb9ge43hyqp53` (`oauth_provider`,`oauth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `planner_simulation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(255) NOT NULL,
  `student_profile_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKnsf22gmdscuudtta5e07uelb7` (`student_profile_id`),
  CONSTRAINT `FKsx2o42bg5iqu18pbtvn1a9v41` FOREIGN KEY (`student_profile_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `planner_term` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `semester` int NOT NULL,
  `year_level` int NOT NULL,
  `planner_simulation_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlpm2amupgop4mw24ohpelcb59` (`planner_simulation_id`),
  CONSTRAINT `FKlpm2amupgop4mw24ohpelcb59` FOREIGN KEY (`planner_simulation_id`) REFERENCES `planner_simulation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `planner_term_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_selected` bit(1) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `version_no` int NOT NULL,
  `version_order` int NOT NULL,
  `planner_term_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5wg5x32xrcb5hmxpwcf2r7vdx` (`planner_term_id`),
  CONSTRAINT `FK5wg5x32xrcb5hmxpwcf2r7vdx` FOREIGN KEY (`planner_term_id`) REFERENCES `planner_term` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `planner_version_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `course_position_order` int NOT NULL,
  `credit` int NOT NULL,
  `course_id` bigint NOT NULL,
  `planned_division_id` bigint DEFAULT NULL,
  `planner_term_version_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqt8avmw49m3nsnsi3nxtdc26u` (`course_id`),
  KEY `FKl0yb8xmdxihuslcmfm17i6nqf` (`planned_division_id`),
  KEY `FKpm952s4wd8vwcat0ssepe8kaq` (`planner_term_version_id`),
  CONSTRAINT `FKl0yb8xmdxihuslcmfm17i6nqf` FOREIGN KEY (`planned_division_id`) REFERENCES `division` (`id`),
  CONSTRAINT `FKpm952s4wd8vwcat0ssepe8kaq` FOREIGN KEY (`planner_term_version_id`) REFERENCES `planner_term_version` (`id`),
  CONSTRAINT `FKqt8avmw49m3nsnsi3nxtdc26u` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `requirement_course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `base_year` int NOT NULL,
  `min_count` int NOT NULL,
  `min_credit` int NOT NULL,
  `name` varchar(255) NOT NULL,
  `department_id` bigint NOT NULL,
  `division_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK71i7h5x6rl8fwq1jrypod15i4` (`department_id`),
  KEY `FKqcc3y54i8ai9ypnjv2sm6wjjy` (`division_id`),
  CONSTRAINT `FK71i7h5x6rl8fwq1jrypod15i4` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FKqcc3y54i8ai9ypnjv2sm6wjjy` FOREIGN KEY (`division_id`) REFERENCES `division` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `requirement_course_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `course_id` bigint NOT NULL,
  `requirement_course_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoteesxio4ok0c04ircf1cnxaj` (`course_id`),
  KEY `FK2ca47hyskjj6pgd4k7vh51jf6` (`requirement_course_id`),
  CONSTRAINT `FK2ca47hyskjj6pgd4k7vh51jf6` FOREIGN KEY (`requirement_course_id`) REFERENCES `requirement_course` (`id`),
  CONSTRAINT `FKoteesxio4ok0c04ircf1cnxaj` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `school` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK251hwtk4rvkoblr76wknh8v41` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `credit` int NOT NULL,
  `is_retake` bit(1) NOT NULL,
  `raw_course_code` varchar(255) DEFAULT NULL,
  `raw_course_name` varchar(255) NOT NULL,
  `source` enum('MANUAL','PDF') NOT NULL,
  `status` enum('COMPLETED','IN_PROGRESS') NOT NULL,
  `taken_semester` enum('FIRST','SECOND','SUMMER','WINTER') DEFAULT NULL,
  `taken_year` int DEFAULT NULL,
  `applied_department_id` bigint DEFAULT NULL,
  `applied_division_id` bigint DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `student_profile_id` bigint NOT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FKkqn7f12gtfshwkci588a5dad6` (`applied_department_id`),
  KEY `FKpqswv4pu9y1w3r69nbsaehbe5` (`applied_division_id`),
  KEY `FKejrkh4gv8iqgmspsanaji90ws` (`course_id`),
  KEY `FKms2u0eeat8qte6t67xs6qel44` (`student_profile_id`),
  CONSTRAINT `FKejrkh4gv8iqgmspsanaji90ws` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `FKkqn7f12gtfshwkci588a5dad6` FOREIGN KEY (`applied_department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FKms2u0eeat8qte6t67xs6qel44` FOREIGN KEY (`student_profile_id`) REFERENCES `student_profile` (`id`),
  CONSTRAINT `FKpqswv4pu9y1w3r69nbsaehbe5` FOREIGN KEY (`applied_division_id`) REFERENCES `division` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_major` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `major_type` enum('DOUBLE','MAIN') NOT NULL,
  `department_id` bigint NOT NULL,
  `student_profile_id` bigint NOT NULL,
  `track_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmu6m9jne46wkfa9qbbjim1p5p` (`department_id`),
  KEY `FKjhfo6pyyu2hqq2u5hw99mjth0` (`student_profile_id`),
  KEY `FKpyapy0u64m3pyy7hl0bnrw7qk` (`track_id`),
  CONSTRAINT `FKjhfo6pyyu2hqq2u5hw99mjth0` FOREIGN KEY (`student_profile_id`) REFERENCES `student_profile` (`id`),
  CONSTRAINT `FKmu6m9jne46wkfa9qbbjim1p5p` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FKpyapy0u64m3pyy7hl0bnrw7qk` FOREIGN KEY (`track_id`) REFERENCES `track` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `admission_year` int NOT NULL,
  `current_grade` int DEFAULT NULL,
  `current_term` int DEFAULT NULL,
  `enrollment_status` varchar(255) DEFAULT NULL,
  `student_no` varchar(255) DEFAULT NULL,
  `department_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `school_id` bigint NOT NULL,
  `onboarding_confirmed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsc16sfn5271xtnv76kix5dmgw` (`member_id`),
  KEY `FKmtpoo92i2dm1t130l12ajtsri` (`department_id`),
  KEY `FKt6df3n7lbn7d1kmksxk7b0e2j` (`school_id`),
  CONSTRAINT `FKj18hmm9u1ftjjwadu5v3leael` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKmtpoo92i2dm1t130l12ajtsri` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FKt6df3n7lbn7d1kmksxk7b0e2j` FOREIGN KEY (`school_id`) REFERENCES `school` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `track` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(255) NOT NULL,
  `department_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoinwh0weqs749j3f1qf71uiyv` (`department_id`),
  CONSTRAINT `FKoinwh0weqs749j3f1qf71uiyv` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
