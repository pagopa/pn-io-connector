"use strict";

const { expect } = require('chai');
const { mockClient } = require('aws-sdk-client-mock');
const { DynamoDBDocumentClient, QueryCommand } = require('@aws-sdk/lib-dynamodb');

const FIXTURE = require('./fixtures/dynamo-record.json');

process.env.REGION = 'eu-south-1';
process.env.REQUESTS_TABLE_NAME = 'pn-IOConnectorRequests';

const { findByRequestIdAndRecipientTaxId } = require('../app/lib/dynamoDbClient');

const REQUEST_ID = 'req-001';
const FISCAL_CODE = 'RSSMRA80A01H501T';

const ddbMock = mockClient(DynamoDBDocumentClient);

describe('dynamoDbClient', () => {
  beforeEach(() => {
    ddbMock.reset();
  });

  it('should return the entity when requestId and fiscalCode match', async () => {
    ddbMock.on(QueryCommand).resolves({ Items: [FIXTURE] });
    const result = await findByRequestIdAndRecipientTaxId(REQUEST_ID, FISCAL_CODE);
    expect(result).to.deep.equal(FIXTURE);
  });

  it('should return null when no items are returned', async () => {
    ddbMock.on(QueryCommand).resolves({ Items: [] });
    const result = await findByRequestIdAndRecipientTaxId('non-existent', FISCAL_CODE);
    expect(result).to.be.null;
  });

  it('should return null when Items is undefined', async () => {
    ddbMock.on(QueryCommand).resolves({});
    const result = await findByRequestIdAndRecipientTaxId('non-existent', FISCAL_CODE);
    expect(result).to.be.null;
  });

  it('should query with correct table name, requestId and fiscalCode', async () => {
    ddbMock.on(QueryCommand).resolves({ Items: [FIXTURE] });
    await findByRequestIdAndRecipientTaxId(REQUEST_ID, FISCAL_CODE);
    const calls = ddbMock.commandCalls(QueryCommand);
    expect(calls).to.have.length(1);
    const input = calls[0].args[0].input;
    expect(input.TableName).to.equal('pn-IOConnectorRequests');
    expect(input.IndexName).to.be.undefined;
    expect(input.KeyConditionExpression).to.equal('requestId = :v');
    expect(input.ExpressionAttributeValues[':v']).to.equal(REQUEST_ID);
    expect(input.FilterExpression).to.equal('recipientTaxId = :cf');
    expect(input.ExpressionAttributeValues[':cf']).to.equal(FISCAL_CODE);
  });

  it('should propagate DynamoDB errors', async () => {
    ddbMock.on(QueryCommand).rejects(new Error('DynamoDB error'));
    try {
      await findByRequestIdAndRecipientTaxId(REQUEST_ID, FISCAL_CODE);
      expect.fail('Should have thrown');
    } catch (err) {
      expect(err.message).to.equal('DynamoDB error');
    }
  });
});
