#!/usr/bin/env python3
"""
Imports the JSON question-bank files in this folder into the SkillForge
Postgres database (sf_subjects / sf_questions / sf_question_versions).

Usage:
    pip install psycopg2-binary --break-system-packages
    export DATABASE_URL="postgresql://user:pass@host:5432/skillforge"
    python3 import_question_bank.py --organization-id <uuid> [--dry-run]

Safe to re-run: uses (organization_id, code) uniqueness on sf_questions
to skip anything already imported, so partial imports or repeated runs
never create duplicates.
"""
import argparse
import glob
import json
import os
import sys
import uuid

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--organization-id", required=True, help="UUID of the org these questions belong to")
    parser.add_argument("--created-by", default=None, help="UUID of the trainer/admin user creating these (optional)")
    parser.add_argument("--dry-run", action="store_true", help="Print what would be inserted without writing to the DB")
    parser.add_argument("--dir", default=os.path.dirname(os.path.abspath(__file__)), help="Folder containing the *.json question files")
    args = parser.parse_args()

    files = sorted(glob.glob(os.path.join(args.dir, "*.json")))
    files = [f for f in files if not f.endswith("schema.json")]
    if not files:
        print("No question-bank JSON files found.", file=sys.stderr)
        sys.exit(1)

    all_questions = []
    for f in files:
        with open(f) as fh:
            all_questions.extend(json.load(fh))

    print(f"Loaded {len(all_questions)} questions from {len(files)} files.")

    if args.dry_run:
        subjects = sorted(set(q["subjectSlug"] for q in all_questions))
        print("Subjects found:", subjects)
        print("Dry run — no database writes performed.")
        return

    import psycopg2
    import psycopg2.extras

    conn = psycopg2.connect(os.environ["DATABASE_URL"])
    conn.autocommit = False
    cur = conn.cursor()

    inserted, skipped = 0, 0
    try:
        for q in all_questions:
            # 1. Ensure the subject exists for this org (reuse global subjects seeded by V2 migration if present)
            cur.execute(
                "SELECT id FROM sf_subjects WHERE slug = %s AND (organization_id = %s OR organization_id IS NULL) LIMIT 1",
                (q["subjectSlug"], args.organization_id),
            )
            row = cur.fetchone()
            if row:
                subject_id = row[0]
            else:
                subject_id = str(uuid.uuid4())
                cur.execute(
                    "INSERT INTO sf_subjects (id, organization_id, name, slug, active) VALUES (%s, %s, %s, %s, true)",
                    (subject_id, args.organization_id, q["subjectName"], q["subjectSlug"]),
                )

            # 2. Skip if this question code already exists for the org (idempotent re-run)
            cur.execute(
                "SELECT id FROM sf_questions WHERE organization_id = %s AND code = %s",
                (args.organization_id, q["code"]),
            )
            if cur.fetchone():
                skipped += 1
                continue

            question_id = str(uuid.uuid4())
            version_id = str(uuid.uuid4())

            cur.execute(
                """
                INSERT INTO sf_questions
                    (id, organization_id, subject_id, code, type, difficulty, status,
                     tags, expected_time_seconds, default_marks, negative_marks,
                     usage_count, created_by, topic, sort_order, shuffle_group)
                VALUES (%s, %s, %s, %s, %s, %s, 'APPROVED',
                        %s, %s, %s, %s,
                        0, %s, %s, %s, %s)
                """,
                (
                    question_id, args.organization_id, subject_id, q["code"], q["type"], q["difficulty"],
                    json.dumps([q["topic"]]), q["expectedTimeSeconds"], q["defaultMarks"], q["negativeMarks"],
                    args.created_by, q["topic"], q["sortOrder"], q["shuffleGroup"],
                ),
            )

            cur.execute(
                """
                INSERT INTO sf_question_versions
                    (id, question_id, version_number, title, prompt, options,
                     correct_answer, explanation, reference_links, scoring, created_by)
                VALUES (%s, %s, 1, %s, %s, %s, %s, %s, '[]', '{}', %s)
                """,
                (
                    version_id, question_id, q["title"], q["prompt"],
                    json.dumps(q["options"]), json.dumps(q["correctAnswer"]), q["explanation"],
                    args.created_by,
                ),
            )

            cur.execute("UPDATE sf_questions SET current_version_id = %s WHERE id = %s", (version_id, question_id))
            inserted += 1

        conn.commit()
        print(f"Done. Inserted {inserted} new questions, skipped {skipped} already-imported.")
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    main()
