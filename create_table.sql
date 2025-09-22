CREATE TABLE "users" (
  "user_id" varchar[20] PRIMARY KEY,
  "pw" password[20] NOT NULL,
  "username" varchar[30] UNIQUE NOT NULL,
  "created_at" timestamp
);

CREATE TABLE "trashcan" (
  "trashcan_id" integer PRIMARY KEY,
  "category" char NOT NULL,
  "body" text,
  "user_id" integer NOT NULL,
  "created_at" timestamp,
  "geom" "GEOMETRY(Point,4326)" NOT NULL,
  "img" varchar
);

CREATE TABLE "report" (
  "report_id" integer PRIMARY KEY,
  "trashcan_id" integer NOT NULL,
  "user_id" varchar NOT NULL,
  "created_at" timestamp
);

CREATE TABLE "opinion" (
  "opinion_id" integer PRIMARY KEY,
  "comment" varchar NOT NULL,
  "trashcan_id" integer NOT NULL,
  "user_id" varchar NOT NULL,
  "created_at" timestamp
);

COMMENT ON COLUMN "trashcan"."body" IS 'Content of the post';

ALTER TABLE "trashcan" ADD CONSTRAINT "user_posts" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");

ALTER TABLE "opinion" ADD FOREIGN KEY ("trashcan_id") REFERENCES "trashcan" ("trashcan_id");

ALTER TABLE "report" ADD FOREIGN KEY ("trashcan_id") REFERENCES "trashcan" ("trashcan_id");
