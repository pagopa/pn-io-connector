"use strict";

const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, QueryCommand } = require('@aws-sdk/lib-dynamodb');

const ddbClient = process.env.AWS_DYNAMODB_ENDPOINT ? new DynamoDBClient({
    region: process.env.AWS_REGION,
    endpoint: process.env.AWS_DYNAMODB_ENDPOINT,
    credentials: {
      accessKeyId: process.env.AWS_ACCESS_KEY_ID || 'test',
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || 'test'
    }
  }) : new DynamoDBClient({});

const client = DynamoDBDocumentClient.from(ddbClient);

async function findByRequestIdAndRecipientTaxId(requestId, recipientTaxId) {
  const result = await client.send(new QueryCommand({
    TableName: process.env.REQUESTS_TABLE_NAME,
    KeyConditionExpression: 'requestId = :v',
    FilterExpression: 'recipientTaxId = :cf',
    ExpressionAttributeValues: { ':v': requestId, ':cf': recipientTaxId },
    Limit: 1
  }));
  return (result.Items && result.Items.length > 0) ? result.Items[0] : null;
}

module.exports = { findByRequestIdAndRecipientTaxId };
