/**
 * Script per invocare la Lambda in locale con un evento di test.
 * Prerequisiti:
 *   1. Avere DynamoDB, SafeStorage e DataVault raggiungibili (es. LocalStack)
 *   2. npm install
 *
 * Uso:
 *   node local-dev.js [requestId] [fileKey] [fiscalCode]
 *
 * Esempio:
 *   node local-dev.js ABCD-EFGH-1234-5678-X PN_AAR-1000078fe1c147c2966a947e0dba5cc5.pdf ABCDEFG1234567890
 */
require('dotenv').config({ path: `${__dirname}/localdev.env` });

const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, PutCommand } = require('@aws-sdk/lib-dynamodb');
const { handler } = require('./index');

const requestId = process.argv[2] || 'io-msg-abc123';
const fileKey       = process.argv[3] || 'attach-001';
const fiscalCode    = process.argv[4] || 'PF-12c09d1a-94de-4e0a-82e6-b7637ae49c62';

const event = {
  pathParameters: { id: requestId, url: fileKey },
  headers: {'x-pagopa-cx-taxid': fiscalCode}
};

async function seedDynamo(fiscalCode) {
  const ddbClient = new DynamoDBClient({
    region: process.env.AWS_REGION,
    endpoint: process.env.AWS_DYNAMODB_ENDPOINT,
    credentials: {
      accessKeyId: process.env.AWS_ACCESS_KEY_ID || 'test',
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || 'test'
    }
  });
  const docClient = DynamoDBDocumentClient.from(ddbClient);
  const record = {
    requestId: requestId,
    recipientTaxId: fiscalCode,
    xPagopaIoConCxId: 'pn-delivery-push',
    iun: 'ABCD-EFGH-1234-5678-X',
    ioMessageId: 'io-msg-test',
    attachments: [{ id: 'attach-001', fileKey }],
    status: 'SENT_TO_IO',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };
  await docClient.send(new PutCommand({
    TableName: process.env.REQUESTS_TABLE_NAME,
    Item: record
  }));
  console.log('DynamoDB seed record inserted:', JSON.stringify(record, null, 2));
}

async function main() {
  console.log('Fetching internalId from DataVault for fiscalCode:', fiscalCode);
  // const internalId = await getInternalId(fiscalCode);
  // console.log('InternalId obtained:', internalId);

  await seedDynamo(fiscalCode);

  console.log('\nInvoking Lambda with event:', JSON.stringify(event, null, 2));
  const result = await handler(event);
  console.log('\nResponse:');
  console.log(JSON.stringify(result, null, 2));
}

main().catch(err => {
  console.error('\nUnhandled error:', err);
  process.exit(1);
});
