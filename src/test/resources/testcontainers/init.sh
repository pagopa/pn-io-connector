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
SECRETS_NAME="Pn-IO-Connector-Secrets"

SQS_QUEUES=(
  "pn-io-connector-send-queue"
  "pn-io-connector-polling-queue"
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

create_secret() {
  local secret_name=$1
  local secret_value=$2

  log "Creating secret: $secret_name"
  if ! silent aws secretsmanager describe-secret \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --endpoint-url "$LOCALSTACK_ENDPOINT" \
    --secret-id "$secret_name" ; then
    aws secretsmanager create-secret \
      --profile "$AWS_PROFILE" \
      --region "$AWS_REGION" \
      --endpoint-url "$LOCALSTACK_ENDPOINT" \
      --name "$secret_name" \
      --secret-string "$secret_value"
    log "Secret created: $secret_name"
  else
    log "Secret already exists: $secret_name"
  fi
}

initialize_secrets() {
  log "Initializing Secrets Manager"
  create_secret "$SECRETS_NAME" '{"io-api-key":"test-api-key"}' || return 1
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

create_sqs_queue() {
  local queue_name=$1

  log "Creating SQS queue: $queue_name"
  if ! aws sqs create-queue \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --endpoint-url "$LOCALSTACK_ENDPOINT" \
    --queue-name "$queue_name" ; then
    log "Failed to create queue: $queue_name"
    return 1
  else
    log "Queue created: $queue_name"
  fi
}

initialize_sqs() {
  log "Initializing SQS queues"
  local return_code=0

  for queue_name in "${SQS_QUEUES[@]}"; do
    create_sqs_queue "$queue_name" || { log "Failed to initialize queue: $queue_name"; return_code=1; }
  done

  return $return_code
}

main() {
  initialize_dynamo || { log "Failed to initialize DynamoDB"; exit 1; }
  initialize_secrets || { log "Failed to initialize Secrets Manager"; exit 1; }
  initialize_sqs || { log "Failed to initialize SQS"; exit 1; }
  log "Initialization completed successfully"
}

main
echo "Initialization terminated"
