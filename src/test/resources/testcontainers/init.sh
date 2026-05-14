#!/bin/bash

set -e

## CONFIGURATION ##
VERBOSE=true
AWS_PROFILE="default"
AWS_REGION="us-east-1"
LOCALSTACK_ENDPOINT="http://localhost:4566"

## DEFINITIONS ##
DYNAMODB_TABLES=(
  "pn-IOConnectorRequests:requestId"
)

## LOGGING FUNCTIONS ##
log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }

silent() {
  if [ "$VERBOSE" = false ]; then
    "$@" > /dev/null 2>&1
  else
    "$@"
  fi
}

## FUNCTIONS ##
create_dynamodb_table() {
  local table_name=$1
  local pk=$2

  log "Creating DynamoDB table: $table_name"
  if ! silent aws dynamodb describe-table --table-name "$table_name" --profile "$AWS_PROFILE" --region "$AWS_REGION" --endpoint-url "$LOCALSTACK_ENDPOINT" ; then
    if ! aws dynamodb create-table \
      --profile "$AWS_PROFILE" \
      --region "$AWS_REGION" \
      --endpoint-url "$LOCALSTACK_ENDPOINT" \
      --table-name "$table_name" \
      --attribute-definitions AttributeName="$pk",AttributeType=S \
      --key-schema AttributeName="$pk",KeyType=HASH \
      --billing-mode PAY_PER_REQUEST ; then
      log "Failed to create table: $table_name"
      return 1
    else
      log "Table created: $table_name"
    fi
  else
    log "Table already exists: $table_name"
  fi
}

add_dynamodb_gsi() {
  local table_name=$1
  local gsi_name=$2
  local gsi_pk=$3

  log "Adding GSI '$gsi_name' to table: $table_name"

  if silent aws dynamodb describe-table \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --endpoint-url "$LOCALSTACK_ENDPOINT" \
    --table-name "$table_name" \
    --query "Table.GlobalSecondaryIndexes[?IndexName=='$gsi_name']" \
    --output text | grep -q "$gsi_name"; then
    log "GSI '$gsi_name' already exists on table: $table_name"
    return 0
  fi

  aws dynamodb update-table \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --endpoint-url "$LOCALSTACK_ENDPOINT" \
    --table-name "$table_name" \
    --attribute-definitions AttributeName="$gsi_pk",AttributeType=S \
    --global-secondary-index-updates '[
      {
        "Create": {
          "IndexName": "'"$gsi_name"'",
          "KeySchema": [
            { "AttributeName": "'"$gsi_pk"'", "KeyType": "HASH" }
          ],
          "Projection": { "ProjectionType": "ALL" }
        }
      }
    ]'
  log "GSI '$gsi_name' added to table: $table_name"
}

initialize_dynamo() {
  log "Initializing DynamoDB tables"
  local return_code=0

  for entry in "${DYNAMODB_TABLES[@]}"; do
    IFS=: read -r table_name pk <<< "$entry"
    silent create_dynamodb_table "$table_name" "$pk" && \
    log "Table initialized: $table_name" || \
    { log "Failed to initialize table: $table_name"; return_code=1; }
  done

  add_dynamodb_gsi "pn-IOConnectorRequests" "ioMessageIdIndex" "ioMessageId" || return_code=1

  return $return_code
}

main() {
  initialize_dynamo || { log "Failed to initialize DynamoDB"; exit 1; }
  log "Initialization completed successfully"
}

main
echo "Initialization terminated"
