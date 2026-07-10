#!/bin/sh
# Normalizes DATABASE_URL provided by Railway/Neon (postgres:// or postgresql://
# with embedded user:pass) into a Spring-compatible jdbc:postgresql:// URL with
# separate username/password env vars, and ensures SSL is enabled for managed
# Postgres providers (Neon requires sslmode=require).
set -e

if [ -n "$DATABASE_URL" ]; then
  case "$DATABASE_URL" in
    jdbc:*)
      # Already a JDBC URL, leave as-is.
      export SPRING_DATASOURCE_URL="$DATABASE_URL"
      ;;
    postgres://*|postgresql://*)
      # Strip scheme
      NO_SCHEME=$(echo "$DATABASE_URL" | sed -E 's#^postgres(ql)?://##')
      # Extract userinfo (before last @)
      USERINFO=$(echo "$NO_SCHEME" | sed -E 's#^([^@]*)@.*#\1#')
      HOSTPART=$(echo "$NO_SCHEME" | sed -E 's#^[^@]*@(.*)#\1#')
      if echo "$USERINFO" | grep -q ':'; then
        DB_USER=$(echo "$USERINFO" | cut -d':' -f1)
        DB_PASS=$(echo "$USERINFO" | cut -d':' -f2-)
      else
        DB_USER="$USERINFO"
        DB_PASS=""
      fi
      export DATABASE_USERNAME="${DATABASE_USERNAME:-$DB_USER}"
      export DATABASE_PASSWORD="${DATABASE_PASSWORD:-$DB_PASS}"

      # Split host/db/query
      HOST_DB=$(echo "$HOSTPART" | cut -d'?' -f1)
      QUERY=$(echo "$HOSTPART" | grep -q '?' && echo "$HOSTPART" | cut -d'?' -f2- || echo "")

      JDBC_URL="jdbc:postgresql://${HOST_DB}"
      if [ -n "$QUERY" ]; then
        JDBC_URL="${JDBC_URL}?${QUERY}"
      fi
      # Ensure sslmode is present (required by Neon, harmless on Railway/local)
      case "$JDBC_URL" in
        *sslmode=*) : ;;
        *\?*) JDBC_URL="${JDBC_URL}&sslmode=require" ;;
        *) JDBC_URL="${JDBC_URL}?sslmode=require" ;;
      esac
      export SPRING_DATASOURCE_URL="$JDBC_URL"
      ;;
    *)
      export SPRING_DATASOURCE_URL="$DATABASE_URL"
      ;;
  esac
fi

exec java -jar app.jar
